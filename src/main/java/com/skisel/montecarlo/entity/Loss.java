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



    public static void main(String[] args) {
        Loss loss = new Loss();
        loss.setAmount(123);
        loss.setRisk(new Risk(0.01, 150));
        ArrayList<Loss> losses = new ArrayList<Loss>();
        losses.add(loss);
        losses.add(loss);
        String s = new String(Loss.toJson(losses));
        System.out.println(s);
        String json = "[{\"amount\":123.0,\"risk\":{\"pd\":0.01,\"value\":150.0}},{\"amount\":123.0,\"risk\":{\"pd\":0.01,\"value\":150.0}}]\n";
        List<Loss> s1 = Loss.fromJson(json);
    }
}

