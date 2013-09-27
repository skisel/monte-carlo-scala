package com.skisel.montecarlo;

import java.io.Serializable;

public class Loss implements Serializable {
    private double amount;

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

}

