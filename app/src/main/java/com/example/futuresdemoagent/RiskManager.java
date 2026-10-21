
package com.example.futuresdemoagent;

public class RiskManager {

    private final double riskPercent;
    private final double maxDailyLossPercent;
    private double startingBalance;
    private double dailyLoss;

    public RiskManager(double riskPercent,
                       double maxDailyLossPercent,
                       double startingBalance) {
        this.riskPercent = Math.max(0.1, Math.min(riskPercent, 1.0));
        this.maxDailyLossPercent =
                Math.max(0.5, Math.min(maxDailyLossPercent, 5.0));
        this.startingBalance = Math.max(0, startingBalance);
        this.dailyLoss = 0;
    }

    public synchronized double calculatePositionSize(
            double balance,
            double entryPrice,
            double stopPrice) {

        if (balance <= 0 || entryPrice <= 0 || stopPrice <= 0) {
            return 0;
        }

        double distance = Math.abs(entryPrice - stopPrice);

        if (distance <= 0) {
            return 0;
        }

        double riskAmount = balance * riskPercent / 100.0;
        double quantity = riskAmount / distance;

        // Position notional is capped at 20% of balance.
        double maxNotional = balance * 0.20;
        quantity = Math.min(quantity, maxNotional / entryPrice);

        return Math.max(0, quantity);
    }

    public synchronized boolean canTrade() {
        if (startingBalance <= 0) return false;

        double maxLoss =
                startingBalance * maxDailyLossPercent / 100.0;

        return dailyLoss < maxLoss;
    }

    public synchronized void recordLoss(double lossAmount) {
        if (lossAmount > 0) {
            dailyLoss += lossAmount;
        }
    }

    public synchronized void resetDailyRisk(double currentBalance) {
        startingBalance = Math.max(0, currentBalance);
        dailyLoss = 0;
    }

    public synchronized double getDailyLoss() {
        return dailyLoss;
    }

    public synchronized double getMaxDailyLoss() {
        return startingBalance * maxDailyLossPercent / 100.0;
    }
}
