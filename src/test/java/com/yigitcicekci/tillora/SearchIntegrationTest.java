package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;

import com.yigitcicekci.tillora.search.api.response.SearchResultResponse;
import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import com.yigitcicekci.tillora.search.infrastructure.persistence.SearchQueryRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class SearchIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17")
    );

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID otherCompanyId;
    private UUID customerId;
    private UUID productId;

    @BeforeEach
    void createSearchData() {
        companyId = insertCompany();
        otherCompanyId = insertCompany();
        customerId = insertCurrentAccount(companyId, "Atlas Market", "Atlas Ticaret");
        productId = insertProduct(companyId, "ATL-001", "Atlas Coffee");
        insertCurrentAccount(otherCompanyId, "Atlas Other Tenant", null);
    }

    @Test
    void searchesAuthorizedSourcesInOneTenant() {
        List<SearchResultResponse> results = searchQueryRepository.search(
            companyId,
            "atlas",
            EnumSet.of(SearchResultType.CURRENT_ACCOUNT, SearchResultType.PRODUCT),
            10
        );

        assertThat(results)
            .extracting(SearchResultResponse::id)
            .containsExactlyInAnyOrder(customerId, productId);
        assertThat(results)
            .extracting(SearchResultResponse::type)
            .containsExactlyInAnyOrder(
                SearchResultType.CURRENT_ACCOUNT,
                SearchResultType.PRODUCT
            );
    }

    @Test
    void treatsLikeWildcardsAsLiteralCharactersAndParsesDocumentSearch() {
        UUID percentProductId = insertProduct(companyId, "PURE-100", "100% Pure Coffee");

        assertThat(searchQueryRepository.search(
            companyId,
            "100%",
            EnumSet.of(SearchResultType.PRODUCT),
            10
        ))
            .extracting(SearchResultResponse::id)
            .containsExactly(percentProductId);
        assertThat(searchQueryRepository.search(
            companyId,
            "missing",
            EnumSet.of(SearchResultType.DOCUMENT),
            10
        )).isEmpty();
    }

    @Test
    void installsTrigramIndexesForEverySearchSource() {
        assertThat(jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname IN (
                  'current_accounts_search_trgm_idx',
                  'products_search_trgm_idx',
                  'invoices_number_search_trgm_idx'
              )
            ORDER BY indexname
            """,
            String.class
        )).hasSize(3);
    }

    private UUID insertCompany() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO companies (
                id, name, legal_name, tax_number, currency, timezone, status
            )
            VALUES (?, ?, ?, ?, 'TRY', 'Europe/Istanbul', 'ACTIVE')
            """,
            id,
            "Company " + id,
            "Company Legal " + id,
            Long.toUnsignedString(id.getMostSignificantBits())
        );
        return id;
    }

    private UUID insertCurrentAccount(UUID ownerCompanyId, String name, String legalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO current_accounts (
                id, company_id, name, legal_name, tax_number, trade_type,
                relationship_type, status
            )
            VALUES (?, ?, ?, ?, ?, 'RETAIL', 'CUSTOMER', 'ACTIVE')
            """,
            id,
            ownerCompanyId,
            name,
            legalName,
            Long.toUnsignedString(id.getMostSignificantBits())
        );
        return id;
    }

    private UUID insertProduct(UUID ownerCompanyId, String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO products (
                id, company_id, code, name, unit, purchase_price, sale_price,
                vat_rate, active
            )
            VALUES (?, ?, ?, ?, 'PIECE', 1, 2, 20, true)
            """,
            id,
            ownerCompanyId,
            code,
            name
        );
        return id;
    }
}
