
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class MarketScanner {

    private final MarketDataClient marketClient;
    private final StrategyEngine strategy;
    private final ExecutorService executor;

    private volatile boolean cancelled = false;

    public static class ScanResult {
        public final TradeSignal signal;
        public final double price;
        public final double change24h;
        public final double quoteVolume;

        public ScanResult(
                TradeSignal signal,
                double price,
                double change24h,
                double quoteVolume) {
            this.signal = signal;
            this.price = price;
            this.change24h = change24h;
            this.quoteVolume = quoteVolume;
        }
    }

    public MarketScanner() {
        marketClient = new MarketDataClient();
        strategy = new StrategyEngine();
        executor = Executors.newFixedThreadPool(3);
    }

    public void stopScan() {
        cancelled = true;
    }

    public void startScan() {
        cancelled = false;
    }

    public List<ScanResult> scanTopLiquidMarkets(
            int maxMarkets) throws Exception {

        if (maxMarkets < 1) {
            throw new IllegalArgumentException(
                    "maxMarkets must be positive");
        }

        cancelled = false;

        List<MarketDataClient.Ticker> tickers =
                marketClient.getUsdtPerpetualTickers();

        List<MarketDataClient.Ticker> eligible =
                new ArrayList<>();

        for (MarketDataClient.Ticker ticker : tickers) {
            if (cancelled) break;

            if (ticker.quoteVolume >= 5_000_000
                    && ticker.price > 0) {
                eligible.add(ticker);
            }
        }

        int limit = Math.min(maxMarkets, eligible.size());
        List<Future<ScanResult>> futures = new ArrayList<>();

        for (int i = 0; i < limit && !cancelled; i++) {
            final MarketDataClient.Ticker ticker = eligible.get(i);

            futures.add(executor.submit(() -> analyzeTicker(ticker)));
        }

        List<ScanResult> results = new ArrayList<>();

        for (Future<ScanResult> future : futures) {
            if (cancelled) {
                future.cancel(true);
                continue;
            }

            try {
                ScanResult result = future.get(35, TimeUnit.SECONDS);
                if (result != null) results.add(result);
            } catch (Exception ignored) {
                future.cancel(true);
            }
        }

        results.sort((a, b) ->
                Double.compare(b.signal.score, a.signal.score));

        return Collections.unmodifiableList(results);
    }

    private ScanResult analyzeTicker(
            MarketDataClient.Ticker ticker) {

        if (cancelled || Thread.currentThread().isInterrupted()) {
            return null;
        }

        try {
            List<MarketDataClient.Candle> one =
                    marketClient.getCandles(ticker.symbol, "1m", 30);

            List<MarketDataClient.Candle> five =
                    marketClient.getCandles(ticker.symbol, "5m", 30);

            List<MarketDataClient.Candle> fifteen =
                    marketClient.getCandles(ticker.symbol, "15m", 30);

            if (one.size() < 25 || five.size() < 25
                    || fifteen.size() < 25) {
                return null;
            }

            double trend1m = calculateTrend(one, 7, 20);
            double trend5m = calculateTrend(five, 7, 20);
            double trend15m = calculateTrend(fifteen, 7, 20);

            double momentum1m = calculateMomentum(one, 5);
            double volumeRatio = calculateVolumeRatio(one);

            StrategyEngine.MarketData data =
                    new StrategyEngine.MarketData(
                            ticker.symbol,
                            ticker.price,
                            ticker.change24h,
                            trend1m,
                            trend5m,
                            trend15m,
                            momentum1m,
                            volumeRatio
                    );

            TradeSignal signal = strategy.analyze(data);

            return new ScanResult(
                    signal,
                    ticker.price,
                    ticker.change24h,
                    ticker.quoteVolume
            );

        } catch (Exception e) {
            return null;
        }
    }

    private double calculateTrend(
            List<MarketDataClient.Candle> candles,
            int fastPeriod,
            int slowPeriod) {

        double[] closes = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            closes[i] = candles.get(i).close;
        }

        double fast = ema(closes, fastPeriod);
        double slow = ema(closes, slowPeriod);

        if (slow == 0) return 0;

        return (fast / slow - 1.0) * 100.0;
    }

    private double calculateMomentum(
            List<MarketDataClient.Candle> candles,
            int lookback) {

        int last = candles.size() - 1;
        int previous = last - lookback;

        if (previous < 0) return 0;

        double oldPrice = candles.get(previous).close;
        double newPrice = candles.get(last).close;

        if (oldPrice <= 0) return 0;

        return (newPrice / oldPrice - 1.0) * 100.0;
    }

    private double calculateVolumeRatio(
            List<MarketDataClient.Candle> candles) {

        int size = candles.size();
        if (size < 20) return 0;

        double recent = 0;
        double baseline = 0;

        for (int i = size - 3; i < size; i++) {
            recent += candles.get(i).volume;
        }

        for (int i = size - 18; i < size - 3; i++) {
            baseline += candles.get(i).volume;
        }

        recent /= 3.0;
        baseline /= 15.0;

        return baseline > 0 ? recent / baseline : 0;
    }

    private double ema(double[] values, int period) {
        double result = values[0];
        double alpha = 2.0 / (period + 1.0);

        for (int i = 1; i < values.length; i++) {
            result = alpha * values[i]
                    + (1.0 - alpha) * result;
        }

        return result;
    }

    public void shutdown() {
        cancelled = true;
        executor.shutdownNow();
    }
}
