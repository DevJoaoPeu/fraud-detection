package com.frauddetection.service;

import com.frauddetection.config.EmbeddingProperties;
import com.frauddetection.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public EmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    public String getModelVersion() {
        return properties.model();
    }

    public float[] generateEmbedding(TransactionRequest transaction) {
        String text = buildTransactionText(transaction);
        return callGeminiEmbedding(text);
    }

    private float[] callGeminiEmbedding(String text) {
        var request = new EmbedRequest(
                "models/" + properties.model(),
                new Content(List.of(new Part(text))),
                properties.dimension()
        );

        var response = restClient.post()
                .uri("/models/{model}:embedContent?key={key}",
                        properties.model(), properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    String safeUri = req.getURI().toString().replaceAll("key=[^&]+", "key=***");
                    log.error("Gemini API error: HTTP {} | URL: {} | body: {}",
                            res.getStatusCode(), safeUri, body);
                    throw new EmbeddingException("Gemini API error: HTTP " + res.getStatusCode());
                })
                .body(EmbedResponse.class);

        if (response == null || response.embedding() == null
                || response.embedding().values() == null) {
            throw new EmbeddingException("Gemini returned empty embedding");
        }

        return toFloatArray(response.embedding().values());
    }

    /**
     * Serializes transaction features as plain text for embedding.
     * Order and format are stable — changing this invalidates stored embeddings.
     */
    private String buildTransactionText(TransactionRequest tx) {
        return String.format("amount:%.2f merchant:%s category:%s country:%s currency:%s",
                tx.amount(),
                tx.merchantId(),
                tx.merchantCategory() != null ? tx.merchantCategory() : "UNKNOWN",
                tx.countryCode(),
                tx.currencyCode());
    }

    private float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }

    // ── Gemini Embedding API contracts (internal) ──────────────────────────

    private record EmbedRequest(String model, Content content, int outputDimensionality) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
    private record EmbedResponse(Embedding embedding) {}
    private record Embedding(List<Double> values) {}
}
