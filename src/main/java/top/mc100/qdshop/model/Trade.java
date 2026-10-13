package top.mc100.qdshop.model;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/** 一份商品对应一至两份付款物品，数量包含在 ItemStack 中。 */
public final class Trade {
    private final ItemStack cost;
    private final ItemStack secondCost;
    private final ItemStack result;

    public Trade(ItemStack cost, ItemStack result) { this(cost, null, result); }
    public Trade(ItemStack cost, ItemStack secondCost, ItemStack result) {
        this.cost = cost;
        this.secondCost = secondCost;
        this.result = result;
    }
    public ItemStack getCost() { return cost; }
    public ItemStack getSecondCost() { return secondCost; }
    public List<ItemStack> getCosts() { return secondCost == null ? List.of(cost) : List.of(cost, secondCost); }
    public ItemStack getResult() { return result; }

    public static boolean validItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0
                && item.getAmount() <= item.getMaxStackSize();
    }
}
