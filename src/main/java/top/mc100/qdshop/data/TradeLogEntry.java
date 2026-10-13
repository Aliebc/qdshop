package top.mc100.qdshop.data;

import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/** 在服务器线程捕获全部数据，数据库线程不访问 Bukkit 对象。 */
public record TradeLogEntry(long timeMillis, String world, double x, double y, double z,
                            String shopId, UUID buyer, ItemData item, ItemData income1, ItemData income2) {
    public static TradeLogEntry capture(Player buyer, Shop shop, Trade trade) {
        Location location = buyer.getLocation();
        return new TradeLogEntry(Instant.now().toEpochMilli(), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), shop.getId(), buyer.getUniqueId(),
                ItemData.capture(trade.getResult()), ItemData.capture(trade.getCost()),
                trade.getSecondCost() == null ? null : ItemData.capture(trade.getSecondCost()));
    }

    public record ItemData(String type, int amount, byte[] data) {
        public ItemData { data = data.clone(); }
        @Override public byte[] data() { return data.clone(); }
        public static ItemData capture(ItemStack item) {
            return new ItemData(item.getType().name(), item.getAmount(), item.serializeAsBytes());
        }
    }
}
