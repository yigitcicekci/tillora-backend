package com.yigitcicekci.tillora.shared.error;

import com.yigitcicekci.tillora.shared.i18n.RequestLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.springframework.stereotype.Component;

@Component
public class ErrorMessageResolver {

    private static final ResourceBundle.Control NO_LOCALE_FALLBACK = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_PROPERTIES
    );

    public LocalizedError resolve(String code, HttpServletRequest request) {
        Locale locale = requestedLocale(request);
        ResourceBundle messages = ResourceBundle.getBundle("messages", locale, NO_LOCALE_FALLBACK);
        String key = "error." + code;
        String message;
        try {
            message = messages.getString(key);
        } catch (MissingResourceException exception) {
            message = messages.getString("error.UNKNOWN");
        }
        return new LocalizedError(message, locale);
    }

    private Locale requestedLocale(HttpServletRequest request) {
        return RequestLocaleResolver.resolve(request.getHeader("Accept-Language"));
    }

    public record LocalizedError(String message, Locale locale) {
    }
}
