package example.reindex;

final class ReindexSettings {
  final String receiptSourceUrl;
  final String webhookUrl;
  final String collection;

  private ReindexSettings(String receiptSourceUrl, String webhookUrl, String collection) {
    this.receiptSourceUrl = receiptSourceUrl;
    this.webhookUrl = webhookUrl;
    this.collection = collection;
  }

  static ReindexSettings fromEnvironment() {
    return new ReindexSettings(required("RECEIPT_SOURCE_URL"), required("REINDEX_WEBHOOK_URL"),
        System.getenv().getOrDefault("VECTOR_COLLECTION", "commerce-receipts"));
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " before running this example.");
    return value;
  }
}
