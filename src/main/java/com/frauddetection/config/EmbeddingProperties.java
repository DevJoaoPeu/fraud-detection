package com.frauddetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimension,
        String model,
        String apiKey,
        String baseUrl
) {}
