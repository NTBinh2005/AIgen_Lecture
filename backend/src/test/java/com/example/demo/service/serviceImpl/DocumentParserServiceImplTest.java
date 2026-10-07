package com.example.demo.service.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentParserServiceImplTest {

    @Test
    void imageOnlyPdfUsesOcrFallback() throws Exception {
        DocumentParserServiceImpl parser = new DocumentParserServiceImpl();
        ReflectionTestUtils.setField(parser, "ocrEnabled", true);
        ReflectionTestUtils.setField(parser, "ocrMaxPages", 2);
        ReflectionTestUtils.setField(parser, "ocrDpi", 200);
        ReflectionTestUtils.setField(parser, "ocrLanguages", "eng");
        ReflectionTestUtils.setField(parser, "maxExtractedCharacters", 60_000);

        String parsed = parser.parseDocument("scan.pdf", createScannedPdf());

        assertThat(parsed.toUpperCase()).contains("SCANNED DOCUMENT");
    }

    private byte[] createScannedPdf() throws Exception {
        BufferedImage image = new BufferedImage(1600, 500, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
            graphics.drawString("SCANNED DOCUMENT FOR OCR", 80, 260);
        } finally {
            graphics.dispose();
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(800, 250));
            document.addPage(page);
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdfImage, 0, 0, 800, 250);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
