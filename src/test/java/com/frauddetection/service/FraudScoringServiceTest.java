package com.frauddetection.service;

import com.frauddetection.config.FraudProperties;
import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudScoringServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private FraudScoringService fraudScoringService;

    private static final FraudProperties PROPERTIES = new FraudProperties(
            new FraudProperties.Threshold(0.7, 0.9),
            new FraudProperties.Scoring(10, 0.5)
    );

    @BeforeEach
    void setUp() {
        fraudScoringService = new FraudScoringService(transactionRepository, PROPERTIES);
    }

    @Test
    void score_withNoNeighbors_returnsColdStartScoreAndApproved() {
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of());

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.5);
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void score_withAllBlockedNeighbors_returnsBlocked() {
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(row("BLOCKED", 1.0), row("BLOCKED", 1.0)));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(1.0);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCKED);
    }

    @Test
    void score_withAllApprovedNeighbors_returnsApproved() {
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(row("APPROVED", 1.0), row("APPROVED", 1.0)));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.0);
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void score_withMixedNeighbors_returnsFlagged() {
        // 3 BLOCKED + 1 APPROVED com similaridade igual → 3/4 = 0.75 → FLAGGED
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(
                        row("BLOCKED", 1.0),
                        row("BLOCKED", 1.0),
                        row("BLOCKED", 1.0),
                        row("APPROVED", 1.0)
                ));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.75);
        assertThat(result.decision()).isEqualTo(FraudDecision.FLAGGED);
    }

    @Test
    void score_withAllNullDecisions_fallsToColdStart() {
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(rowNullDecision(), rowNullDecision()));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.5);
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void score_withFlaggedNeighbors_blendedScoreIsFlagged() {
        // FLAGGED tem peso 0.5 → score = 0.5 → abaixo do threshold de flag (0.7) → APPROVED
        // Para atingir FLAGGED com FLAGGED neighbors: precisamos de maioria
        // 1 BLOCKED (peso 1.0) + 1 FLAGGED (peso 0.5) com sim=1.0 → (1.0 + 0.5) / 2 = 0.75 → FLAGGED
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(row("BLOCKED", 1.0), row("FLAGGED", 1.0)));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.75);
        assertThat(result.decision()).isEqualTo(FraudDecision.FLAGGED);
    }

    @Test
    void score_atExactBlockThreshold_returnsBlocked() {
        // 9 BLOCKED + 1 APPROVED com similaridade igual → 9/10 = 0.9 → exatamente no threshold → BLOCKED
        when(transactionRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(
                        row("BLOCKED", 1.0), row("BLOCKED", 1.0), row("BLOCKED", 1.0),
                        row("BLOCKED", 1.0), row("BLOCKED", 1.0), row("BLOCKED", 1.0),
                        row("BLOCKED", 1.0), row("BLOCKED", 1.0), row("BLOCKED", 1.0),
                        row("APPROVED", 1.0)
                ));

        var result = fraudScoringService.score(new float[768]);

        assertThat(result.fraudScore()).isEqualTo(0.9);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCKED);
    }

    private Object[] row(String decision, double similarity) {
        return new Object[]{UUID.randomUUID(), "txn-" + UUID.randomUUID(), decision, 0.5, similarity};
    }

    private Object[] rowNullDecision() {
        return new Object[]{UUID.randomUUID(), "txn-" + UUID.randomUUID(), null, null, 1.0};
    }
}
