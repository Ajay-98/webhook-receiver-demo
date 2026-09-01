# webhook-receiver-demo

The subscriber/receiver side of the webhook system. Implements the theory
from Part C: signature verification, replay protection, idempotency, fast ack,
and correct status-code semantics.

## Run

```bash
mvn spring-boot:run
```

Runs on **port 8082** (kept distinct from `kafka-webhook-demo`'s 8081).

## Manually testing signature verification

The tricky part about testing this by hand is that the signature must be
computed over the EXACT raw body bytes. Here's a quick way using `openssl`
to compute an HMAC-SHA256 signature matching what the Java service expects:

```bash
BODY='{"orderId":"order-1","status":"CREATED"}'
SECRET='demo-shared-secret-change-me'

SIGNATURE=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -binary | base64)

curl -X POST http://localhost:8082/webhooks/order-events \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIGNATURE" \
  -H "X-Webhook-Id: test-webhook-001" \
  -H "X-Webhook-Timestamp: $(date +%s)" \
  -d "$BODY"
```

Expected: `200 Processed`.

### Test cases to try

**1. Wrong signature (should get 401):**
```bash
curl -X POST http://localhost:8082/webhooks/order-events \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: bogus-signature" \
  -H "X-Webhook-Id: test-webhook-002" \
  -H "X-Webhook-Timestamp: $(date +%s)" \
  -d "$BODY"
```

**2. Duplicate webhook ID (should get 200 "Already processed", no reprocessing):**
Re-run the first (valid) curl again with the SAME `X-Webhook-Id: test-webhook-001`.

**3. Stale timestamp (should get 401):**
Same as test 1 but with a valid signature and `-H "X-Webhook-Timestamp: 1000000000"` (year 2001 - way outside the 300s tolerance window).

## What's next (Part D)

This receiver currently only gets called manually via curl. In Part D we'll
wire `kafka-webhook-demo`'s consumer to actually compute these headers and
call this endpoint automatically, completing the full loop:

```
Order event -> Outbox table -> Kafka -> Consumer -> WebClient (signs + POSTs) -> THIS receiver
```
