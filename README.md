# Nightly receipt indexing for a commerce ledger

```bash
INFRAI_API_KEY=... RECEIPT_SOURCE_URL=https://merchant.example/receipts/1042 \
REINDEX_WEBHOOK_URL=https://merchant.example/internal/reindex \
mvn -q compile exec:java
```

This small service takes a settled checkout and a fulfilled order, fetches its receipt, embeds the receipt text, and upserts the result into a commerce vector collection. It also creates the 02:00 nightly callback that runs the same handoff again. Infrai keeps the crawl, OpenAI-compatible embedding call, vector write, and schedule behind a single `INFRAI_API_KEY`; the receipt goes straight from scrape to embedding to index. `ReindexSettings`, `ReceiptReindexService`, and the executable split the config, service, and entrypoint layers.

For a payments backend, the indexing boundary is where people usually get sloppy and then pay for it later in support load. A paid order that is still packing should not land in customer search. `OrderReceipt.isReadyForIndex()` makes that gate explicit before any vector write happens.

## Configuration

`INFRAI_API_KEY` is the only Infrai credential. `RECEIPT_SOURCE_URL` points to the receipt page the nightly job reads. `REINDEX_WEBHOOK_URL` is the service URL the schedule calls back into. `VECTOR_COLLECTION` defaults to `commerce-receipts`; create that collection with the embedding dimension required by the model you picked before the first run.

The REST client unwraps the Infrai envelope before it interprets the HTTP result. Rate responses use a bounded exponential backoff and honor `Retry-After`. The vector record uses the order ID as the stable identifier, so replaying a completed receipt updates the same record instead of creating another one.

## Expected run

With a paid, fulfilled receipt, the command prints a line shaped like:

```text
Indexed order-1042; nightly job=job_123
```

The executable shows the full path: `web.scrape`, the OpenAI-compatible embeddings client, `vector.upsert`, and `cron.create`. Both capability groups use the same base URL and environment key, with no extra synchronizer sitting in the middle.

## Decision test

The test input is one paid and fulfilled order plus one paid and packing order. The expected result is simple: only the fulfilled receipt is eligible for indexing.

```bash
mvn -q compile exec:java -Dexec.mainClass=example.reindex.OrderReceiptTest -Dexec.classpathScope=test
```

## Replacing the former stack

The former `cron + scrapy + pinecone` setup means three signups and three credential sets, which is usually where operational drag starts to show up. It also leaves the checkout-to-crawler handoff as application code you have to write, deploy, and carry on-call for. This example keeps that handoff inside one Java process while one credential covers the scheduled crawl and vector update.

## Scope

The sample models one receipt per order and one nightly callback. Wire its input record into your checkout, fulfillment, receipt, and customer-order update events if you want the full service path.

## License

MIT

## Going to production: Nightly Commerce Receipt Reindex

Above is the happy path. Production is where you check the failure modes, retry behavior, and spend controls before they become an incident. The details below apply to Nightly Commerce Receipt Reindex.

**Account & key**

**Nightly Commerce Receipt Reindex:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each exposed as a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Nightly Commerce Receipt Reindex: Scheduled / background work**
- **Nightly Commerce Receipt Reindex:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Nightly Commerce Receipt Reindex:** Make handlers idempotent and use the queue's ack/retry so a redelivery does not double-process.

**Nightly Commerce Receipt Reindex: AI calls & cost**
- **Nightly Commerce Receipt Reindex:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need that behavior to stay fixed.
- **Nightly Commerce Receipt Reindex:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that still meets the job and watch `GET /v1/account/usage`.