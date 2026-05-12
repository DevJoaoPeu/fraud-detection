package com.frauddetection.config;

import com.frauddetection.domain.enums.FraudDecision;
import com.frauddetection.domain.repository.TransactionRepository;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.service.SeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TransactionRepository transactionRepository;
    private final SeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        if (transactionRepository.count() > 0) {
            return;
        }

        log.info("Empty database detected — seeding labeled transactions for KNN training");
        seedAll();
        log.info("Seed complete: {} transactions inserted", transactionRepository.count());
    }

    private void seedAll() {
        BLOCKED_TRANSACTIONS.forEach(r -> seedService.seed(r, FraudDecision.BLOCKED, 0.95));
        FLAGGED_TRANSACTIONS.forEach(r -> seedService.seed(r, FraudDecision.FLAGGED, 0.75));
        APPROVED_TRANSACTIONS.forEach(r -> seedService.seed(r, FraudDecision.APPROVED, 0.05));
    }

    // --- Seed data ---

    private static final List<TransactionRequest> BLOCKED_TRANSACTIONS = List.of(
            new TransactionRequest("SEED-BLOCK-001", new BigDecimal("14999.99"), "MERCH-JEWEL-GH-001",  "5944", "GH", "USD"),
            new TransactionRequest("SEED-BLOCK-002", new BigDecimal("5000.00"),  "MERCH-GAMBLE-RO-001", "7995", "RO", "USD"),
            new TransactionRequest("SEED-BLOCK-003", new BigDecimal("8750.00"),  "MERCH-ELEC-UA-001",   "5732", "UA", "USD"),
            new TransactionRequest("SEED-BLOCK-004", new BigDecimal("12000.00"), "MERCH-WIRE-NG-001",   "4829", "NG", "USD")
    );

    private static final List<TransactionRequest> FLAGGED_TRANSACTIONS = List.of(
            new TransactionRequest("SEED-FLAG-001", new BigDecimal("2500.00"), "MERCH-JEWEL-US-001",  "5944", "US", "USD"),
            new TransactionRequest("SEED-FLAG-002", new BigDecimal("3200.00"), "MERCH-ELEC-DE-001",   "5732", "DE", "EUR")
    );

    private static final List<TransactionRequest> APPROVED_TRANSACTIONS = List.of(
            new TransactionRequest("SEED-APPR-001", new BigDecimal("127.40"), "MERCH-CARREFOUR-SP-001", "5411", "BR", "BRL"),
            new TransactionRequest("SEED-APPR-002", new BigDecimal("89.50"),  "MERCH-OUTBACK-SP-001",   "5812", "BR", "BRL"),
            new TransactionRequest("SEED-APPR-003", new BigDecimal("210.00"), "MERCH-PETROBRAS-MG-001", "5541", "BR", "BRL"),
            new TransactionRequest("SEED-APPR-004", new BigDecimal("45.90"),  "MERCH-PREZUNIC-RJ-001",  "5411", "BR", "BRL"),
            new TransactionRequest("SEED-APPR-005", new BigDecimal("35.00"),  "MERCH-FARMACIA-SP-001",  "5912", "BR", "BRL")
    );
}
