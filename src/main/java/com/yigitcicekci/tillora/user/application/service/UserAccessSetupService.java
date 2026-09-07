package com.yigitcicekci.tillora.user.application.service;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.domain.entity.Permission;
import com.yigitcicekci.tillora.user.domain.entity.Role;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.repository.PermissionRepository;
import com.yigitcicekci.tillora.user.domain.repository.RoleRepository;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserAccessSetupService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public UserAccessSetupService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    public void initializeForCompany(UUID companyId) {
        if (roleRepository.existsByCompanyId(companyId)) {
            throw new BusinessException("COMPANY_ACCESS_ALREADY_INITIALIZED", "Company access setup already exists.");
        }
        Map<String, Permission> permissions = permissionRepository.findByCodeIn(allPermissionCodes()).stream()
            .collect(Collectors.toMap(Permission::code, Function.identity()));
        List<Role> roles = rolePermissions().entrySet().stream()
            .map(entry -> Role.create(companyId, entry.getKey(), permissions(entry.getValue(), permissions)))
            .toList();
        roleRepository.saveAll(roles);
    }

    private Set<Permission> permissions(Collection<String> codes, Map<String, Permission> permissions) {
        Set<Permission> matched = codes.stream()
            .map(permissions::get)
            .collect(Collectors.toSet());
        if (matched.size() != codes.size()) {
            throw new BusinessException("PERMISSION_SETUP_INCOMPLETE", "Permission setup is incomplete.");
        }
        return matched;
    }

    private Set<String> allPermissionCodes() {
        return rolePermissions().values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private Map<RoleName, Set<String>> rolePermissions() {
        Map<RoleName, Set<String>> permissions = new EnumMap<>(RoleName.class);
        permissions.put(RoleName.ADMIN, Set.of(
            "COMPANY_READ",
            "USER_READ",
            "USER_CREATE",
            "USER_UPDATE",
            "USER_DISABLE",
            "CHART_OF_ACCOUNT_READ",
            "CHART_OF_ACCOUNT_MANAGE",
            "CURRENT_ACCOUNT_READ",
            "CURRENT_ACCOUNT_CREATE",
            "CURRENT_ACCOUNT_UPDATE",
            "CASH_ACCOUNT_READ",
            "CASH_ACCOUNT_CREATE",
            "CASH_ACCOUNT_DISABLE",
            "BANK_ACCOUNT_READ",
            "BANK_ACCOUNT_CREATE",
            "BANK_ACCOUNT_DISABLE",
            "VOUCHER_READ",
            "VOUCHER_CREATE",
            "VOUCHER_APPROVE",
            "VOUCHER_CANCEL",
            "INVOICE_READ",
            "INVOICE_CREATE",
            "INVOICE_APPROVE",
            "INVOICE_CANCEL",
            "PRODUCT_READ",
            "PRODUCT_MANAGE",
            "DASHBOARD_VIEW",
            "REPORT_VIEW",
            "AUDIT_VIEW"
        ));
        permissions.put(RoleName.ACCOUNTING, Set.of(
            "CHART_OF_ACCOUNT_READ",
            "CURRENT_ACCOUNT_READ",
            "CURRENT_ACCOUNT_CREATE",
            "CURRENT_ACCOUNT_UPDATE",
            "CASH_ACCOUNT_READ",
            "CASH_ACCOUNT_CREATE",
            "CASH_ACCOUNT_DISABLE",
            "BANK_ACCOUNT_READ",
            "BANK_ACCOUNT_CREATE",
            "BANK_ACCOUNT_DISABLE",
            "VOUCHER_READ",
            "VOUCHER_CREATE",
            "VOUCHER_APPROVE",
            "VOUCHER_CANCEL",
            "INVOICE_READ",
            "INVOICE_CREATE",
            "INVOICE_APPROVE",
            "INVOICE_CANCEL",
            "PRODUCT_READ",
            "DASHBOARD_VIEW",
            "REPORT_VIEW"
        ));
        permissions.put(RoleName.SALES, Set.of(
            "CURRENT_ACCOUNT_READ",
            "CURRENT_ACCOUNT_CREATE",
            "INVOICE_READ",
            "INVOICE_CREATE",
            "PRODUCT_READ",
            "DASHBOARD_VIEW"
        ));
        permissions.put(RoleName.VIEWER, Set.of(
            "COMPANY_READ",
            "CHART_OF_ACCOUNT_READ",
            "CURRENT_ACCOUNT_READ",
            "CASH_ACCOUNT_READ",
            "BANK_ACCOUNT_READ",
            "VOUCHER_READ",
            "INVOICE_READ",
            "PRODUCT_READ",
            "DASHBOARD_VIEW",
            "REPORT_VIEW"
        ));
        return permissions;
    }
}
