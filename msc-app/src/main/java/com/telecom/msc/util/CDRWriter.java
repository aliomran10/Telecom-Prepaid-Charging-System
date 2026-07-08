package com.telecom.msc.util;

import java.io.IOException;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes Call Detail Records (CDR) to /tmp/calls_CDR_yyyy_MM_dd_HH.cdr
 * A new file is created automatically whenever the current hour changes
 * (i.e. the CDR file "rotates" every hour), satisfying the bonus requirement
 * without requiring the external Log4j dependency.
 *
 * CDR line format:
 * MSISDN, StartTime, EndTime, Duration(min), CallResult, CallCost, BalanceAfterCall
 */
public class CDRWriter {

    private static final DateTimeFormatter FILE_SUFFIX_FMT =
            DateTimeFormatter.ofPattern("yyyy_MM_dd_HH");

    private static final String BASE_DIR = "/tmp";
    private static final String FILE_PREFIX = "calls_CDR_";
    private static final String FILE_EXT = ".cdr";

    /**
     * Append a single CDR line to the file matching the current hour.
     * Thread-safe (synchronized) so multiple concurrent calls can write safely.
     */
    public static synchronized void writeCDR(String cdrLine) {
        String fileName = BASE_DIR + "/" + FILE_PREFIX
                + LocalDateTime.now().format(FILE_SUFFIX_FMT) + FILE_EXT;

        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(cdrLine);
            fw.write(System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Failed to write CDR: " + e.getMessage());
        }
    }
}
