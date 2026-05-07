package com.frauddetection.service;

import com.frauddetection.config.EmbeddingProperties;
import com.frauddetection.dto.TransactionRequest;
import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final Client client;
    private final EmbeddingProperties properties;

    public EmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
        this.client = new Client();
    }

    public String getModelVersion() {
        return properties.model();
    }

    public float[] generateEmbedding(TransactionRequest transaction) {
        String text = buildTransactionText(transaction);
        return callGeminiEmbedding(text);
    }

    private float[] callGeminiEmbedding(String text) {
        try {
            EmbedContentConfig config = EmbedContentConfig.builder()
                    .outputDimensionality(properties.dimension())
                    .build();

            EmbedContentResponse response = client.models.embedContent(
                    properties.model(), text, config);

            if (response.embedding() == null || response.embedding().values() == null) {
                throw new EmbeddingException("Gemini returned empty embedding");
            }

            return toFloatArray(response.embedding().values());

        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini API error: model={} message={}", properties.model(), e.getMessage());
            throw new EmbeddingException("Gemini API error: " + e.getMessage(), e);
        }
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

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
