package com.ajay.webhookreceiver.dedup;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same idea as the Kafka consumer's dedup guard from Chapter 3 - and the
 * SAME lesson applies: only mark a webhook ID as processed AFTER it genuinely
 * succeeds, never before, or a retry-after-failure gets misclassified as a
 * duplicate-of-success (exactly the bug you found and fixed in
 * OrderEventConsumer).
 *
 * NOTE: in-memory only, resets on restart - a real system needs a durable
 * store (DB table or Redis with a TTL) so idempotency survives a crash, not
 * just a request retry within the same process lifetime.
 */
@Component
public class WebhookDedupStore {

    private final Set<String> processedWebhookIds = ConcurrentHashMap.newKeySet();

    public boolean isAlreadyProcessed(String webhookId) {
        return processedWebhookIds.contains(webhookId);
    }

    public void markProcessed(String webhookId) {
        processedWebhookIds.add(webhookId);
    }
}
