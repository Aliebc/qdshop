package top.mc100.qdshop.extension;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一份成功成交的不可变快照，包含实际成交价格；Shift 的每份成交有独立 transactionId。 */
public record TradeCompletion(TradeContext context, UUID transactionId, ItemStack item,
                              List<ItemStack> paidCosts, Instant completedAt, Location location) {
    public TradeCompletion {
        item = item.clone(); paidCosts = paidCosts.stream().map(ItemStack::clone).toList(); location = location.clone();
    }
    @Override public ItemStack item() { return item.clone(); }
    @Override public List<ItemStack> paidCosts() { return paidCosts.stream().map(ItemStack::clone).toList(); }
    @Override public Location location() { return location.clone(); }
}
