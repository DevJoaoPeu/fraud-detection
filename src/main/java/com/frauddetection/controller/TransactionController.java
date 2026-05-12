package com.frauddetection.controller;

import com.frauddetection.dto.TransactionBatchResponse;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.service.TransactionBatchService;
import com.frauddetection.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionBatchService transactionBatchService;

    public TransactionController(TransactionService transactionService,
                                 TransactionBatchService transactionBatchService) {
        this.transactionService = transactionService;
        this.transactionBatchService = transactionBatchService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<TransactionResponse> analyzeTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.analyze(request));
    }

    @PostMapping("/ingest")
    public ResponseEntity<TransactionBatchResponse> ingestTransactions(
            @RequestBody List<@Valid TransactionRequest> requests) {
        return ResponseEntity.ok(transactionBatchService.ingest(requests));
    }
}
