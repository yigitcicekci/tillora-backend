package com.yigitcicekci.tillora.company.application.service;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CompanyProvisioningTokenVerifier {

    private final byte[] expectedToken;

    public CompanyProvisioningTokenVerifier(
        @Value("${tillora.security.company-provisioning-token}") String expectedToken
    ) {
        if (expectedToken == null || expectedToken.length() < 32) {
            throw new IllegalStateException("Company provisioning token must be at least 32 characters.");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String suppliedToken) {
        byte[] supplied = suppliedToken == null
            ? new byte[0]
            : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, supplied)) {
            throw new BusinessException(
                "COMPANY_PROVISIONING_TOKEN_INVALID",
                "Company provisioning token is invalid.",
                HttpStatus.FORBIDDEN
            );
        }
    }
}
