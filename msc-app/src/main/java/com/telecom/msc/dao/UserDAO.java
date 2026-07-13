package com.telecom.msc.dao;

import com.telecom.msc.model.User;
import com.telecom.msc.util.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data access object for the USERS table.
 * Handles balance lookup and atomic charging (deduction) operations.
 */
public class UserDAO {

    /**
     * Find a user by MSISDN.
     * @return User object, or null if not found.
     */
    public User findByMsisdn(String msisdn) throws SQLException {
        String sql = "SELECT id, msisdn, balance FROM users WHERE msisdn = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msisdn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getInt("id"), rs.getString("msisdn"), rs.getBigDecimal("balance"));
                }
            }
        }
        return null;
    }

    /**
     * Atomically deduct 'amount' from the user's balance and return the new balance.
     * Balance is allowed to go negative (call continues per assignment spec;
     * you may add a cutoff check in the caller if you want to stop charging at zero).
     */
    public BigDecimal deductBalance(String msisdn, BigDecimal amount) throws SQLException {
        String sql = "UPDATE users SET balance = balance - ? WHERE msisdn = ? RETURNING balance";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, msisdn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        return null;
    }

    /**
     * Get the current balance for a MSISDN without modifying it.
     */
    public BigDecimal getBalance(String msisdn) throws SQLException {
        User u = findByMsisdn(msisdn);
        return (u != null) ? u.getBalance() : null;
    }
}
