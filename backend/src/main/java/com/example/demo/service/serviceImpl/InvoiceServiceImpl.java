package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.InvoiceResponse;
import com.example.demo.entity.Invoice;
import com.example.demo.entity.Payment;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.service.InvoiceService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter INVOICE_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final Locale VIETNAMESE_LOCALE = Locale.forLanguageTag("vi-VN");

    private final InvoiceRepository invoiceRepository;
    private final com.example.demo.repository.UserRepository userRepository;

    private boolean isAdmin(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"))
                .getRole() == com.example.demo.entity.UserRole.ADMIN;
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InvoiceResponse> getMyInvoices(
            Integer userId, org.springframework.data.domain.Pageable pageable) {
        return (isAdmin(userId) ? invoiceRepository.findAll(pageable)
                : invoiceRepository.findByUserUserId(userId, pageable)).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Integer userId, Integer invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!invoice.getUser().getUserId().equals(userId) && !isAdmin(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem hoá đơn này");
        }

        return toResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getInvoicePdf(Integer userId, Integer invoiceId) {
        Invoice invoice = findInvoice(invoiceId);
        if (!invoice.getUser().getUserId().equals(userId) && !isAdmin(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem hoá đơn này");
        }
        return generatePdf(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getInvoicePdfForDelivery(Integer invoiceId) {
        return generatePdf(findInvoice(invoiceId));
    }

    private Invoice findInvoice(Integer invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private byte[] generatePdf(Invoice invoice) {
        Payment payment = invoice.getPayment();
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            if (fontStream == null) {
                throw new IllegalStateException("Invoice font resource is missing");
            }

            PDFont font = PDType0Font.load(document, fontStream, true);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawText(content, font, 20, 54, 780, "EDUMIND");
                drawText(content, font, 16, 54, 750, "HÓA ĐƠN THANH TOÁN");
                drawText(content, font, 10, 54, 726, "Hóa đơn điện tử cho giao dịch gói cước");

                content.moveTo(54, 710);
                content.lineTo(541, 710);
                content.stroke();

                float y = 682;
                y = drawField(content, font, y, "Số hóa đơn", invoice.getInvoiceNumber());
                y = drawField(content, font, y, "Ngày phát hành", INVOICE_DATE_FORMAT.format(invoice.getIssuedAt()));
                y = drawField(content, font, y, "Khách hàng", invoice.getUser().getName());
                y = drawField(content, font, y, "Email", invoice.getUser().getEmail());

                y -= 12;
                drawText(content, font, 13, 54, y, "THÔNG TIN GIAO DỊCH");
                y -= 28;
                y = drawField(content, font, y, "Gói cước", planName(payment));
                y = drawField(content, font, y, "Mã giao dịch", payment.getTransactionId());
                y = drawField(content, font, y, "Phương thức", payment.getPaymentMethod() == null
                        ? "Không xác định" : payment.getPaymentMethod().name());
                y = drawField(content, font, y, "Trạng thái", "Đã thanh toán");

                y -= 10;
                content.moveTo(54, y);
                content.lineTo(541, y);
                content.stroke();
                y -= 32;
                drawText(content, font, 13, 54, y, "TỔNG THANH TOÁN");
                drawText(content, font, 16, 385, y, formatCurrency(invoice.getAmount()));

                drawText(content, font, 9, 54, 70,
                        "Cảm ơn bạn đã sử dụng EduMind. Vui lòng lưu hóa đơn này để đối chiếu khi cần.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate invoice PDF", exception);
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getUser().getUserId(),
                invoice.getPayment().getPaymentId(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getIssuedAt(),
                invoice.getCreatedAt()
        );
    }

    private float drawField(PDPageContentStream content, PDFont font, float y, String label, String value)
            throws IOException {
        drawText(content, font, 10, 54, y, label + ":");
        drawText(content, font, 10, 180, y, safe(value));
        return y - 24;
    }

    private void drawText(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
    }

    private String planName(Payment payment) {
        if (payment.getPlan() != null && payment.getPlan().getName() != null) {
            return payment.getPlan().getName();
        }
        return payment.getOrderInfo() == null ? "Gói cước EduMind" : payment.getOrderInfo();
    }

    private String formatCurrency(java.math.BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(VIETNAMESE_LOCALE).format(amount);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
