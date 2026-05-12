package com.frauddetection.dto;

import java.util.List;

public record TransactionBatchResponse(
        int total,
        int approved,
        int flagged,
        int blocked,
        int failed,
        List<ItemResult> results
) {
    public record ItemResult(
            String transactionId,
            boolean success,
            TransactionResponse data,
            String error
    ) {}
}
