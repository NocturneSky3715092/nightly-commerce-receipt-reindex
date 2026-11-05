package example.reindex;

import java.time.Instant;

public final class OrderReceiptTest {
  public static void main(String[] args) {
    OrderReceipt completed = new OrderReceipt("o-1", "c-1", "paid", "fulfilled", "paid receipt", Instant.EPOCH, false);
    OrderReceipt pending = new OrderReceipt("o-2", "c-2", "paid", "packing", "pending receipt", Instant.EPOCH, false);
    if (!completed.isReadyForIndex()) throw new AssertionError("fulfilled paid receipt must be indexed");
    if (pending.isReadyForIndex()) throw new AssertionError("unfulfilled receipt must remain out of the index");
    System.out.println("receipt indexing decision verified");
  }
}
