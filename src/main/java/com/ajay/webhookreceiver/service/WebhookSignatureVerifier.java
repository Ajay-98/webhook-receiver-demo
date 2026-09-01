package com.ajay.webhookreceiver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Verifies that an incoming webhook was genuinely sent by someone who knows
 * our shared secret, and that the raw body hasn't been tampered with.
 *
 * IMPORTANT: this must run against the RAW request body bytes, not a
 * re-serialized version of a parsed object - see the controller, which reads
 * the body as a raw String specifically so this verification is accurate.
 * Re-serializing a parsed JSON object before verifying is a very common
 * real-world bug (whitespace/field-order differences silently break every
 * signature check).
 */
@Service
@Slf4j
public class WebhookSignatureVerifier {

    private static final String ALGO = "HmacSHA256";

    @Value("${webhook.shared-secret}")
    private String sharedSecret;

    public boolean isValid(String rawBody, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            log.warn("Missing signature header");
            return false;
        }

        String expectedSignature = computeSignature(rawBody);

        // Constant-time comparison - NEVER use String.equals() or == here.
        // A naive equals() short-circuits on the first mismatched character,
        // which leaks timing information an attacker could use to guess the
        // correct signature one byte at a time across many requests.
        boolean matches = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            log.warn("Signature mismatch - rejecting webhook");
        }
        return matches;
    }

    private String computeSignature(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute webhook signature", e);
        }
    }
}
