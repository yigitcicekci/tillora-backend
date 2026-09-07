package com.yigitcicekci.tillora.finance.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountProvision;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.domain.entity.BankAccount;
import com.yigitcicekci.tillora.finance.domain.repository.BankAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BankAccountServiceTest {

    private final BankAccountRepository bankAccountRepository = mock(BankAccountRepository.class);
    private final LedgerAccountProvisioningService ledgerAccountProvisioningService =
        mock(LedgerAccountProvisioningService.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final BankAccountService service = new BankAccountService(
        bankAccountRepository,
        ledgerAccountProvisioningService,
        companyService,
        mock(AuditLogService.class)
    );

    @Test
    void normalizesIbanAndAccountNumberBeforePersistence() {
        UUID companyId = UUID.randomUUID();
        UUID chartOfAccountId = UUID.randomUUID();
        when(companyService.currency(companyId)).thenReturn("TRY");
        when(ledgerAccountProvisioningService.createBankPostingAccount(companyId, "Main Bank"))
            .thenReturn(new PostingAccountProvision(chartOfAccountId, "102.01"));
        when(bankAccountRepository.saveAndFlush(any(BankAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        BankAccountResponse response = service.create(
            companyId,
            UUID.randomUUID(),
            new CreateBankAccountRequest(
                " Main Bank ",
                " Istanbul Branch ",
                " tr33 0006 1005 1978 6457 8413 26 ",
                " 00-12/ab.34 ",
                null
            )
        );

        assertThat(response.name()).isEqualTo("Main Bank");
        assertThat(response.branch()).isEqualTo("Istanbul Branch");
        assertThat(response.iban()).isEqualTo("TR330006100519786457841326");
        assertThat(response.accountNumber()).isEqualTo("0012AB34");
        assertThat(response.accountCode()).isEqualTo("102.01");
        assertThat(response.currency()).isEqualTo("TRY");
        verify(bankAccountRepository)
            .existsByCompanyIdAndIban(companyId, "TR330006100519786457841326");
        verify(bankAccountRepository)
            .existsByCompanyIdAndNameIgnoreCaseAndBranchIgnoreCaseAndAccountNumber(
                companyId,
                "Main Bank",
                "Istanbul Branch",
                "0012AB34"
            );
    }

    @Test
    void rejectsInvalidIbanChecksumBeforeProvisioningLedgerAccount() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            new CreateBankAccountRequest(
                "Main Bank",
                "Istanbul Branch",
                "TR340006100519786457841326",
                "12345678",
                "TRY"
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_IBAN");
        verifyNoInteractions(ledgerAccountProvisioningService);
    }

    @Test
    void rejectsAccountNumberThatIsEmptyAfterNormalization() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            new CreateBankAccountRequest(
                "Main Bank",
                "Istanbul Branch",
                "TR330006100519786457841326",
                " .-/ ",
                "TRY"
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_BANK_ACCOUNT_NUMBER");
        verifyNoInteractions(ledgerAccountProvisioningService);
    }
}
