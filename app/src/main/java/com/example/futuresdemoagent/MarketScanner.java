
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class MarketScanner {

    private final MarketDataClient marketClient;
    private final StrategyEngine strategy;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

    private final double minimumVolumeUsdt;
    private volatile boolean cancelled = false;
    private volatile String lastScanMessage = "Ready";
    private volatile String lastError = "";
    private volatile int analyzedCount = 0;
    private volatile int failedCount = 0;

    public String getLastScanMessage() {
        return lastScanMessage;
    }

    public MarketScanner() {
        this(5_000_000.0, 70.0);
    }

    public MarketScanner(double minimumVolumeUsdt,
                         double minimumScore) {
        this.minimumVolumeUsdt =
                Double.isNaN(minimumVolumeUsdt)
                || Double.isInfinite(minimumVolumeUsdt)
                ? 5_000_000.0
                : Math.max(0, minimumVolumeUsdt);

        marketClient = new MarketDataClient();
        strategy = new StrategyEngine(minimumScore, 1.5);
    }

    public void stopScan() {
        cancelled = true;
        lastScanMessage = "Scan cancelled by user.";
    }

    public void startScan() {
        cancelled = false;
    }

    public List<ScanResult> scanTopLiquidMarkets(int maxMarkets)
            throws Exception {

        if (maxMarkets < 1) {
            throw new IllegalArgumentException(
                    "maxMarkets must be positive");
        }

        if (executor.isShutdown()) {
            throw new IllegalStateException(
                    "Scanner stopped. Restart the app.");
        }

        cancelled = false;
        lastError = "";
        analyzedCount = 0;
        failedCount = 0;
        lastScanMessage = "Connecting to Binance Futures...";

        List<MarketDataClient.Ticker> tickers;

        try {
            tickers = marketClient.getUsdtPerpetualTickers();
        } catch (Exception e) {
            lastError = errorText(e);
            lastScanMessage = "Binance market data failed: " + lastError;
            throw new Exception(lastScanMessage, e);
        }

        if (tickers.isEmpty()) {
            lastScanMessage = "Binance returned zero eligible USDT perpetual markets.";
            return Collections.emptyList();
        }

        List<MarketDataClient.Ticker> eligible = new ArrayList<>();

        for (MarketDataClient.Ticker t : tickers) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                break;
            }

            if (t.price > 0 && t.quoteVolume >= minimumVolumeUsdt) {
                eligible.add(t);
            }
        }

        int limit = Math.min(maxMarkets, eligible.size());

        lastScanMessage = "Markets: " + tickers.size()
                + " | Volume-qualified: " + eligible.size()
                + " | Scanning: " + limit;

        List<Future<ScanResult>> futures = new ArrayList<>();

        for (int i = 0; i < limit && !cancelled; i++) {
            final MarketDataClient.Ticker ticker = eligible.get(i);
            futures.add(executor.submit(() -> analyzeTicker(ticker)));
        }

        List<ScanResult> results = new ArrayList<>();

        for (Future<ScanResult> future : futures) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                future.cancel(true);
                continue;
            }

            try {
                ScanResult result = future.get(45, TimeUnit.SECONDS);
                if (result != null) {
                    results.add(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                break;
            } catch (TimeoutException e) {
                failedCount++;
                future.cancel(true);
                rememberError("Candle request timed out");
            } catch (ExecutionException e) {
                failedCount++;
                rememberError(errorText(e.getCause() == null
                        ? e : e.getCause()));
            } catch (Exception e) {
                failedCount++;
                future.cancel(true);
                rememberError(errorText(e));
            }
        }

        results.sort((a, b) ->
                Double.compare(b.signal.score, a.signal.score));

        if (cancelled) {
            lastScanMessage = "Scan cancelled.";
        } else if (!results.isEmpty()) {
            lastScanMessage = "Scan complete. Analyzed: "
                    + analyzedCount + " | Results: " + results.size()
                    + " | Candle errors: " + failedCount;
        } else if (!lastError.isEmpty()) {
            lastScanMessage = "No results. Analyzed: " + analyzedCount
                    + " | Errors: " + failedCount
                    + " | First error: " + lastError;
        } else {
            lastScanMessage = "No setups passed the filters. Markets checked: "
                    + limit + " | Valid analyses: " + analyzedCount
                    + " | Try again when market conditions change.";
        }

        return Collections.unmodifiableList(results);
    }

    private ScanResult analyzeTicker(MarketDataClient.Ticker ticker) {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            return null;
        }

        try {
            List<MarketDataClient.Candle> one =
                    marketClient.getCandles(ticker.symbol, "1m", 40);

            if (cancelled || Thread.currentThread().isInterrupted()) {
                return null;
            }

            List<MarketDataClient.Candle> five =
                    marketClient.getCandles(ticker.symbol, "5m", 40);

            if (cancelled || Thread.currentThread().isInterrupted()) {
                return null;
            }

            List<MarketDataClient.Candle> fifteen =
                    marketClient.getCandles(ticker.symbol, "15m", 40);

            long now = System.currentTimeMillis();
            one = closedCandles(one, now);
            five = closedCandles(five, now);
            fifteen = closedCandles(fifteen, now);

            if (one.size() < 25 || five.size() < 25
                    || fifteen.size() < 25) {
                failedCount++;
                rememberError("Insufficient closed candles for "
                        + ticker.symbol);
                return null;
            }

            double t1 = calculateTrend(one, 7, 20);
            double t5 = calculateTrend(five, 7, 20);
            double t15 = calculateTrend(fifteen, 7, 20);
            double momentum = calculateMomentum(one, 5);
            double volumeRatio = calculateVolumeRatio(one);

            StrategyEngine.MarketData data =
                    new StrategyEngine.MarketData(
                            ticker.symbol, ticker.price,
                            ticker.change24h, t1, t5, t15,
                            momentum, volumeRatio);

            TradeSignal signal = strategy.analyze(data);
            if (signal == null) {
                failedCount++;
                rememberError("Strategy returned no signal for "
                        + ticker.symbol);
                return null;
            }

            analyzedCount++;

            double alignment = 0;

            if (signal.direction == TradeSignal.Direction.LONG) {
                alignment = ((t1 > 0 ? 1 : 0)
                        + (t5 > 0 ? 1 : 0)
                        + (t15 > 0 ? 1 : 0)) / 3.0;
            } else if (signal.direction == TradeSignal.Direction.SHORT) {
                alignment = ((t1 < 0 ? 1 : 0)
                        + (t5 < 0 ? 1 : 0)
                        + (t15 < 0 ? 1 : 0)) / 3.0;
            }

            return new ScanResult(
                    signal, ticker.price, ticker.change24h,
                    ticker.quoteVolume, volumeRatio, alignment);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            failedCount++;
            rememberError(ticker.symbol + ": " + errorText(e));
            return null;
        }
    }

    private synchronized void rememberError(String message) {
        if (lastError.isEmpty() && message != null) {
            lastError = message;
        }
    }

    private String errorText(Throwable e) {
        if (e == null) return "Unknown error";

        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }

        return e.getClass().getSimpleName() + ": " + message;
    }

    private List<MarketDataClient.Candle> closedCandles(
            List<MarketDataClient.Candle> candles, long now) {
        List<MarketDataClient.Candle> closed = new ArrayList<>();

        for (MarketDataClient.Candle candle : candles) {
            if (candle.closeTime < now) {
                closed.add(candle);
            }
        }
        return closed;
    }

    private double calculateTrend(
            List<MarketDataClient.Candle> candles,
            int fastPeriod, int slowPeriod) {

        double[] closes = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            closes[i] = candles.get(i).close;
        }

        double fast = ema(closes, fastPeriod);
        double slow = ema(closes, slowPeriod);

        return slow <= 0 ? 0 : (fast / slow - 1.0) * 100.0;
    }

    private double calculateMomentum(
            List<MarketDataClient.Candle> candles, int lookback) {

        int last = candles.size() - 1;
        int previous = last - lookback;

        if (previous < 0) return 0;

        double oldPrice = candles.get(previous).close;
        double newPrice = candles.get(last).close;

        return oldPrice <= 0 ? 0
                : (newPrice / oldPrice - 1.0) * 100.0;
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
        if (values.length == 0) return 0;

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

    public static class ScanResult {
        public final TradeSignal signal;
        public final double price;
        public final double change24h;
        public final double quoteVolume;
        public final double volumeRatio;
        public final double trendAlignment;

        public ScanResult(
                TradeSignal signal,
                double price,
                double change24h,
                double quoteVolume,
                double volumeRatio,
                double trendAlignment) {
            this.signal = signal;
            this.price = price;
            this.change24h = change24h;
            this.quoteVolume = quoteVolume;
            this.volumeRatio = volumeRatio;
            this.trendAlignment = trendAlignment;
        }
    }
}
