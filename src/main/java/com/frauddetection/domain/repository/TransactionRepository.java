package com.frauddetection.domain.repository;

import com.frauddetection.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByTransactionId(String transactionId);

    @Query(value = """
            SELECT * FROM transactions
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<TransactionEntity> findSimilarTransactions(
            @Param("embedding") String embedding,
            @Param("limit") int limit);
}
