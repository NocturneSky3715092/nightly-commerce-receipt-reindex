package example.reindex;

import java.time.Instant;

public record OrderReceipt(
    String orderId,
    String customerId,
    String checkoutStatus,
    String fulfillmentStatus,
    String receiptText,
    Instant updatedAt,
    boolean indexed) {

  public boolean isReadyForIndex() {
    return "paid".equals(checkoutStatus) && "fulfilled".equals(fulfillmentStatus) && !indexed;
  }

  public String searchableText() {
    return "order=" + orderId + " customer=" + customerId + " receipt=" + receiptText;
  }
}
