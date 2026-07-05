-- =====================================================
-- Prepaid Charging System - Database Schema
-- Database: PostgreSQL
-- =====================================================

-- Create database (run this line separately while connected to 'postgres' db)
-- CREATE DATABASE prepaid_charging;

-- Connect to prepaid_charging database, then run the rest:

DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id          SERIAL PRIMARY KEY,
    msisdn      VARCHAR(15) UNIQUE NOT NULL,
    balance     NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

-- Seed data
INSERT INTO users (msisdn, balance) VALUES
('01223456789', 50.00),
('01112345678', 20.00),
('01098765432', 5.00);

-- Quick checks
SELECT * FROM users;
