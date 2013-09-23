package com.skisel.montecarlo;

import java.util.List;

public class MonteCarloSimulator {

    private Simulation dealPortfolioCalculator;
    private Simulation dealPlusBackgroundPortfolioCalculator;

    public MonteCarloSimulator() throws Exception {
        dealPortfolioCalculator = new Simulation();
        dealPlusBackgroundPortfolioCalculator = new Simulation();
    }
    
    public List<Loss> simulateDeal() throws Exception {
        return dealPortfolioCalculator.calculate();
    }

    public List<Loss> simulateBackground() throws Exception {
        return dealPlusBackgroundPortfolioCalculator.calculate();
    }
    
    
}
