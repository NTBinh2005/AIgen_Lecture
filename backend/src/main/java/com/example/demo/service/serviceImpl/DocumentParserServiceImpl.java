package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.service.DocumentParserService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    @Override
    public String parseDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Document must not be empty");
        }
        try {
            return parseDocument(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new BadRequestException("Document could not be read");
        }
    }

    @Override
    public String parseDocument(String originalFilename, byte[] content) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BadRequestException("Document filename is required");
        }
        if (content == null || content.length == 0) {
            throw new BadRequestException("Document must not be empty");
        }

        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        try (InputStream input = new ByteArrayInputStream(content)) {
            String parsed;
            if (lowerName.endsWith(".pdf")) {
                parsed = parsePdf(input);
            } else if (lowerName.endsWith(".docx")) {
                parsed = parseDocx(input);
            } else if (lowerName.endsWith(".pptx")) {
                parsed = parsePptx(input);
            } else {
                throw new BadRequestException("Only PDF, DOCX, and PPTX documents are supported");
            }
            if (!StringUtils.hasText(parsed)) {
                throw new BadRequestException("The document does not contain readable text");
            }
            return parsed.trim();
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("Document could not be parsed safely");
        }
    }

    private String parsePdf(InputStream input) throws IOException {
        try (PDDocument document = PDDocument.load(input)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDocx(InputStream input) throws IOException {
        try (XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parsePptx(InputStream input) throws IOException {
        try (XMLSlideShow presentation = new XMLSlideShow(input)) {
            StringBuilder result = new StringBuilder();
            for (XSLFSlide slide : presentation.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape && StringUtils.hasText(textShape.getText())) {
                        result.append(textShape.getText()).append('\n');
                    }
                }
            }
            return result.toString();
        }
    }
}
