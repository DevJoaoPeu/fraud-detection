package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class RuleBasedScoringService {

    // MCC codes with elevated fraud rate
    private static final Set<String> HIGH_RISK_MCC = Set.of(
            "5944", // Jewelry stores
            "7995", // Gambling / betting
            "6051", // Non-financial institutions (crypto exchanges)
            "4829", // Money orders / wire transfers
            "6211", // Securities brokers
            "5912", // Drug stores (card-testing target)
            "5732"  // Electronics (high-value theft target)
    );

    // Countries outside BR with elevated cross-border fraud
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
            "GH", "NG", "RO", "UA", "KP", "IR", "SY", "YE", "MM"
    );

    private static final BigDecimal THRESHOLD_HIGH   = BigDecimal.valueOf(10_000);
    private static final BigDecimal THRESHOLD_MEDIUM = BigDecimal.valueOf(5_000);

    /**
     * Returns a rule-based fraud score in [0.0, 1.0].
     * Used as fallback when KNN has no labeled history, and as a
     * secondary signal when blended with KNN.
     */
    public double score(TransactionRequest request) {
        double score = 0.0;

        if (HIGH_RISK_MCC.contains(request.merchantCategory()))      score += 0.35;
        if (HIGH_RISK_COUNTRIES.contains(request.countryCode()))      score += 0.35;
        if (request.amount().compareTo(THRESHOLD_HIGH) > 0)           score += 0.20;
        else if (request.amount().compareTo(THRESHOLD_MEDIUM) > 0)    score += 0.10;

        return Math.min(1.0, score);
    }
}
