package com.yigitcicekci.tillora.search.api.controller;

import com.yigitcicekci.tillora.search.api.response.SearchResultResponse;
import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import com.yigitcicekci.tillora.search.application.service.SearchService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @PreAuthorize(
        "hasAnyAuthority('CURRENT_ACCOUNT_READ', 'PRODUCT_READ', 'INVOICE_READ')"
    )
    List<SearchResultResponse> search(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam("q") String query,
        @RequestParam(required = false) Set<SearchResultType> types,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return searchService.search(
            principal.companyId(),
            principal.authorities(),
            query,
            types,
            limit
        );
    }
}
