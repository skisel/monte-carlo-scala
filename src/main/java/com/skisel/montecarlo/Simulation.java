package com.skisel.montecarlo;

import com.skisel.montecarlo.entity.Loss;
import com.skisel.montecarlo.entity.Risk;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.commons.math.random.MersenneTwister;
import org.apache.commons.math.random.RandomDataImpl;

import java.util.ArrayList;
import java.util.List;

public class Simulation {

    private List<Risk> risks;

    private final NormalDistributionImpl normalDistribution;
    private final double[] defaultThresholds;
    private ThreadLocal<RandomDataImpl> randomData = new ThreadLocal<RandomDataImpl>();

    public RandomDataImpl getRandomData() {
        if (randomData.get()==null) {
            randomData.set(new RandomDataImpl(new MersenneTwister()));
        }
        return randomData.get();
    }

    public Simulation(ArrayList<Risk> risks) {
        try {
            this.risks = risks;
            normalDistribution = new NormalDistributionImpl(0d, 1d);
            defaultThresholds = new double[this.risks.size()];
            int i = 0;
            for (Risk risk : this.risks) {
                defaultThresholds[i++] = normalDistribution.inverseCumulativeProbability(risk.getPd());
            }
        } catch (MathException e) {
            throw new RuntimeException("Initialization Failed");
        }
    }

    public List<Loss> calculate() {
        try {
            double[] sample = generate();
            int i = 0;
            ArrayList<Loss> losses = new ArrayList<Loss>();
            for (double v : sample) {
                Risk risk = risks.get(i);
                if (v <= defaultThresholds[i]) {
                    Loss loss = new Loss();
                    loss.setAmount(risk.getValue());
                    loss.setRisk(risk);
                    losses.add(loss);
                }
                i++;
            }
            return losses;
        } catch (MathException e) {
            throw new RuntimeException("Generation Failed");
        }
    }

    private double[] generate() throws MathException {
        double [] sample = new double[risks.size()];
        for (int i = 0; i < sample.length; i++) {
            sample[i] = getRandomData().nextInversionDeviate(normalDistribution);
        }

        return sample;
    }

}
