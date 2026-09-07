package com.yigitcicekci.tillora.product.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.product.api.request.CreateProductRequest;
import com.yigitcicekci.tillora.product.api.request.UpdateProductRequest;
import com.yigitcicekci.tillora.product.api.response.ProductResponse;
import com.yigitcicekci.tillora.product.application.service.ProductService;
import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProductControllerTest {

    private final ProductService service = mock(ProductService.class);
    private final ProductController controller = new ProductController(service);

    @Test
    void usesAuthenticatedTenantAndActorForProductOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "admin",
            Set.of("PRODUCT_READ", "PRODUCT_MANAGE")
        );
        CreateProductRequest createRequest = new CreateProductRequest(
            "PRD-001",
            "869000000001",
            "Product",
            ProductUnit.PIECE,
            new BigDecimal("100"),
            new BigDecimal("120"),
            new BigDecimal("20")
        );
        UpdateProductRequest updateRequest = new UpdateProductRequest(
            "869000000001",
            "Updated Product",
            ProductUnit.PIECE,
            new BigDecimal("105"),
            new BigDecimal("125"),
            new BigDecimal("20")
        );
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
            productId,
            "PRD-001",
            "869000000001",
            "Product",
            ProductUnit.PIECE,
            new BigDecimal("100.0000"),
            new BigDecimal("120.0000"),
            new BigDecimal("20.00"),
            true,
            now,
            now
        );
        when(service.create(companyId, actorId, createRequest)).thenReturn(response);

        var created = controller.create(principal, createRequest);
        controller.list(principal, true, PageRequest.of(0, 20));
        controller.get(principal, productId);
        controller.update(principal, productId, updateRequest);
        controller.disable(principal, productId);
        var deleted = controller.delete(principal, productId);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/products/" + productId));
        verify(service).create(companyId, actorId, createRequest);
        verify(service).list(companyId, true, PageRequest.of(0, 20));
        verify(service).get(companyId, productId);
        verify(service).update(companyId, actorId, productId, updateRequest);
        verify(service).disable(companyId, actorId, productId);
        verify(service).delete(companyId, actorId, productId);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
    }
}
