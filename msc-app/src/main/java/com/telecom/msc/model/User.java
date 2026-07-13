package com.telecom.msc.model;

import java.math.BigDecimal;

public class User {
    private int id;
    private String msisdn;
    private BigDecimal balance;

    public User() {}

    public User(int id, String msisdn, BigDecimal balance) {
        this.id = id;
        this.msisdn = msisdn;
        this.balance = balance;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
