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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private FraudAuditLogRepository auditLogRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private FraudScoringService fraudScoringService;

    @InjectMocks
    private TransactionService transactionService;

    private static final TransactionRequest REQUEST = new TransactionRequest(
            "TXN-001", new BigDecimal("127.40"), "MERCH-001", "5411", "BR", "BRL"
    );

    @Test
    void analyze_withDuplicateTransactionId_throwsDuplicateException() {
        when(transactionRepository.findByTransactionId("TXN-001"))
                .thenReturn(Optional.of(new TransactionEntity()));

        assertThatThrownBy(() -> transactionService.analyze(REQUEST))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("TXN-001");

        verifyNoInteractions(embeddingService, fraudScoringService, auditLogRepository);
    }

    @Test
    void analyze_happyPath_returnsCorrectResponse() {
        when(transactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(REQUEST)).thenReturn(new float[768]);
        when(embeddingService.getModelVersion()).thenReturn("gemini-embedding-001");
        when(fraudScoringService.score(any())).thenReturn(
                new FraudScoringService.ScoringResult(0.05, FraudDecision.APPROVED));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = transactionService.analyze(REQUEST);

        assertThat(response.transactionId()).isEqualTo("TXN-001");
        assertThat(response.amount()).isEqualByComparingTo("127.40");
        assertThat(response.fraudScore()).isEqualByComparingTo("0.05");
        assertThat(response.decision()).isEqualTo(FraudDecision.APPROVED);
        assertThat(response.processedAt()).isNotNull();
        verify(auditLogRepository).save(any());
    }

    @Test
    void analyze_happyPath_savedEntityHasCorrectDecision() {
        when(transactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(REQUEST)).thenReturn(new float[768]);
        when(embeddingService.getModelVersion()).thenReturn("gemini-embedding-001");
        when(fraudScoringService.score(any())).thenReturn(
                new FraudScoringService.ScoringResult(0.95, FraudDecision.BLOCKED));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = transactionService.analyze(REQUEST);

        assertThat(response.decision()).isEqualTo(FraudDecision.BLOCKED);
        assertThat(response.fraudScore()).isEqualByComparingTo("0.95");
    }

    @Test
    void analyze_whenEmbeddingFails_propagatesExceptionWithoutSaving() {
        when(transactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(REQUEST))
                .thenThrow(new EmbeddingException("Gemini API unavailable"));

        assertThatThrownBy(() -> transactionService.analyze(REQUEST))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Gemini API unavailable");

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(fraudScoringService, auditLogRepository);
    }
}
