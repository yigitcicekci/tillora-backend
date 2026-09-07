package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.product.api.request.CreateProductRequest;
import com.yigitcicekci.tillora.product.api.request.UpdateProductRequest;
import com.yigitcicekci.tillora.product.api.response.ProductResponse;
import com.yigitcicekci.tillora.product.application.service.ProductService;
import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class ProductIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        REDIS.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserAccessSetupService userAccessSetupService;

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;

    @BeforeEach
    void createCompany() {
        company = companyRepository.saveAndFlush(company("70" + System.nanoTime()));
        userAccessSetupService.initializeForCompany(company.id());
        actorId = UUID.randomUUID();
    }

    @Test
    void createsListsAndBootstrapsProductReadPermissions() {
        ProductResponse response = productService.create(
            company.id(),
            actorId,
            request(" prd-001 ", " 869000000001 ", " Product ", "100", "120", "20")
        );

        assertThat(response.code()).isEqualTo("PRD-001");
        assertThat(response.barcode()).isEqualTo("869000000001");
        assertThat(response.name()).isEqualTo("Product");
        assertThat(response.purchasePrice()).isEqualByComparingTo("100.0000");
        assertThat(response.salePrice()).isEqualByComparingTo("120.0000");
        assertThat(response.vatRate()).isEqualByComparingTo("20.00");
        assertThat(productService.list(company.id(), true, PageRequest.of(0, 20)).getContent())
            .extracting(ProductResponse::id)
            .containsExactly(response.id());
        assertThat(permissionCodes("ADMIN")).contains("PRODUCT_READ", "PRODUCT_MANAGE");
        assertThat(permissionCodes("ACCOUNTING")).contains("PRODUCT_READ").doesNotContain("PRODUCT_MANAGE");
        assertThat(permissionCodes("SALES")).contains("PRODUCT_READ").doesNotContain("PRODUCT_MANAGE");
        assertThat(permissionCodes("VIEWER")).contains("PRODUCT_READ").doesNotContain("PRODUCT_MANAGE");
        assertThat(auditCount(response.id(), "PRODUCT_CREATE")).isEqualTo(1);
    }

    @Test
    void updatesDisablesAndIsolatesTenantData() {
        ProductResponse created = productService.create(
            company.id(),
            actorId,
            request("PRD-002", null, "Product", "100", "120", "20")
        );
        Company otherCompany = companyRepository.saveAndFlush(company("71" + System.nanoTime()));

        assertThatThrownBy(() -> productService.get(otherCompany.id(), created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_NOT_FOUND");

        ProductResponse updated = productService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateProductRequest(
                "869000000002",
                "Updated Product",
                ProductUnit.BOX,
                new BigDecimal("110"),
                new BigDecimal("135"),
                new BigDecimal("10")
            )
        );
        ProductResponse disabled = productService.disable(company.id(), actorId, created.id());

        assertThat(updated.code()).isEqualTo(created.code());
        assertThat(updated.unit()).isEqualTo(ProductUnit.BOX);
        assertThat(disabled.active()).isFalse();
        assertThat(productService.list(company.id(), true, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(productService.list(company.id(), false, PageRequest.of(0, 20)).getContent())
            .extracting(ProductResponse::id)
            .containsExactly(created.id());
        assertThat(auditCount(created.id(), "PRODUCT_UPDATE")).isEqualTo(1);
        assertThat(auditCount(created.id(), "PRODUCT_DISABLE")).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateBarcodeAndDatabaseInvalidAmounts() {
        productService.create(
            company.id(),
            actorId,
            request("PRD-003", "869000000001", "Product", "100", "120", "20")
        );
        assertThatThrownBy(() -> productService.create(
            company.id(),
            actorId,
            request("PRD-004", "869000000001", "Duplicate Barcode", "100", "120", "20")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_BARCODE_ALREADY_EXISTS");
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO products (
                id,
                company_id,
                code,
                name,
                unit,
                purchase_price,
                sale_price,
                vat_rate
            )
            VALUES (?, ?, 'INVALID-PRICE', 'Invalid Product', 'PIECE', -1, 0, 0)
            """,
            UUID.randomUUID(),
            company.id()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingAndDuplicateProductCode() {
        assertThatThrownBy(() -> productService.create(
            company.id(),
            actorId,
            request(" ", null, "Missing Code", "0", "0", "0")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_CODE_INVALID");

        productService.create(
            company.id(),
            actorId,
            request("PRD-DUPLICATE", null, "First Product", "0", "0", "0")
        );

        assertThatThrownBy(() -> productService.create(
            company.id(),
            actorId,
            request("prd-duplicate", null, "Second Product", "0", "0", "0")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_CODE_ALREADY_EXISTS");
    }

    @Test
    void concurrentCreatesPersistDifferentProvidedCodes() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> createOutcome("PRD-CONCURRENT-1", ready, start)),
                executor.submit(() -> createOutcome("PRD-CONCURRENT-2", ready, start))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = futures.stream().map(this::result).toList();
        }

        List<ProductResponse> products = outcomes.stream()
            .filter(ProductResponse.class::isInstance)
            .map(ProductResponse.class::cast)
            .toList();
        assertThat(products).hasSize(2);
        assertThat(products).extracting(ProductResponse::code).doesNotHaveDuplicates();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND action = 'PRODUCT_CREATE'",
            Integer.class,
            company.id()
        )).isEqualTo(2);
    }

    @Test
    void deletesUnusedProducts() {
        ProductResponse unused = productService.create(
            company.id(),
            actorId,
            request("PRD-UNUSED", null, "Unused Product", "0", "0", "0")
        );
        productService.delete(company.id(), actorId, unused.id());

        assertThatThrownBy(() -> productService.get(company.id(), unused.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(auditCount(unused.id(), "PRODUCT_DELETE")).isEqualTo(1);

    }

    private Object createOutcome(
        String productCode,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        try {
            return productService.create(
                company.id(),
                actorId,
                request(productCode, null, "Product", "100", "120", "20")
            );
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Object result(Future<Object> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CreateProductRequest request(
        String productCode,
        String barcode,
        String name,
        String purchasePrice,
        String salePrice,
        String vatRate
    ) {
        return new CreateProductRequest(
            productCode,
            barcode,
            name,
            ProductUnit.PIECE,
            new BigDecimal(purchasePrice),
            new BigDecimal(salePrice),
            new BigDecimal(vatRate)
        );
    }

    private List<String> permissionCodes(String roleName) {
        return jdbcTemplate.queryForList(
            """
            SELECT p.code
            FROM roles r
            JOIN role_permissions rp ON rp.role_id = r.id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE r.company_id = ?
              AND r.name = ?
            ORDER BY p.code
            """,
            String.class,
            company.id(),
            roleName
        );
    }

    private int auditCount(UUID productId, String action) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND entity_id = ? AND action = ?",
            Integer.class,
            company.id(),
            productId,
            action
        );
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Product Test Company " + taxNumber,
            "Product Test Company " + taxNumber,
            taxNumber,
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        );
    }
}
