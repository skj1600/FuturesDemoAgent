
package com.example.futuresdemoagent;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    private TextView status, dashboard, output, chatLog;
    private EditText chatInput;
    private Button startButton, stopButton, scanButton;
    private Button tradesButton, chatButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MarketScanner scanner;
    private DemoTrader trader;
    private ChatAssistant assistant;
    private AppSettings settings;

    private volatile boolean running = false;
    private volatile boolean scanning = false;
    private String currentPage = "scanner";
    private String chatTranscript = "";

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            if (running) {
                runScan();
                handler.postDelayed(this,
                        settings.getScanIntervalSeconds() * 1000L);
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        settings = new AppSettings(this);
        trader = new DemoTrader(settings.getDemoBalance());
        assistant = new ChatAssistant();
        scanner = new MarketScanner();

        buildUI();
        updateDashboard();
    }

    private TextView makeText(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(Color.rgb(14, 19, 30));

        TextView title = makeText(
                "FUTURES AI AGENT", 23, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        dashboard = makeText("", 15, Color.rgb(80, 220, 165));
        root.addView(dashboard);

        status = makeText("Status: STOPPED", 14, Color.YELLOW);
        root.addView(status);

        LinearLayout nav = new LinearLayout(this);
        tradesButton = makeButton("TRADES");
        chatButton = makeButton("AI CHAT");
        nav.addView(tradesButton,
                new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(chatButton,
                new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);

        LinearLayout controls = new LinearLayout(this);
        startButton = makeButton("START AUTO");
        stopButton = makeButton("STOP");
        controls.addView(startButton,
                new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(stopButton,
                new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);

        scanButton = makeButton("SCAN NOW");
        root.addView(scanButton);

        output = makeText(
                "Scanner ready. Tap SCAN NOW to fetch market data.",
                13, Color.WHITE);

        ScrollView outputScroll = new ScrollView(this);
        outputScroll.addView(output);
        root.addView(outputScroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        chatLog = makeText("", 13, Color.WHITE);
        chatInput = new EditText(this);
        chatInput.setSingleLine(false);
        chatInput.setHint("Hindi mein sawal likho…");
        chatInput.setTextColor(Color.WHITE);
        chatInput.setHintTextColor(Color.LTGRAY);

        Button send = makeButton("SEND");
        LinearLayout chatControls = new LinearLayout(this);
        chatControls.addView(chatInput,
                new LinearLayout.LayoutParams(0, -2, 1));
        chatControls.addView(send);

        root.addView(chatLog);
        root.addView(chatControls);
        setContentView(root);

        showPage("scanner");

        startButton.setOnClickListener(v -> {
            if (running) return;
            running = true;
            status.setText("Status: AUTO SCANNING — DEMO ONLY");
            status.setTextColor(Color.GREEN);
            runScan();
            handler.postDelayed(scanLoop,
                    settings.getScanIntervalSeconds() * 1000L);
        });

        stopButton.setOnClickListener(v -> {
            running = false;
            handler.removeCallbacks(scanLoop);
            if (scanner != null) scanner.stopScan();
            status.setText("Status: STOPPED");
            status.setTextColor(Color.YELLOW);
        });

        scanButton.setOnClickListener(v -> runScan());

        tradesButton.setOnClickListener(v -> showPage("trades"));
        chatButton.setOnClickListener(v -> showPage("chat"));

        send.setOnClickListener(v -> sendChat());
    }

    private void showPage(String page) {
        currentPage = page;

        if ("chat".equals(page)) {
            output.setText(
                    "Offline assistant: scanner aur demo trading ke baare mein poochho.");
            chatLog.setVisibility(View.VISIBLE);
            chatInput.setVisibility(View.VISIBLE);
        } else if ("trades".equals(page)) {
            chatLog.setVisibility(View.GONE);
            chatInput.setVisibility(View.GONE);
            showTrades();
        } else {
            chatLog.setVisibility(View.GONE);
            chatInput.setVisibility(View.GONE);
            output.setText(
                    "Scanner: top liquid USDT perpetual markets.");
        }
    }

    private void runScan() {
        if (scanning) return;
        scanning = true;

        status.setText("Status: FETCHING AND ANALYZING…");

        worker.execute(() -> {
            try {
                List<MarketScanner.ScanResult> results =
                        scanner.scanTopLiquidMarkets(30);

                StringBuilder text = new StringBuilder();
                int watchCount = 0;
                int newTrades = 0;

                for (MarketScanner.ScanResult result : results) {
                    if (result == null || result.signal == null) continue;

                    TradeSignal signal = result.signal;

                    // Update existing simulated positions using fetched prices.
                    trader.updatePrice(signal.symbol, result.price);

                    // Entry is simulated locally, never sent to Binance.
                    if (running
                            && signal.isTradeCandidate()
                            && signal.score >= settings.getMinimumScore()
                            && trader.canTrade()) {

                        DemoTrader.Trade trade = trader.openTrade(
                                signal.symbol,
                                signal.direction.name(),
                                signal.entryPrice,
                                signal.stopLoss,
                                signal.takeProfit);

                        if (trade != null) newTrades++;
                    }

                    if (signal.direction != TradeSignal.Direction.WAIT) {
                        watchCount++;
                    }

                    text.append(String.format(Locale.US,
                            "%s | Score %.0f/100\n",
                            signal.symbol, signal.score));

                    text.append("Signal: ")
                            .append(signal.direction)
                            .append(" | ")
                            .append(signal.reason)
                            .append("\n");

                    if (signal.isTradeCandidate()) {
                        text.append(String.format(Locale.US,
                                "Entry %.8f | SL %.8f | TP %.8f\n",
                                signal.entryPrice,
                                signal.stopLoss,
                                signal.takeProfit));
                    }

                    text.append(String.format(Locale.US,
                            "24h change %+.2f%% | Volume %.1fM USDT\n\n",
                            result.change24h,
                            result.quoteVolume / 1_000_000.0));
                }

                final String display = text.length() == 0
                        ? "No results. Check network or try again."
                        : "Analyzed " + results.size()
                        + " markets • Watch signals: " + watchCount
                        + " • New simulated trades: " + newTrades
                        + "\n\n" + text;

                runOnUiThread(() -> {
                    if ("scanner".equals(currentPage)) {
                        output.setText(display);
                    }
                    status.setText(running
                            ? "Status: AUTO SCAN ACTIVE — SIMULATED"
                            : "Status: SCAN COMPLETE — DEMO ONLY");
                    status.setTextColor(Color.GREEN);
                    updateDashboard();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Scan error. Check internet and retry.");
                    status.setTextColor(Color.RED);
                });
            } finally {
                scanning = false;
            }
        });
    }

    private void updateDashboard() {
        if (dashboard == null || trader == null) return;

        dashboard.setText(String.format(Locale.US,
                "Demo balance: %.2f USDT | Open positions: %d | Realized P/L: %.2f",
                trader.getBalance(),
                trader.getOpenTradeCount(),
                trader.getRealizedProfitLoss()));
    }

    private void showTrades() {
        StringBuilder text = new StringBuilder();
        text.append("SIMULATED TRADE HISTORY\n\n");

        List<DemoTrader.Trade> trades = trader.getTradeHistory();

        if (trades.isEmpty()) {
            text.append("Abhi koi simulated trade nahi hai.");
        }

        for (DemoTrader.Trade t : trades) {
            text.append(t.symbol).append(" | ").append(t.side).append("\n");
            text.append(String.format(Locale.US,
                    "Entry: %.8f | SL: %.8f | TP: %.8f\n",
                    t.entryPrice, t.stopLoss, t.takeProfit));

            if (t.closed) {
                text.append(String.format(Locale.US,
                        "Exit: %.8f | P/L: %.4f | %s\n",
                        t.exitPrice, t.profitLoss, t.exitReason));
            } else {
                text.append("Status: OPEN — SIMULATED\n");
            }
            text.append("\n");
        }

        output.setText(text.toString());
        updateDashboard();
    }

    private void sendChat() {
        String message = chatInput.getText().toString().trim();
        if (message.isEmpty()) return;

        String reply = assistant.reply(message);

        chatTranscript += "You: " + message + "\n"
                + "Assistant: " + reply + "\n\n";

        chatLog.setText(chatTranscript);
        chatInput.setText("");
        showPage("chat");
        chatLog.setText(chatTranscript);
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(scanLoop);

        if (scanner != null) scanner.shutdown();
        worker.shutdownNow();

        super.onDestroy();
    }
}
