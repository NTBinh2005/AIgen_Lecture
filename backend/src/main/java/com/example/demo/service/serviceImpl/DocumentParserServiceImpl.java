package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.service.DocumentParserService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentParserServiceImpl implements DocumentParserService {

    @Value("${app.document.ocr.enabled:true}")
    private boolean ocrEnabled = true;

    @Value("${app.document.ocr.max-pages:30}")
    private int ocrMaxPages = 30;

    @Value("${app.document.ocr.dpi:200}")
    private int ocrDpi = 200;

    @Value("${app.document.ocr.languages:vie+eng}")
    private String ocrLanguages = "vie+eng";

    @Value("${app.document.ocr.data-path:}")
    private String configuredTessdataPath = "";

    @Value("${app.document.max-extracted-characters:60000}")
    private int maxExtractedCharacters = 60_000;

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
                throw new BadRequestException(
                        "Không tìm thấy nội dung chữ có thể đọc được trong tài liệu, kể cả sau khi OCR");
            }
            return limitExtractedText(parsed.trim());
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("Document could not be parsed safely");
        }
    }

    private String parsePdf(InputStream input) throws IOException {
        try (PDDocument document = PDDocument.load(input)) {
            String embeddedText = new PDFTextStripper().getText(document);
            if (StringUtils.hasText(embeddedText)) {
                return embeddedText;
            }
            if (!ocrEnabled) {
                throw new BadRequestException(
                        "PDF này là tài liệu scan và chức năng OCR đang bị tắt");
            }
            return parseScannedPdf(document);
        }
    }

    private String parseScannedPdf(PDDocument document) throws IOException {
        Path tessdataPath = resolveTessdataPath();
        String availableLanguages = resolveAvailableLanguages(tessdataPath);

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath.toString());
        tesseract.setLanguage(availableLanguages);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);

        PDFRenderer renderer = new PDFRenderer(document);
        renderer.setSubsamplingAllowed(true);
        int pageLimit = Math.min(document.getNumberOfPages(), Math.max(1, ocrMaxPages));
        int dpi = Math.max(100, Math.min(ocrDpi, 300));
        StringBuilder result = new StringBuilder();

        log.info("PDF has no embedded text; starting OCR for {} of {} pages at {} DPI using {}",
                pageLimit, document.getNumberOfPages(), dpi, availableLanguages);

        try {
            for (int pageIndex = 0; pageIndex < pageLimit; pageIndex++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.GRAY);
                try {
                    String pageText = tesseract.doOCR(pageImage);
                    if (StringUtils.hasText(pageText)) {
                        result.append("\n--- Trang ").append(pageIndex + 1).append(" ---\n")
                                .append(pageText.trim()).append('\n');
                    }
                } finally {
                    pageImage.flush();
                }

                if (result.length() >= maxExtractedCharacters) {
                    break;
                }
            }
        } catch (TesseractException | RuntimeException | UnsatisfiedLinkError exception) {
            log.error("OCR failed for scanned PDF: {}", exception.getMessage(), exception);
            throw new BadRequestException(
                    "Không thể đọc chữ trong PDF scan. Vui lòng thử PDF rõ hơn hoặc bật/cài đặt OCR tiếng Việt");
        }

        return result.toString();
    }

    private Path resolveTessdataPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(configuredTessdataPath)) {
            candidates.add(Path.of(configuredTessdataPath));
        }
        String environmentPath = System.getenv("TESSDATA_PREFIX");
        if (StringUtils.hasText(environmentPath)) {
            candidates.add(Path.of(environmentPath));
        }
        candidates.add(Path.of("/usr/share/tessdata"));
        candidates.add(Path.of("/usr/share/tesseract-ocr/5/tessdata"));
        candidates.add(Path.of("C:/Program Files/Tesseract-OCR/tessdata"));

        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate) && containsTrainedData(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        File bundledData = LoadLibs.extractTessResources("tessdata");
        return bundledData.toPath().toAbsolutePath().normalize();
    }

    private String resolveAvailableLanguages(Path tessdataPath) {
        List<String> available = new ArrayList<>();
        for (String language : ocrLanguages.split("\\+")) {
            String normalized = language.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()
                    && Files.isRegularFile(tessdataPath.resolve(normalized + ".traineddata"))) {
                available.add(normalized);
            }
        }
        if (available.isEmpty() && Files.isRegularFile(tessdataPath.resolve("eng.traineddata"))) {
            available.add("eng");
        }
        if (available.isEmpty()) {
            throw new BadRequestException(
                    "Không tìm thấy dữ liệu ngôn ngữ OCR trong " + tessdataPath);
        }
        return String.join("+", available);
    }

    private boolean containsTrainedData(Path directory) {
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".traineddata"));
        } catch (IOException exception) {
            return false;
        }
    }

    private String limitExtractedText(String text) {
        int limit = Math.max(1_000, maxExtractedCharacters);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit)
                + "\n\n[Phần nội dung còn lại đã được rút gọn để tạo bài giảng.]";
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
