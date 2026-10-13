package top.mc100.qdshop.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 单线程 SQLite 写入，关闭插件时排空队列并关闭连接。 */
public final class TransactionDatabase implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement insert;
    private final ExecutorService writer;
    private final Logger logger;
    private boolean closed;

    public TransactionDatabase(Path file, Logger logger) throws SQLException, IOException {
        this.logger = logger;
        Files.createDirectories(file.toAbsolutePath().getParent());
        connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = FULL");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        occurred_at TEXT NOT NULL,
                        occurred_at_ms INTEGER NOT NULL,
                        world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        shop_id TEXT NOT NULL, buyer_uuid TEXT NOT NULL,
                        item_type TEXT NOT NULL, item_amount INTEGER NOT NULL, item_data BLOB NOT NULL,
                        income1_type TEXT NOT NULL, income1_amount INTEGER NOT NULL, income1_data BLOB NOT NULL,
                        income2_type TEXT, income2_amount INTEGER, income2_data BLOB
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_shop_time ON transactions(shop_id, occurred_at_ms)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_buyer_time ON transactions(buyer_uuid, occurred_at_ms)");
            }
            insert = connection.prepareStatement("""
                INSERT INTO transactions (
                    occurred_at, occurred_at_ms, world, x, y, z, shop_id, buyer_uuid,
                    item_type, item_amount, item_data,
                    income1_type, income1_amount, income1_data, income2_type, income2_amount, income2_data
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "qdshop-transaction-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized CompletableFuture<Void> record(TradeLogEntry entry) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("交易数据库已关闭"));
        return CompletableFuture.runAsync(() -> {
            try {
                insert.setString(1, Instant.ofEpochMilli(entry.timeMillis()).toString());
                insert.setLong(2, entry.timeMillis());
                insert.setString(3, entry.world());
                insert.setDouble(4, entry.x()); insert.setDouble(5, entry.y()); insert.setDouble(6, entry.z());
                insert.setString(7, entry.shopId()); insert.setString(8, entry.buyer().toString());
                bindItem(9, entry.item()); bindItem(12, entry.income1()); bindItem(15, entry.income2());
                insert.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "写入交易记录失败（商店 " + entry.shopId() + "，买家 " + entry.buyer() + "）", e);
                throw new CompletionException(e);
            }
        }, writer);
    }

    private void bindItem(int start, TradeLogEntry.ItemData item) throws SQLException {
        if (item == null) {
            insert.setNull(start, Types.VARCHAR); insert.setNull(start + 1, Types.INTEGER); insert.setNull(start + 2, Types.BLOB);
        } else {
            insert.setString(start, item.type()); insert.setInt(start + 1, item.amount()); insert.setBytes(start + 2, item.data());
        }
    }

    @Override public void close() {
        Future<?> cleanup;
        synchronized (this) {
            if (closed) return;
            closed = true;
            cleanup = writer.submit(() -> {
                try { insert.close(); }
                catch (SQLException e) { logger.log(Level.SEVERE, "关闭交易写入语句失败", e); }
                finally {
                    try { connection.close(); }
                    catch (SQLException e) { logger.log(Level.SEVERE, "关闭交易数据库失败", e); }
                }
            });
            writer.shutdown();
        }
        try { cleanup.get(30, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); logger.warning("等待交易记录写入时被中断。"); }
        catch (ExecutionException | TimeoutException e) { logger.log(Level.SEVERE, "交易记录队列未能在关闭等待时间内完成", e); }
    }
}
