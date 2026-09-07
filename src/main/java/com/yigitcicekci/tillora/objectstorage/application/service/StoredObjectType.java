package com.yigitcicekci.tillora.objectstorage.application.service;

public enum StoredObjectType {
    COMPANY_LOGO_PNG("branding", "logo.png", "image/png"),
    COMPANY_LOGO_JPEG("branding", "logo.jpg", "image/jpeg"),
    REPORT_CSV("reports", "report.csv", "text/csv"),
    REPORT_XLSX(
        "reports",
        "report.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ),
    REPORT_PDF("reports", "report.pdf", "application/pdf");

    private final String directory;
    private final String fileName;
    private final String contentType;

    StoredObjectType(String directory, String fileName, String contentType) {
        this.directory = directory;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    String directory() {
        return directory;
    }

    String fileName() {
        return fileName;
    }

    String contentType() {
        return contentType;
    }
}
