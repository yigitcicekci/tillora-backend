package com.yigitcicekci.tillora.audit.infrastructure.persistence;

import com.yigitcicekci.tillora.audit.api.response.PlatformAdminAuditLogResponse;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformAdminAuditQueryRepository {

    private static final Map<String, String> SORTS = Map.of(
        "createdAt", "audit.created_at",
        "action", "audit.action",
        "entityType", "audit.entity_type",
        "result", "audit.result"
    );

    private final JdbcClient jdbcClient;

    public PlatformAdminAuditQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page<PlatformAdminAuditLogResponse> findAll(
        UUID platformAdminId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String action,
        String entityType,
        Pageable pageable
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("date_from", dateFrom);
        params.put("date_to", dateTo);
        StringBuilder source = new StringBuilder("""
            FROM audit_logs audit
            WHERE audit.platform_admin_id IS NOT NULL
              AND audit.created_at::date BETWEEN :date_from AND :date_to
            """);
        optional(source, params, "audit.platform_admin_id", "platform_admin_id", platformAdminId);
        optional(source, params, "audit.action", "action", action);
        optional(source, params, "audit.entity_type", "entity_type", entityType);
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("limit", pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());
        var rows = jdbcClient.sql("""
            SELECT audit.id, audit.platform_admin_id, audit.action, audit.entity_type,
                   audit.entity_id, audit.result, audit.correlation_id, audit.ip_address,
                   audit.user_agent, audit.created_at
            """ + source + " ORDER BY " + orderBy(pageable) + " LIMIT :limit OFFSET :offset")
            .params(pageParams)
            .query(this::row)
            .list();
        long total = jdbcClient.sql("SELECT count(*) " + source)
            .params(params)
            .query(Long.class)
            .single();
        return new PageImpl<>(rows, pageable, total);
    }

    private String orderBy(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return "audit.created_at DESC, audit.id DESC";
        }
        return pageable.getSort().stream()
            .map(order -> {
                String column = SORTS.get(order.getProperty());
                if (column == null) {
                    throw new BusinessException(
                        "AUDIT_SORT_INVALID",
                        "Unsupported audit sort property: " + order.getProperty(),
                        HttpStatus.BAD_REQUEST
                    );
                }
                return column + " " + order.getDirection().name();
            })
            .reduce((left, right) -> left + ", " + right)
            .orElse("audit.created_at DESC, audit.id DESC");
    }

    private void optional(
        StringBuilder source,
        Map<String, Object> params,
        String column,
        String parameter,
        Object value
    ) {
        if (value != null) {
            source.append(" AND ").append(column).append(" = :").append(parameter);
            params.put(parameter, value);
        }
    }

    private PlatformAdminAuditLogResponse row(ResultSet result, int row) throws SQLException {
        return new PlatformAdminAuditLogResponse(
            result.getObject("id", UUID.class),
            result.getObject("platform_admin_id", UUID.class),
            result.getString("action"),
            result.getString("entity_type"),
            result.getObject("entity_id", UUID.class),
            result.getString("result"),
            result.getString("correlation_id"),
            result.getString("ip_address"),
            result.getString("user_agent"),
            result.getTimestamp("created_at").toInstant()
        );
    }
}
