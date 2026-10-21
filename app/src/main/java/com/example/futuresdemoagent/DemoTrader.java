
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

        public boolean closed;
        public double exitPrice;
        public double profitLoss;
        public String exitReason = "OPEN";

        Trade(String symbol, String side, double entry,
              double stop, double target, double quantity) {
            this.symbol = symbol;
            this.side = side;
            this.entryPrice = entry;
            this.stopLoss = stop;
            this.takeProfit = target;
            this.quantity = quantity;
            this.entryTime = System.currentTimeMillis();
            this.closed = false;
        }
    }

    private final ArrayList<Trade> history = new ArrayList<>();
    private final ArrayList<Trade> openTrades = new ArrayList<>();

    private double balance;
    private final double startingBalance;
    private final double feeRate;
    private final RiskManager riskManager;

    public DemoTrader(double initialBalance) {
        this(initialBalance, 0.0005);
    }

    public DemoTrader(double initialBalance, double feeRate) {
        this.balance = Math.max(0, initialBalance);
        this.startingBalance = this.balance;
        this.feeRate = Math.max(0, feeRate);
        this.riskManager = new RiskManager(0.5, 2.0, this.balance);
    }

    public synchronized Trade openTrade(
            String symbol,
            String side,
            double entryPrice,
            double stopLoss,
            double takeProfit) {

        if (symbol == null || symbol.trim().isEmpty()) return null;
        if (side == null) return null;

        String direction = side.trim().toUpperCase(Locale.US);

        if (!direction.equals("LONG") && !direction.equals("SHORT")) {
            return null;
        }

        if (entryPrice <= 0 || stopLoss <= 0 || takeProfit <= 0) {
            return null;
        }

        if (balance <= 0 || !riskManager.canTrade()) return null;

        if (direction.equals("LONG")
                && !(stopLoss < entryPrice && takeProfit > entryPrice)) {
            return null;
        }

        if (direction.equals("SHORT")
                && !(stopLoss > entryPrice && takeProfit < entryPrice)) {
            return null;
        }

        // Prevent multiple simultaneous positions on the same symbol.
        for (Trade t : openTrades) {
            if (t.symbol.equalsIgnoreCase(symbol)) return null;
        }

        double quantity = riskManager.calculatePositionSize(
                balance, entryPrice, stopLoss);

        if (quantity <= 0) return null;

        double notional = quantity * entryPrice;
        double openingFee = notional * feeRate;

        if (openingFee >= balance) return null;

        Trade trade = new Trade(
                symbol, direction, entryPrice,
                stopLoss, takeProfit, quantity);

        balance -= openingFee;
        openTrades.add(trade);
        history.add(trade);

        return trade;
    }

    /*
     * Feed this method the latest market price.
     * It simulates exits only; it does not send exchange orders.
     */
    public synchronized void updatePrice(String symbol, double price) {
        if (symbol == null || price <= 0) return;

        for (Trade t : new ArrayList<>(openTrades)) {
            if (!t.symbol.equalsIgnoreCase(symbol) || t.closed) continue;

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

    public synchronized boolean closeTrade(
            String symbol, double price) {

        if (symbol == null || price <= 0) return false;

        for (Trade t : new ArrayList<>(openTrades)) {
            if (t.symbol.equalsIgnoreCase(symbol)) {
                closeTrade(t, price, "MANUAL DEMO EXIT");
                return true;
            }
        }

        return false;
    }

    private void closeTrade(Trade t, double exitPrice, String reason) {
        if (t.closed) return;

        double gross;

        if (t.side.equals("LONG")) {
            gross = (exitPrice - t.entryPrice) * t.quantity;
        } else {
            gross = (t.entryPrice - exitPrice) * t.quantity;
        }

        double closingFee = exitPrice * t.quantity * feeRate;
        double net = gross - closingFee;

        t.exitPrice = exitPrice;
        t.profitLoss = net;
        t.exitReason = reason;
        t.closed = true;

        balance += net;

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
            if (t.closed) total += t.profitLoss;
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

        return closed == 0 ? 0 : (wins * 100.0 / closed);
    }

    public synchronized boolean canTrade() {
        return riskManager.canTrade();
    }

    public synchronized void resetDailyRisk() {
        riskManager.resetDailyRisk(balance);
    }
}
