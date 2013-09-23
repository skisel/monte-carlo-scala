package com.skisel.montecarlo;

import java.io.Serializable;

public class Loss implements Serializable {
    private double lossAmount;

    public double getLossAmount() {
        return lossAmount;
    }

    public void setLossAmount(double lossAmount) {
        this.lossAmount = lossAmount;
    }
}

