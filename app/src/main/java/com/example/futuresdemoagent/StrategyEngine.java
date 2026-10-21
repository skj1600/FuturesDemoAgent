
package com.example.futuresdemoagent;

public class StrategyEngine {

    public static class MarketData {
        public final String symbol;
        public final double price, change24h;
        public final double trend1m, trend5m, trend15m;
        public final double momentum1m, volumeRatio;

        public MarketData(String symbol, double price,
                double change24h, double trend1m,
                double trend5m, double trend15m,
                double momentum1m, double volumeRatio) {
            this.symbol = symbol;
            this.price = price;
            this.change24h = change24h;
            this.trend1m = trend1m;
            this.trend5m = trend5m;
            this.trend15m = trend15m;
            this.momentum1m = momentum1m;
            this.volumeRatio = volumeRatio;
        }
    }

    private final double minimumScore;
    private final double minimumRiskReward;

    public StrategyEngine() {
        this(70, 1.5);
    }

    public StrategyEngine(double minimumScore, double minimumRiskReward) {
        this.minimumScore = Math.max(50, Math.min(minimumScore, 95));
        this.minimumRiskReward =
                Math.max(1.0, Math.min(minimumRiskReward, 5.0));
    }

    public TradeSignal analyze(MarketData d) {
        if (d == null || d.symbol == null || d.price <= 0
                || !finite(d.price) || !finite(d.change24h)
                || !finite(d.trend1m) || !finite(d.trend5m)
                || !finite(d.trend15m) || !finite(d.momentum1m)
                || !finite(d.volumeRatio) || d.volumeRatio < 0) {
            return waitSignal(d, "Invalid market data");
        }

        boolean bullish = d.trend1m > 0 && d.trend5m > 0
                && d.trend15m > 0 && d.momentum1m > 0;
        boolean bearish = d.trend1m < 0 && d.trend5m < 0
                && d.trend15m < 0 && d.momentum1m < 0;

        if (!bullish && !bearish) {
            return waitSignal(d, "Timeframes or momentum disagree");
        }

        if (d.volumeRatio < 1.2) {
            return waitSignal(d, "Volume confirmation missing");
        }

        if (Math.abs(d.change24h) > 8) {
            return waitSignal(d, "24h move too extended");
        }

        double score = calculateScore(d, bullish);

        if (score < minimumScore) {
            return waitSignal(d, "Setup score below threshold");
        }

        TradeSignal.Direction direction = bullish
                ? TradeSignal.Direction.LONG
                : TradeSignal.Direction.SHORT;

        return createSignal(d, direction, score,
                "Trend aligned; momentum and volume confirmed");
    }

    private double calculateScore(MarketData d, boolean bullish) {
        double score = 40;

        // Trend alignment
        score += 20;

        // Momentum must support the selected direction.
        double alignedMomentum = bullish
                ? d.momentum1m : -d.momentum1m;

        if (alignedMomentum > 0.05) score += 12;
        else if (alignedMomentum > 0) score += 7;

        // Stronger volume confirmation receives a larger score.
        if (d.volumeRatio >= 2.0) score += 18;
        else if (d.volumeRatio >= 1.5) score += 14;
        else if (d.volumeRatio >= 1.2) score += 8;

        // Penalize very weak EMA separation.
        double trendStrength = Math.min(
                Math.abs(d.trend1m),
                Math.min(Math.abs(d.trend5m), Math.abs(d.trend15m)));

        if (trendStrength < 0.03) score -= 12;
        else if (trendStrength < 0.08) score -= 5;

        return Math.max(0, Math.min(100, score));
    }

    private TradeSignal createSignal(
            MarketData d, TradeSignal.Direction direction,
            double score, String reason) {

        double stopDistance = d.price * 0.005;
        double targetDistance = stopDistance * minimumRiskReward;

        double stop, target;

        if (direction == TradeSignal.Direction.LONG) {
            stop = d.price - stopDistance;
            target = d.price + targetDistance;
        } else {
            stop = d.price + stopDistance;
            target = d.price - targetDistance;
        }

        TradeSignal.Confidence confidence = score >= 85
                ? TradeSignal.Confidence.HIGH
                : score >= 75 ? TradeSignal.Confidence.MEDIUM
                : TradeSignal.Confidence.LOW;

        return new TradeSignal(d.symbol, direction, confidence,
                d.price, stop, target, score, reason);
    }

    private TradeSignal waitSignal(MarketData d, String reason) {
        String symbol = d == null || d.symbol == null ? "" : d.symbol;
        double price = d == null || !finite(d.price) ? 0 : d.price;

        return new TradeSignal(symbol, TradeSignal.Direction.WAIT,
                TradeSignal.Confidence.LOW, price, 0, 0, 0, reason);
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
