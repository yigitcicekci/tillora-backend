package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.auth.infrastructure.security.LoginRateLimiter;
import com.yigitcicekci.tillora.auth.api.request.ChangePasswordRequest;
import com.yigitcicekci.tillora.auth.api.request.LoginRequest;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationResult;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationService;
import com.yigitcicekci.tillora.auth.application.service.JwtTokenService;
import com.yigitcicekci.tillora.auth.application.service.VerifiedJwtToken;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.company.api.request.CreateCompanyRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import com.yigitcicekci.tillora.user.application.service.UserService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class FinancialAndTenantIntegrityTest {

    private static final AtomicLong COMPANY_TAX_NUMBER_SEQUENCE = new AtomicLong(
        System.currentTimeMillis() % 1_000_000_000L
    );

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        if (Boolean.getBoolean("tillora.test.external-services")) {
            registry.add("spring.datasource.url", () -> System.getProperty(
                "tillora.test.postgres-url",
                "jdbc:postgresql://127.0.0.1:55432/tillora"
            ));
            registry.add("spring.datasource.username", () -> "tillora");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.data.redis.host", () -> "127.0.0.1");
            registry.add("spring.data.redis.port", () -> Integer.getInteger("tillora.test.redis-port", 56379));
            return;
        }
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
    private DefaultAccountingSetupService defaultAccountingSetupService;

    @Autowired
    private UserAccessSetupService userAccessSetupService;

    @Autowired
    private CurrentAccountService currentAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private Company company;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void createCompany() {
        company = companyRepository.saveAndFlush(company("10" + System.nanoTime()));
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(securityFilterChain)
            .build();
    }

    @Test
    @Transactional
    void bootstrapLockCanBeAcquiredInsideTransaction() {
        companyRepository.acquireBootstrapLock();
    }

    @Test
    void protectsLastActiveAdministratorDuringDisable() {
        userAccessSetupService.initializeForCompany(company.id());
        UUID firstAdmin = createAdminUser("first-admin", "first-admin-password-123");

        assertThatThrownBy(() -> userService.disable(company.id(), firstAdmin, firstAdmin))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("LAST_ACTIVE_ADMIN_CANNOT_BE_DISABLED");

        UUID secondAdmin = createAdminUser("second-admin", "second-admin-password-123");
        userService.disable(company.id(), secondAdmin, firstAdmin);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id = ?",
            String.class,
            firstAdmin
        )).isEqualTo("PASSIVE");
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM users user_account
            JOIN user_roles mapping ON mapping.user_id = user_account.id
            JOIN roles role ON role.id = mapping.role_id
            WHERE user_account.company_id = ?
              AND user_account.status = 'ACTIVE'
              AND role.name = 'ADMIN'
            """,
            Integer.class,
            company.id()
        )).isEqualTo(1);
    }

    @Test
    void concurrentCurrentAccountsReceiveDifferentLedgerCodes() throws Exception {
        defaultAccountingSetupService.initializeForCompany(company.id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UUID actorId = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<CurrentAccountResponse>> futures = List.of(
                executor.submit(() -> createCurrentAccount(ready, start, actorId, "Customer One", "1234567890")),
                executor.submit(() -> createCurrentAccount(ready, start, actorId, "Customer Two", "1234567891"))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> codes = futures.stream()
                .map(this::result)
                .flatMap(response -> response.ledgerAccounts().stream())
                .map(ledger -> ledger.fullAccountCode())
                .toList();
            assertThat(codes).hasSize(2).doesNotHaveDuplicates();
        }
    }

    @Test
    void scopesCaseInsensitiveCurrentAccountNameUniquenessByRelationshipType() {
        defaultAccountingSetupService.initializeForCompany(company.id());
        UUID actorId = UUID.randomUUID();

        currentAccountService.create(
            company.id(),
            actorId,
            currentAccountRequest("Acme", "1234567890", RelationshipType.CUSTOMER)
        );
        currentAccountService.create(
            company.id(),
            actorId,
            currentAccountRequest(" acme ", "1234567891", RelationshipType.SUPPLIER)
        );

        assertThatThrownBy(() -> currentAccountService.create(
            company.id(),
            actorId,
            currentAccountRequest("ACME", "1234567892", RelationshipType.CUSTOMER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CURRENT_ACCOUNT_NAME_ALREADY_EXISTS");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM current_accounts WHERE company_id = ? AND lower(btrim(name)) = 'acme'",
            Integer.class,
            company.id()
        )).isEqualTo(2);
    }

    @Test
    void databaseRejectsCaseInsensitiveDuplicateUsernameAcrossCompanies() {
        Company otherCompany = companyRepository.saveAndFlush(company("12" + System.nanoTime()));
        insertUser(company.id(), "Admin", "admin-one@example.com");

        assertThatThrownBy(() -> insertUser(otherCompany.id(), "admin", "admin-two@example.com"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsCrossCompanyRoleAssignment() {
        Company otherCompany = companyRepository.saveAndFlush(company("11" + System.nanoTime()));
        userAccessSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(otherCompany.id());
        UUID userId = insertUser(company.id(), "tenant-user", "tenant-user@example.com");
        UUID otherRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE company_id = ? AND name = 'ADMIN'",
            UUID.class,
            otherCompany.id()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
            userId,
            otherRoleId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void redisTemporaryStateIsAvailable() {
        String key = "test:temporary:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, "available", Duration.ofSeconds(5));
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("available");
    }

    @Test
    void loginAttemptsAreRateLimitedAtomically() {
        String identifier = "rate-limit-" + UUID.randomUUID();
        String remoteAddress = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 10; attempt++) {
            loginRateLimiter.check(identifier, remoteAddress);
        }

        assertThatThrownBy(() -> loginRateLimiter.check(identifier, remoteAddress))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("LOGIN_RATE_LIMIT_EXCEEDED");
    }

    @Test
    void loginResolvesCompanyFromEmailAndUsername() throws Exception {
        String username = "login-user-" + UUID.randomUUID();
        String email = username + "@example.com";
        createAdmin(username, "temporary-password");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest(email, username, "temporary-password")
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.companyId").value(company.id().toString()))
            .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void provisioningTokenCreatesCompanyWithoutAuthenticationOrCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(companyRequest("invalid-token", "1234567891"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMPANY_PROVISIONING_TOKEN_INVALID"));

        MvcResult creation = mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(companyRequest("1234567892"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.taxNumber").value("1234567892"))
            .andReturn();

        UUID createdCompanyId = UUID.fromString(objectMapper.readTree(creation.getResponse().getContentAsString()).path("id").asText());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM roles WHERE company_id = ?",
            Integer.class,
            createdCompanyId
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM chart_of_accounts WHERE company_id = ?",
            Integer.class,
            createdCompanyId
        )).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE company_id = ? AND app_owner = false",
            Integer.class,
            createdCompanyId
        )).isEqualTo(1);
    }

    @Test
    void mandatoryPasswordChangeIsEnforcedThroughHttpSecurity() throws Exception {
        String username = "http-password-user-" + UUID.randomUUID();
        String currentPassword = "temporary-password";
        String replacementPassword = "permanent-password-123";
        createAdmin(username, currentPassword);
        AuthenticationResult login = authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        );
        String authorization = "Bearer " + login.response().accessToken();

        mockMvc.perform(get("/api/v1/current-accounts").header(HttpHeaders.AUTHORIZATION, authorization))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, replacementPassword))))
            .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.AUTHORIZATION, authorization))
            .andExpect(status().isOk())
            .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).path("token").asText();
        assertThat(csrfToken).isNotBlank();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(csrfCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, "short"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        MvcResult changeResult = mockMvc.perform(post("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(csrfCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, replacementPassword))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andReturn();
        String replacementAccessToken = objectMapper.readTree(changeResult.getResponse().getContentAsString())
            .path("accessToken")
            .asText();

        mockMvc.perform(get("/api/v1/current-accounts").header(HttpHeaders.AUTHORIZATION, authorization))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/current-accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + replacementAccessToken))
            .andExpect(status().isOk());
    }

    @Test
    void passwordChangeInvalidatesPreviousTokensAndClearsMandatoryFlag() {
        String username = "password-user-" + UUID.randomUUID();
        String currentPassword = "temporary-password";
        UUID userId = createAdmin(username, currentPassword);
        AuthenticationResult firstLogin = authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        );
        AuthenticationResult secondLogin = authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        );
        String otherUsername = "other-password-user-" + UUID.randomUUID();
        UUID otherUserId = createAdminUser(otherUsername, currentPassword);
        AuthenticationResult otherLogin = authenticationService.login(
            new LoginRequest(otherUsername + "@example.com", otherUsername, currentPassword)
        );

        AuthenticationResult changed = authenticationService.changePassword(
            company.id(),
            userId,
            new ChangePasswordRequest(currentPassword, "permanent-password-123")
        );

        Boolean mustChangePassword = jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM users WHERE id = ?",
            Boolean.class,
            userId
        );
        Integer activeRefreshTokens = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM auth_refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            userId
        );
        Integer otherActiveRefreshTokens = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM auth_refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            otherUserId
        );
        Integer passwordChangeAudits = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'PASSWORD_CHANGE'",
            Integer.class,
            userId
        );
        AuthenticatedUserInfo currentUser = userService.getActiveAuthenticationUser(userId);
        VerifiedJwtToken firstAccessToken = jwtTokenService.verifyAccessToken(firstLogin.response().accessToken());
        VerifiedJwtToken secondAccessToken = jwtTokenService.verifyAccessToken(secondLogin.response().accessToken());
        VerifiedJwtToken replacementAccessToken = jwtTokenService.verifyAccessToken(changed.response().accessToken());

        assertThat(mustChangePassword).isFalse();
        assertThat(currentUser.passwordChangedAt()).isNotNull();
        assertThat(activeRefreshTokens).isEqualTo(1);
        assertThat(otherActiveRefreshTokens).isEqualTo(1);
        assertThat(passwordChangeAudits).isEqualTo(1);
        assertThat(changed.response().user().mustChangePassword()).isFalse();
        assertThatThrownBy(() -> jwtTokenService.verifyUserState(firstAccessToken, currentUser))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("TOKEN_REVOKED");
        assertThatThrownBy(() -> jwtTokenService.verifyUserState(secondAccessToken, currentUser))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("TOKEN_REVOKED");
        assertThatThrownBy(() -> authenticationService.refresh(firstLogin.refreshToken()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("REFRESH_TOKEN_EXPIRED");
        assertThatThrownBy(() -> authenticationService.refresh(secondLogin.refreshToken()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("REFRESH_TOKEN_EXPIRED");
        jwtTokenService.verifyUserState(replacementAccessToken, currentUser);
        assertThat(authenticationService.refresh(changed.refreshToken()).response().user().mustChangePassword()).isFalse();
        assertThat(authenticationService.refresh(otherLogin.refreshToken()).response().user().id()).isEqualTo(otherUserId);
        assertThatThrownBy(() -> authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CREDENTIALS");
        assertThat(authenticationService.login(
            new LoginRequest(username + "@example.com", username, "permanent-password-123")
        ).response().user().mustChangePassword()).isFalse();
    }

    @Test
    void incorrectCurrentPasswordLeavesCredentialsAndTokensUnchanged() {
        String username = "unchanged-user-" + UUID.randomUUID();
        String currentPassword = "temporary-password";
        UUID userId = createAdmin(username, currentPassword);
        AuthenticationResult login = authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        );
        UUID refreshTokenId = jwtTokenService.verifyRefreshToken(login.refreshToken()).tokenId();

        assertThatThrownBy(() -> authenticationService.changePassword(
            company.id(),
            userId,
            new ChangePasswordRequest("incorrect-password", "permanent-password-123")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CURRENT_PASSWORD");

        Boolean mustChangePassword = jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM users WHERE id = ?",
            Boolean.class,
            userId
        );
        Integer changedPasswords = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE id = ? AND password_changed_at IS NOT NULL",
            Integer.class,
            userId
        );
        Integer activeRefreshTokens = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM auth_refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            userId
        );
        Integer passwordChangeAudits = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'PASSWORD_CHANGE'",
            Integer.class,
            userId
        );
        Boolean originalTokenRevoked = jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM auth_refresh_tokens WHERE id = ?",
            Boolean.class,
            refreshTokenId
        );

        assertThat(mustChangePassword).isTrue();
        assertThat(changedPasswords).isZero();
        assertThat(activeRefreshTokens).isEqualTo(1);
        assertThat(passwordChangeAudits).isZero();
        assertThat(originalTokenRevoked).isFalse();
        assertThat(authenticationService.refresh(login.refreshToken()).response().user().mustChangePassword()).isTrue();
        assertThat(authenticationService.login(
            new LoginRequest(username + "@example.com", username, currentPassword)
        ).response().user().mustChangePassword()).isTrue();
        assertThatThrownBy(() -> authenticationService.login(
            new LoginRequest(username + "@example.com", username, "permanent-password-123")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CREDENTIALS");
    }

    private CurrentAccountResponse createCurrentAccount(
        CountDownLatch ready,
        CountDownLatch start,
        UUID actorId,
        String name,
        String taxNumber
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return currentAccountService.create(company.id(), actorId, new CreateCurrentAccountRequest(
            name,
            null,
            taxNumber,
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            RelationshipType.CUSTOMER
        ));
    }

    private CreateCurrentAccountRequest currentAccountRequest(
        String name,
        String taxNumber,
        RelationshipType relationshipType
    ) {
        return new CreateCurrentAccountRequest(
            name,
            null,
            taxNumber,
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            relationshipType
        );
    }

    private CreateCompanyRequest companyRequest(String taxNumber) {
        return companyRequest(
            webApplicationContext.getEnvironment().getRequiredProperty("tillora.security.company-provisioning-token"),
            taxNumber
        );
    }

    private CreateCompanyRequest companyRequest(String provisioningToken, String taxNumber) {
        String username = "new-company-admin-" + UUID.randomUUID();
        return new CreateCompanyRequest(
            provisioningToken,
            "New Company",
            "New Company AŞ",
            taxNumber,
            "Kadıköy",
            "İstanbul",
            null,
            "info@new-company.example",
            "TRY",
            "Europe/Istanbul",
            username,
            username + "@example.com",
            "temporary-password",
            "New",
            "Admin"
        );
    }

    private CurrentAccountResponse result(Future<CurrentAccountResponse> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private UUID insertUser(UUID companyId, String username, String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO users (id, company_id, username, email, password_hash, first_name, last_name, status, must_change_password) " +
                "VALUES (?, ?, ?, ?, ?, 'Test', 'User', 'ACTIVE', true)",
            id,
            companyId,
            username,
            email,
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }

    private UUID createAdmin(String username, String password) {
        userAccessSetupService.initializeForCompany(company.id());
        return createAdminUser(username, password);
    }

    private UUID createAdminUser(String username, String password) {
        return userService.createCompanyAdmin(
            company.id(),
            username,
            username + "@example.com",
            password,
            "Test",
            "Admin"
        );
    }

    private Company company(String ignoredSource) {
        String sequence = Long.toString(
            COMPANY_TAX_NUMBER_SEQUENCE.getAndIncrement() % 1_000_000_000L
        );
        String taxNumber = "1" + "000000000".substring(sequence.length()) + sequence;
        return Company.create(
            "Test Company " + taxNumber,
            "Test Company " + taxNumber,
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
