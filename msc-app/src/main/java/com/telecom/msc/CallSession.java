package com.telecom.msc;

import com.telecom.msc.dao.UserDAO;
import com.telecom.msc.model.User;
import com.telecom.msc.util.CDRWriter;
import com.telecom.msc.util.VoiceFileWriter;

import javax.sound.sampled.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Represents one active voice call session between a Mobile Phone application
 * and the MSC. Each session:
 *  - Reads signaling messages over the given TCP socket ("Start Call <MSISDN>" / "End Call")
 *  - Opens a dedicated UDP/RTP socket to receive voice packets for this call
 *  - Charges 1 L.E per minute from the user's balance while the call is active
 *  - Plays audio live at the speaker (single-call mode) OR records it to a .wav file
 *    (concurrent mode, when more than one call is active)
 *  - Writes a CDR line to /tmp/calls_CDR_yyyy_MM_dd_HH.cdr when the call ends
 *
 * Threading model:
 *  - run() (the TCP signaling reader) runs on the thread submitted by MSCServer's executor.
 *  - When "Start Call" is received, a SEPARATE audio thread is spawned to receive/play/record
 *    UDP traffic, so the signaling reader loop can keep listening for "End Call".
 */
public class CallSession implements Runnable {

    private static final BigDecimal CHARGE_PER_MINUTE = new BigDecimal("1.00");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Audio format used for both speaker playback and WAV recording.
    // 8kHz, 16-bit, mono PCM is a good match for voice.
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(8000.0f, 16, 1, true, false);

    private final Socket signalingSocket;
    private final MSCServer server; // back-reference, used to check concurrency mode

    private String msisdn;
    private DatagramSocket udpSocket;
    private volatile boolean running = true;
    private volatile boolean callStarted = false;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Timer billingTimer;
    private int elapsedMinutes = 0;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private BigDecimal balanceAfterCall = BigDecimal.ZERO;
    private String callResult = "Normal Call Clearing";

    private Thread audioThread;

    private final UserDAO userDAO = new UserDAO();

    public CallSession(Socket signalingSocket, MSCServer server) {
        this.signalingSocket = signalingSocket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            handleSignaling();
        } catch (Exception e) {
            System.err.println("Call session error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Reads "Start Call <MSISDN>" then "End Call" lines from the TCP signaling socket.
     * This loop keeps running (concurrently with the audio thread) so "End Call" can
     * be detected immediately.
     */
    private void handleSignaling() throws IOException, SQLException {
        try (java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(signalingSocket.getInputStream()))) {

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("Start Call")) {
                    String[] parts = line.split("\\s+");
                    msisdn = parts[parts.length - 1];
                    System.out.println("Accept Voice call start signaling message from MSISDN " + msisdn);
                    if (!startCall()) {
                        // User not found -> CDR already written, end this session
                        running = false;
                        break;
                    }

                } else if (line.equalsIgnoreCase("End Call")) {
                    System.out.println("Call End after receiving end call signaling message");
                    running = false;
                    break;
                }
            }
        }
    }

    /**
     * Validates the user, starts billing, and spawns a separate thread to receive
     * and play/record the UDP/RTP audio stream.
     *
     * @return true if the call was accepted and started, false if the user was not found
     *         (in which case a CDR is written immediately and the session ends).
     */
    private boolean startCall() throws SQLException, IOException {
        // Validate user
        User user = userDAO.findByMsisdn(msisdn);
        if (user == null) {
            callResult = "User not found on DB";
            startTime = LocalDateTime.now();
            endTime = startTime;
            balanceAfterCall = BigDecimal.ZERO;
            System.out.println("MSISDN " + msisdn + " not found in DB. Rejecting call.");
            writeCDR();
            return false;
        }

        startTime = LocalDateTime.now();
        callStarted = true;

        // Open a dedicated UDP socket (port 0 = pick any free port)
        udpSocket = new DatagramSocket(0);
        int assignedPort = udpSocket.getLocalPort();

        // Send the assigned UDP port back to the mobile app so it knows where to stream audio
        java.io.PrintWriter out = new java.io.PrintWriter(signalingSocket.getOutputStream(), true);
        out.println("UDP_PORT " + assignedPort);

        System.out.println("Capturing UDP traffic and play via speaker (port " + assignedPort + ") .....");

        // Start billing timer: every 60 seconds deduct 1 L.E
        billingTimer = new Timer(true);
        billingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    elapsedMinutes++;
                    BigDecimal newBalance = userDAO.deductBalance(msisdn, CHARGE_PER_MINUTE);
                    totalCost = totalCost.add(CHARGE_PER_MINUTE);
                    System.out.println("[Billing] " + msisdn + " charged 1 L.E. Minute "
                            + elapsedMinutes + ". New balance: " + newBalance);
                    balanceAfterCall = newBalance;
                } catch (SQLException e) {
                    System.err.println("Billing error: " + e.getMessage());
                }
            }
        }, 60_000, 60_000);

        // Start receiving + playing/recording audio on a separate thread so the
        // signaling reader loop remains free to detect "End Call"
        audioThread = new Thread(this::receiveAndPlayAudio, "AudioThread-" + msisdn);
        audioThread.start();

        return true;
    }

    /**
     * Receives UDP/RTP packets and either plays them live (single-call mode)
     * or buffers them for writing to a WAV file (concurrent mode).
     */
    private void receiveAndPlayAudio() {
        SourceDataLine speaker = null;
        VoiceFileWriter voiceFileWriter = null;
        boolean concurrentMode = server.isConcurrentMode();

        try {
            if (concurrentMode) {
                voiceFileWriter = new VoiceFileWriter(msisdn, AUDIO_FORMAT);
                System.out.println("Concurrent mode active: saving voice to file instead of speaker.");
            } else {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                speaker = (SourceDataLine) AudioSystem.getLine(info);
                speaker.open(AUDIO_FORMAT);
                speaker.start();
            }

            byte[] buffer = new byte[2048];
            udpSocket.setSoTimeout(2000); // allow periodic check of 'running' flag

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    int dataLength = packet.getLength();
                    int payloadOffset = 0;

                    // If using RTP, strip the 12-byte RTP header before processing the payload
                    if (server.isRtpMode() && dataLength > 12) {
                        payloadOffset = 12;
                        dataLength -= 12;
                    }

                    if (speaker != null) {
                        speaker.write(buffer, payloadOffset, dataLength);
                    } else if (voiceFileWriter != null) {
                        if (payloadOffset == 0) {
                            voiceFileWriter.addChunk(buffer, dataLength);
                        } else {
                            byte[] payload = new byte[dataLength];
                            System.arraycopy(buffer, payloadOffset, payload, 0, dataLength);
                            voiceFileWriter.addChunk(payload, dataLength);
                        }
                    }
                } catch (java.net.SocketTimeoutException ste) {
                    // normal — loop again and re-check 'running'
                } catch (IOException io) {
                    // socket likely closed by cleanup(); exit loop
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Audio handling error: " + e.getMessage());
        } finally {
            if (speaker != null) {
                speaker.drain();
                speaker.stop();
                speaker.close();
            }
            if (voiceFileWriter != null) {
                try {
                    String path = voiceFileWriter.writeToFile();
                    System.out.println("Voice recording saved to: " + path);
                } catch (IOException e) {
                    System.err.println("Failed to save voice file: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stops billing, computes final stats, waits for the audio thread to finish,
     * and writes the CDR.
     */
    private void cleanup() {
        running = false;

        if (billingTimer != null) {
            billingTimer.cancel();
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        if (audioThread != null) {
            try {
                audioThread.join(5000);
            } catch (InterruptedException ignored) {}
        }

        if (callStarted) {
            endTime = LocalDateTime.now();
            try {
                BigDecimal currentBalance = userDAO.getBalance(msisdn);
                if (currentBalance != null) {
                    balanceAfterCall = currentBalance;
                }
            } catch (SQLException e) {
                System.err.println("Could not fetch final balance: " + e.getMessage());
            }
            writeCDR();
        }

        try {
            if (signalingSocket != null && !signalingSocket.isClosed()) {
                signalingSocket.close();
            }
        } catch (IOException ignored) {}

        server.sessionEnded(this);
    }

    /**
     * Build and append the CDR line.
     * Format: MSISDN, StartTime, EndTime, DurationMinutes, CallResult, CallCost, BalanceAfterCall
     */
    private void writeCDR() {
        long durationMinutes = (startTime != null && endTime != null)
                ? Math.max(ChronoUnit.MINUTES.between(startTime, endTime), elapsedMinutes)
                : 0;

        String line = String.join(",",
                msisdn != null ? msisdn : "UNKNOWN",
                startTime != null ? startTime.format(ISO_FMT) : "",
                endTime != null ? endTime.format(ISO_FMT) : "",
                String.valueOf(durationMinutes),
                callResult,
                totalCost.toPlainString(),
                balanceAfterCall.toPlainString()
        );

        System.out.println("Generating CDR line: " + line);
        CDRWriter.writeCDR(line);
    }

    public void stop() {
        running = false;
    }
}
