package com.yigitcicekci.tillora.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import com.yigitcicekci.tillora.search.infrastructure.persistence.SearchQueryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private final SearchQueryRepository repository = mock(SearchQueryRepository.class);
    private final SearchService service = new SearchService(repository);

    @Test
    void searchesOnlyRequestedAndAuthorizedTypes() {
        UUID companyId = UUID.randomUUID();

        service.search(
            companyId,
            Set.of("CURRENT_ACCOUNT_READ", "INVOICE_READ"),
            "  atlas  ",
            Set.of(SearchResultType.CURRENT_ACCOUNT, SearchResultType.PRODUCT),
            10
        );

        verify(repository).search(
            companyId,
            "atlas",
            EnumSet.of(SearchResultType.CURRENT_ACCOUNT),
            10
        );
    }

    @Test
    void rejectsQueriesShorterThanThreeCharactersAfterTrimming() {
        assertThatThrownBy(() -> service.search(
            UUID.randomUUID(),
            Set.of("CURRENT_ACCOUNT_READ"),
            "  ab ",
            null,
            10
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("SEARCH_QUERY_INVALID");
    }

    @Test
    void returnsNoResultsWithoutAnAuthorizedRequestedType() {
        assertThat(service.search(
            UUID.randomUUID(),
            Set.of("CURRENT_ACCOUNT_READ"),
            "atlas",
            Set.of(SearchResultType.PRODUCT),
            10
        )).isEmpty();
        verifyNoInteractions(repository);
    }
}
