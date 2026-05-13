package com.frauddetection.service;

import com.frauddetection.domain.entity.TransactionEntity;
import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.FraudAuditLogRepository;
import com.frauddetection.domain.repository.TransactionRepository;
import com.frauddetection.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeedServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private FraudAuditLogRepository auditLogRepository;
    @Mock private EmbeddingService embeddingService;

    @InjectMocks
    private SeedService seedService;

    private static final TransactionRequest FRAUDULENT = new TransactionRequest(
            "SEED-001", new BigDecimal("14999.99"), "MERCH-JEWEL-GH-001", "5944", "GH", "USD"
    );

    private static final TransactionRequest LEGITIMATE = new TransactionRequest(
            "SEED-002", new BigDecimal("89.50"), "MERCH-OUTBACK-SP-001", "5812", "BR", "BRL"
    );

    @Test
    void seed_whenTransactionAlreadyExists_skipsInsert() {
        when(transactionRepository.findByTransactionId("SEED-001"))
                .thenReturn(Optional.of(new TransactionEntity()));

        seedService.seed(FRAUDULENT, FraudDecision.BLOCKED, 0.95);

        verifyNoInteractions(embeddingService);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void seed_withNewFraudulentTransaction_savesWithBlockedDecision() {
        when(transactionRepository.findByTransactionId("SEED-001")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(FRAUDULENT)).thenReturn(new float[768]);
        when(embeddingService.getModelVersion()).thenReturn("gemini-embedding-001");
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        seedService.seed(FRAUDULENT, FraudDecision.BLOCKED, 0.95);

        verify(transactionRepository).save(argThat(entity ->
                "SEED-001".equals(entity.getTransactionId()) &&
                entity.getDecision() == FraudDecision.BLOCKED &&
                entity.getFraudScore().compareTo(new BigDecimal("0.95")) == 0 &&
                entity.getEmbedding() != null
        ));
    }

    @Test
    void seed_withNewTransaction_savesAuditLogWithSeedReasoning() {
        when(transactionRepository.findByTransactionId("SEED-001")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(FRAUDULENT)).thenReturn(new float[768]);
        when(embeddingService.getModelVersion()).thenReturn("gemini-embedding-001");
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        seedService.seed(FRAUDULENT, FraudDecision.BLOCKED, 0.95);

        verify(auditLogRepository).save(argThat(log ->
                "Seed data".equals(log.getReasoning()) &&
                log.getDecision() == FraudDecision.BLOCKED &&
                "gemini-embedding-001".equals(log.getModelVersion())
        ));
    }

    @Test
    void seed_withNewLegitimateTransaction_savesWithApprovedDecision() {
        when(transactionRepository.findByTransactionId("SEED-002")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(LEGITIMATE)).thenReturn(new float[768]);
        when(embeddingService.getModelVersion()).thenReturn("gemini-embedding-001");
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        seedService.seed(LEGITIMATE, FraudDecision.APPROVED, 0.05);

        verify(transactionRepository).save(argThat(entity ->
                entity.getDecision() == FraudDecision.APPROVED &&
                entity.getFraudScore().compareTo(new BigDecimal("0.05")) == 0
        ));
    }
}
