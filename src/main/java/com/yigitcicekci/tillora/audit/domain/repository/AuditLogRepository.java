package com.yigitcicekci.tillora.audit.domain.repository;

import com.yigitcicekci.tillora.audit.domain.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
