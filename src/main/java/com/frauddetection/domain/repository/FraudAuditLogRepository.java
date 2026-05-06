package com.frauddetection.domain.repository;

import com.frauddetection.domain.entity.FraudAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudAuditLogRepository extends JpaRepository<FraudAuditLogEntity, UUID> {

    List<FraudAuditLogEntity> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}
