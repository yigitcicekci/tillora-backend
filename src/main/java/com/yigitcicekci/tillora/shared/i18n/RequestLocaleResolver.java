package com.yigitcicekci.tillora.shared.i18n;

import java.util.List;
import java.util.Locale;

public final class RequestLocaleResolver {

    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private RequestLocaleResolver() {
    }

    public static Locale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }
        try {
            Locale locale = Locale.lookup(
                Locale.LanguageRange.parse(acceptLanguage),
                List.of(TURKISH, ENGLISH)
            );
            return locale != null && TURKISH.getLanguage().equals(locale.getLanguage())
                ? TURKISH
                : ENGLISH;
        } catch (IllegalArgumentException exception) {
            return ENGLISH;
        }
    }
}
