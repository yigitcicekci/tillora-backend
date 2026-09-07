package com.yigitcicekci.tillora.user.domain.repository;

import com.yigitcicekci.tillora.user.domain.entity.Role;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findByCompanyIdAndNameIn(UUID companyId, Collection<RoleName> names);
}
