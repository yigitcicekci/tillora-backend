package com.yigitcicekci.tillora.user.api.controller;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.user.api.request.CreateUserRequest;
import com.yigitcicekci.tillora.user.api.response.UserResponse;
import com.yigitcicekci.tillora.user.application.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    ResponseEntity<UserResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    Page<UserResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal, Pageable pageable) {
        return userService.list(principal.companyId(), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    UserResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return userService.get(principal.companyId(), id);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_DISABLE')")
    UserResponse disable(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return userService.disable(principal.companyId(), principal.userId(), id);
    }
}
