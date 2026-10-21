
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeHistory {

    public static class Record {
        public final String symbol;
        public final String side;
        public final double entryPrice;
        public final double exitPrice;
        public final double quantity;
        public final double profitLoss;
        public final String result;
        public final long timestamp;

        public Record(
                String symbol,
                String side,
                double entryPrice,
                double exitPrice,
                double quantity,
                double profitLoss,
                String result) {

            this.symbol = symbol == null ? "" : symbol;
            this.side = side == null ? "" : side;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.quantity = quantity;
            this.profitLoss = profitLoss;
            this.result = result == null ? "" : result;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final ArrayList<Record> records = new ArrayList<>();

    public synchronized void add(Record record) {
        if (record != null) {
            records.add(record);
        }
    }

    public synchronized List<Record> getAll() {
        return Collections.unmodifiableList(
                new ArrayList<>(records));
    }

    public synchronized int getTotalTrades() {
        return records.size();
    }

    public synchronized int getWinningTrades() {
        int wins = 0;

        for (Record r : records) {
            if (r.profitLoss > 0) {
                wins++;
            }
        }

        return wins;
    }

    public synchronized int getLosingTrades() {
        int losses = 0;

        for (Record r : records) {
            if (r.profitLoss < 0) {
                losses++;
            }
        }

        return losses;
    }

    public synchronized double getWinRate() {
        int completed = getWinningTrades() + getLosingTrades();

        if (completed == 0) {
            return 0;
        }

        return getWinningTrades() * 100.0 / completed;
    }

    public synchronized double getTotalProfitLoss() {
        double total = 0;

        for (Record r : records) {
            total += r.profitLoss;
        }

        return total;
    }

    public synchronized void clear() {
        records.clear();
    }
}
