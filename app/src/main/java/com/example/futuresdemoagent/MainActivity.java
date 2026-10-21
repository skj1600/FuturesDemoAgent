package com.example.futuresdemoagent;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    TextView status, summary, results;
    Button start, stop, scan;

    Handler handler = new Handler(Looper.getMainLooper());
    volatile boolean running = false;
    volatile boolean scanning = false;

    Runnable autoScan = new Runnable() {
        @Override
        public void run() {
            if (running) {
                scanMarkets();
                handler.postDelayed(this, 60000);
            }
        }
    };

    static class Coin {
        String symbol;
        String signal = "WAIT";
        String reason = "Conditions not met";
        double price, change, volume, score;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
    }

    TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 15);
        root.setBackgroundColor(Color.rgb(14, 19, 30));

        TextView title = makeText("FUTURES AI AGENT", 24, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(makeText(
            "BINANCE USDT PERPETUAL SCANNER", 12, Color.LTGRAY));

        summary = makeText("Ready to scan", 16,
                Color.rgb(80, 220, 165));
        root.addView(summary);

        status = makeText("Status: STOPPED", 15, Color.YELLOW);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);

        start = new Button(this);
        start.setText("START AUTO");

        stop = new Button(this);
        stop.setText("STOP");

        buttons.addView(start,
                new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop,
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(buttons);

        scan = new Button(this);
        scan.setText("SCAN NOW");
        root.addView(scan);

        root.addView(makeText(
            "Analysis: 1m momentum + 5m trend + volume",
            12, Color.LTGRAY));

        results = makeText(
            "Tap SCAN NOW to load market data.",
            13, Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(makeText(
            "SIGNALS ONLY • NO REAL ORDERS",
            12, Color.LTGRAY));

        start.setOnClickListener(v -> {
            if (!running) {
                running = true;
                status.setText("Status: AUTO SCANNING");
                status.setTextColor(Color.GREEN);
                scanMarkets();
                handler.postDelayed(autoScan, 60000);
            }
        });

        stop.setOnClickListener(v -> {
            running = false;
            handler.removeCallbacks(autoScan);
            status.setText("Status: STOPPED");
            status.setTextColor(Color.YELLOW);
        });

        scan.setOnClickListener(v -> scanMarkets());

        setContentView(root);
    }

    void scanMarkets() {
        if (scanning) return;
        scanning = true;

        runOnUiThread(() ->
                status.setText("Loading Binance market data..."));

        new Thread(() -> {
            try {
                JSONArray tickers = getArray(
                    "https://fapi.binance.com/fapi/v1/ticker/24hr");

                ArrayList<Coin> coins = new ArrayList<>();

                for (int i = 0; i < tickers.length(); i++) {
                    JSONObject obj = tickers.getJSONObject(i);
                    String symbol = obj.optString("symbol", "");

                    if (!symbol.endsWith("USDT")) continue;

                    Coin c = new Coin();
                    c.symbol = symbol;
                    c.price = obj.optDouble("lastPrice", 0);
                    c.change = obj.optDouble("priceChangePercent", 0);
                    c.volume = obj.optDouble("quoteVolume", 0);

                    if (c.price > 0 && c.volume >= 5000000) {
                        coins.add(c);
                    }
                }

                coins.sort((a, b) ->
                    Double.compare(b.volume, a.volume));

                // Phone/network load ko control karne ke liye
                // har scan mein top 30 liquid markets analyze honge.
                int count = Math.min(30, coins.size());
                ArrayList<Coin> analyzed = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    Coin c = coins.get(i);

                    try {
                        analyzeCoin(c);
                    } catch (Exception e) {
                        c.signal = "WAIT";
                        c.reason = "Candle data unavailable";
                    }

                    analyzed.add(c);
                }

                analyzed.sort((a, b) ->
                    Double.compare(b.score, a.score));

                runOnUiThread(() -> showResults(analyzed));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText(
                        "Network error. Internet check karo.");
                    status.setTextColor(Color.RED);
                });
            } finally {
                scanning = false;
            }
        }).start();
    }

    void analyzeCoin(Coin c) throws Exception {
        JSONArray one = getArray(
            "https://fapi.binance.com/fapi/v1/klines?symbol="
            + c.symbol + "&interval=1m&limit=30");

        JSONArray five = getArray(
            "https://fapi.binance.com/fapi/v1/klines?symbol="
            + c.symbol + "&interval=5m&limit=30");

        if (one.length() < 25 || five.length() < 25) {
            c.reason = "Insufficient candles";
            return;
        }

        double[] close1 = new double[one.length()];
        double[] volume1 = new double[one.length()];
        double[] close5 = new double[five.length()];

        for (int i = 0; i < one.length(); i++) {
            JSONArray k = one.getJSONArray(i);
            close1[i] = k.getDouble(4);
            volume1[i] = k.getDouble(5);
        }

        for (int i = 0; i < five.length(); i++) {
            close5[i] = five.getJSONArray(i).getDouble(4);
        }

        double fast = ema(close5, 7);
        double slow = ema(close5, 20);

        double trend = (fast / slow - 1) * 100;

        double momentum =
            (close1[close1.length - 1]
            / close1[close1.length - 6] - 1) * 100;

        double recentVolume =
            average(volume1, volume1.length - 3, 3);

        double normalVolume =
            average(volume1, volume1.length - 18, 15);

        double volumeRatio = normalVolume > 0
            ? recentVolume / normalVolume : 1;

        c.score = Math.max(0, Math.min(100,
            50 + trend * 12
            + momentum * 8
            + (volumeRatio - 1) * 8));

        boolean volumeRising = volumeRatio >= 1.15;
        boolean bullish = trend > 0.04 && momentum > 0.03;
        boolean bearish = trend < -0.04 && momentum < -0.03;

        if (bullish && volumeRising && c.change < 8) {
            c.signal = "LONG WATCH";
            c.reason = "Uptrend + momentum + rising volume";
        } else if (bearish && volumeRising && c.change > -8) {
            c.signal = "SHORT WATCH";
            c.reason = "Downtrend + momentum + rising volume";
        } else {
            c.signal = "WAIT";
            c.reason = "Entry filters not satisfied";
        }
    }

    double ema(double[] values, int period) {
        double result = values[0];
        double alpha = 2.0 / (period + 1);

        for (int i = 1; i < values.length; i++) {
            result = alpha * values[i]
                   + (1 - alpha) * result;
        }

        return result;
    }

    double average(double[] values, int start, int count) {
        double total = 0;
        int n = 0;

        for (int i = Math.max(0, start);
             i < Math.min(values.length, start + count); i++) {
            total += values[i];
            n++;
        }

        return n == 0 ? 0 : total / n;
    }

    JSONArray getArray(String address) throws Exception {
        HttpURLConnection connection =
            (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try {
            int code = connection.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));

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

    void showResults(ArrayList<Coin> coins) {
        StringBuilder out = new StringBuilder();
        int signals = 0;

        for (Coin c : coins) {
            if (c.signal.endsWith("WATCH")) signals++;
        }

        out.append("Analyzed markets: ")
           .append(coins.size())
           .append("\nWatch signals: ")
           .append(signals)
           .append("\nUpdated: ")
           .append(new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()).format(new Date()))
           .append("\n\n");

        for (int i = 0; i < coins.size(); i++) {
            Coin c = coins.get(i);

            out.append(String.format(Locale.US,
                "%02d  %s | Score %.0f/100\n",
                i + 1, c.symbol, c.score));

            out.append(String.format(Locale.US,
                "Price: %.8f USDT\n24h change: %+.2f%%\nVolume: %.1fM USDT\n",
                c.price, c.change, c.volume / 1000000.0));

            out.append("Signal: ")
               .append(c.signal)
               .append("\n")
               .append(c.reason)
               .append("\n\n");
        }

        results.setText(out.toString());
        summary.setText("Analyzed " + coins.size()
                + " liquid markets");
        status.setText("Scan complete • Signals only");
        status.setTextColor(Color.GREEN);
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(autoScan);
        super.onDestroy();
    }
}
