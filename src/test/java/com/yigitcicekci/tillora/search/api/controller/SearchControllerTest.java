package com.yigitcicekci.tillora.search.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import com.yigitcicekci.tillora.search.application.service.SearchService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchControllerTest {

    @Test
    void usesAuthenticatedCompanyAndAuthorities() {
        SearchService service = mock(SearchService.class);
        SearchController controller = new SearchController(service);
        UUID companyId = UUID.randomUUID();
        Set<String> authorities = Set.of("CURRENT_ACCOUNT_READ");
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            UUID.randomUUID(),
            companyId,
            "search-user",
            authorities
        );
        Set<SearchResultType> types = Set.of(SearchResultType.CURRENT_ACCOUNT);

        controller.search(principal, "atlas", types, 8);

        verify(service).search(companyId, authorities, "atlas", types, 8);
    }
}
