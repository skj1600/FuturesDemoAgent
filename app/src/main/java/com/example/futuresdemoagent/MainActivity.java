
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
    private AiEngine aiEngine;

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
        trader = new DemoTrader(
                settings.getDemoBalance(),
                settings.getRiskPercent(),
                settings.getDailyLossLimitPercent());
        assistant = new ChatAssistant();
        aiEngine = new AiEngine(settings.getMinimumScore(), 1.5);
        scanner = new MarketScanner(
                settings.getMinimumVolumeUsdt(),
                settings.getMinimumScore());

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

        TextView title = makeText("FUTURES AI AGENT", 23, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        dashboard = makeText("", 14, Color.rgb(80, 220, 165));
        root.addView(dashboard);

        status = makeText("Status: STOPPED", 14, Color.YELLOW);
        root.addView(status);

        LinearLayout nav = new LinearLayout(this);
        tradesButton = makeButton("TRADES");
        chatButton = makeButton("AI CHAT");
        nav.addView(tradesButton, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(chatButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);

        LinearLayout controls = new LinearLayout(this);
        startButton = makeButton("START DEMO");
        stopButton = makeButton("STOP");
        controls.addView(startButton, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(stopButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);

        scanButton = makeButton("SCAN NOW");
        root.addView(scanButton);

        output = makeText("Ready. Tap SCAN NOW to analyze markets.",
                13, Color.WHITE);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        chatLog = makeText("", 13, Color.WHITE);
        chatInput = new EditText(this);
        chatInput.setSingleLine(false);
        chatInput.setHint("Ask about signals or trades...");
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
            scanner.startScan();
            status.setText("DEMO AUTO-SCAN ACTIVE");
            status.setTextColor(Color.GREEN);
            handler.removeCallbacks(scanLoop);
            runScan();
            handler.postDelayed(scanLoop,
                    settings.getScanIntervalSeconds() * 1000L);
        });

        stopButton.setOnClickListener(v -> {
            running = false;
            handler.removeCallbacks(scanLoop);
            scanner.stopScan();
            status.setText("STOPPED — DEMO ONLY");
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
            output.setText("Offline assistant. Chat integration is basic.");
            chatLog.setVisibility(View.VISIBLE);
            chatInput.setVisibility(View.VISIBLE);
        } else {
            chatLog.setVisibility(View.GONE);
            chatInput.setVisibility(View.GONE);
            if ("trades".equals(page)) {
                showTrades();
            } else {
                output.setText("Scanner ready. Tap SCAN NOW.");
            }
        }
    }

    private void runScan() {
        if (scanning) {
            status.setText("Scan already running. Please wait.");
            return;
        }
        if (worker.isShutdown()) {
            status.setText("Worker stopped. Restart the app.");
            return;
        }

        scanning = true;
        scanButton.setEnabled(false);
        scanButton.setText("SCANNING...");
        status.setText("Connecting to Binance Futures...");
        output.setText("Fetching market data. Please wait...");

        try {
            worker.execute(() -> {
                String display;
                boolean failed = false;

                try {
                    List<MarketScanner.ScanResult> results =
                            scanner.scanTopLiquidMarkets(30);

                    List<AiEngine.Candidate> candidates = new ArrayList<>();

                    for (MarketScanner.ScanResult r : results) {
                        if (r == null || r.signal == null) continue;
                        if (r.signal.isTradeCandidate()) {
                            candidates.add(new AiEngine.Candidate(
                                    r.signal, r.volumeRatio, r.trendAlignment));
                        }
                    }

                    List<AiEngine.Candidate> ranked =
                            aiEngine.rankCandidates(candidates);

                    for (MarketScanner.ScanResult r : results) {
                        if (r != null && r.signal != null) {
                            trader.updatePrice(r.signal.symbol, r.price);
                        }
                    }

                    int newTrades = 0;
                    AiEngine.Candidate best =
                            ranked.isEmpty() ? null : ranked.get(0);

                    if (running && best != null && trader.canTrade()) {
                        TradeSignal s = best.signal;
                        DemoTrader.Trade trade = trader.openTrade(
                                s.symbol, s.direction.name(),
                                s.entryPrice, s.stopLoss, s.takeProfit);
                        if (trade != null) newTrades++;
                    }

                    StringBuilder b = new StringBuilder();
                    b.append("Markets analyzed: ").append(results.size())
                            .append("\nQualified setups: ").append(ranked.size())
                            .append("\nNew demo trades: ").append(newTrades)
                            .append("\nReserved open risk: ")
                            .append(String.format(Locale.US, "%.4f USDT",
                                    trader.getReservedRiskAmount()))
                            .append("\n\n");

                    if (best == null) {
                        b.append("No setup passed filters. WAIT.\n\n");
                    } else {
                        b.append("TOP SETUP\n")
                                .append(aiEngine.explain(best)).append("\n\n");
                    }

                    for (MarketScanner.ScanResult r : results) {
                        if (r == null || r.signal == null) continue;
                        TradeSignal s = r.signal;
                        b.append(s.symbol).append(" | ")
                                .append(s.direction).append(" | Score ")
                                .append(String.format(Locale.US, "%.0f", s.score))
                                .append("\n").append(s.reason).append("\n")
                                .append(String.format(Locale.US,
                                        "Price %.8f | 24h %+.2f%% | Volume %.1fM\n",
                                        r.price, r.change24h,
                                        r.quoteVolume / 1_000_000.0));

                        if (s.isTradeCandidate()) {
                            b.append(String.format(Locale.US,
                                    "Entry %.8f | SL %.8f | TP %.8f\n",
                                    s.entryPrice, s.stopLoss, s.takeProfit));
                        }
                        b.append("\n");
                    }
                    display = b.toString();

                } catch (Exception e) {
                    failed = true;
                    display = "SCAN FAILED\n"
                            + e.getClass().getSimpleName() + "\n"
                            + String.valueOf(e.getMessage())
                            + "\n\nCheck internet connection and try again.";
                }

                final String finalDisplay = display;
                final boolean scanFailed = failed;

                runOnUiThread(() -> {
                    if ("scanner".equals(currentPage)) {
                        output.setText(finalDisplay);
                    }
                    status.setText(scanFailed
                            ? "SCAN FAILED — SEE ERROR BELOW"
                            : "SCAN COMPLETE — DEMO ONLY");
                    status.setTextColor(scanFailed ? Color.RED : Color.GREEN);
                    scanButton.setEnabled(true);
                    scanButton.setText("SCAN NOW");
                    updateDashboard();
                    scanning = false;
                });
            });
        } catch (RejectedExecutionException e) {
            scanning = false;
            scanButton.setEnabled(true);
            scanButton.setText("SCAN NOW");
            status.setText("Scan could not start. Restart app.");
            output.setText(e.toString());
        }
    }

    private void updateDashboard() {
        if (dashboard == null || trader == null) return;
        dashboard.setText(String.format(Locale.US,
                "Demo balance: %.2f USDT | Open: %d | Realized P/L: %.2f",
                trader.getBalance(), trader.getOpenTradeCount(),
                trader.getRealizedProfitLoss()));
    }

    private void showTrades() {
        StringBuilder b = new StringBuilder("SIMULATED TRADE HISTORY\n\n");
        List<DemoTrader.Trade> trades = trader.getTradeHistory();

        if (trades.isEmpty()) b.append("No demo trades yet.\n");

        for (DemoTrader.Trade t : trades) {
            b.append(t.symbol).append(" | ").append(t.side).append("\n");
            b.append(String.format(Locale.US,
                    "Entry %.8f | SL %.8f | TP %.8f\n",
                    t.entryPrice, t.stopLoss, t.takeProfit));

            if (t.closed) {
                b.append(String.format(Locale.US,
                        "Exit %.8f | P/L %.4f | %s\n",
                        t.exitPrice, t.profitLoss, t.exitReason));
            } else {
                b.append("OPEN — SIMULATED\n");
            }
            b.append("\n");
        }

        output.setText(b.toString());
        updateDashboard();
    }

    private void sendChat() {
        String message = chatInput.getText().toString().trim();
        if (message.isEmpty()) return;

        String reply = assistant.reply(message);
        chatTranscript += "You: " + message
                + "\nAssistant: " + reply + "\n\n";
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
