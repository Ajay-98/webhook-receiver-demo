package com.ajay.webhookreceiver.controller;

import com.ajay.webhookreceiver.dedup.WebhookDedupStore;
import com.ajay.webhookreceiver.model.OrderEvent;
import com.ajay.webhookreceiver.service.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Receives webhooks from the sender (kafka-webhook-demo project). Implements
 * the 5 theory points from Part C, in order:
 *
 *  1. Signature verification  - reject anything not genuinely signed by the sender
 *  2. Replay protection       - reject stale timestamps
 *  3. Idempotent processing   - dedupe by webhook ID, mark processed only after success
 *  4. Fast ack                - verify/dedupe cheaply, return 2xx quickly
 *  5. Correct status codes    - 401 for bad signature (sender should NOT retry),
 *                                200 for success/duplicate, 500 only for genuine
 *                                transient failure (sender SHOULD retry)
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookReceiverController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookDedupStore dedupStore;
    private final ObjectMapper objectMapper;

    @Value("${webhook.timestamp-tolerance-seconds}")
    private long timestampToleranceSeconds;

    /**
     * Note: @RequestBody here captures the raw String, NOT a parsed OrderEvent.
     * This is deliberate - signature verification needs the exact raw bytes
     * the sender signed. We parse into OrderEvent ourselves, AFTER verification,
     * only once we know the payload is genuine.
     */
    @PostMapping("/order-events")
    public ResponseEntity<String> receiveOrderEventWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Webhook-Signature") String signature,
            @RequestHeader("X-Webhook-Id") String webhookId,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) Long timestampEpochSeconds) {

        // --- 1. Signature verification ---
        if (!signatureVerifier.isValid(rawBody, signature)) {
            // 401, not 500: this is the SENDER's fault (or an attacker) - retrying
            // won't help, so we deliberately don't want the sender to retry this.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        // --- 2. Replay protection ---
        if (timestampEpochSeconds != null) {
            long now = Instant.now().getEpochSecond();
            long age = Math.abs(now - timestampEpochSeconds);
            if (age > timestampToleranceSeconds) {
                log.warn("Rejecting stale webhook id={}, age={}s", webhookId, age);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Timestamp outside tolerance window");
            }
        }

        // --- 3. Idempotency check ---
        if (dedupStore.isAlreadyProcessed(webhookId)) {
            log.info("Duplicate webhook id={} - already processed, acking again without reprocessing", webhookId);
            // Still 200: from the sender's point of view this IS delivered.
            // We just don't repeat the side effect.
            return ResponseEntity.ok("Already processed");
        }

        // --- 4. Fast ack: verify/dedupe done, now actually process ---
        try {
            OrderEvent event = objectMapper.readValue(rawBody, OrderEvent.class);
            processEvent(event);

            // Mark processed only AFTER success - same lesson as the Kafka
            // consumer bug: marking too early turns genuine retries-after-failure
            // into false "duplicates".
            dedupStore.markProcessed(webhookId);

            log.info("Successfully processed webhook id={} event={}", webhookId, event);
            return ResponseEntity.ok("Processed");

        } catch (Exception ex) {
            log.error("Failed to process webhook id={}", webhookId, ex);
            // --- 5. Correct status code for genuine transient failure ---
            // 500: this IS our fault / a transient issue - the sender SHOULD
            // retry this one (contrast with the 401 case above).
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }

    private void processEvent(OrderEvent event) {
        // Placeholder for real business logic (update local records, notify
        // someone, etc.). Left simple for now - we'll extend this once we
        // wire the sender's outbox pattern through to here in Part D.
        log.info("Business logic: handling order {} with status {}", event.getOrderId(), event.getStatus());
    }
}
