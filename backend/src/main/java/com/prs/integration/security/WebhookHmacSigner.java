package com.prs.integration.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class WebhookHmacSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Computes HMAC-SHA256 signature for payload using provided secret.
     * Returns "sha256=<hex-encoded-hash>"
     */
    public String signPayload(String payload, String secret) {
        if (payload == null || secret == null) {
            throw new IllegalArgumentException("Payload and secret must not be null.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verifies the provided signature against the computed HMAC-SHA256 signature in constant time.
     */
    public boolean verifySignature(String payload, String secret, String expectedSignature) {
        if (expectedSignature == null || payload == null || secret == null) {
            return false;
        }
        String computed = signPayload(payload, secret);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
