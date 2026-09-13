package com.devfat.mini_ecommerce.payment;

import com.devfat.mini_ecommerce.payment.internal.VNPayUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VNPayUtilTest {

    private final String secretKey = "DEMOSECRETKEYFORTESTING";

    @Test
    @DisplayName("Should extract first IP when X-FORWARDED-FOR contains multiple IPs")
    void shouldExtractFirstIpFromForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FORWARDED-FOR", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = VNPayUtil.getIpAddress(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    @DisplayName("Should use X-Real-IP when X-FORWARDED-FOR is absent")
    void shouldUseRealIpWhenForwardedForAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.42");

        String ip = VNPayUtil.getIpAddress(request);
        assertEquals("198.51.100.42", ip);
    }

    @Test
    @DisplayName("Should fallback to remoteAddr when proxy headers are absent")
    void shouldFallbackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String ip = VNPayUtil.getIpAddress(request);
        assertEquals("127.0.0.1", ip);
    }

    @Test
    @DisplayName("Should correctly generate and verify signature")
    void shouldGenerateAndVerifySignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "TESTTMN");
        params.put("vnp_Amount", "10000000");
        params.put("vnp_OrderInfo", "Thanh toan don hang 123");

        Map<String, String> result = VNPayUtil.buildQueryAndHash(params, secretKey);
        assertNotNull(result.get("queryUrl"));
        assertNotNull(result.get("secureHash"));

        // Simulate incoming return request
        Map<String, String> returnParams = new HashMap<>(params);
        returnParams.put("vnp_SecureHash", result.get("secureHash"));

        assertTrue(VNPayUtil.verifySignature(returnParams, secretKey));

        // Tampered param
        returnParams.put("vnp_Amount", "50000000");
        assertFalse(VNPayUtil.verifySignature(returnParams, secretKey));
    }
}
