package com.telecom.web.dao;

import com.telecom.web.model.User;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD data access object for the USERS table.
 */
public class UserDAO {

    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, msisdn, balance FROM users ORDER BY id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public User findById(int id) throws SQLException {
        String sql = "SELECT id, msisdn, balance FROM users WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public void create(User user) throws SQLException {
        String sql = "INSERT INTO users (msisdn, balance) VALUES (?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getMsisdn());
            ps.setBigDecimal(2, user.getBalance());
            ps.executeUpdate();
        }
    }

    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET msisdn = ?, balance = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getMsisdn());
            ps.setBigDecimal(2, user.getBalance());
            ps.setInt(3, user.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("msisdn"),
                rs.getBigDecimal("balance")
        );
    }
}
