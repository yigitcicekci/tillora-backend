package com.yigitcicekci.tillora.audit.application.service;

import com.yigitcicekci.tillora.audit.api.response.PlatformAdminAuditLogResponse;
import com.yigitcicekci.tillora.audit.infrastructure.persistence.PlatformAdminAuditQueryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminAuditQueryService {

    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private final PlatformAdminAuditQueryRepository queryRepository;

    public PlatformAdminAuditQueryService(PlatformAdminAuditQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlatformAdminAuditLogResponse> findAll(
        UUID platformAdminId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String action,
        String entityType,
        Pageable pageable
    ) {
        LocalDate from = dateFrom == null ? MIN_DATE : dateFrom;
        LocalDate to = dateTo == null ? MAX_DATE : dateTo;
        if (from.isBefore(MIN_DATE) || to.isAfter(MAX_DATE) || from.isAfter(to)) {
            throw invalid("Audit date range is invalid.");
        }
        validateText(action, "action");
        validateText(entityType, "entityType");
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw invalid("Audit page size must be between 1 and 100.");
        }
        return queryRepository.findAll(platformAdminId, from, to, action, entityType, pageable);
    }

    private void validateText(String value, String field) {
        if (value != null && (value.isBlank() || value.length() > 80)) {
            throw invalid(field + " is invalid.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException("AUDIT_FILTER_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
