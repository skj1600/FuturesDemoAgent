
package com.example.futuresdemoagent;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {

    private static final String PREFS = "futures_agent_settings";

    private static final String KEY_SCAN_INTERVAL = "scan_interval";
    private static final String KEY_MIN_SCORE = "min_score";
    private static final String KEY_MIN_VOLUME = "min_volume";
    private static final String KEY_RISK_PERCENT = "risk_percent";
    private static final String KEY_DAILY_LOSS = "daily_loss";
    private static final String KEY_DEMO_BALANCE = "demo_balance";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getScanIntervalSeconds() {
        return prefs.getInt(KEY_SCAN_INTERVAL, 60);
    }

    public void setScanIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_SCAN_INTERVAL,
                Math.max(30, Math.min(seconds, 3600))).apply();
    }

    public int getMinimumScore() {
        return prefs.getInt(KEY_MIN_SCORE, 70);
    }

    public void setMinimumScore(int score) {
        prefs.edit().putInt(KEY_MIN_SCORE,
                Math.max(50, Math.min(score, 95))).apply();
    }

    public double getMinimumVolumeUsdt() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_MIN_VOLUME,
                Double.doubleToLongBits(5_000_000.0)));
    }

    public void setMinimumVolumeUsdt(double volume) {
        if (!Double.isNaN(volume) && !Double.isInfinite(volume)
                && volume >= 0) {
            prefs.edit().putLong(KEY_MIN_VOLUME,
                    Double.doubleToLongBits(volume)).apply();
        }
    }

    public double getRiskPercent() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_RISK_PERCENT, Double.doubleToLongBits(0.5)));
    }

    public void setRiskPercent(double percent) {
        if (percent >= 0.1 && percent <= 1.0) {
            prefs.edit().putLong(KEY_RISK_PERCENT,
                    Double.doubleToLongBits(percent)).apply();
        }
    }

    public double getDailyLossLimitPercent() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_DAILY_LOSS, Double.doubleToLongBits(2.0)));
    }

    public void setDailyLossLimitPercent(double percent) {
        if (percent >= 0.5 && percent <= 5.0) {
            prefs.edit().putLong(KEY_DAILY_LOSS,
                    Double.doubleToLongBits(percent)).apply();
        }
    }

    public double getDemoBalance() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_DEMO_BALANCE, Double.doubleToLongBits(1500.0)));
    }

    public void setDemoBalance(double balance) {
        if (!Double.isNaN(balance) && !Double.isInfinite(balance)
                && balance >= 0) {
            prefs.edit().putLong(KEY_DEMO_BALANCE,
                    Double.doubleToLongBits(balance)).apply();
        }
    }

    public void resetSettings() {
        prefs.edit().clear().apply();
    }
}
