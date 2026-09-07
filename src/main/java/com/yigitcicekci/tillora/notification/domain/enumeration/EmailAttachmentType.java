package com.yigitcicekci.tillora.notification.domain.enumeration;

public enum EmailAttachmentType {
    STATEMENT_CSV("current-account-statement.csv", "text/csv"),
    STATEMENT_PDF("current-account-statement.pdf", "application/pdf");

    private final String fileName;
    private final String contentType;

    EmailAttachmentType(String fileName, String contentType) {
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }
}
