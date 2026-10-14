package com.example.demo.service;

import com.example.demo.entity.PresentationExport;
import java.util.UUID;

public final class PresentationExportContent {
    private final UUID exportId;
    private final String fileName;
    private final String contentType;
    private final byte[] bytes;

    private PresentationExportContent(PresentationExport export) {
        this.exportId = export.getPresentationExportId();
        this.fileName = export.getFileName();
        this.contentType = export.getContentType();
        this.bytes = export.getFileBytes().clone();
    }

    public static PresentationExportContent from(PresentationExport export) {
        return new PresentationExportContent(export);
    }

    public UUID getExportId() { return exportId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getBytes() { return bytes.clone(); }
}
