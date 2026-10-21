
package com.example.futuresdemoagent;

public class RiskManager {

    private final double riskPercent;
    private final double maxDailyLossPercent;

    private double startingBalance;
    private double dailyLoss;

    public RiskManager(double riskPercent,
                       double maxDailyLossPercent,
                       double startingBalance) {
        this.riskPercent = clamp(riskPercent, 0.1, 1.0);
        this.maxDailyLossPercent =
                clamp(maxDailyLossPercent, 0.5, 5.0);

        this.startingBalance = validAmount(startingBalance)
                ? startingBalance : 0;

        this.dailyLoss = 0;
    }

    public synchronized double calculatePositionSize(
            double balance, double entryPrice, double stopPrice) {

        if (!validAmount(balance)
                || !validAmount(entryPrice)
                || !validAmount(stopPrice)
                || entryPrice == stopPrice
                || !canTrade()) {
            return 0;
        }

        double distance = Math.abs(entryPrice - stopPrice);
        double normalRisk = balance * riskPercent / 100.0;
        double remainingRisk = getRemainingDailyRisk();

        double riskAmount = Math.min(normalRisk, remainingRisk);
        if (riskAmount <= 0) return 0;

        double riskQuantity = riskAmount / distance;

        // Maximum notional exposure: 20% of balance.
        double maxNotional = balance * 0.20;
        double notionalQuantity = maxNotional / entryPrice;

        double quantity = Math.min(riskQuantity, notionalQuantity);

        if (!validAmount(quantity)) return 0;
        return quantity;
    }

    public synchronized boolean canTrade() {
        if (startingBalance <= 0) return false;
        return dailyLoss < getMaxDailyLoss();
    }

    public synchronized void recordLoss(double lossAmount) {
        if (validAmount(lossAmount)) {
            dailyLoss += lossAmount;
        }
    }

    public synchronized void resetDailyRisk(double currentBalance) {
        startingBalance = validAmount(currentBalance)
                ? currentBalance : 0;
        dailyLoss = 0;
    }

    public synchronized double getDailyLoss() {
        return dailyLoss;
    }

    public synchronized double getMaxDailyLoss() {
        return startingBalance * maxDailyLossPercent / 100.0;
    }

    public synchronized double getRemainingDailyRisk() {
        return Math.max(0, getMaxDailyLoss() - dailyLoss);
    }

    public double getRiskPercent() {
        return riskPercent;
    }

    public double getMaxDailyLossPercent() {
        return maxDailyLossPercent;
    }

    private static double clamp(double value,
                                double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private static boolean validAmount(double value) {
        return value > 0
                && !Double.isNaN(value)
                && !Double.isInfinite(value);
    }
}
