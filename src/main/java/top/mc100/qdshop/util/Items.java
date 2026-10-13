package top.mc100.qdshop.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 物品相关工具。 */
public final class Items {

    private Items() {}

    /** 用可读方式描述一个物品：显示名/材质名 + 数量。 */
    public static String describe(ItemStack s) {
        if (s == null) return "无";
        String name = (s.hasItemMeta() && s.getItemMeta().hasDisplayName())
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(s.getItemMeta().displayName())
                : s.getType().name();
        return name + " x" + s.getAmount();
    }

    /** 从玩家背包精确扣除一份 cost（调用前请先 containsAtLeast 校验）。 */
    public static void remove(Player p, ItemStack cost) {
        p.getInventory().removeItem(cost.clone());
    }

    /** 给玩家物品，背包放不下则掉落在脚下。 */
    public static void giveOrDrop(Player p, ItemStack item) {
        var left = p.getInventory().addItem(item);
        left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
    }
}
