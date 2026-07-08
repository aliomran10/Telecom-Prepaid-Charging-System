package com.telecom.msc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MSC (Mobile Switching Center) application.
 *
 * Usage:
 *   java -jar msc.jar
 *   java -jar msc.jar --rtp        (enable RTP header stripping mode)
 *
 * Listens for TCP signaling connections on SIGNALING_PORT.
 * Each connecting Mobile application gets its own CallSession, which:
 *  - Receives "Start Call <MSISDN>" / "End Call" signaling messages
 *  - Opens a per-call UDP/RTP socket for voice traffic
 *  - Charges the user 1 L.E per minute
 *  - Plays audio at speaker (single call) or records to .wav (concurrent calls)
 *  - Writes a CDR to /tmp/calls_CDR_yyyy_MM_dd_HH.cdr
 */
public class MSCServer {

    public static final int SIGNALING_PORT = 5000;

    private final ExecutorService callExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<CallSession, Boolean> activeSessions = new ConcurrentHashMap<>();

    private boolean rtpMode = false;

    public static void main(String[] args) {
        MSCServer server = new MSCServer();

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--rtp")) {
                server.rtpMode = true;
            }
        }

        server.start();
    }

    public void start() {
        System.out.println("Waiting for voice call Signaling start message via TCP");
        if (rtpMode) {
            System.out.println("RTP mode enabled: stripping 12-byte RTP headers from incoming packets.");
        }

        try (ServerSocket serverSocket = new ServerSocket(SIGNALING_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                CallSession session = new CallSession(clientSocket, this);
                activeSessions.put(session, Boolean.TRUE);
                callExecutor.submit(session);
            }
        } catch (IOException e) {
            System.err.println("MSC server error: " + e.getMessage());
        }
    }

    /**
     * Concurrent mode is automatically enabled once more than one call is active
     * at the same time. In concurrent mode, audio is recorded to .wav files
     * instead of being played at the speaker (per bonus requirement).
     */
    public boolean isConcurrentMode() {
        return activeSessions.size() > 1;
    }

    public boolean isRtpMode() {
        return rtpMode;
    }

    public void sessionEnded(CallSession session) {
        activeSessions.remove(session);
    }
}
