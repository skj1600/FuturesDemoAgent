
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AiEngine {

    private final double minimumScore;
    private final double minimumRiskReward;

    public AiEngine() {
        this(70.0, 1.5);
    }

    public AiEngine(double minimumScore, double minimumRiskReward) {
        this.minimumScore = clamp(minimumScore, 50, 95);
        this.minimumRiskReward = clamp(minimumRiskReward, 1.0, 5.0);
    }

    public static class Candidate {
        public final TradeSignal signal;
        public final double volumeRatio;
        public final double trendAlignment;

        public Candidate(TradeSignal signal,
                         double volumeRatio,
                         double trendAlignment) {
            this.signal = signal;
            this.volumeRatio = volumeRatio;
            this.trendAlignment = trendAlignment;
        }
    }

    public List<Candidate> rankCandidates(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Candidate> valid = new ArrayList<>();

        for (Candidate c : candidates) {
            if (c == null || c.signal == null) continue;

            TradeSignal s = c.signal;

            if (!s.isTradeCandidate()) continue;
            if (!finite(c.volumeRatio) || c.volumeRatio < 1.2) continue;
            if (!finite(c.trendAlignment)
                    || c.trendAlignment < 0.99) continue;
            if (!finite(s.score) || s.score < minimumScore) continue;
            if (s.getRiskRewardRatio() < minimumRiskReward) continue;

            valid.add(c);
        }

        valid.sort(new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(calculateQuality(b),
                        calculateQuality(a));
            }
        });

        return Collections.unmodifiableList(valid);
    }

    public double calculateQuality(Candidate c) {
        if (c == null || c.signal == null) return 0;

        double volumeBonus = Math.min(12,
                Math.max(0, c.volumeRatio - 1.0) * 6);

        double alignmentBonus = Math.min(10,
                Math.max(0, c.trendAlignment) * 10);

        double rr = c.signal.getRiskRewardRatio();
        double rrBonus = Math.min(10, Math.max(0, rr - 1.0) * 5);

        return Math.min(100,
                c.signal.score + volumeBonus
                        + alignmentBonus + rrBonus);
    }

    public Candidate selectBest(List<Candidate> candidates) {
        List<Candidate> ranked = rankCandidates(candidates);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    public String explain(Candidate candidate) {
        if (candidate == null || candidate.signal == null) {
            return "Koi setup filters pass nahi kar raha. WAIT.";
        }

        TradeSignal s = candidate.signal;

        return String.format(Locale.US,
                "%s | %s | Score %.1f | RR %.2f | Volume %.2fx | Alignment %.0f%% | %s",
                s.symbol, s.direction, s.score,
                s.getRiskRewardRatio(), candidate.volumeRatio,
                candidate.trendAlignment * 100, s.reason);
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double clamp(double value, double min, double max) {
        if (!finite(value)) return min;
        return Math.max(min, Math.min(value, max));
    }
}
