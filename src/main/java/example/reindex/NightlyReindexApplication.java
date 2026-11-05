package example.reindex;

public final class NightlyReindexApplication {
  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("INFRAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before running this example.");
    ReceiptReindexService service = new ReceiptReindexService(
        new InfraiSearchClient(apiKey), ReindexSettings.fromEnvironment());
    String orderId = service.reindexFulfilledReceipt();
    String jobId = service.scheduleNightlyRun();
    System.out.println("Indexed " + orderId + "; nightly job=" + jobId);
  }
}
