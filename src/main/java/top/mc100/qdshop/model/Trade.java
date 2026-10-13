package top.mc100.qdshop.model;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/** 一份商品对应一至两份付款物品，数量包含在 ItemStack 中。 */
public final class Trade {
    private final ItemStack cost;
    private final ItemStack secondCost;
    private final ItemStack result;
    private final String extensionId;
    private final List<String> extensionArguments;
    private final boolean requiresStock, available;
    private final UUID tradeId;
    private final String reason;

    public Trade(ItemStack cost, ItemStack result) { this(cost, null, result); }
    public Trade(ItemStack cost, ItemStack secondCost, ItemStack result) {
        this(cost, secondCost, result, null, List.of());
    }
    public Trade(ItemStack cost, ItemStack secondCost, ItemStack result, String extensionId, List<String> arguments) {
        this(cost, secondCost, result, extensionId, arguments, UUID.randomUUID());
    }
    public Trade(ItemStack cost, ItemStack secondCost, ItemStack result, String extensionId, List<String> arguments, UUID tradeId) {
        this(cost, secondCost, result, extensionId, arguments, true, true, tradeId, "");
    }
    private Trade(ItemStack cost, ItemStack secondCost, ItemStack result, String extensionId,
                  List<String> arguments, boolean requiresStock, boolean available, UUID tradeId, String reason) {
        this.cost = cost; this.secondCost = secondCost; this.result = result;
        this.extensionId = extensionId; this.extensionArguments = List.copyOf(arguments);
        this.requiresStock = requiresStock; this.available = available; this.tradeId = tradeId; this.reason = reason;
    }
    public Trade resolve(ItemStack result, boolean requiresStock, boolean available) {
        return new Trade(cost.clone(), secondCost == null ? null : secondCost.clone(), result.clone(),
                extensionId, extensionArguments, requiresStock, available, tradeId, reason);
    }
    public Trade reprice(List<ItemStack> costs, boolean available, String reason) {
        return new Trade(costs.getFirst().clone(), costs.size() == 2 ? costs.get(1).clone() : null, result.clone(),
                extensionId, extensionArguments, requiresStock, available, tradeId, reason);
    }
    public UUID getTradeId() { return tradeId; }
    public String getUnavailableReason() { return reason; }
    public String getExtensionId() { return extensionId; }
    public List<String> getExtensionArguments() { return extensionArguments; }
    public boolean requiresStock() { return requiresStock; }
    public boolean isAvailable() { return available; }
    public ItemStack getCost() { return cost; }
    public ItemStack getSecondCost() { return secondCost; }
    public List<ItemStack> getCosts() { return secondCost == null ? List.of(cost) : List.of(cost, secondCost); }
    public ItemStack getResult() { return result; }

    public static boolean validItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0
                && item.getAmount() <= item.getMaxStackSize();
    }
}
