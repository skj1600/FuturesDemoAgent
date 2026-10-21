
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DemoTrader {

    public static class Trade {
        public final String symbol;
        public final String side;
        public final double entryPrice;
        public final double stopLoss;
        public final double takeProfit;
        public final double quantity;
        public final long entryTime;
        public final double openingFee;
        public final double estimatedRisk;

        public boolean closed;
        public double exitPrice;
        public double profitLoss;
        public String exitReason = "OPEN";

        Trade(String symbol, String side, double entry,
              double stop, double target, double quantity,
              double openingFee, double estimatedRisk) {
            this.symbol = symbol;
            this.side = side;
            this.entryPrice = entry;
            this.stopLoss = stop;
            this.takeProfit = target;
            this.quantity = quantity;
            this.openingFee = openingFee;
            this.estimatedRisk = estimatedRisk;
            this.entryTime = System.currentTimeMillis();
        }
    }

    private final ArrayList<Trade> history = new ArrayList<>();
    private final ArrayList<Trade> openTrades = new ArrayList<>();

    private double balance;
    private final double startingBalance;
    private final double feeRate;
    private final RiskManager riskManager;
    private final double dailyLossLimitPercent;

    private long riskDay = System.currentTimeMillis() / 86400000L;

    public DemoTrader(double initialBalance) {
        this(initialBalance, 0.5, 2.0, 0.0005);
    }

    public DemoTrader(double initialBalance, double riskPercent,
                      double dailyLossLimitPercent) {
        this(initialBalance, riskPercent, dailyLossLimitPercent, 0.0005);
    }

    public DemoTrader(double initialBalance, double riskPercent,
                      double dailyLossLimitPercent, double feeRate) {
        balance = valid(initialBalance) ? Math.max(0, initialBalance) : 0;
        startingBalance = balance;
        this.feeRate = valid(feeRate) ? Math.max(0, feeRate) : 0.0005;

        this.dailyLossLimitPercent =
                valid(dailyLossLimitPercent)
                ? Math.max(0.5, Math.min(5.0, dailyLossLimitPercent))
                : 2.0;

        double safeRiskPercent = valid(riskPercent)
                ? Math.max(0.1, Math.min(1.0, riskPercent))
                : 0.5;

        riskManager = new RiskManager(
                safeRiskPercent,
                this.dailyLossLimitPercent,
                balance);
    }

    private static boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private void checkNewDay() {
        long today = System.currentTimeMillis() / 86400000L;

        if (today != riskDay) {
            riskDay = today;
            riskManager.resetDailyRisk(balance);
        }
    }

    private double getReservedRisk() {
        double reserved = 0;

        for (Trade t : openTrades) {
            if (!t.closed) {
                reserved += t.estimatedRisk;
            }
        }

        return reserved;
    }

    public synchronized Trade openTrade(
            String symbol, String side, double entryPrice,
            double stopLoss, double takeProfit) {

        checkNewDay();

        if (symbol == null || symbol.trim().isEmpty()
                || side == null
                || !valid(entryPrice)
                || !valid(stopLoss)
                || !valid(takeProfit)) {
            return null;
        }

        String direction = side.trim().toUpperCase(Locale.US);

        if (!direction.equals("LONG") && !direction.equals("SHORT")) {
            return null;
        }

        if (entryPrice <= 0 || stopLoss <= 0 || takeProfit <= 0) {
            return null;
        }

        if (direction.equals("LONG")
                && !(stopLoss < entryPrice && takeProfit > entryPrice)) {
            return null;
        }

        if (direction.equals("SHORT")
                && !(stopLoss > entryPrice && takeProfit < entryPrice)) {
            return null;
        }

        if (balance <= 0 || !riskManager.canTrade()) {
            return null;
        }

        for (Trade t : openTrades) {
            if (t.symbol.equalsIgnoreCase(symbol)) {
                return null;
            }
        }

        double quantity = riskManager.calculatePositionSize(
                balance, entryPrice, stopLoss);

        if (!valid(quantity) || quantity <= 0) {
            return null;
        }

        double openingFee = quantity * entryPrice * feeRate;
        double closingFeeEstimate = quantity * stopLoss * feeRate;
        double estimatedRisk =
                Math.abs(entryPrice - stopLoss) * quantity
                + openingFee + closingFeeEstimate;

        double dailyLimit =
                balance * dailyLossLimitPercent / 100.0;

        if (!valid(estimatedRisk) || estimatedRisk <= 0
                || openingFee >= balance) {
            return null;
        }

        // Do not open a position if combined open risk exceeds
        // the configured daily loss allowance.
        if (getReservedRisk() + estimatedRisk > dailyLimit) {
            return null;
        }

        Trade trade = new Trade(
                symbol, direction, entryPrice,
                stopLoss, takeProfit, quantity,
                openingFee, estimatedRisk);

        balance -= openingFee;
        openTrades.add(trade);
        history.add(trade);

        return trade;
    }

    public synchronized void updatePrice(String symbol, double price) {
        checkNewDay();

        if (symbol == null || !valid(price) || price <= 0) {
            return;
        }

        for (Trade t : new ArrayList<>(openTrades)) {
            if (!t.symbol.equalsIgnoreCase(symbol) || t.closed) {
                continue;
            }

            if (t.side.equals("LONG")) {
                if (price <= t.stopLoss) {
                    closeTrade(t, t.stopLoss, "STOP LOSS");
                } else if (price >= t.takeProfit) {
                    closeTrade(t, t.takeProfit, "TAKE PROFIT");
                }
            } else {
                if (price >= t.stopLoss) {
                    closeTrade(t, t.stopLoss, "STOP LOSS");
                } else if (price <= t.takeProfit) {
                    closeTrade(t, t.takeProfit, "TAKE PROFIT");
                }
            }
        }
    }

    public synchronized boolean closeTrade(String symbol, double price) {
        if (symbol == null || !valid(price) || price <= 0) {
            return false;
        }

        for (Trade t : new ArrayList<>(openTrades)) {
            if (t.symbol.equalsIgnoreCase(symbol)) {
                closeTrade(t, price, "MANUAL DEMO EXIT");
                return true;
            }
        }

        return false;
    }

    private void closeTrade(Trade t, double exitPrice, String reason) {
        if (t.closed || !valid(exitPrice) || exitPrice <= 0) {
            return;
        }

        double gross = t.side.equals("LONG")
                ? (exitPrice - t.entryPrice) * t.quantity
                : (t.entryPrice - exitPrice) * t.quantity;

        double closingFee = exitPrice * t.quantity * feeRate;
        double net = gross - t.openingFee - closingFee;

        if (!valid(net)) {
            return;
        }

        t.exitPrice = exitPrice;
        t.profitLoss = net;
        t.exitReason = reason;
        t.closed = true;

        // Opening fee was deducted at entry.
        balance += gross - closingFee;

        if (net < 0) {
            riskManager.recordLoss(-net);
        }

        openTrades.remove(t);
    }

    public synchronized double getBalance() {
        return balance;
    }

    public double getStartingBalance() {
        return startingBalance;
    }

    public synchronized double getRealizedProfitLoss() {
        double total = 0;

        for (Trade t : history) {
            if (t.closed) {
                total += t.profitLoss;
            }
        }

        return total;
    }

    public synchronized int getOpenTradeCount() {
        return openTrades.size();
    }

    public synchronized List<Trade> getOpenTrades() {
        return Collections.unmodifiableList(
                new ArrayList<>(openTrades));
    }

    public synchronized List<Trade> getTradeHistory() {
        return Collections.unmodifiableList(
                new ArrayList<>(history));
    }

    public synchronized double getWinRatePercent() {
        int closed = 0;
        int wins = 0;

        for (Trade t : history) {
            if (!t.closed) continue;

            closed++;
            if (t.profitLoss > 0) wins++;
        }

        return closed == 0 ? 0 : wins * 100.0 / closed;
    }

    public synchronized boolean canTrade() {
        checkNewDay();

        if (!riskManager.canTrade()) {
            return false;
        }

        double dailyLimit =
                balance * dailyLossLimitPercent / 100.0;

        return getReservedRisk() < dailyLimit;
    }

    public synchronized double getReservedRiskAmount() {
        return getReservedRisk();
    }

    public synchronized void resetDailyRisk() {
        riskDay = System.currentTimeMillis() / 86400000L;
        riskManager.resetDailyRisk(balance);
    }
}
