
package com.example.futuresdemoagent;

public class TradeSignal {

    public enum Direction {
        LONG, SHORT, WAIT
    }

    public enum Confidence {
        LOW, MEDIUM, HIGH
    }

    public final String symbol;
    public final Direction direction;
    public final Confidence confidence;
    public final double entryPrice;
    public final double stopLoss;
    public final double takeProfit;
    public final double score;
    public final String reason;
    public final long timestamp;

    public TradeSignal(
            String symbol,
            Direction direction,
            Confidence confidence,
            double entryPrice,
            double stopLoss,
            double takeProfit,
            double score,
            String reason) {

        this.symbol = symbol == null ? "" : symbol;
        this.direction = direction == null
                ? Direction.WAIT : direction;
        this.confidence = confidence == null
                ? Confidence.LOW : confidence;

        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.score = Math.max(0, Math.min(100, score));
        this.reason = reason == null ? "" : reason;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isTradeCandidate() {
        if (direction == Direction.WAIT
                || symbol.isEmpty()
                || entryPrice <= 0
                || stopLoss <= 0
                || takeProfit <= 0
                || score < 70) {
            return false;
        }

        if (direction == Direction.LONG) {
            return stopLoss < entryPrice
                    && takeProfit > entryPrice;
        }

        return stopLoss > entryPrice
                && takeProfit < entryPrice;
    }

    public double getRiskRewardRatio() {
        if (entryPrice <= 0) return 0;

        double risk = Math.abs(entryPrice - stopLoss);
        double reward = Math.abs(takeProfit - entryPrice);

        if (risk <= 0) return 0;

        return reward / risk;
    }

    @Override
    public String toString() {
        return symbol + " | " + direction
                + " | Score: " + score
                + " | " + reason;
    }
}
