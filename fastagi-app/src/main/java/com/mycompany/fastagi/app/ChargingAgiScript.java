
package com.mycompany.fastagi.app;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;
/**
 *
 * @author marwan
 */


public class ChargingAgiScript extends BaseAgiScript {

    /**
     * Helper method to generate and play text dynamically using espeak
     */
    private void speak(String text, String fileName) throws AgiException {
        // Command to generate TTS and convert it to Asterisk 8000Hz format
        String command = "espeak-ng -v en -s 140 -w /tmp/" + fileName + ".wav \"" + text + "\" && sox /tmp/" + fileName + ".wav -r 8000 -c 1 /tmp/" + fileName + "_8k.wav";        
        // Execute the Linux command via Asterisk
        exec("System", command);
        
        // Play the generated file (without the .wav extension)
        streamFile("/tmp/" + fileName + "_8k");
    }

    /**
     * Helper method to generate text, play it, and wait for user input (Digits)
     */
    private String speakAndGetDigits(String text, String fileName, long timeout, int maxDigits) throws AgiException {
          String command = "espeak-ng -v en -s 140 -w /tmp/" + fileName + ".wav \"" + text + "\" && sox /tmp/" + fileName + ".wav -r 8000 -c 1 /tmp/" + fileName + "_8k.wav";     
          exec("System", command);
        
        // Play the file and wait for the digits
        return getData("/tmp/" + fileName + "_8k", timeout, maxDigits);
    }

    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        // 1. Answer the call
        answer();
        
        // 2. Play welcome message and ask for MSISDN
        String welcomeText = "Welcome to the balance inquiry service. Please enter your phone number.";
        
        // Wait for up to 10 seconds (10000ms) to get max 11 digits
        String msisdn = speakAndGetDigits(welcomeText, "welcome_msg", 10000, 11);
        
        System.out.println("User entered MSISDN: " + msisdn);
        
        // 3. Process the input
        if (msisdn != null && !msisdn.trim().isEmpty()) {
            double balance = 0.0;
            boolean userExists = false;
            
            // Query the database
            String sql = "SELECT balance FROM users WHERE msisdn = ?";
            
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, msisdn);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        balance = rs.getDouble("balance");
                        userExists = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Database error: " + e.getMessage());
            }
            
            // 4. Play balance or error message dynamically
            if (userExists) {
                System.out.println("Successfully found MSISDN: " + msisdn + " | Balance: " + balance);
                String balanceText = "Your current balance is " + balance + " pounds.";
                speak(balanceText, "balance_msg");
            } else {
                System.out.println("Authentication failed: MSISDN " + msisdn + " is not registered.");
                speak("The number you entered is not registered in our system.", "error_msg");
            }
        } else {
            System.out.println("No MSISDN was entered by the caller.");
            speak("You did not enter any number. Goodbye.", "timeout_msg");
        }
        
        // 5. Terminate the call
        hangup();
    }
}