package com.yigitcicekci.tillora.notification.application.service;

import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountEmailSnapshot;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.notification.application.port.EmailMessage;
import com.yigitcicekci.tillora.notification.application.port.EmailMessageAttachment;
import com.yigitcicekci.tillora.notification.application.port.EmailProvider;
import com.yigitcicekci.tillora.notification.application.port.EmailProviderException;
import com.yigitcicekci.tillora.notification.application.port.EmailSendResult;
import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.web.request.SendCurrentAccountStatementEmailRequest;
import com.yigitcicekci.tillora.notification.web.response.EmailDeliveryResponse;
import com.yigitcicekci.tillora.reporting.application.service.CurrentAccountStatementExportRow;
import com.yigitcicekci.tillora.reporting.application.service.ReportingService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDeliveryService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_END = "%%EOF".getBytes(StandardCharsets.US_ASCII);

    private final EmailProvider emailProvider;
    private final EmailLimits limits;
    private final CurrentAccountService currentAccountService;
    private final ReportingService reportingService;
    private final CompanyService companyService;
    private final EmailDeliveryStateService stateService;
    private final EmailDeliveryMetrics metrics;

    public EmailDeliveryService(
        EmailProvider emailProvider,
        EmailLimits limits,
        CurrentAccountService currentAccountService,
        ReportingService reportingService,
        CompanyService companyService,
        EmailDeliveryStateService stateService,
        EmailDeliveryMetrics metrics
    ) {
        this.emailProvider = emailProvider;
        this.limits = limits;
        this.currentAccountService = currentAccountService;
        this.reportingService = reportingService;
        this.companyService = companyService;
        this.stateService = stateService;
        this.metrics = metrics;
    }

    public EmailDeliveryResponse sendCurrentAccountStatement(
        UUID companyId,
        UUID actorUserId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String idempotencyKey,
        SendCurrentAccountStatementEmailRequest request
    ) {
        if (!emailProvider.enabled()) {
            throw new BusinessException(
                "EMAIL_DISABLED",
                "Transactional email is disabled.",
                HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        NormalizedRequest normalized = normalize(idempotencyKey, request);
        byte[] pdf = statementPdf(request.pdfBase64());
        CurrentAccountEmailSnapshot currentAccount = currentAccountService.emailSnapshot(companyId, currentAccountId);
        List<CurrentAccountStatementExportRow> rows = reportingService.currentAccountStatementForExport(
            companyId,
            currentAccountId,
            dateFrom,
            dateTo
        );
        String companyName = companyService.name(companyId);
        byte[] csv = statementCsv(rows);
        long attachmentSize = (long) csv.length + pdf.length;
        if (attachmentSize > limits.maxAttachmentSize()) {
            throw new BusinessException(
                "EMAIL_ATTACHMENT_TOO_LARGE",
                "Email attachments exceed the configured size limit.",
                HttpStatus.PAYLOAD_TOO_LARGE
            );
        }
        String fingerprint = EmailRequestFingerprint.create(
            normalized.to(),
            normalized.cc(),
            normalized.subject(),
            normalized.message(),
            normalized.attachments(),
            dateContext(dateFrom, dateTo) + "|" + pdfChecksum(pdf)
        );
        PreparedEmailDelivery prepared = stateService.prepareCurrentAccountStatement(
            companyId,
            actorUserId,
            currentAccountId,
            normalized.idempotencyKey(),
            fingerprint,
            normalized.subject(),
            normalized.to(),
            normalized.cc(),
            normalized.attachments()
        );
        if (!prepared.created()) {
            return EmailDeliveryResponse.from(prepared.delivery());
        }
        long started = System.nanoTime();
        EmailDelivery delivery;
        try {
            EmailSendResult result = emailProvider.send(statementMessage(
                currentAccount,
                companyName,
                dateFrom,
                dateTo,
                normalized,
                csv,
                pdf
            ));
            if (result == null
                || result.providerMessageId() == null
                || result.providerMessageId().isBlank()) {
                throw new EmailProviderException(
                    "EMAIL_PROVIDER_MALFORMED_RESPONSE",
                    "Email provider returned an invalid response."
                );
            }
            delivery = stateService.sent(
                companyId,
                prepared.delivery().id(),
                result.providerMessageId()
            );
        } catch (EmailProviderException exception) {
            delivery = stateService.failed(
                companyId,
                prepared.delivery().id(),
                exception.code(),
                exception.getMessage()
            );
        } catch (RuntimeException exception) {
            delivery = stateService.failed(
                companyId,
                prepared.delivery().id(),
                "EMAIL_PROVIDER_UNAVAILABLE",
                "Email provider is unavailable."
            );
        }
        metrics.record(delivery.provider(), delivery.status(), System.nanoTime() - started);
        LOGGER.atInfo()
            .addKeyValue("deliveryId", delivery.id())
            .addKeyValue("provider", delivery.provider())
            .addKeyValue("status", delivery.status())
            .log("Email delivery completed");
        return EmailDeliveryResponse.from(delivery);
    }

    private EmailMessage statementMessage(
        CurrentAccountEmailSnapshot currentAccount,
        String companyName,
        LocalDate dateFrom,
        LocalDate dateTo,
        NormalizedRequest request,
        byte[] csv,
        byte[] pdf
    ) {
        String escapedMessage = HtmlUtils.htmlEscape(request.message())
            .replace("\r\n", "<br>")
            .replace("\n", "<br>")
            .replace("\r", "<br>");
        String safeAccountName = HtmlUtils.htmlEscape(currentAccount.name());
        String safeCompanyName = HtmlUtils.htmlEscape(companyName);
        String safePeriod = HtmlUtils.htmlEscape(period(dateFrom, dateTo));
        String html = """
            <!doctype html><html><body style="margin:0;background:#f5f7fa;font-family:Arial,sans-serif;color:#182230">
            <div style="max-width:640px;margin:24px auto;background:#ffffff;border-radius:10px;padding:32px">
            <div style="font-size:24px;font-weight:700;color:#2557d6">Tillora</div>
            <h1 style="font-size:20px;margin:28px 0 8px">Cari hesap ekstresi</h1>
            <p style="margin:0 0 8px"><strong>%s</strong> cari hesabının ekstresi %s tarafından iletilmiştir.</p>
            <p style="margin:0 0 20px;color:#667085">Dönem: %s</p>
            <div style="padding:16px;background:#f5f7fa;border-radius:8px;line-height:1.6">%s</div>
            <p style="margin:24px 0 0;color:#667085;font-size:13px">Ekstre CSV ve PDF dosyaları bu e-postanın ekindedir.</p>
            </div></body></html>
            """.formatted(safeAccountName, safeCompanyName, safePeriod, escapedMessage);
        String text = "Tillora\n\nCari hesap: " + currentAccount.name()
            + "\nŞirket: " + companyName
            + "\nDönem: " + period(dateFrom, dateTo)
            + "\n\n" + request.message()
            + "\n\nEkstre CSV ve PDF dosyaları bu e-postanın ekindedir.";
        return new EmailMessage(
            request.to(),
            request.cc(),
            request.subject(),
            text,
            html,
            List.of(
                new EmailMessageAttachment(
                    EmailAttachmentType.STATEMENT_CSV.fileName(),
                    EmailAttachmentType.STATEMENT_CSV.contentType(),
                    csv
                ),
                new EmailMessageAttachment(
                    EmailAttachmentType.STATEMENT_PDF.fileName(),
                    EmailAttachmentType.STATEMENT_PDF.contentType(),
                    pdf
                )
            )
        );
    }

    private byte[] statementCsv(List<CurrentAccountStatementExportRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Tarih,Fiş No,Fiş Türü,Belge No,Açıklama,Borç,Alacak,Bakiye,Para Birimi,Kur\r\n");
        for (CurrentAccountStatementExportRow row : rows) {
            csv.append(csvValue(row.voucherDate()))
                .append(',').append(csvValue(row.voucherNumber()))
                .append(',').append(csvValue(row.voucherType()))
                .append(',').append(csvValue(row.documentNumber()))
                .append(',').append(csvValue(row.movementNote()))
                .append(',').append(csvValue(row.debit()))
                .append(',').append(csvValue(row.credit()))
                .append(',').append(csvValue(row.balance()))
                .append(',').append(csvValue(row.currency()))
                .append(',').append(csvValue(row.exchangeRate()))
                .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] statementPdf(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalidStatementPdf();
        }
        String value = encoded.trim();
        if (value.regionMatches(true, 0, "data:", 0, 5)) {
            int separator = value.indexOf(',');
            if (separator < 0
                || !value.substring(0, separator)
                    .equalsIgnoreCase("data:application/pdf;base64")) {
                throw invalidStatementPdf();
            }
            value = value.substring(separator + 1);
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalidStatementPdf();
        }
        if (!startsWith(content, PDF_HEADER)
            || !containsFrom(content, PDF_END, Math.max(0, content.length - 1024))) {
            throw invalidStatementPdf();
        }
        return content;
    }

    private String pdfChecksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean startsWith(byte[] content, byte[] expected) {
        if (content.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsFrom(byte[] content, byte[] expected, int start) {
        for (int index = start; index <= content.length - expected.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < expected.length; offset++) {
                if (content[index + offset] != expected[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private String csvValue(Object value) {
        String text = value instanceof BigDecimal amount
            ? amount.toPlainString()
            : value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String period(LocalDate dateFrom, LocalDate dateTo) {
        return (dateFrom == null ? "Başlangıç" : dateFrom)
            + " - " + (dateTo == null ? "Bugün" : dateTo);
    }

    private String dateContext(LocalDate dateFrom, LocalDate dateTo) {
        return (dateFrom == null ? "" : dateFrom.toString())
            + "|" + (dateTo == null ? "" : dateTo.toString());
    }

    private NormalizedRequest normalize(
        String idempotencyKey,
        SendCurrentAccountStatementEmailRequest request
    ) {
        if (request == null) {
            throw invalidRequest();
        }
        return normalize(
            idempotencyKey,
            request.to(),
            request.cc(),
            request.subject(),
            request.message(),
            List.of(
                EmailAttachmentType.STATEMENT_CSV.name(),
                EmailAttachmentType.STATEMENT_PDF.name()
            )
        );
    }

    private NormalizedRequest normalize(
        String idempotencyKey,
        List<String> recipientValues,
        List<String> ccValues,
        String subjectValue,
        String messageValue,
        List<String> attachmentValues
    ) {
        if (idempotencyKey == null
            || !IDEMPOTENCY_PATTERN.matcher(idempotencyKey).matches()) {
            throw invalidRequest();
        }
        List<String> to = normalizeEmails(recipientValues);
        List<String> cc = normalizeEmails(ccValues).stream()
            .filter(value -> !to.contains(value))
            .toList();
        if (to.isEmpty()) {
            throw invalidRequest();
        }
        if (to.size() + cc.size() > limits.maxRecipients()) {
            throw new BusinessException(
                "EMAIL_TOO_MANY_RECIPIENTS",
                "Email recipient limit was exceeded.",
                HttpStatus.BAD_REQUEST
            );
        }
        String subject = subjectValue == null ? "" : subjectValue.trim();
        String message = messageValue == null ? "" : messageValue.trim();
        if (subject.isEmpty()
            || subject.length() > 200
            || message.length() > 5000
            || headerValueInvalid(subject)) {
            throw invalidRequest();
        }
        List<EmailAttachmentType> attachments = normalizeAttachments(attachmentValues);
        return new NormalizedRequest(
            idempotencyKey,
            to,
            cc,
            subject,
            message,
            attachments
        );
    }

    private List<String> normalizeEmails(List<String> values) {
        if (values == null) {
            throw invalidRecipient();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (email.length() > 254
                || headerValueInvalid(email)
                || !EMAIL_PATTERN.matcher(email).matches()) {
                throw invalidRecipient();
            }
            normalized.add(email);
        }
        return List.copyOf(normalized);
    }

    private List<EmailAttachmentType> normalizeAttachments(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw unsupportedAttachment();
        }
        LinkedHashSet<EmailAttachmentType> types = new LinkedHashSet<>();
        try {
            for (String value : values) {
                types.add(EmailAttachmentType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            }
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw unsupportedAttachment();
        }
        return List.copyOf(types);
    }

    private boolean headerValueInvalid(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private BusinessException invalidRecipient() {
        return new BusinessException(
            "EMAIL_RECIPIENT_INVALID",
            "Email recipient is invalid.",
            HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException unsupportedAttachment() {
        return new BusinessException(
            "EMAIL_ATTACHMENT_NOT_SUPPORTED",
            "Email attachment type is not supported.",
            HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException invalidStatementPdf() {
        return new BusinessException(
            "EMAIL_ATTACHMENT_INVALID",
            "Current account statement PDF is invalid.",
            HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException invalidRequest() {
        return new BusinessException(
            "EMAIL_REQUEST_INVALID",
            "Email delivery request is invalid.",
            HttpStatus.BAD_REQUEST
        );
    }

    private record NormalizedRequest(
        String idempotencyKey,
        List<String> to,
        List<String> cc,
        String subject,
        String message,
        List<EmailAttachmentType> attachments
    ) {
    }
}
