package com.yigitcicekci.tillora.product.domain.repository;

import com.yigitcicekci.tillora.product.domain.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    boolean existsByCompanyIdAndBarcodeIgnoreCase(UUID companyId, String barcode);

    boolean existsByCompanyIdAndBarcodeIgnoreCaseAndIdNot(UUID companyId, String barcode, UUID id);

    Page<Product> findByCompanyId(UUID companyId, Pageable pageable);

    Page<Product> findByCompanyIdAndActive(UUID companyId, boolean active, Pageable pageable);

    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id and product.companyId = :companyId")
    Optional<Product> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select product
        from Product product
        where product.id = :id
          and product.companyId = :companyId
          and product.active = true
        """)
    Optional<Product> findActive(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
