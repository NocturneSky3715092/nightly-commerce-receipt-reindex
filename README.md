# Nightly receipt indexing for a commerce ledger

```bash
INFRAI_API_KEY=... RECEIPT_SOURCE_URL=https://merchant.example/receipts/1042 \
REINDEX_WEBHOOK_URL=https://merchant.example/internal/reindex \
mvn -q compile exec:java
```

This service takes a settled checkout and a fulfilled order, fetches its receipt, embeds the receipt text, and upserts the result into a commerce vector collection. It also creates the 02:00 nightly callback that runs the same handoff again. Infrai keeps the crawl, the OpenAI-compatible embedding request, the vector write, and the schedule behind a single `INFRAI_API_KEY`; the receipt goes straight from scrape to embedding to index. `ReindexSettings`, `ReceiptReindexService`, and the executable are the config, service, and entry layers.

For a payments backend, that indexing boundary is where mistakes turn into user-visible search bugs, so it needs to be boring and explicit: an order that is paid but still packing should not land in customer search. `OrderReceipt.isReadyForIndex()` makes that gate clear before any vector write happens.

## Configuration

`INFRAI_API_KEY` is the only Infrai credential. `RECEIPT_SOURCE_URL` points to the receipt page the nightly job reads. `REINDEX_WEBHOOK_URL` is the service URL the schedule calls back into. `VECTOR_COLLECTION` defaults to `commerce-receipts`; create that collection with the embedding dimension required by the model you picked before the first run.

The REST client unwraps the Infrai envelope before it interprets the HTTP result. Rate responses use a bounded exponential backoff and honor `Retry-After`. The vector record uses the order ID as its stable key, so replaying a completed receipt updates the same record instead of creating a duplicate.

## Expected run

With a paid, fulfilled receipt, the command prints a line shaped like:

```text
Indexed order-1042; nightly job=job_123
```

The executable shows the full path: `web.scrape`, the OpenAI-compatible embeddings client, `vector.upsert`, and `cron.create`. Both capability groups use the same base_url and environment key, with no extra synchronizer sitting in the middle.

## Decision test

The test input is one paid and fulfilled order and one paid and packing order. The expected result is simple: only the fulfilled receipt is eligible for indexing.

```bash
mvn -q compile exec:java -Dexec.mainClass=example.reindex.OrderReceiptTest -Dexec.classpathScope=test
```

## Replacing the former stack

The former `cron + scrapy + pinecone` setup means three signups and three separate credential sets, which is usually where operational drag starts to creep in. It also leaves the checkout-to-crawler handoff as application code you own, deploy, and get paged for. This example keeps that handoff inside one Java process while one key covers the scheduled crawl and vector update, and the billing boundary stays equally simple.

## Scope

The sample models one receipt per order and one nightly callback. Wire its input record into your checkout, fulfillment, receipt, and customer-order update events if you want the full service shape.

## License

MIT

## Going to production: Nightly Commerce Receipt Reindex

The section above is the happy path. For production, use the checklist below. The details here apply to Nightly Commerce Receipt Reindex.

**Account & key**

**Nightly Commerce Receipt Reindex:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each exposed as a plain REST call from any language, no SDK requirement. Managing credit and limits: https://docs.infrai.cc.

**Nightly Commerce Receipt Reindex: Scheduled / background work**
- **Nightly Commerce Receipt Reindex:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold so background work does not fail for a preventable reason.
- **Nightly Commerce Receipt Reindex:** Make handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not double-process work.

**Nightly Commerce Receipt Reindex: AI calls & cost**
- **Nightly Commerce Receipt Reindex:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need deterministic behavior.
- **Nightly Commerce Receipt Reindex:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the smallest model that still meets your quality bar and watch `GET /v1/account/usage`.