package com.frauddetection.service;

import com.frauddetection.domain.entity.FraudAuditLogEntity;
import com.frauddetection.domain.entity.TransactionEntity;
import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.FraudAuditLogRepository;
import com.frauddetection.domain.repository.TransactionRepository;
import com.frauddetection.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SeedService {

    private final TransactionRepository transactionRepository;
    private final FraudAuditLogRepository auditLogRepository;
    private final EmbeddingService embeddingService;

    @Transactional
    public void seed(TransactionRequest request, FraudDecision decision, double fraudScore) {
        if (transactionRepository.findByTransactionId(request.transactionId()).isPresent()) {
            return;
        }

        float[] embedding = embeddingService.generateEmbedding(request);

        var entity = new TransactionEntity();
        entity.setTransactionId(request.transactionId());
        entity.setAmount(request.amount());
        entity.setMerchantId(request.merchantId());
        entity.setMerchantCategory(request.merchantCategory());
        entity.setCountryCode(request.countryCode());
        entity.setCurrencyCode(request.currencyCode());
        entity.setEmbedding(embedding);
        entity.setFraudScore(BigDecimal.valueOf(fraudScore));
        entity.setDecision(decision);
        entity.setProcessedAt(LocalDateTime.now());
        entity = transactionRepository.save(entity);

        var log = new FraudAuditLogEntity();
        log.setTransaction(entity);
        log.setFraudScore(BigDecimal.valueOf(fraudScore));
        log.setDecision(decision);
        log.setReasoning("Seed data");
        log.setModelVersion(embeddingService.getModelVersion());
        auditLogRepository.save(log);
    }
}
