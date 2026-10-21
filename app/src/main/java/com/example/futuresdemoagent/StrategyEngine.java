
package com.example.futuresdemoagent;

public class StrategyEngine {

    public static class MarketData {
        public final String symbol;
        public final double price;
        public final double change24h;
        public final double trend1m;
        public final double trend5m;
        public final double trend15m;
        public final double momentum1m;
        public final double volumeRatio;

        public MarketData(
                String symbol,
                double price,
                double change24h,
                double trend1m,
                double trend5m,
                double trend15m,
                double momentum1m,
                double volumeRatio) {

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
        this(70.0, 1.5);
    }

    public StrategyEngine(
            double minimumScore,
            double minimumRiskReward) {

        this.minimumScore = Math.max(
                50, Math.min(minimumScore, 95));

        this.minimumRiskReward = Math.max(
                1.0, Math.min(minimumRiskReward, 5.0));
    }

    public TradeSignal analyze(MarketData data) {

        if (data == null
                || data.symbol == null
                || data.price <= 0
                || data.volumeRatio < 0) {
            return waitSignal(data, "Invalid market data");
        }

        boolean volumeConfirmed = data.volumeRatio >= 1.2;

        boolean bullish =
                data.trend5m > 0
                && data.trend15m > 0
                && data.momentum1m > 0
                && data.trend1m > 0;

        boolean bearish =
                data.trend5m < 0
                && data.trend15m < 0
                && data.momentum1m < 0
                && data.trend1m < 0;

        double score = calculateScore(data);

        if (!volumeConfirmed) {
            return waitSignal(data,
                    "Volume confirmation missing");
        }

        if (score < minimumScore) {
            return waitSignal(data,
                    "Setup score below threshold");
        }

        if (bullish && data.change24h < 8.0) {
            return createSignal(
                    data, TradeSignal.Direction.LONG,
                    score,
                    "Multi-timeframe bullish trend with volume");
        }

        if (bearish && data.change24h > -8.0) {
            return createSignal(
                    data, TradeSignal.Direction.SHORT,
                    score,
                    "Multi-timeframe bearish trend with volume");
        }

        return waitSignal(data,
                "Timeframes are not aligned");
    }

    private double calculateScore(MarketData d) {

        double score = 50;

        if (d.trend1m > 0 && d.trend5m > 0
                && d.trend15m > 0) {
            score += 15;
        } else if (d.trend1m < 0 && d.trend5m < 0
                && d.trend15m < 0) {
            score += 15;
        }

        if (d.momentum1m > 0) score += 8;
        if (d.momentum1m < 0) score += 8;

        if (d.volumeRatio >= 1.5) score += 15;
        else if (d.volumeRatio >= 1.2) score += 8;

        if (Math.abs(d.change24h) > 8) score -= 15;

        return Math.max(0, Math.min(100, score));
    }

    private TradeSignal createSignal(
            MarketData d,
            TradeSignal.Direction direction,
            double score,
            String reason) {

        double stopDistance = d.price * 0.005;
        double targetDistance =
                stopDistance * minimumRiskReward;

        double stop;
        double target;

        if (direction == TradeSignal.Direction.LONG) {
            stop = d.price - stopDistance;
            target = d.price + targetDistance;
        } else {
            stop = d.price + stopDistance;
            target = d.price - targetDistance;
        }

        TradeSignal.Confidence confidence =
                score >= 85
                ? TradeSignal.Confidence.HIGH
                : TradeSignal.Confidence.MEDIUM;

        return new TradeSignal(
                d.symbol, direction, confidence,
                d.price, stop, target, score, reason);
    }

    private TradeSignal waitSignal(
            MarketData d, String reason) {

        String symbol = d == null || d.symbol == null
                ? "" : d.symbol;

        double price = d == null ? 0 : d.price;
        double score = d == null ? 0 : calculateScore(d);

        return new TradeSignal(
                symbol,
                TradeSignal.Direction.WAIT,
                TradeSignal.Confidence.LOW,
                price, 0, 0, score, reason);
    }
}
