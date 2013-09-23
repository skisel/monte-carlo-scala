package com.skisel.montecarlo;

import java.util.ArrayList;
import java.util.List;

public class Simulation {
    public List<Loss> calculate() {
        ArrayList<Loss> losses = new ArrayList<Loss>();
        for (int i =0;i<500;i++)
            appendLoss(losses);
        return losses;
    }

    private void appendLoss(ArrayList<Loss> losses) {
        Loss loss = new Loss();
        loss.setLossAmount(Math.random()*10000);
        losses.add(loss);
    }
}
