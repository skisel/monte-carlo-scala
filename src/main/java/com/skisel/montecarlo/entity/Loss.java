package com.skisel.montecarlo.entity;

import java.io.Serializable;

public class Loss implements Serializable {
    private double amount;
    private Risk risk;

    public Loss() {
    }

    public Risk getRisk() {
        return risk;
    }

    public void setRisk(Risk risk) {
        this.risk = risk;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

}

