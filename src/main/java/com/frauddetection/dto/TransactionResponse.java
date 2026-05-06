package com.frauddetection.dto;

import com.frauddetection.domain.enums.FraudDecision;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String transactionId,
        BigDecimal amount,
        BigDecimal fraudScore,
        FraudDecision decision,
        LocalDateTime processedAt
) {}
