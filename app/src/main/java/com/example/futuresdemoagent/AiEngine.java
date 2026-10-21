
package com.example.futuresdemoagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AiEngine {

    private final double minimumScore;
    private final double minimumRiskReward;

    public AiEngine() {
        this(70.0, 1.5);
    }

    public AiEngine(double minimumScore, double minimumRiskReward) {
        this.minimumScore = Math.max(50,
                Math.min(minimumScore, 95));
        this.minimumRiskReward = Math.max(1.0,
                Math.min(minimumRiskReward, 5.0));
    }

    public static class Candidate {
        public final TradeSignal signal;
        public final double volumeRatio;
        public final double trendAlignment;

        public Candidate(
                TradeSignal signal,
                double volumeRatio,
                double trendAlignment) {
            this.signal = signal;
            this.volumeRatio = volumeRatio;
            this.trendAlignment = trendAlignment;
        }
    }

    public List<Candidate> rankCandidates(
            List<Candidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Candidate> valid = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.signal == null) {
                continue;
            }

            TradeSignal s = candidate.signal;

            if (!s.isTradeCandidate()) continue;
            if (s.score < minimumScore) continue;
            if (s.getRiskRewardRatio() < minimumRiskReward) continue;
            if (candidate.volumeRatio < 1.2) continue;
            if (candidate.trendAlignment < 0.75) continue;

            valid.add(candidate);
        }

        valid.sort(new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(
                        calculateQuality(b),
                        calculateQuality(a));
            }
        });

        return Collections.unmodifiableList(valid);
    }

    public double calculateQuality(Candidate c) {
        if (c == null || c.signal == null) return 0;

        double score = c.signal.score;

        double volumeBonus = Math.min(
                10, Math.max(0, c.volumeRatio - 1) * 5);

        double alignmentBonus = Math.min(
                10, Math.max(0, c.trendAlignment) * 10);

        double rrBonus = Math.min(
                10, c.signal.getRiskRewardRatio() * 3);

        return Math.min(
                100, score + volumeBonus
                + alignmentBonus + rrBonus);
    }

    public Candidate selectBest(List<Candidate> candidates) {
        List<Candidate> ranked = rankCandidates(candidates);

        if (ranked.isEmpty()) return null;

        return ranked.get(0);
    }

    public String explain(Candidate candidate) {
        if (candidate == null || candidate.signal == null) {
            return "Abhi koi valid setup nahi mila. WAIT.";
        }

        TradeSignal s = candidate.signal;

        return s.symbol + " | " + s.direction
                + " | Score: " + s.score
                + " | Risk/Reward: "
                + String.format(
                        java.util.Locale.US, "%.2f",
                        s.getRiskRewardRatio())
                + " | Volume ratio: "
                + String.format(
                        java.util.Locale.US, "%.2f",
                        candidate.volumeRatio)
                + " | Reason: " + s.reason;
    }
}
