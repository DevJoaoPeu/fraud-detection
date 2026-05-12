package com.frauddetection.service;

import com.frauddetection.config.FraudProperties;
import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.TransactionRepository;
import com.frauddetection.dto.TransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FraudScoringService {

    private static final double KNN_WEIGHT  = 0.7;
    private static final double RULE_WEIGHT = 0.3;

    private final TransactionRepository transactionRepository;
    private final FraudProperties properties;
    private final RuleBasedScoringService ruleBasedScoringService;

    public FraudScoringService(TransactionRepository transactionRepository,
                               FraudProperties properties,
                               RuleBasedScoringService ruleBasedScoringService) {
        this.transactionRepository = transactionRepository;
        this.properties = properties;
        this.ruleBasedScoringService = ruleBasedScoringService;
    }

    public ScoringResult score(float[] embedding, TransactionRequest request) {
        int k = properties.scoring().similarTransactionsCount();
        var rows = transactionRepository.findSimilarWithScore(toVectorString(embedding), k);

        double ruleScore = ruleBasedScoringService.score(request);
        double finalScore;

        if (rows.isEmpty()) {
            // Cold start: no labeled history — rely entirely on rules
            finalScore = ruleScore;
        } else {
            double knnScore = computeWeightedKnnScore(rows);
            // Blend KNN (primary) with rules (secondary signal)
            finalScore = clamp(KNN_WEIGHT * knnScore + RULE_WEIGHT * ruleScore);
        }

        return new ScoringResult(finalScore, resolveDecision(finalScore));
    }

    private double computeWeightedKnnScore(List<Object[]> rows) {
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
