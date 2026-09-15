package com.example.demo.service.serviceImpl;

import com.example.demo.service.DocumentParserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    public String parseDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Invalid file name");
        }

        try (InputStream is = file.getInputStream()) {
            String lowerName = filename.toLowerCase();
            if (lowerName.endsWith(".pdf")) {
                return parsePdf(is);
            } else if (lowerName.endsWith(".docx")) {
                return parseDocx(is);
            } else if (lowerName.endsWith(".pptx")) {
                return parsePptx(is);
            } else {
                throw new IllegalArgumentException("Unsupported file type. Only PDF, DOCX, and PPTX are allowed.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing document: " + e.getMessage(), e);
        }
    }

    private String parsePdf(InputStream is) throws Exception {
        try (PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String parsePptx(InputStream is) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(is)) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        sb.append(textShape.getText()).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }
}
