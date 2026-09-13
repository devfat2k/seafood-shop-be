package com.devfat.mini_ecommerce.payment;

import com.devfat.mini_ecommerce.order.OrderStatus;
import com.devfat.mini_ecommerce.order.internal.OrderEntity;
import com.devfat.mini_ecommerce.order.internal.OrderRepository;
import com.devfat.mini_ecommerce.payment.internal.*;
import com.devfat.mini_ecommerce.product.internal.ProductRepository;
import com.devfat.mini_ecommerce.notification.EmailService;
import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.user.internal.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VNPayConfig vnPayConfig;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final String secretKey = "TEST_VN_PAY_SECRET_KEY";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    @DisplayName("Should update order to CONFIRMED when return code is 00 and order is PENDING")
    void shouldConfirmOrderWhenPending() {
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        UserEntity user = new UserEntity();
        user.setEmail("user@test.com");

        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);

        PaymentEntity payment = new PaymentEntity();
        payment.setId(100L);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setOrder(order);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "12345678");

        Map<String, String> hashResult = VNPayUtil.buildQueryAndHash(params, secretKey);
        params.put("vnp_SecureHash", hashResult.get("secureHash"));

        String redirectUrl = paymentService.handleVnPayReturn(params);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
        assertTrue(redirectUrl.contains("status=success"));
    }

    @Test
    @DisplayName("Should NOT overwrite order status to CONFIRMED if order is already CANCELLED")
    void shouldNotConfirmCancelledOrder() {
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        UserEntity user = new UserEntity();
        user.setEmail("user@test.com");

        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setStatus(OrderStatus.CANCELLED);
        order.setUser(user);

        PaymentEntity payment = new PaymentEntity();
        payment.setId(100L);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setOrder(order);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "12345678");

        Map<String, String> hashResult = VNPayUtil.buildQueryAndHash(params, secretKey);
        params.put("vnp_SecureHash", hashResult.get("secureHash"));

        paymentService.handleVnPayReturn(params);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, never()).save(order);
    }

    @Test
    @DisplayName("Should throw BadRequestException on invalid signature")
    void shouldThrowOnInvalidSignature() {
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "INVALIDSIGNATURE");

        assertThrows(BadRequestException.class, () -> paymentService.handleVnPayReturn(params));
    }
}
