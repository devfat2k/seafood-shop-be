package com.devfat.mini_ecommerce.payment.internal;

import com.devfat.mini_ecommerce.notification.EmailService;
import com.devfat.mini_ecommerce.order.OrderStatus;
import com.devfat.mini_ecommerce.order.internal.OrderEntity;
import com.devfat.mini_ecommerce.order.internal.OrderRepository;
import com.devfat.mini_ecommerce.payment.PaymentMethod;
import com.devfat.mini_ecommerce.payment.PaymentProvider;
import com.devfat.mini_ecommerce.payment.PaymentService;
import com.devfat.mini_ecommerce.payment.PaymentStatus;
import com.devfat.mini_ecommerce.payment.dto.CreatePaymentResponseDto;
import com.devfat.mini_ecommerce.product.internal.ProductEntity;
import com.devfat.mini_ecommerce.product.internal.ProductRepository;
import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final VNPayConfig vnPayConfig;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;


    @Scheduled(cron = "${app.scheduler.payment-expired.cron}")
    @Transactional
    public void expiredPayment() {
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(15);
        List<PaymentEntity> expiredPayments = paymentRepository
                .findAllByPaymentStatusAndCreatedAtBefore(
                        PaymentStatus.PENDING, expireThreshold);

        for (PaymentEntity payment : expiredPayments) {
            payment.setPaymentStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);

            OrderEntity order = payment.getOrder();
            if (order != null && order.getStatus() == OrderStatus.PENDING) {
                order.getItems().forEach(item -> {
                    productRepository.increaseStockAtomically(item.getProduct().getId(), item.getQuantity());
                });
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }
        }
    }


    @Override
    @Transactional
    public CreatePaymentResponseDto createPayment(Long userId, Long orderId, HttpServletRequest request) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new BadRequestException("Order Not Found!"));

        if (!order.getUser().getId().equals(userId)) throw new BadRequestException("Order Not Found!");

        if (!(order.getStatus().equals(OrderStatus.PENDING)))
            throw new BadRequestException("Order Not Pending!");

        if (!order.getPaymentMethod().equals(PaymentMethod.VNPAY))
            throw new BadRequestException("Payment method " + order.getPaymentMethod() + " does not require online payment.");

        if (paymentRepository.existsByOrderIdAndPaymentStatus(order.getId(), PaymentStatus.SUCCESS))
            throw new BadRequestException("Payment already success, cannot create new payment");

        PaymentEntity payment = new PaymentEntity();
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentProvider(PaymentProvider.VNPAY);
        payment.setPaymentMethod(order.getPaymentMethod());
        payment.setOrder(order);
        payment = paymentRepository.save(payment);

        // ─── 3. Build Map tham số — value lấy từ vnPayConfig (biến), tên field hardcode (chuỗi) ───
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");                          // hardcode, VNPay quy định cố định
        vnpParams.put("vnp_Command", "pay");                            // hardcode
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());         // lấy từ .env qua VNPayConfig

        long amount = payment.getAmount().multiply(new BigDecimal("100")).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(amount));

        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getId().toString());        // dùng paymentId làm mã giao dịch, đảm bảo unique
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getId());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());     // lấy từ .env

        // Các dòng dưới đây gọi VNPayUtil (HÀM XỬ LÝ), KHÔNG PHẢI vnPayConfig
        vnpParams.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        vnpParams.put("vnp_CreateDate", now.format(fmt));

        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(fmt));


        // ─── 4. Build query + ký chữ ký — gọi VNPayUtil, truyền secretKey lấy từ vnPayConfig ───
        Map<String, String> result = VNPayUtil.buildQueryAndHash(vnpParams, vnPayConfig.getSecretKey());

        // ─── 5. Nối URL cuối cùng — payUrl lấy từ vnPayConfig, phần còn lại từ VNPayUtil ───
        String paymentUrl = vnPayConfig.getPayUrl() + "?" + result.get("queryUrl")
                + "&vnp_SecureHash=" + result.get("secureHash");

        return new CreatePaymentResponseDto(paymentUrl);
    }

    @Override
    @Transactional
    public void handleVnPayIpn(Map<String, String> params) {
        // Bước 1: Verify chữ ký
        boolean isValidSignature = VNPayUtil.verifySignature(params, vnPayConfig.getSecretKey());
        if (!isValidSignature) {
            throw new BadRequestException("Invalid signature - possible fraud attempt");
        }

        // Bước 2: Chữ ký hợp lệ rồi mới tin tham số, lấy paymentId (đã lưu ở vnp_TxnRef từ P3)
        Long paymentId = Long.parseLong(params.get("vnp_TxnRef"));
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Not Found"));

        // Bước 3: Chống xử lý trùng — nếu ĐÃ SUCCESS rồi thì dừng lại, không làm gì thêm
        if (payment.getPaymentStatus().equals(PaymentStatus.SUCCESS)) {
            return; // webhook gọi lại lần 2, coi như đã xử lý xong, không báo lỗi
        }

        // Bước 4: Đọc vnp_ResponseCode để biết giao dịch thành công hay thất bại
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        if ("00".equals(responseCode)) {
            // Thanh toán thành công
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            if (transactionNo != null && !transactionNo.isBlank()) {
                payment.setProviderTransactionId(transactionNo);
            }
            paymentRepository.save(payment);

            OrderEntity order = payment.getOrder();
            if (order != null) {
                if (order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.CONFIRMED);
                    orderRepository.save(order);
                    try {
                        emailService.sendPaymentSuccessEmail(order.getUser().getEmail(), order.getId());
                    } catch (Exception e) {
                        log.error("Failed to send payment success email via IPN: ", e);
                    }
                } else if (order.getStatus() == OrderStatus.CANCELLED) {
                    log.warn("⚠️ VNPay IPN received SUCCESS for CANCELLED order {}. Stock may need manual review.", order.getId());
                }
            }
        } else {
            // Thanh toán thất bại
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    @Override
    @Transactional
    public String handleVnPayReturn(Map<String, String> allParams) {
        boolean isValidSignature = VNPayUtil.verifySignature(allParams, vnPayConfig.getSecretKey());
        if (!isValidSignature) {
            throw new BadRequestException("Invalid signature - possible fraud attempt");
        }

        String txnRef = allParams.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            throw new BadRequestException("Missing vnp_TxnRef parameter");
        }
        Long paymentId = Long.parseLong(txnRef);

        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Not Found"));

        String responseCode = allParams.getOrDefault("vnp_ResponseCode", "");
        String status = "00".equals(responseCode) ? "success" : "failed";
        String vnpTransactionNo = allParams.getOrDefault("vnp_TransactionNo", "");

        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            if ("00".equals(responseCode)) {
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                if (vnpTransactionNo != null && !vnpTransactionNo.isBlank()) {
                    payment.setProviderTransactionId(vnpTransactionNo);
                }
                paymentRepository.save(payment);

                OrderEntity order = payment.getOrder();
                if (order != null) {
                    if (order.getStatus() == OrderStatus.PENDING) {
                        order.setStatus(OrderStatus.CONFIRMED);
                        orderRepository.save(order);
                        try {
                            emailService.sendPaymentSuccessEmail(order.getUser().getEmail(), order.getId());
                        } catch (Exception e) {
                            log.error("Failed to send payment success email via Return: ", e);
                        }
                    } else if (order.getStatus() == OrderStatus.CANCELLED) {
                        log.warn("⚠️ VNPay Return received SUCCESS for CANCELLED order {}.", order.getId());
                    }
                }
            } else {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        String baseUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        StringBuilder redirectUrl = new StringBuilder(baseUrl)
                .append("/payment-result")
                .append("?orderId=").append(payment.getOrder().getId())
                .append("&status=").append(status)
                .append("&paymentMethod=").append(payment.getPaymentMethod());

        if (!vnpTransactionNo.isEmpty()) {
            redirectUrl.append("&paymentId=").append(vnpTransactionNo);
        }

        return redirectUrl.toString();
    }
}

