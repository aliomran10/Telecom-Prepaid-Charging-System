package com.telecom.msc.util;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Buffers raw audio bytes coming from UDP/RTP packets and writes them out
 * as a .wav file when the call ends.
 *
 * File name format (per spec):
 * /tmp/voice_call_msisdn_<MSISDN>_date_yyyy_MM_dd_Time_HH_mm_ss.wav
 */
public class VoiceFileWriter {

    private final List<byte[]> chunks = new ArrayList<>();
    private final AudioFormat format;
    private final String msisdn;

    public VoiceFileWriter(String msisdn, AudioFormat format) {
        this.msisdn = msisdn;
        this.format = format;
    }

    /** Add a chunk of raw PCM audio bytes received from the network. */
    public synchronized void addChunk(byte[] data, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(data, 0, copy, 0, length);
        chunks.add(copy);
    }

    /**
     * Write all buffered audio to a .wav file and return the absolute path.
     */
    public synchronized String writeToFile() throws IOException {
        int totalLength = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] allBytes = new byte[totalLength];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, allBytes, pos, c.length);
            pos += c.length;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy_MM_dd");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH_mm_ss");
        LocalDateTime now = LocalDateTime.now();

        String fileName = String.format("/tmp/voice_call_msisdn_%s_date_%s_Time_%s.wav",
                msisdn, now.format(dateFmt), now.format(timeFmt));

        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(allBytes), format, allBytes.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(fileName));
        }

        return fileName;
    }
}
