
package com.example.futuresdemoagent;

import java.util.Locale;

public class ChatAssistant {

    public String reply(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Bhai, apna sawal likho.";
        }

        String q = message.toLowerCase(Locale.ROOT).trim();

        if (containsAny(q, "hello", "hi", "hey", "namaste", "नमस्ते")) {
            return "Namaste bhai! Main Futures Demo Agent hoon. "
                    + "Scanner, signals aur risk ke baare mein poochho.";
        }

        if (containsAny(q, "signal", "setup", "coin", "कौन सा coin")) {
            return "Scanner ke LONG WATCH ya SHORT WATCH signal ko "
                    + "candidate samjho, guaranteed entry nahi. "
                    + "Trend, volume, Stop-Loss aur risk verify karo.";
        }

        if (containsAny(q, "stop loss", "sl", "स्टॉप लॉस")) {
            return "Stop-Loss entry se pehle define karo. "
                    + "LONG mein aam taur par SL entry ke neeche, "
                    + "SHORT mein entry ke upar hota hai.";
        }

        if (containsAny(q, "take profit", "target", "tp")) {
            return "Take-Profit target risk/reward aur market structure "
                    + "ke hisaab se set karo. Fees bhi calculation mein shamil karo.";
        }

        if (containsAny(q, "loss", "nuksan", "नुकसान", "profit", "पैसा")) {
            return "Profit guaranteed nahi hai. Position size ko risk ke "
                    + "hisab se rakho aur demo trade history evaluate karo.";
        }

        if (containsAny(q, "balance", "capital", "पैसा कितना")) {
            return "Balance aur P/L ke exact numbers tabhi bata sakta hoon "
                    + "jab app ka trading dashboard is assistant se connect hoga.";
        }

        if (containsAny(q, "long", "buy", "खरीद")) {
            return "LONG ka matlab price badhne par profit ki ummeed. "
                    + "Entry se pehle trend, volume aur risk check karo.";
        }

        if (containsAny(q, "short", "sell", "बेचना")) {
            return "SHORT ka matlab price girne par profit ki ummeed. "
                    + "Leverage risk badhata hai; pehle demo mein test karo.";
        }

        if (containsAny(q, "stop", "band", "रोक दो")) {
            return "Trading rokne ke liye app ka STOP button use karo. "
                    + "Chat command abhi trading ko control nahi karti.";
        }

        return "Main abhi basic offline assistant hoon. "
                + "Poochho: signals, LONG/SHORT, Stop-Loss, "
                + "Take-Profit ya risk management. "
                + "Live market samajhne ke liye real AI integration abhi baaki hai.";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
