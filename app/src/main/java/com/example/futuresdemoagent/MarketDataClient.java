
package com.example.futuresdemoagent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketDataClient {

    private static final String BASE_URL =
            "https://fapi.binance.com";

    public static class Ticker {
        public final String symbol;
        public final double price;
        public final double change24h;
        public final double quoteVolume;

        public Ticker(String symbol, double price,
                      double change24h, double quoteVolume) {
            this.symbol = symbol;
            this.price = price;
            this.change24h = change24h;
            this.quoteVolume = quoteVolume;
        }
    }

    public static class Candle {
        public final long openTime;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final double volume;
        public final long closeTime;

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
        JSONArray data = getArray(
                BASE_URL + "/fapi/v1/ticker/24hr");

        ArrayList<Ticker> tickers = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            String symbol = item.optString("symbol", "");

            if (!symbol.endsWith("USDT")) continue;

            double price = item.optDouble("lastPrice", 0);
            double change = item.optDouble("priceChangePercent", 0);
            double volume = item.optDouble("quoteVolume", 0);

            if (price > 0) {
                tickers.add(new Ticker(
                        symbol, price, change, volume));
            }
        }

        tickers.sort((a, b) ->
                Double.compare(b.quoteVolume, a.quoteVolume));

        return Collections.unmodifiableList(tickers);
    }

    public List<Candle> getCandles(
            String symbol, String interval, int limit)
            throws Exception {

        if (symbol == null ||
                !symbol.matches("[A-Z0-9]{2,30}USDT")) {
            throw new IllegalArgumentException("Invalid symbol");
        }

        if (!interval.matches("1m|3m|5m|15m|30m|1h|4h")) {
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
        ArrayList<Candle> candles = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONArray k = data.getJSONArray(i);

            candles.add(new Candle(
                    k.getLong(0),
                    k.getDouble(1),
                    k.getDouble(2),
                    k.getDouble(3),
                    k.getDouble(4),
                    k.getDouble(5),
                    k.getLong(6)
            ));
        }

        return Collections.unmodifiableList(candles);
    }

    private JSONArray getArray(String address) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty(
                "User-Agent", "FuturesDemoAgent/2.0");

        try {
            int code = connection.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception(
                        "Binance HTTP error: " + code);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream()));

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            reader.close();
            return new JSONArray(body.toString());

        } finally {
            connection.disconnect();
        }
    }
}
