package com.frauddetection.service;

import com.frauddetection.domain.entity.FraudAuditLogEntity;
import com.frauddetection.domain.entity.TransactionEntity;
import com.frauddetection.domain.repository.FraudAuditLogRepository;
import com.frauddetection.domain.repository.TransactionRepository;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final FraudAuditLogRepository auditLogRepository;
    private final EmbeddingService embeddingService;
    private final FraudScoringService fraudScoringService;

    @Transactional
    public TransactionResponse analyze(TransactionRequest request) {
        if (transactionRepository.findByTransactionId(request.transactionId()).isPresent()) {
            throw new DuplicateTransactionException(request.transactionId());
        }

        float[] embedding = embeddingService.generateEmbedding(request);
        var scoring = fraudScoringService.score(embedding, request);

        var entity = buildEntity(request, embedding, scoring);
        entity = transactionRepository.save(entity);

        auditLogRepository.save(buildAuditLog(entity, scoring));

        return new TransactionResponse(
                entity.getTransactionId(),
                entity.getAmount(),
                entity.getFraudScore(),
                entity.getDecision(),
                entity.getProcessedAt()
        );
    }

    private TransactionEntity buildEntity(TransactionRequest request,
                                          float[] embedding,
                                          FraudScoringService.ScoringResult scoring) {
        var entity = new TransactionEntity();
        entity.setTransactionId(request.transactionId());
        entity.setAmount(request.amount());
        entity.setMerchantId(request.merchantId());
        entity.setMerchantCategory(request.merchantCategory());
        entity.setCountryCode(request.countryCode());
        entity.setCurrencyCode(request.currencyCode());
        entity.setEmbedding(embedding);
        entity.setFraudScore(BigDecimal.valueOf(scoring.fraudScore()));
        entity.setDecision(scoring.decision());
        entity.setProcessedAt(LocalDateTime.now());
        return entity;
    }

    private FraudAuditLogEntity buildAuditLog(TransactionEntity entity,
                                              FraudScoringService.ScoringResult scoring) {
        var log = new FraudAuditLogEntity();
        log.setTransaction(entity);
        log.setFraudScore(BigDecimal.valueOf(scoring.fraudScore()));
        log.setDecision(scoring.decision());
        log.setModelVersion(embeddingService.getModelVersion());
        return log;
    }
}
