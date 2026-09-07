package com.yigitcicekci.tillora.audit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.audit.api.response.AuditLogResponse;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditQueryRepository {

    private static final TypeReference<Map<String, String>> DATA_TYPE = new TypeReference<>() {
    };
    private static final Map<String, String> SORTS = Map.of(
        "createdAt", "audit.created_at",
        "username", "app_user.username",
        "action", "audit.action",
        "entityType", "audit.entity_type"
    );

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AuditQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public Page<AuditLogResponse> findAll(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID userId,
        String action,
        String entityType,
        Pageable pageable
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("company_id", companyId);
        params.put("date_from", dateFrom);
        params.put("date_to", dateTo);
        StringBuilder source = new StringBuilder("""
            FROM audit_logs audit
            LEFT JOIN users app_user ON app_user.id = audit.user_id
                AND app_user.company_id = audit.company_id
            WHERE audit.company_id = :company_id
              AND audit.created_at::date BETWEEN :date_from AND :date_to
            """);
        optional(source, params, "audit.user_id", "user_id", userId);
        optional(source, params, "audit.action", "action", action);
        optional(source, params, "audit.entity_type", "entity_type", entityType);
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("limit", pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());
        var rows = jdbcClient.sql("""
            SELECT audit.id, audit.user_id, app_user.username, audit.action,
                   audit.entity_type, audit.entity_id, audit.correlation_id,
                   audit.ip_address, audit.user_agent, audit.before_data,
                   audit.after_data, audit.created_at
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

    private AuditLogResponse row(ResultSet result, int row) throws SQLException {
        return new AuditLogResponse(
            result.getObject("id", UUID.class),
            result.getObject("user_id", UUID.class),
            result.getString("username"),
            result.getString("action"),
            result.getString("entity_type"),
            result.getObject("entity_id", UUID.class),
            result.getString("correlation_id"),
            result.getString("ip_address"),
            result.getString("user_agent"),
            data(result.getString("before_data")),
            data(result.getString("after_data")),
            result.getTimestamp("created_at").toInstant()
        );
    }

    private Map<String, String> data(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return Map.copyOf(objectMapper.readValue(json, DATA_TYPE));
        } catch (JsonProcessingException exception) {
            throw new DataRetrievalFailureException("Audit data could not be read.", exception);
        }
    }
}
