package example.reindex;

import java.io.IOException;
import java.time.Instant;

final class ReceiptReindexService {
  private final InfraiSearchClient infrai;
  private final ReindexSettings settings;

  ReceiptReindexService(InfraiSearchClient infrai, ReindexSettings settings) {
    this.infrai = infrai;
    this.settings = settings;
  }

  String reindexFulfilledReceipt() throws IOException, InterruptedException {
    OrderReceipt receipt = new OrderReceipt("order-1042", "customer-88", "paid", "fulfilled",
        infrai.scrapeReceiptSource(settings.receiptSourceUrl), Instant.now(), false);
    if (receipt.isReadyForIndex()) {
      infrai.upsertReceipt(settings.collection, receipt, infrai.embed(receipt.searchableText()));
      return receipt.orderId();
    }
    return "no eligible receipt";
  }

  String scheduleNightlyRun() throws IOException, InterruptedException {
    return infrai.createNightlySchedule("0 2 * * *", settings.webhookUrl);
  }
}
