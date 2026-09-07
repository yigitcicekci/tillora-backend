package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yigitcicekci.tillora.auth.api.request.LoginRequest;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.platformadmin.api.request.CreatePlatformAdminCompanyRequest;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminChangePasswordRequest;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminLoginRequest;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationService;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationResult;
import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import com.yigitcicekci.tillora.platformadmin.domain.repository.PlatformAdminRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.user.application.service.UserService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class PlatformAdminIntegrationTest {

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
    private PlatformAdminAuthenticationService platformAdminAuthenticationService;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserAccessSetupService userAccessSetupService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        if (platformAdminRepository.count() == 0) {
            platformAdminAuthenticationService.bootstrap("platform-admin@example.com", "platform-password-123");
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(securityFilterChain)
            .build();
    }

    @Test
    void isolatesPlatformAdminAuthenticationFromNormalAuthentication() throws Exception {
        mockMvc.perform(post("/internal/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminLoginRequest(
                    "platform-admin@example.com",
                    "wrong-password"
                ))))
            .andExpect(status().isUnauthorized());

        MvcResult login = loginThroughHttp();
        String platformAdminToken = json(login).path("accessToken").asText();
        String normalUserToken = normalUserToken();

        mockMvc.perform(get("/internal/admin/companies"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/admin/companies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalUserToken))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/admin/companies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken))
            .andExpect(status().isUnauthorized());

        PlatformAdmin disabled = PlatformAdmin.create(
            "disabled-platform-admin-" + UUID.randomUUID() + "@example.com",
            "not-a-password-hash"
        );
        disabled.disable();
        platformAdminRepository.saveAndFlush(disabled);
        mockMvc.perform(post("/internal/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminLoginRequest(
                    disabled.email(),
                    "platform-password-123"
                ))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesPlatformAdminRefreshCookieWithCsrfProtection() throws Exception {
        MvcResult login = loginThroughHttp();
        Cookie refreshCookie = login.getResponse().getCookie("TILLORA_PLATFORM_ADMIN_REFRESH_TOKEN");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getPath()).isEqualTo("/internal/admin/auth");
        assertThat(login.getResponse().getCookie("TILLORA_REFRESH_TOKEN")).isNull();

        MvcResult csrf = mockMvc.perform(get("/internal/admin/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        String csrfToken = json(csrf).path("token").asText();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/internal/admin/auth/refresh")
                .cookie(refreshCookie, csrfCookie)
                .header("X-XSRF-TOKEN", csrfToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformAdmin.email").value("platform-admin@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void changesPasswordRevokesAllSessionsClearsCookieAndAuditsWithoutCredentials() throws Exception {
        String currentPassword = "current-password-123";
        String newPassword = "new-password-123";
        PlatformAdmin admin = createPlatformAdmin(currentPassword);
        PlatformAdminAuthenticationResult firstSession = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), currentPassword)
        );
        PlatformAdminAuthenticationResult secondSession = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), currentPassword)
        );

        MvcResult result = mockMvc.perform(postWithCsrf(
                "/internal/admin/auth/change-password",
                csrfCredentials()
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstSession.response().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminChangePasswordRequest(
                    currentPassword,
                    newPassword
                ))))
            .andExpect(status().isOk())
            .andReturn();

        assertRefreshCookieCleared(result);
        assertRefreshTokenRejected(firstSession.refreshToken());
        assertRefreshTokenRejected(secondSession.refreshToken());
        platformAdminAuthenticationService.login(new PlatformAdminLoginRequest(admin.email(), newPassword));
        assertThatThrownBy(() -> platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), currentPassword)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CREDENTIALS");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE platform_admin_id = ? AND action = 'PLATFORM_ADMIN_PASSWORD_CHANGED' AND result = 'SUCCESS'",
            Integer.class,
            admin.id()
        )).isEqualTo(1);
        String auditData = jdbcTemplate.queryForObject(
            "SELECT coalesce(before_data::text, '') || coalesce(after_data::text, '') FROM audit_logs "
                + "WHERE platform_admin_id = ? AND action = 'PLATFORM_ADMIN_PASSWORD_CHANGED' "
                + "ORDER BY created_at DESC LIMIT 1",
            String.class,
            admin.id()
        );
        assertThat(auditData).doesNotContain(currentPassword, newPassword, firstSession.refreshToken(), secondSession.refreshToken());
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() throws Exception {
        String currentPassword = "current-password-456";
        PlatformAdmin admin = createPlatformAdmin(currentPassword);
        PlatformAdminAuthenticationResult session = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), currentPassword)
        );

        mockMvc.perform(postWithCsrf("/internal/admin/auth/change-password", csrfCredentials())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.response().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminChangePasswordRequest(
                    "wrong-password-456",
                    "new-password-456"
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        platformAdminAuthenticationService.login(new PlatformAdminLoginRequest(admin.email(), currentPassword));
    }

    @Test
    void enforcesPlatformAdminPasswordPolicy() throws Exception {
        String currentPassword = "current-password-789";
        PlatformAdmin admin = createPlatformAdmin(currentPassword);
        PlatformAdminAuthenticationResult session = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), currentPassword)
        );

        mockMvc.perform(postWithCsrf("/internal/admin/auth/change-password", csrfCredentials())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.response().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminChangePasswordRequest(
                    currentPassword,
                    "short"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        platformAdminAuthenticationService.login(new PlatformAdminLoginRequest(admin.email(), currentPassword));
    }

    @Test
    void revokesAllSessionsClearsCookieAndAuditsThePlatformAdmin() throws Exception {
        String password = "revoke-password-123";
        PlatformAdmin admin = createPlatformAdmin(password);
        PlatformAdminAuthenticationResult firstSession = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), password)
        );
        PlatformAdminAuthenticationResult secondSession = platformAdminAuthenticationService.login(
            new PlatformAdminLoginRequest(admin.email(), password)
        );

        MvcResult result = mockMvc.perform(postWithCsrf(
                "/internal/admin/auth/revoke-sessions",
                csrfCredentials()
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstSession.response().accessToken()))
            .andExpect(status().isOk())
            .andReturn();

        assertRefreshCookieCleared(result);
        assertRefreshTokenRejected(firstSession.refreshToken());
        assertRefreshTokenRejected(secondSession.refreshToken());
        platformAdminAuthenticationService.login(new PlatformAdminLoginRequest(admin.email(), password));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE platform_admin_id = ? AND action = 'PLATFORM_ADMIN_SESSIONS_REVOKED' AND result = 'SUCCESS'",
            Integer.class,
            admin.id()
        )).isEqualTo(1);
    }

    @Test
    void normalUserCannotAccessPlatformAdminSessionLifecycleEndpoints() throws Exception {
        String normalUserToken = normalUserToken();
        CsrfCredentials csrf = csrfCredentials();

        mockMvc.perform(postWithCsrf("/internal/admin/auth/revoke-sessions", csrf)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalUserToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createsCompanyThroughSharedProvisioningAndAuditsPlatformActor() throws Exception {
        UUID platformAdminId = platformAdminRepository.findByEmailIgnoreCase("platform-admin@example.com")
            .orElseThrow()
            .id();
        String token = platformAdminToken();
        CreatePlatformAdminCompanyRequest request = companyRequest("1234567893", "platform-company-admin-" + UUID.randomUUID());
        ObjectNode body = objectMapper.valueToTree(request);
        body.put("role", "PLATFORM_ADMIN");
        body.put("platformAdmin", true);
        CsrfCredentials csrf = csrfCredentials();

        MvcResult result = mockMvc.perform(postWithCsrf("/internal/admin/companies", csrf)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().isCreated())
            .andReturn();
        UUID companyId = UUID.fromString(json(result).path("id").asText());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM roles WHERE company_id = ?", Integer.class, companyId))
            .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chart_of_accounts WHERE company_id = ?", Integer.class, companyId))
            .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE company_id = ?", Integer.class, companyId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM users WHERE company_id = ?",
            Boolean.class,
            companyId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM platform_admins WHERE email = ?",
            Integer.class,
            "platform-admin@example.com"
        ))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE platform_admin_id = ? AND entity_id = ? AND action = 'COMPANY_CREATE' AND result = 'SUCCESS'",
            Integer.class,
            platformAdminId,
            companyId
        )).isEqualTo(1);
    }

    @Test
    void rollsBackCompanyProvisioningWhenInitialUserCannotBeCreated() throws Exception {
        Company existingCompany = companyRepository.saveAndFlush(Company.create(
            "Existing Company " + UUID.randomUUID(),
            "Existing Company Legal",
            "1234567894",
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        ));
        userAccessSetupService.initializeForCompany(existingCompany.id());
        String duplicateUsername = "duplicate-company-admin-" + UUID.randomUUID();
        userService.createCompanyAdmin(
            existingCompany.id(),
            duplicateUsername,
            duplicateUsername + "@example.com",
            "temporary-password",
            "Existing",
            "Admin"
        );

        UUID platformAdminId = platformAdminRepository.findByEmailIgnoreCase("platform-admin@example.com")
            .orElseThrow()
            .id();
        CreatePlatformAdminCompanyRequest request = companyRequest("1234567895", duplicateUsername);
        CsrfCredentials csrf = csrfCredentials();

        mockMvc.perform(postWithCsrf("/internal/admin/companies", csrf)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM companies WHERE tax_number = '1234567895'",
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE platform_admin_id = ? AND action = 'COMPANY_CREATE' AND result = 'FAILURE'",
            Integer.class,
            platformAdminId
        )).isGreaterThanOrEqualTo(1);
    }

    @Test
    void protectsSystemHealthWithPlatformAdminToken() throws Exception {
        mockMvc.perform(get("/internal/admin/system/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void rejectsOversizedCompanyPage() throws Exception {
        mockMvc.perform(get("/internal/admin/companies?size=101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMPANY_PAGE_INVALID"));
    }

    private MvcResult loginThroughHttp() throws Exception {
        return mockMvc.perform(post("/internal/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlatformAdminLoginRequest(
                    "platform-admin@example.com",
                    "platform-password-123"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformAdmin.email").value("platform-admin@example.com"))
            .andReturn();
    }

    private String platformAdminToken() {
        return platformAdminAuthenticationService.login(new PlatformAdminLoginRequest(
            "platform-admin@example.com",
            "platform-password-123"
        )).response().accessToken();
    }

    private PlatformAdmin createPlatformAdmin(String password) {
        return platformAdminRepository.saveAndFlush(PlatformAdmin.create(
            "platform-admin-" + UUID.randomUUID() + "@example.com",
            passwordEncoder.encode(password)
        ));
    }

    private void assertRefreshTokenRejected(String refreshToken) {
        assertThatThrownBy(() -> platformAdminAuthenticationService.refresh(refreshToken))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("REFRESH_TOKEN_EXPIRED");
    }

    private void assertRefreshCookieCleared(MvcResult result) {
        Cookie refreshCookie = result.getResponse().getCookie("TILLORA_PLATFORM_ADMIN_REFRESH_TOKEN");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isZero();
        assertThat(refreshCookie.getPath()).isEqualTo("/internal/admin/auth");
    }

    private String normalUserToken() {
        String username = "normal-user-" + UUID.randomUUID();
        Company company = companyRepository.saveAndFlush(Company.create(
            "Normal Company " + UUID.randomUUID(),
            "Normal Company Legal",
            uniqueTaxNumber(),
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        ));
        userAccessSetupService.initializeForCompany(company.id());
        userService.createCompanyAdmin(
            company.id(),
            username,
            username + "@example.com",
            "normal-password-123",
            "Normal",
            "Admin"
        );
        return authenticationService.login(new LoginRequest(
            username + "@example.com",
            username,
            "normal-password-123"
        )).response().accessToken();
    }

    private CreatePlatformAdminCompanyRequest companyRequest(String taxNumber, String adminUsername) {
        return new CreatePlatformAdminCompanyRequest(
            "Platform Company " + taxNumber,
            "Platform Company Legal " + taxNumber,
            taxNumber,
            "Kadikoy",
            "Istanbul",
            null,
            "info-" + taxNumber + "@example.com",
            "TRY",
            "Europe/Istanbul",
            adminUsername,
            adminUsername + "@example.com",
            "temporary-password",
            "Company",
            "Admin"
        );
    }

    private String uniqueTaxNumber() {
        return (String.valueOf(System.nanoTime()) + "0000000000").substring(0, 10);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private CsrfCredentials csrfCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/internal/admin/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        return new CsrfCredentials(
            json(result).path("token").asText(),
            result.getResponse().getCookie("XSRF-TOKEN")
        );
    }

    private MockHttpServletRequestBuilder postWithCsrf(String path, CsrfCredentials csrf) {
        return post(path)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token());
    }

    private record CsrfCredentials(String token, Cookie cookie) {
    }
}
