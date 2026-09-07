package com.yigitcicekci.tillora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.yigitcicekci.tillora.auth.infrastructure.security.SecurityErrorWriter;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ErrorMessageResolverTest {

    private final ErrorMessageResolver resolver = new ErrorMessageResolver();

    @Test
    void resolvesTurkishFromAcceptLanguage() {
        MockHttpServletRequest request = request("tr-TR");

        ErrorMessageResolver.LocalizedError error = resolver.resolve("INVOICE_NOT_FOUND", request);

        assertThat(error.message()).isEqualTo("Fatura bulunamadı.");
        assertThat(error.locale().getLanguage()).isEqualTo("tr");
    }

    @Test
    void defaultsToEnglishForUnsupportedLanguage() {
        ErrorMessageResolver.LocalizedError error = resolver.resolve("INVOICE_NOT_FOUND", request("de-DE"));

        assertThat(error.message()).isEqualTo("Invoice not found.");
        assertThat(error.locale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void languageBundlesContainTheSameErrorCodes() {
        ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        ResourceBundle english = ResourceBundle.getBundle("messages", Locale.ENGLISH, control);
        ResourceBundle turkish = ResourceBundle.getBundle("messages", Locale.forLanguageTag("tr"), control);

        assertThat(turkish.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
    }

    @Test
    void securityErrorsUseRequestedLanguage() throws Exception {
        MockHttpServletRequest request = request("tr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityErrorWriter(resolver).unauthorized(request, response);

        assertThat(response.getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("tr");
        assertThat(response.getContentAsString()).contains("\"message\":\"Kimlik doğrulaması gereklidir.\"");
    }

    @Test
    void businessErrorsUseRequestedLanguageAtTheHttpBoundary() {
        MockHttpServletRequest request = request("tr-TR,tr;q=0.9,en;q=0.8");

        ResponseEntity<ApiErrorResponse> response = new GlobalExceptionHandler(resolver).handleBusinessException(
            new BusinessException("INVOICE_NOT_FOUND", "Invoice not found.", HttpStatus.NOT_FOUND),
            request
        );

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("tr");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Fatura bulunamadı.");
    }

    private MockHttpServletRequest request(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, language);
        return request;
    }
}
