package com.frauddetection.service;

import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.dto.TransactionBatchResponse;
import com.frauddetection.dto.TransactionBatchResponse.ItemResult;
import com.frauddetection.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionBatchService {

    private final TransactionService transactionService;

    public TransactionBatchResponse ingest(List<TransactionRequest> requests) {
        List<ItemResult> results = new ArrayList<>();

        for (TransactionRequest request : requests) {
            try {
                var response = transactionService.analyze(request);
                results.add(new ItemResult(request.transactionId(), true, response, null));
            } catch (Exception e) {
                results.add(new ItemResult(request.transactionId(), false, null, e.getMessage()));
            }
        }

        int approved = count(results, FraudDecision.APPROVED);
        int flagged = count(results, FraudDecision.FLAGGED);
        int blocked = count(results, FraudDecision.BLOCKED);
        int failed = (int) results.stream().filter(r -> !r.success()).count();

        return new TransactionBatchResponse(requests.size(), approved, flagged, blocked, failed, results);
    }

    private int count(List<ItemResult> results, FraudDecision decision) {
        return (int) results.stream()
                .filter(r -> r.success() && r.data().decision() == decision)
                .count();
    }
}
