package top.mc100.qdshop.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TransactionDatabaseTest {
    @TempDir Path dir;
    final UUID buyer = UUID.randomUUID();
    final long timestamp = Instant.parse("2026-10-13T05:00:00Z").toEpochMilli();

    TradeLogEntry entry(boolean twoCosts) {
        return new TradeLogEntry(timestamp, "world", 12.5, 64, -3.25, "起点-shop", buyer,
                new TradeLogEntry.ItemData("DIAMOND_SWORD", 1, new byte[]{1, 2, 3}),
                new TradeLogEntry.ItemData("EMERALD", 2, new byte[]{4, 5}),
                twoCosts ? new TradeLogEntry.ItemData("GOLD_INGOT", 3, new byte[]{6, 7}) : null);
    }

    @Test void realSqliteStoresTimeLocationBuyerAndBothItemPayloads() throws Exception {
        Path file = dir.resolve("transactions.db");
        try (var database = new TransactionDatabase(file, Logger.getLogger("qdshop-test"))) {
            database.record(entry(true)).join();
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var rows = connection.createStatement().executeQuery("SELECT * FROM transactions")) {
            assertTrue(rows.next());
            assertEquals("2026-10-13T05:00:00Z", rows.getString("occurred_at"));
            assertEquals(timestamp, rows.getLong("occurred_at_ms"));
            assertEquals("world", rows.getString("world"));
            assertEquals(12.5, rows.getDouble("x")); assertEquals(64, rows.getDouble("y")); assertEquals(-3.25, rows.getDouble("z"));
            assertEquals("起点-shop", rows.getString("shop_id"));
            assertEquals(buyer.toString(), rows.getString("buyer_uuid"));
            assertEquals("DIAMOND_SWORD", rows.getString("item_type"));
            assertEquals(1, rows.getInt("item_amount")); assertArrayEquals(new byte[]{1, 2, 3}, rows.getBytes("item_data"));
            assertEquals(2, rows.getInt("income1_amount")); assertArrayEquals(new byte[]{4, 5}, rows.getBytes("income1_data"));
            assertEquals("GOLD_INGOT", rows.getString("income2_type")); assertEquals(3, rows.getInt("income2_amount"));
            assertArrayEquals(new byte[]{6, 7}, rows.getBytes("income2_data"));
            assertFalse(rows.next());
        }
    }

    @Test void shutdownFlushesQueueAndReopeningPreservesHistoryAndOptionalCostIsNull() throws Exception {
        Path file = dir.resolve("transactions.db");
        TransactionDatabase database = new TransactionDatabase(file, Logger.getLogger("qdshop-test"));
        for (int i = 0; i < 20; i++) database.record(entry(false));
        database.close();
        assertTrue(database.record(entry(true)).isCompletedExceptionally());
        try (var reopened = new TransactionDatabase(file, Logger.getLogger("qdshop-test"))) {
            reopened.record(entry(true)).join();
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var rows = connection.createStatement().executeQuery("SELECT count(*) AS total, sum(income2_type IS NULL) AS single_cost FROM transactions")) {
            assertTrue(rows.next());
            assertEquals(21, rows.getInt("total")); assertEquals(20, rows.getInt("single_cost"));
        }
    }
}
