package com.yigitcicekci.tillora.user.domain.repository;

import com.yigitcicekci.tillora.user.domain.entity.Permission;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findByCodeIn(Collection<String> codes);
}
