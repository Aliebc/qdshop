package top.mc100.qdshop.extension;

import top.mc100.qdshop.model.Trade;
import org.bukkit.inventory.ItemStack;
import java.time.Instant;
import java.util.List;

/** 同时决定能否交易、实际付款和下一次检查时间。禁止报价仍需提供有效价格用于原生界面。 */
public record TradeQuote(boolean allowed, List<ItemStack> costs, String reason, Instant refreshAt) {
    public TradeQuote {
        if (costs == null || costs.isEmpty() || costs.size() > 2 || costs.stream().anyMatch(i -> !Trade.validItem(i)))
            throw new IllegalArgumentException("报价必须包含一至两份有效付款物品");
        costs = costs.stream().map(ItemStack::clone).toList();
        reason = reason == null ? "" : reason;
    }
    @Override public List<ItemStack> costs() { return costs.stream().map(ItemStack::clone).toList(); }
    public static TradeQuote allow(List<ItemStack> costs) { return new TradeQuote(true, costs, "", null); }
    public static TradeQuote deny(List<ItemStack> costs, String reason, Instant retryAt) { return new TradeQuote(false, costs, reason, retryAt); }
}
