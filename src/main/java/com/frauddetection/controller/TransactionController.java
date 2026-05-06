package com.frauddetection.controller;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    @PostMapping("/analyze")
    public ResponseEntity<TransactionResponse> analyzeTransaction(
            @Valid @RequestBody TransactionRequest request) {
        // TODO: TransactionService (próximo passo)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
