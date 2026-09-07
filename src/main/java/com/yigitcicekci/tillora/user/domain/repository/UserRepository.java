package com.yigitcicekci.tillora.user.domain.repository;

import com.yigitcicekci.tillora.user.domain.entity.User;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByCompanyIdAndEmailIgnoreCase(UUID companyId, String email);

    Page<User> findByCompanyId(UUID companyId, Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    @Query("select distinct u from User u where u.id in :ids")
    List<User> findWithRolesByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByIdAndCompanyIdAndStatus(UUID id, UUID companyId, UserStatus status);

    long countByCompanyIdAndStatusAndRolesName(UUID companyId, UserStatus status, RoleName roleName);

    @EntityGraph(attributePaths = "roles")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u
        from User u
        where u.id = :id
          and u.companyId = :companyId
          and u.status = :status
        """)
    Optional<User> findForDisable(
        @Param("id") UUID id,
        @Param("companyId") UUID companyId,
        @Param("status") UserStatus status
    );

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.status = com.yigitcicekci.tillora.user.domain.enumeration.UserStatus.ACTIVE and lower(u.email) = lower(:email) and lower(u.username) = lower(:username)")
    Optional<User> findActiveLoginUser(String email, String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByIdAndStatus(UUID id, UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u
        from User u
        where u.id = :id
          and u.companyId = :companyId
          and u.status = :status
        """)
    Optional<User> findForPasswordChange(UUID id, UUID companyId, UserStatus status);
}
