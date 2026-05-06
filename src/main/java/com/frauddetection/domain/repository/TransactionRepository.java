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

    /**
     * Returns [id, transaction_id, decision, fraud_score, similarity] rows.
     * similarity = 1 - cosine_distance, so higher = more similar.
     * Only includes transactions that already have a decision (training data).
     */
    @Query(value = """
            SELECT t.id,
                   t.transaction_id,
                   t.decision,
                   t.fraud_score,
                   1 - (t.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM transactions t
            WHERE t.embedding IS NOT NULL
              AND t.decision   IS NOT NULL
            ORDER BY t.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarWithScore(
            @Param("embedding") String embedding,
            @Param("limit") int limit);
}
