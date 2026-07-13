/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.fastagi.app;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
/**
 *
 * @author marwan
 */
public class DBUtil {

    // Database connection details matching your local setup
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/prepaid_charging";
    private static final String DB_USER = "chargenix";
    private static final String DB_PASSWORD = "chargenix123";

    /**
     * Establishes and returns a connection to the database
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
