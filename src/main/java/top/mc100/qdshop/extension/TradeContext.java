package top.mc100.qdshop.extension;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 每次报价的新上下文；baseCosts 永远是设置者配置的基础价格，不是上一次涨价后的价格。 */
public record TradeContext(Player player, String shopId, UUID tradeId, List<String> arguments,
                           ItemStack item, List<ItemStack> baseCosts, Instant now) {
    public TradeContext {
        arguments = List.copyOf(arguments); item = item.clone(); baseCosts = copy(baseCosts);
    }
    private static List<ItemStack> copy(List<ItemStack> items) { return items.stream().map(ItemStack::clone).toList(); }
    @Override public ItemStack item() { return item.clone(); }
    @Override public List<ItemStack> baseCosts() { return copy(baseCosts); }
}
