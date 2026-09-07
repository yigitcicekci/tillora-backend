package com.yigitcicekci.tillora.company.domain.repository;

import com.yigitcicekci.tillora.company.domain.entity.Company;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Query(value = "select pg_advisory_xact_lock(837451092734)", nativeQuery = true)
    void acquireBootstrapLock();

    @Query(value = "select pg_advisory_xact_lock(837451092734)", nativeQuery = true)
    void acquireCompanyCreationLock();

    boolean existsByTaxNumber(String taxNumber);

    Optional<Company> findByIdAndStatus(UUID id, com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus status);
}
