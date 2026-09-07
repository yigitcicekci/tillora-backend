package com.yigitcicekci.tillora.search.application.service;

import com.yigitcicekci.tillora.search.api.response.SearchResultResponse;
import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import com.yigitcicekci.tillora.search.infrastructure.persistence.SearchQueryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private static final int MINIMUM_TERM_LENGTH = 3;
    private static final int MAXIMUM_TERM_LENGTH = 100;
    private static final int MAXIMUM_RESULT_LIMIT = 20;

    private final SearchQueryRepository searchQueryRepository;

    public SearchService(SearchQueryRepository searchQueryRepository) {
        this.searchQueryRepository = searchQueryRepository;
    }

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(
        UUID companyId,
        Set<String> authorities,
        String query,
        Set<SearchResultType> requestedTypes,
        int limit
    ) {
        String term = query == null ? "" : query.trim();
        if (term.length() < MINIMUM_TERM_LENGTH || term.length() > MAXIMUM_TERM_LENGTH) {
            throw new BusinessException(
                "SEARCH_QUERY_INVALID",
                "Search query length must be between 3 and 100 characters.",
                HttpStatus.BAD_REQUEST
            );
        }
        if (limit < 1 || limit > MAXIMUM_RESULT_LIMIT) {
            throw new BusinessException(
                "SEARCH_LIMIT_INVALID",
                "Search result limit must be between 1 and 20.",
                HttpStatus.BAD_REQUEST
            );
        }
        EnumSet<SearchResultType> allowedTypes = allowedTypes(authorities);
        if (requestedTypes != null && !requestedTypes.isEmpty()) {
            allowedTypes.retainAll(requestedTypes);
        }
        if (allowedTypes.isEmpty()) {
            return List.of();
        }
        return searchQueryRepository.search(companyId, term, allowedTypes, limit);
    }

    private EnumSet<SearchResultType> allowedTypes(Set<String> authorities) {
        EnumSet<SearchResultType> types = EnumSet.noneOf(SearchResultType.class);
        if (authorities.contains("CURRENT_ACCOUNT_READ")) {
            types.add(SearchResultType.CURRENT_ACCOUNT);
        }
        if (authorities.contains("PRODUCT_READ")) {
            types.add(SearchResultType.PRODUCT);
        }
        if (authorities.contains("INVOICE_READ")) {
            types.add(SearchResultType.DOCUMENT);
        }
        return types;
    }
}
