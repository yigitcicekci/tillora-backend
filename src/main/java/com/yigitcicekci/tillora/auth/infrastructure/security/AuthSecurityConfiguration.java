package com.yigitcicekci.tillora.auth.infrastructure.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class AuthSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain managementSecurityFilterChain(
        HttpSecurity http,
        @Value("${tillora.security.management.password}") String password,
        PasswordEncoder passwordEncoder
    ) throws Exception {
        if (password == null || password.length() < 16) {
            throw new IllegalStateException("Management password must be at least 16 characters.");
        }
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(User.withUsername("tillora-monitoring")
            .password(passwordEncoder.encode(password))
            .authorities("ACTUATOR_READ")
            .build());
        DaoAuthenticationProvider managementAuthenticationProvider = new DaoAuthenticationProvider(users);
        managementAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        http
            .securityMatcher("/actuator/**")
            .authenticationProvider(managementAuthenticationProvider)
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().hasAuthority("ACTUATOR_READ")
            );
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        PlatformAdminJwtAuthenticationFilter platformAdminJwtAuthenticationFilter,
        SecurityErrorWriter securityErrorWriter,
        CorsConfigurationSource corsConfigurationSource,
        @Value("${tillora.security.cookie.secure:true}") boolean secureCookie,
        @Value("${tillora.security.cookie.same-site:Lax}") String sameSite
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite(sameSite).path("/"));
        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfTokenRequestHandler)
                .ignoringRequestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/companies",
                    "/internal/admin/auth/login"
                )
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> securityErrorWriter.unauthorized(request, response))
                .accessDeniedHandler((request, response, exception) -> securityErrorWriter.forbidden(request, response))
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api/v1/auth/csrf",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/internal/admin/auth/csrf",
                    "/internal/admin/auth/login",
                    "/internal/admin/auth/refresh"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/companies").permitAll()
                .requestMatchers("/internal/admin/**").hasAuthority("PLATFORM_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(platformAdminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${tillora.security.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-XSRF-TOKEN",
            "Idempotency-Key",
            "Accept-Language",
            "X-Correlation-Id"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
