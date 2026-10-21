
package com.example.futuresdemoagent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarketDataClient {

    private static final String BASE_URL = "https://fapi.binance.com";

    public static class Ticker {
        public final String symbol;
        public final double price, change24h, quoteVolume;

        public Ticker(String symbol, double price,
                      double change24h, double quoteVolume) {
            this.symbol = symbol;
            this.price = price;
            this.change24h = change24h;
            this.quoteVolume = quoteVolume;
        }
    }

    public static class Candle {
        public final long openTime, closeTime;
        public final double open, high, low, close, volume;

        public Candle(long openTime, double open, double high,
                      double low, double close, double volume,
                      long closeTime) {
            this.openTime = openTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.closeTime = closeTime;
        }
    }

    public List<Ticker> getUsdtPerpetualTickers() throws Exception {
        // Exchange metadata confirms contract type and trading status.
        JSONArray exchange = getArray(BASE_URL + "/fapi/v1/exchangeInfo");
        Set<String> eligibleSymbols = new HashSet<>();

        for (int i = 0; i < exchange.length(); i++) {
            JSONObject item = exchange.getJSONObject(i);
            String symbol = item.optString("symbol", "");

            if ("TRADING".equals(item.optString("status"))
                    && "PERPETUAL".equals(item.optString("contractType"))
                    && "USDT".equals(item.optString("quoteAsset"))
                    && symbol.endsWith("USDT")) {
                eligibleSymbols.add(symbol);
            }
        }

        JSONArray data = getArray(BASE_URL + "/fapi/v1/ticker/24hr");
        List<Ticker> tickers = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            String symbol = item.optString("symbol", "");

            if (!eligibleSymbols.contains(symbol)) continue;

            double price = item.optDouble("lastPrice", 0);
            double change = item.optDouble("priceChangePercent", 0);
            double volume = item.optDouble("quoteVolume", 0);

            if (price > 0 && finite(price)
                    && finite(change) && finite(volume) && volume >= 0) {
                tickers.add(new Ticker(symbol, price, change, volume));
            }
        }

        tickers.sort((a, b) ->
                Double.compare(b.quoteVolume, a.quoteVolume));

        return Collections.unmodifiableList(tickers);
    }

    public List<Candle> getCandles(
            String symbol, String interval, int limit) throws Exception {

        if (symbol == null || !symbol.matches("[A-Z0-9]{2,30}USDT")) {
            throw new IllegalArgumentException("Invalid symbol");
        }

        if (interval == null
                || !interval.matches("1m|3m|5m|15m|30m|1h|4h")) {
            throw new IllegalArgumentException("Unsupported interval");
        }

        if (limit < 1 || limit > 1500) {
            throw new IllegalArgumentException("Invalid candle limit");
        }

        String address = BASE_URL + "/fapi/v1/klines"
                + "?symbol=" + symbol
                + "&interval=" + interval
                + "&limit=" + limit;

        JSONArray data = getArray(address);
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONArray k = data.getJSONArray(i);

            double open = k.getDouble(1);
            double high = k.getDouble(2);
            double low = k.getDouble(3);
            double close = k.getDouble(4);
            double volume = k.getDouble(5);

            if (!finite(open) || !finite(high) || !finite(low)
                    || !finite(close) || !finite(volume)
                    || open <= 0 || high <= 0 || low <= 0
                    || close <= 0 || volume < 0) {
                continue;
            }

            candles.add(new Candle(
                    k.getLong(0), open, high, low,
                    close, volume, k.getLong(6)));
        }

        return Collections.unmodifiableList(candles);
    }

    private JSONArray getArray(String address) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "FuturesDemoAgent/3.0");

        try {
            int code = connection.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("Binance HTTP error: " + code);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(), "UTF-8"));

            try {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return new JSONArray(body.toString());
            } finally {
                reader.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
