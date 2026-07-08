package com.telecom.msc.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centralized JDBC connection helper for the MSC application.
 * Update DB_URL / DB_USER / DB_PASSWORD to match your local PostgreSQL setup.
 */
public class DBUtil {

    private static final String DB_URL  = "jdbc:postgresql://localhost:5432/prepaid_charging";
    private static final String DB_USER = "chargenix";
    private static final String DB_PASSWORD = "chargenix123";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found. Add postgresql-x.x.x.jar to classpath.", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
