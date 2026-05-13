package com.frauddetection.service;

import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionBatchServiceTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionBatchService transactionBatchService;

    @Test
    void ingest_allApproved_returnsCorrectCounts() {
        var req1 = request("TXN-001");
        var req2 = request("TXN-002");
        when(transactionService.analyze(req1)).thenReturn(response("TXN-001", FraudDecision.APPROVED));
        when(transactionService.analyze(req2)).thenReturn(response("TXN-002", FraudDecision.APPROVED));

        var result = transactionBatchService.ingest(List.of(req1, req2));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.approved()).isEqualTo(2);
        assertThat(result.flagged()).isEqualTo(0);
        assertThat(result.blocked()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    void ingest_mixedDecisions_returnsCorrectCounts() {
        var req1 = request("TXN-001");
        var req2 = request("TXN-002");
        var req3 = request("TXN-003");
        when(transactionService.analyze(req1)).thenReturn(response("TXN-001", FraudDecision.APPROVED));
        when(transactionService.analyze(req2)).thenReturn(response("TXN-002", FraudDecision.FLAGGED));
        when(transactionService.analyze(req3)).thenReturn(response("TXN-003", FraudDecision.BLOCKED));

        var result = transactionBatchService.ingest(List.of(req1, req2, req3));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.approved()).isEqualTo(1);
        assertThat(result.flagged()).isEqualTo(1);
        assertThat(result.blocked()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    void ingest_withDuplicateTransaction_capturesErrorAndContinues() {
        var req1 = request("TXN-001");
        var req2 = request("TXN-DUP");
        when(transactionService.analyze(req1)).thenReturn(response("TXN-001", FraudDecision.APPROVED));
        when(transactionService.analyze(req2)).thenThrow(new DuplicateTransactionException("TXN-DUP"));

        var result = transactionBatchService.ingest(List.of(req1, req2));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.approved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);

        var failedItem = result.results().get(1);
        assertThat(failedItem.success()).isFalse();
        assertThat(failedItem.error()).contains("TXN-DUP");
        assertThat(failedItem.data()).isNull();
    }

    @Test
    void ingest_withEmbeddingFailure_capturesErrorAndContinues() {
        var req1 = request("TXN-001");
        var req2 = request("TXN-002");
        when(transactionService.analyze(req1)).thenThrow(new EmbeddingException("Gemini unavailable"));
        when(transactionService.analyze(req2)).thenReturn(response("TXN-002", FraudDecision.APPROVED));

        var result = transactionBatchService.ingest(List.of(req1, req2));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.approved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results().get(0).error()).contains("Gemini unavailable");
    }

    @Test
    void ingest_withEmptyList_returnsAllZeros() {
        var result = transactionBatchService.ingest(List.of());

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.approved()).isEqualTo(0);
        assertThat(result.flagged()).isEqualTo(0);
        assertThat(result.blocked()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.results()).isEmpty();
    }

    private static TransactionRequest request(String id) {
        return new TransactionRequest(id, new BigDecimal("100.00"), "MERCH-001", "5411", "BR", "BRL");
    }

    private static TransactionResponse response(String id, FraudDecision decision) {
        return new TransactionResponse(id, new BigDecimal("100.00"), new BigDecimal("0.05"), decision, LocalDateTime.now());
    }
}
