package com.skisel.montecarlo;

import com.skisel.montecarlo.entity.Loss;
import com.skisel.montecarlo.entity.Risk;

import java.util.ArrayList;
import java.util.List;

public class MonteCarloSimulator {

    private Simulation dealPortfolioCalculator;
    private Simulation dealPlusBackgroundPortfolioCalculator;

    private ArrayList<Risk> risks;

    public MonteCarloSimulator(Input input) throws Exception {
        this.risks = new ArrayList<Risk>();
        risks.addAll(input.getRisks());
        dealPortfolioCalculator = new Simulation(risks);
        dealPlusBackgroundPortfolioCalculator = new Simulation(risks);
    }
    
    public List<Loss> simulateDeal() throws Exception {
        return dealPortfolioCalculator.calculate();
    }

    public List<Loss> simulateBackground() throws Exception {
        return dealPlusBackgroundPortfolioCalculator.calculate();
    }

    public ArrayList<Risk> getRisks() {
        return risks;
    }
}
