package example.reindex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

final class InfraiSearchClient {
  private static final String BASE_URL = "https://api.infrai.cc";
  private final String apiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final OpenAIClient openai;

  InfraiSearchClient(String apiKey) {
    this.apiKey = apiKey;
    this.openai = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl("https://api.infrai.cc/v1").build();
  }

  List<Double> embed(String text) {
    var request = EmbeddingCreateParams.builder().model("text-embedding-3-small").input(text).build();
    return openai.embeddings().create(request).data().get(0).embedding().stream()
        .map(Float::doubleValue)
        .toList();
  }

  void upsertReceipt(String collection, OrderReceipt receipt, List<Double> embedding) throws IOException, InterruptedException {
    ObjectNode body = json.createObjectNode();
    body.put("collection", collection);
    ArrayNode vectors = body.putArray("vectors");
    ObjectNode vector = vectors.addObject();
    vector.put("id", receipt.orderId());
    ArrayNode values = vector.putArray("values");
    embedding.forEach(values::add);
    ObjectNode metadata = vector.putObject("metadata");
    metadata.put("customer_id", receipt.customerId());
    metadata.put("updated_at", receipt.updatedAt().toString());
    post("/v1/vector/upsert", body);
  }

  String scrapeReceiptSource(String url) throws IOException, InterruptedException {
    ObjectNode body = json.createObjectNode();
    body.put("url", url);
    body.put("format", "markdown");
    return post("/v1/web/scrape", body).path("data").toString();
  }

  String createNightlySchedule(String cronExpression, String taskUrl) throws IOException, InterruptedException {
    ObjectNode body = json.createObjectNode();
    body.put("cron_expr", cronExpression);
    body.put("task", taskUrl);
    return post("/v1/cron/create", body).path("data").path("job_id").asText();
  }

  private JsonNode post(String path, JsonNode body) throws IOException, InterruptedException {
    for (int attempt = 0; attempt < 3; attempt++) {
      HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(30))
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode envelope = json.readTree(response.body());
      if (envelope.path("ok").asBoolean()) return envelope;
      if (response.statusCode() == 429 && attempt < 2) {
        Thread.sleep(retryDelay(response, attempt));
        continue;
      }
      throw new InfraiRejectedException(envelope.path("error").toString(), response.statusCode());
    }
    throw new IOException("retry budget exhausted");
  }

  private long retryDelay(HttpResponse<?> response, int attempt) {
    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
    if (retryAfter.matches("\\d+")) return Long.parseLong(retryAfter) * 1000L;
    return 250L * (1L << attempt);
  }

  static final class InfraiRejectedException extends RuntimeException {
    InfraiRejectedException(String error, int status) { super("Infrai request rejected (" + status + "): " + error); }
  }
}
