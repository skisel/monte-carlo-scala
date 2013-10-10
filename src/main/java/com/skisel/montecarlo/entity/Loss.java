package com.skisel.montecarlo.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    public static String toJson(List<Loss> losses) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(losses);
    }

    public static List<Loss> fromJson(String losses) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(losses, new TypeToken<List<Loss>>(){}.getType());
    }

}

