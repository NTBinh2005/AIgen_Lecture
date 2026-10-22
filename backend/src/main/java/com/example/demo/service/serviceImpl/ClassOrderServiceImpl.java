package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.ClassOrderRequest;
import com.example.demo.dto.response.ClassOrderResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassOrder;
import com.example.demo.entity.ClassOrderStatus;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.ClassStudent;
import com.example.demo.entity.ClassStudentId;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WalletTransactionType;
import com.example.demo.gateway.SepayGateway;
import com.example.demo.repository.ClassOrderRepository;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ClassLectureProgressService;
import com.example.demo.service.ClassOrderService;
import com.example.demo.service.StudyNotificationService;
import com.example.demo.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassOrderServiceImpl implements ClassOrderService {

    private final ClassOrderRepository classOrderRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final WalletService walletService;
    private final ClassLectureProgressService progressService;
    private final StudyNotificationService studyNotifications;
    private final SepayGateway sepayGateway;
    private final PaymentRepository paymentRepository;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal PLATFORM_FEE_PERCENT = new BigDecimal("5.00");

    @Override
    @Transactional
    public ClassOrderResponse purchaseClass(Integer studentId, ClassOrderRequest request) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));
        ClassEntity classEntity = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));

        if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException("Lớp học hiện không ở trạng thái hoạt động");
        }

        // Kiểm tra học sinh đã có order ACTIVE chưa
        boolean alreadyOrdered = classOrderRepository.existsByStudentUserIdAndClassEntityClassIdAndStatus(
                studentId, classEntity.getClassId(), ClassOrderStatus.ACTIVE);
        if (alreadyOrdered) {
            throw new BadRequestException("Bạn đã mua và đang tham gia lớp học này");
        }

        BigDecimal price = classEntity.getPrice() != null ? classEntity.getPrice() : BigDecimal.ZERO;
        BigDecimal feeAmount = price.multiply(PLATFORM_FEE_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal teacherAmount = price.subtract(feeAmount);

        // Sinh mã đơn hàng LH...
        String orderCode = "LH" + String.format("%08d", RANDOM.nextInt(100_000_000));
        while (classOrderRepository.findByOrderCode(orderCode).isPresent()) {
            orderCode = "LH" + String.format("%08d", RANDOM.nextInt(100_000_000));
        }

        Instant holdUntil = Instant.now().plus(4, ChronoUnit.DAYS);

        // 1. Nếu lớp học MIỄN PHÍ (price == 0)
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            ClassOrder order = new ClassOrder();
            order.setOrderCode(orderCode);
            order.setStudent(student);
            order.setClassEntity(classEntity);
            order.setPrice(BigDecimal.ZERO);
            order.setPlatformFeePercent(PLATFORM_FEE_PERCENT);
            order.setPlatformFeeAmount(BigDecimal.ZERO);
            order.setTeacherAmount(BigDecimal.ZERO);
            order.setPaymentMethod("FREE");
            order.setStatus(ClassOrderStatus.ACTIVE);
            order.setHoldUntil(holdUntil);
            order.setSettledAt(Instant.now());
            classOrderRepository.save(order);

            enrollStudent(student, classEntity);
            return toResponse(order, null);
        }

        // 2. Mua bằng VÍ TIỀN (WALLET)
        if ("WALLET".equalsIgnoreCase(request.getPaymentMethod())) {
            Wallet studentWallet = walletService.getOrCreateWallet(studentId);
            if (studentWallet.getBalance().compareTo(price) < 0) {
                throw new BadRequestException("Số dư ví không đủ (" + studentWallet.getBalance() + " VNĐ). Cần nạp thêm.");
            }

            // Trừ ví học sinh
            walletService.debit(studentWallet, price, WalletTransactionType.CLASS_PURCHASE,
                    "Mua khóa học: " + classEntity.getClassName(), orderCode);

            // Cộng vào pending balance của giáo viên (giữ 4 ngày)
            Wallet teacherWallet = walletService.getOrCreateWallet(classEntity.getTeacher().getUserId());
            walletService.creditPending(teacherWallet, teacherAmount,
                    "Học phí lớp " + classEntity.getClassName() + " từ học sinh " + student.getName() + " (giữ 4 ngày)", orderCode);

            ClassOrder order = new ClassOrder();
            order.setOrderCode(orderCode);
            order.setStudent(student);
            order.setClassEntity(classEntity);
            order.setPrice(price);
            order.setPlatformFeePercent(PLATFORM_FEE_PERCENT);
            order.setPlatformFeeAmount(feeAmount);
            order.setTeacherAmount(teacherAmount);
            order.setPaymentMethod("WALLET");
            order.setStatus(ClassOrderStatus.ACTIVE);
            order.setHoldUntil(holdUntil);
            classOrderRepository.save(order);

            enrollStudent(student, classEntity);
            return toResponse(order, null);
        }

        // 3. Mua bằng SEPAY_QR (Quét VietQR chuyển khoản trực tiếp)
        if ("SEPAY_QR".equalsIgnoreCase(request.getPaymentMethod())) {
            String qrUrl = sepayGateway.createPaymentUrl(
                    orderCode,
                    price,
                    "Hoc phi lop " + classEntity.getClassId(),
                    null,
                    "127.0.0.1"
            );

            // Lưu payment PENDING để webhook SePay bắt được
            Payment payment = new Payment();
            payment.setUser(student);
            payment.setAmount(price);
            payment.setTransactionId(orderCode);
            payment.setPaymentMethod(PaymentMethod.SEPAY);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaymentUrl(qrUrl);
            paymentRepository.save(payment);

            // Lưu ClassOrder ở trạng thái chưa hoàn tất (hoặc lưu order để webhook kích hoạt)
            ClassOrder order = new ClassOrder();
            order.setOrderCode(orderCode);
            order.setStudent(student);
            order.setClassEntity(classEntity);
            order.setPrice(price);
            order.setPlatformFeePercent(PLATFORM_FEE_PERCENT);
            order.setPlatformFeeAmount(feeAmount);
            order.setTeacherAmount(teacherAmount);
            order.setPaymentMethod("SEPAY_QR");
            order.setStatus(ClassOrderStatus.ACTIVE); // Sẽ được kích hoạt khi webhook thanh toán
            order.setHoldUntil(holdUntil);
            // Lưu order
            classOrderRepository.save(order);

            return toResponse(order, qrUrl);
        }

        throw new BadRequestException("Phương thức thanh toán không hợp lệ (hỗ trợ WALLET hoặc SEPAY_QR)");
    }

    @Override
    @Transactional
    public void processSepayOrderSuccess(String orderCode, BigDecimal transferAmount, String referenceCode) {
        ClassOrder order = classOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mua lớp: " + orderCode));

        ClassEntity classEntity = order.getClassEntity();
        User student = order.getStudent();

        // Cộng pending balance cho giáo viên
        Wallet teacherWallet = walletService.getOrCreateWallet(classEntity.getTeacher().getUserId());
        walletService.creditPending(teacherWallet, order.getTeacherAmount(),
                "Học phí lớp " + classEntity.getClassName() + " từ học sinh " + student.getName() + " qua SePay (giữ 4 ngày)", referenceCode != null ? referenceCode : orderCode);

        // Ghi danh học sinh
        enrollStudent(student, classEntity);
        log.info("[ClassOrder] Hoàn tất thanh toán SePay đơn hàng {} cho lớp {}", orderCode, classEntity.getClassId());
    }

    @Override
    @Transactional
    public void refundClass(Integer studentId, Integer classId) {
        ClassOrder order = classOrderRepository.findByStudentUserIdAndClassEntityClassIdAndStatus(
                studentId, classId, ClassOrderStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy đơn mua lớp học hợp lệ để hoàn tiền"));

        Instant now = Instant.now();
        if (now.isAfter(order.getHoldUntil())) {
            throw new BadRequestException("Đã quá thời hạn 4 ngày để yêu cầu hoàn tiền khóa học này");
        }

        // Kiểm tra điều kiện tiến độ xem bài giảng <= 5%
        if (!progressService.isEligibleForRefundProgress(studentId, classId)) {
            throw new BadRequestException("Bạn đã học quá 5% bài giảng của lớp, không đủ điều kiện hoàn tiền theo chính sách");
        }

        ClassEntity classEntity = order.getClassEntity();

        // 1. Hoàn 100% học phí về ví sinh viên
        Wallet studentWallet = walletService.getOrCreateWallet(studentId);
        walletService.credit(studentWallet, order.getPrice(), WalletTransactionType.CLASS_REFUND_STUDENT,
                "Hoàn 100% học phí lớp: " + classEntity.getClassName(), order.getOrderCode());

        // 2. Thu hồi 95% học phí từ ví giáo viên (trừ vào pending_balance nếu chưa giải ngân, hoặc trừ balance)
        Wallet teacherWallet = walletService.getOrCreateWallet(classEntity.getTeacher().getUserId());
        walletService.deductPending(teacherWallet, order.getTeacherAmount(), WalletTransactionType.CLASS_REFUND_TEACHER_DEDUCTION,
                "Thu hồi học phí lớp: " + classEntity.getClassName() + " (học sinh hoàn tiền)", order.getOrderCode());

        // 3. Đánh dấu đơn hàng là REFUNDED
        order.setStatus(ClassOrderStatus.REFUNDED);
        order.setRefundedAt(now);
        classOrderRepository.save(order);

        // 4. Hủy ghi danh học sinh khỏi lớp
        ClassStudentId enrollmentId = new ClassStudentId(classId, studentId);
        classStudentRepository.findById(enrollmentId).ifPresent(cs -> {
            cs.setStatus(EnrollmentStatus.CANCELLED);
            classStudentRepository.save(cs);
        });

        studyNotifications.notify(studentId, "Hoàn học phí thành công",
                "Bạn đã được hoàn 100% học phí (" + order.getPrice() + " VNĐ) của lớp " + classEntity.getClassName() + " về ví cá nhân.",
                NotificationType.CLASS_ENROLLED, "CLASS", classId);

        studyNotifications.notify(classEntity.getTeacher().getUserId(), "Học sinh yêu cầu hoàn tiền",
                "Học sinh " + order.getStudent().getName() + " đã yêu cầu hoàn tiền lớp " + classEntity.getClassName() + " theo chính sách 4 ngày.",
                NotificationType.CLASS_ENROLLED, "CLASS", classId);

        log.info("[ClassOrder] Hoàn tiền thành công cho đơn hàng {} (student={}, class={})", order.getOrderCode(), studentId, classId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassOrderResponse> getStudentOrders(Integer studentId, Pageable pageable) {
        return classOrderRepository.findByStudentUserIdOrderByCreatedAtDesc(studentId, pageable)
                .map(o -> toResponse(o, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassOrderResponse> getClassOrders(Integer classId, Pageable pageable) {
        return classOrderRepository.findByClassEntityClassIdOrderByCreatedAtDesc(classId, pageable)
                .map(o -> toResponse(o, null));
    }

    /**
     * Tác vụ định kỳ chạy mỗi 10 phút:
     * Quét các đơn hàng đã hết thời gian giữ 4 ngày (hold_until <= now và chưa giải ngân)
     * -> chuyển tiền từ pending_balance sang balance khả dụng cho giáo viên.
     */
    @Scheduled(fixedDelay = 600000)
    @Transactional
    public void releaseMaturedPendingBalances() {
        Instant now = Instant.now();
        List<ClassOrder> maturedOrders = classOrderRepository.findByStatusAndHoldUntilLessThanEqualAndSettledAtIsNull(
                ClassOrderStatus.ACTIVE, now);

        if (maturedOrders.isEmpty()) {
            return;
        }

        log.info("[ClassOrder] Bắt đầu giải ngân cho {} đơn hàng hết hạn giữ tiền 4 ngày", maturedOrders.size());
        for (ClassOrder order : maturedOrders) {
            try {
                Wallet teacherWallet = walletService.getOrCreateWallet(order.getClassEntity().getTeacher().getUserId());
                walletService.releasePendingToAvailable(
                        teacherWallet,
                        order.getTeacherAmount(),
                        "Giải ngân học phí lớp " + order.getClassEntity().getClassName() + " sau 4 ngày",
                        order.getOrderCode()
                );
                order.setSettledAt(now);
                classOrderRepository.save(order);
            } catch (Exception ex) {
                log.error("[ClassOrder] Lỗi khi giải ngân đơn hàng {}: {}", order.getOrderCode(), ex.getMessage(), ex);
            }
        }
    }

    private void enrollStudent(User student, ClassEntity classEntity) {
        ClassStudentId id = new ClassStudentId(classEntity.getClassId(), student.getUserId());
        ClassStudent enrollment = classStudentRepository.findById(id).orElseGet(() -> {
            ClassStudent cs = new ClassStudent();
            cs.setId(id);
            cs.setClassEntity(classEntity);
            cs.setStudent(student);
            return cs;
        });
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        classStudentRepository.save(enrollment);

        studyNotifications.notify(student.getUserId(), "Đã tham gia lớp",
                "Bạn đã được thêm vào lớp " + classEntity.getClassName(),
                NotificationType.CLASS_ENROLLED, "CLASS", classEntity.getClassId());
    }

    private ClassOrderResponse toResponse(ClassOrder order, String qrUrl) {
        return ClassOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .studentId(order.getStudent().getUserId())
                .studentName(order.getStudent().getName())
                .classId(order.getClassEntity().getClassId())
                .className(order.getClassEntity().getClassName())
                .price(order.getPrice())
                .platformFeePercent(order.getPlatformFeePercent())
                .platformFeeAmount(order.getPlatformFeeAmount())
                .teacherAmount(order.getTeacherAmount())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .holdUntil(order.getHoldUntil())
                .createdAt(order.getCreatedAt())
                .refundedAt(order.getRefundedAt())
                .qrUrl(qrUrl)
                .accountNumber(sepayGateway.getBankAccount())
                .accountName(sepayGateway.getAccountHolder())
                .bankName(sepayGateway.getBankName())
                .build();
    }
}
