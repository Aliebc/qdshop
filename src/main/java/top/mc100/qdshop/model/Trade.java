package top.mc100.qdshop.model;

import org.bukkit.inventory.ItemStack;

/**
 * 一条交易 = 顾客支付 {@code cost}，从箱子取出 {@code result} 给顾客。
 * 数量已包含在 ItemStack#getAmount() 中。
 */
public class Trade {

    private ItemStack cost;    // 顾客支付的物品（收进箱子）
    private ItemStack result;  // 顾客获得的物品（从箱子取出）

    public Trade(ItemStack cost, ItemStack result) {
        this.cost = cost;
        this.result = result;
    }

    public ItemStack getCost() { return cost; }
    public void setCost(ItemStack cost) { this.cost = cost; }

    public ItemStack getResult() { return result; }
    public void setResult(ItemStack result) { this.result = result; }
}
