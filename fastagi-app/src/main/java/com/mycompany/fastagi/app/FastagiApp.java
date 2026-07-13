/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.fastagi.app;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.AgiScript;
import org.asteriskjava.fastagi.DefaultAgiServer;
import org.asteriskjava.fastagi.MappingStrategy;
/**
 *
 * @author marwan
 */

public class FastagiApp {

    public static void main(String[] args) {
        System.out.println("Starting FastAGI Server...");

        // Create a new FastAGI server instance
        DefaultAgiServer server = new DefaultAgiServer();

        // Implement a custom mapping strategy directly
        server.setMappingStrategy(new MappingStrategy() {
            @Override
            public AgiScript determineScript(AgiRequest request, AgiChannel channel) {
                // Check if the requested script from Asterisk is "charging"
                if ("charging".equals(request.getScript())) {
                    return new ChargingAgiScript();
                }
                // Return null if no matching script is found
                return null; 
            }
        });

        // Start the server (Listens on port 4573 by default)
        try {
            server.startup();
        } catch (Exception e) {
            System.err.println("Error starting FastAGI server: " + e.getMessage());
        }
    }
}