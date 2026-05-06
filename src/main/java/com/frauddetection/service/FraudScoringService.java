package com.frauddetection.service;

import com.frauddetection.config.FraudProperties;
import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FraudScoringService {

    private final TransactionRepository transactionRepository;
    private final FraudProperties properties;

    public FraudScoringService(TransactionRepository transactionRepository,
                               FraudProperties properties) {
        this.transactionRepository = transactionRepository;
        this.properties = properties;
    }

    public ScoringResult score(float[] embedding) {
        int k = properties.scoring().similarTransactionsCount();
        var rows = transactionRepository.findSimilarWithScore(toVectorString(embedding), k);

        double fraudScore = computeWeightedScore(rows);
        FraudDecision decision = resolveDecision(fraudScore);

        return new ScoringResult(fraudScore, decision);
    }

    /**
     * KNN weighted score: each neighbor votes with weight = cosine similarity.
     * BLOCKED = 1.0, FLAGGED = 0.5, APPROVED = 0.0.
     * Falls back to cold-start score when no labeled neighbors exist.
     */
    private double computeWeightedScore(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return properties.scoring().coldStartScore();
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Object[] row : rows) {
            String decisionStr = (String) row[2];
            if (decisionStr == null) continue;

            double similarity = Math.max(0.0, ((Number) row[4]).doubleValue());
            double fraudWeight = switch (FraudDecision.valueOf(decisionStr)) {
                case BLOCKED  -> 1.0;
                case FLAGGED  -> 0.5;
                case APPROVED -> 0.0;
            };

            weightedSum += similarity * fraudWeight;
            totalWeight += similarity;
        }

        if (totalWeight == 0.0) {
            return properties.scoring().coldStartScore();
        }

        return clamp(weightedSum / totalWeight);
    }

    private FraudDecision resolveDecision(double score) {
        if (score >= properties.threshold().block()) return FraudDecision.BLOCKED;
        if (score >= properties.threshold().flag())  return FraudDecision.FLAGGED;
        return FraudDecision.APPROVED;
    }

    private double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private String toVectorString(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        return sb.append("]").toString();
    }

    public record ScoringResult(double fraudScore, FraudDecision decision) {}
}
