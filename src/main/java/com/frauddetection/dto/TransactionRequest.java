package com.frauddetection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank
        String transactionId,

        @NotNull @Positive
        BigDecimal amount,

        @NotBlank
        String merchantId,

        String merchantCategory,

        @NotBlank @Size(min = 2, max = 2)
        String countryCode,

        @NotBlank @Size(min = 3, max = 3)
        String currencyCode
) {}
