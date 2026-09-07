package com.yigitcicekci.tillora.product.application.service;

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
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    public ProductService(
        ProductRepository productRepository,
        AuditLogService auditLogService
    ) {
        this.productRepository = productRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProductResponse create(UUID companyId, UUID actorUserId, CreateProductRequest request) {
        ProductDetails details = normalize(
            request == null ? null : request.productCode(),
            request == null ? null : request.barcode(),
            request == null ? null : request.name(),
            request == null ? null : request.unit(),
            request == null ? null : request.purchasePrice(),
            request == null ? null : request.salePrice(),
            request == null ? null : request.vatRate()
        );
        validateUniqueness(companyId, null, details);
        Product product = productRepository.saveAndFlush(Product.create(
            companyId,
            details.code(),
            details.barcode(),
            details.name(),
            details.unit(),
            details.purchasePrice(),
            details.salePrice(),
            details.vatRate()
        ));
        auditLogService.record(companyId, actorUserId, AuditAction.PRODUCT_CREATE, "PRODUCT", product.id());
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(UUID companyId, Boolean active, Pageable pageable) {
        Page<Product> products = active == null
            ? productRepository.findByCompanyId(companyId, pageable)
            : productRepository.findByCompanyIdAndActive(companyId, active, pageable);
        return products.map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID companyId, UUID id) {
        return productRepository.findByIdAndCompanyId(id, companyId)
            .map(ProductResponse::from)
            .orElseThrow(() -> notFound());
    }

    @Transactional
    public ProductResponse update(
        UUID companyId,
        UUID actorUserId,
        UUID id,
        UpdateProductRequest request
    ) {
        Product product = productRepository.findForUpdate(id, companyId)
            .orElseThrow(this::notFound);
        ProductDetails details = normalize(
            product.code(),
            request == null ? null : request.barcode(),
            request == null ? null : request.name(),
            request == null ? null : request.unit(),
            request == null ? null : request.purchasePrice(),
            request == null ? null : request.salePrice(),
            request == null ? null : request.vatRate()
        );
        validateUniqueness(companyId, id, details);
        if (product.update(
            details.barcode(),
            details.name(),
            details.unit(),
            details.purchasePrice(),
            details.salePrice(),
            details.vatRate()
        )) {
            productRepository.saveAndFlush(product);
            auditLogService.record(companyId, actorUserId, AuditAction.PRODUCT_UPDATE, "PRODUCT", product.id());
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse disable(UUID companyId, UUID actorUserId, UUID id) {
        Product product = productRepository.findForUpdate(id, companyId)
            .orElseThrow(this::notFound);
        if (product.disable()) {
            productRepository.saveAndFlush(product);
            auditLogService.record(companyId, actorUserId, AuditAction.PRODUCT_DISABLE, "PRODUCT", product.id());
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(UUID companyId, UUID actorUserId, UUID id) {
        Product product = productRepository.findForUpdate(id, companyId)
            .orElseThrow(this::notFound);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                "PRODUCT_IN_USE",
                "A product with invoice records cannot be deleted. Disable it instead.",
                HttpStatus.CONFLICT
            );
        }
        auditLogService.record(companyId, actorUserId, AuditAction.PRODUCT_DELETE, "PRODUCT", id);
    }

    @Transactional
    public ProductReference findActiveProduct(UUID companyId, UUID id) {
        Product product = productRepository.findActive(id, companyId)
            .orElseThrow(() -> new BusinessException(
                "PRODUCT_NOT_ACTIVE",
                "An active product is required.",
                HttpStatus.BAD_REQUEST
            ));
        return new ProductReference(
            product.id(),
            product.code(),
            product.name(),
            product.unit().name(),
            product.purchasePrice(),
            product.vatRate()
        );
    }

    private void validateUniqueness(UUID companyId, UUID excludedId, ProductDetails details) {
        if (excludedId == null && productRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, details.code())) {
            throw new BusinessException(
                "PRODUCT_CODE_ALREADY_EXISTS",
                "Product code already exists.",
                HttpStatus.CONFLICT
            );
        }
        if (details.barcode() == null) {
            return;
        }
        boolean barcodeExists = excludedId == null
            ? productRepository.existsByCompanyIdAndBarcodeIgnoreCase(companyId, details.barcode())
            : productRepository.existsByCompanyIdAndBarcodeIgnoreCaseAndIdNot(
                companyId,
                details.barcode(),
                excludedId
            );
        if (barcodeExists) {
            throw new BusinessException(
                "PRODUCT_BARCODE_ALREADY_EXISTS",
                "Product barcode already exists.",
                HttpStatus.CONFLICT
            );
        }
    }

    private ProductDetails normalize(
        String code,
        String barcode,
        String name,
        ProductUnit unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal vatRate
    ) {
        return new ProductDetails(
            normalizeIdentifier(code, 64, "PRODUCT_CODE_INVALID", "Product code is required.", false),
            normalizeIdentifier(barcode, 64, "PRODUCT_BARCODE_INVALID", "Product barcode is invalid.", true),
            normalizeName(name),
            requiredUnit(unit),
            normalizeAmount(
                purchasePrice,
                4,
                new BigDecimal("999999999999999.9999"),
                "PRODUCT_PURCHASE_PRICE_INVALID",
                "Product purchase price is invalid."
            ),
            normalizeAmount(
                salePrice,
                4,
                new BigDecimal("999999999999999.9999"),
                "PRODUCT_SALE_PRICE_INVALID",
                "Product sale price is invalid."
            ),
            normalizeAmount(
                vatRate,
                2,
                new BigDecimal("100.00"),
                "PRODUCT_VAT_RATE_INVALID",
                "Product VAT rate must be between 0 and 100."
            )
        );
    }

    private String normalizeIdentifier(
        String value,
        int maxLength,
        String errorCode,
        String message,
        boolean optional
    ) {
        if (value == null || value.isBlank()) {
            if (optional) {
                return null;
            }
            throw new BusinessException(errorCode, message, HttpStatus.BAD_REQUEST);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength || !normalized.matches("[A-Z0-9._-]+")) {
            throw new BusinessException(errorCode, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                "PRODUCT_NAME_INVALID",
                "Product name is required.",
                HttpStatus.BAD_REQUEST
            );
        }
        String normalized = value.trim();
        if (normalized.length() > 180) {
            throw new BusinessException(
                "PRODUCT_NAME_INVALID",
                "Product name is too long.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private ProductUnit requiredUnit(ProductUnit unit) {
        if (unit == null) {
            throw new BusinessException(
                "PRODUCT_UNIT_INVALID",
                "Product unit is required.",
                HttpStatus.BAD_REQUEST
            );
        }
        return unit;
    }

    private BigDecimal normalizeAmount(
        BigDecimal value,
        int scale,
        BigDecimal maximum,
        String errorCode,
        String message
    ) {
        if (value == null || value.signum() < 0 || value.compareTo(maximum) > 0) {
            throw new BusinessException(errorCode, message, HttpStatus.BAD_REQUEST);
        }
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(errorCode, message, HttpStatus.BAD_REQUEST);
        }
    }

    private BusinessException notFound() {
        return new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND);
    }

    private record ProductDetails(
        String code,
        String barcode,
        String name,
        ProductUnit unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal vatRate
    ) {
    }
}
