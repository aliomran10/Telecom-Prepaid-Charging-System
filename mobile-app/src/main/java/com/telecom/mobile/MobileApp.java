package com.telecom.mobile;

import javax.sound.sampled.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mobile Phone application.
 *
 * Usage:
 *   java mobile <MSISDN>
 *   java mobile <MSISDN> <MSC_HOST>      (optional, defaults to localhost)
 *   java mobile <MSISDN> <MSC_HOST> --rtp  (send packets with RTP headers)
 *
 * On startup:
 *  - Connects to MSC via TCP and sends "Start Call <MSISDN>"
 *  - Receives the UDP port assigned by the MSC for this call
 *  - Captures audio from the microphone and streams it via UDP/RTP
 *  - Prints "N minutes elapsed" once per minute
 *  - On shutdown (Ctrl+C), sends "End Call" to the MSC via the same TCP connection
 */
public class MobileApp {

    private static final int SIGNALING_PORT = 5000;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(8000.0f, 16, 1, true, false);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java mobile <MSISDN> [MSC_HOST] [--rtp]");
            return;
        }

        String msisdn = args[0];
        String mscHost = "localhost";
        boolean rtpMode = false;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--rtp")) {
                rtpMode = true;
            } else {
                mscHost = args[i];
            }
        }

        System.out.println("Starting voice call as MSISDN " + msisdn);

        try {
            run(msisdn, mscHost, rtpMode);
        } catch (Exception e) {
            System.err.println("Mobile app error: " + e.getMessage());
        }
    }

    private static void run(String msisdn, String mscHost, boolean rtpMode) throws Exception {
        // 1. Connect to MSC via TCP and send "Start Call <MSISDN>"
        Socket signalingSocket = new Socket(mscHost, SIGNALING_PORT);
        PrintWriter out = new PrintWriter(signalingSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(signalingSocket.getInputStream()));

        out.println("Start Call " + msisdn);

        // 2. Receive the UDP port assigned by the MSC for voice traffic
        String response = in.readLine();
        int udpPort = 5005; // fallback default
        if (response != null && response.startsWith("UDP_PORT")) {
            udpPort = Integer.parseInt(response.trim().split("\\s+")[1]);
        }

        // 3. Prepare UDP socket and target address
        DatagramSocket udpSocket = new DatagramSocket();
        InetAddress mscAddress = InetAddress.getByName(mscHost);

        // 4. Open microphone line
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(AUDIO_FORMAT);
        microphone.start();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger sequenceNumber = new AtomicInteger(0);

        // 5. Shutdown hook: send "End Call" and clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            try {
                out.println("End Call");
                out.flush();
            } catch (Exception ignored) {}
            microphone.stop();
            microphone.close();
            udpSocket.close();
            try {
                signalingSocket.close();
            } catch (IOException ignored) {}
            System.out.println("Call ended. 'End Call' signaling sent to MSC.");
        }));

        System.out.println("Capturing Voice from Microphone and send via UDP.....");

        // 6. Start a background thread that prints "N minutes elapsed" once per minute
        Thread minuteTicker = new Thread(() -> {
            int minutes = 0;
            while (running.get()) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    break;
                }
                minutes++;
                System.out.println(minutes + " minutes elapsed");
            }
        });
        minuteTicker.setDaemon(true);
        minuteTicker.start();

        // 7. Main loop: capture audio from microphone and send via UDP/RTP
        byte[] buffer = new byte[1024];
        while (running.get()) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                byte[] packetData;

                if (rtpMode) {
                    packetData = wrapInRtp(buffer, bytesRead, sequenceNumber.getAndIncrement());
                } else {
                    packetData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, packetData, 0, bytesRead);
                }

                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, mscAddress, udpPort);
                udpSocket.send(packet);
            }
        }
    }

    /**
     * Wrap raw PCM audio bytes in a minimal 12-byte RTP header.
     * RTP header layout (RFC 3550, simplified — version 2, no extensions):
     *  Byte 0: V(2)=2, P=0, X=0, CC=0          -> 0x80
     *  Byte 1: M=0, PT=11 (defined as a generic payload type for this project)
     *  Bytes 2-3: sequence number
     *  Bytes 4-7: timestamp (using sequence * frame size as a simple monotonic value)
     *  Bytes 8-11: SSRC (fixed identifier for this session)
     */
    private static byte[] wrapInRtp(byte[] audio, int length, int seq) {
        byte[] packet = new byte[12 + length];

        packet[0] = (byte) 0x80; // version 2
        packet[1] = (byte) 0x0B; // payload type 11 (generic audio)

        packet[2] = (byte) (seq >> 8);
        packet[3] = (byte) seq;

        int timestamp = seq * length;
        packet[4] = (byte) (timestamp >> 24);
        packet[5] = (byte) (timestamp >> 16);
        packet[6] = (byte) (timestamp >> 8);
        packet[7] = (byte) timestamp;

        int ssrc = 0x12345678; // fixed synchronization source identifier
        packet[8]  = (byte) (ssrc >> 24);
        packet[9]  = (byte) (ssrc >> 16);
        packet[10] = (byte) (ssrc >> 8);
        packet[11] = (byte) ssrc;

        System.arraycopy(audio, 0, packet, 12, length);
        return packet;
    }
}
