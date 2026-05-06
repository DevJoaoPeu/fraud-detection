package com.frauddetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fraud")
public record FraudProperties(
        Threshold threshold,
        Scoring scoring
) {
    public record Threshold(double flag, double block) {}
    public record Scoring(int similarTransactionsCount, double coldStartScore) {}
}
