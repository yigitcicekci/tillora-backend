package com.yigitcicekci.tillora.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.product.api.request.CreateProductRequest;
import com.yigitcicekci.tillora.product.api.request.UpdateProductRequest;
import com.yigitcicekci.tillora.product.api.response.ProductResponse;
import com.yigitcicekci.tillora.product.domain.entity.Product;
import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import com.yigitcicekci.tillora.product.domain.repository.ProductRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ProductService service = new ProductService(productRepository, auditLogService);

    @Test
    void normalizesAndCreatesProduct() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(productRepository.saveAndFlush(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(
            companyId,
            actorId,
            new CreateProductRequest(
                " prd-001 ",
                " abc-123 ",
                "  Product  ",
                ProductUnit.PIECE,
                new BigDecimal("100"),
                new BigDecimal("120.5"),
                new BigDecimal("20")
            )
        );

        assertThat(response.code()).isEqualTo("PRD-001");
        assertThat(response.barcode()).isEqualTo("ABC-123");
        assertThat(response.name()).isEqualTo("Product");
        assertThat(response.purchasePrice()).isEqualByComparingTo("100.0000");
        assertThat(response.salePrice()).isEqualByComparingTo("120.5000");
        assertThat(response.vatRate()).isEqualByComparingTo("20.00");
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.PRODUCT_CREATE,
            "PRODUCT",
            response.id()
        );
    }

    @Test
    void rejectsDuplicateBarcodeBeforePersistence() {
        UUID companyId = UUID.randomUUID();
        when(productRepository.existsByCompanyIdAndBarcodeIgnoreCase(companyId, "ABC-123"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            new CreateProductRequest(
                "PRD-001",
                "ABC-123",
                "Product",
                ProductUnit.PIECE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_BARCODE_ALREADY_EXISTS");
        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void requiresProductCode() {
        assertThatThrownBy(() -> service.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new CreateProductRequest(
                " ",
                null,
                "Product",
                ProductUnit.PIECE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_CODE_INVALID");
        verifyNoInteractions(productRepository, auditLogService);
    }

    @Test
    void rejectsDuplicateProductCodeBeforePersistence() {
        UUID companyId = UUID.randomUUID();
        when(productRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, "PRD-001"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            new CreateProductRequest(
                "PRD-001",
                null,
                "Product",
                ProductUnit.PIECE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_CODE_ALREADY_EXISTS");
        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updatesProductAndAuditsOnlyActualChanges() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Product product = Product.create(
            companyId,
            "PRD-001",
            null,
            "Product",
            ProductUnit.PIECE,
            new BigDecimal("100.0000"),
            new BigDecimal("120.0000"),
            new BigDecimal("20.00")
        );
        when(productRepository.findForUpdate(product.id(), companyId)).thenReturn(Optional.of(product));
        UpdateProductRequest unchanged = new UpdateProductRequest(
            null,
            "Product",
            ProductUnit.PIECE,
            new BigDecimal("100"),
            new BigDecimal("120"),
            new BigDecimal("20")
        );

        service.update(companyId, actorId, product.id(), unchanged);

        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);

        UpdateProductRequest changed = new UpdateProductRequest(
            "ABC-123",
            "Updated Product",
            ProductUnit.BOX,
            new BigDecimal("101"),
            new BigDecimal("125"),
            new BigDecimal("10")
        );
        ProductResponse response = service.update(companyId, actorId, product.id(), changed);

        assertThat(response.code()).isEqualTo(product.code());
        assertThat(response.unit()).isEqualTo(ProductUnit.BOX);
        verify(productRepository).saveAndFlush(product);
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.PRODUCT_UPDATE,
            "PRODUCT",
            product.id()
        );
    }

    @Test
    void rejectsAmountsWithUnsupportedScale() {
        assertThatThrownBy(() -> service.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new CreateProductRequest(
                "PRD-001",
                null,
                "Product",
                ProductUnit.PIECE,
                new BigDecimal("1.00001"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_PURCHASE_PRICE_INVALID");
        verifyNoInteractions(productRepository, auditLogService);
    }

    @Test
    void deletesUnusedProductAndRejectsUsedProduct() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Product unused = product(companyId, "Unused");
        when(productRepository.findForUpdate(unused.id(), companyId)).thenReturn(Optional.of(unused));

        service.delete(companyId, actorId, unused.id());

        verify(productRepository).delete(unused);
        verify(productRepository).flush();
        verify(auditLogService).record(companyId, actorId, AuditAction.PRODUCT_DELETE, "PRODUCT", unused.id());

        Product used = product(companyId, "Used");
        when(productRepository.findForUpdate(used.id(), companyId)).thenReturn(Optional.of(used));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("referenced"))
            .when(productRepository).flush();

        assertThatThrownBy(() -> service.delete(companyId, actorId, used.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PRODUCT_IN_USE");
    }

    private Product product(UUID companyId, String name) {
        return Product.create(
            companyId,
            "PRD-" + UUID.randomUUID(),
            null,
            name,
            ProductUnit.PIECE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}
