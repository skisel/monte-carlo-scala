package com.skisel.montecarlo.entity;

import java.io.Serializable;

public class Risk implements Serializable{
    private double pd;
    private double value;

    public Risk() {
    }

    public Risk(double pd, double value) {
        this.pd = pd;
        this.value = value;
    }

    public double getPd() {
        return pd;
    }

    public void setPd(double pd) {
        this.pd = pd;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

}
