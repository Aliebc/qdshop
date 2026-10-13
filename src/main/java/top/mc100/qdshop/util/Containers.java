package top.mc100.qdshop.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 箱子/容器批量存取工具（支持一个商店绑定多个箱子）。 */
public final class Containers {

    private Containers() {}

    /** 把一个坐标解析为容器；不是容器或世界不存在则返回 null。 */
    public static Container resolve(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Block b = loc.getBlock();
        return (b.getState() instanceof Container c) ? c : null;
    }

    /** 批量解析坐标列表，自动跳过失效的箱子。 */
    public static List<Container> resolveAll(List<Location> locs) {
        List<Container> out = new ArrayList<>();
        for (Location loc : locs) {
            Container c = resolve(loc);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 一组容器中与 item 同类物品的总数量。 */
    public static int count(List<Container> containers, ItemStack item) {
        int total = 0;
        for (Container c : containers) {
            Inventory inv = c.getInventory();
            for (ItemStack s : inv.getContents()) {
                if (s != null && s.isSimilar(item)) total += s.getAmount();
            }
        }
        return total;
    }

    /** 从一组容器中扣除 item 共 amount 个（尽量扣满），返回实际扣除数量。 */
    public static int remove(List<Container> containers, ItemStack item, int amount) {
        int need = amount;
        for (Container c : containers) {
            if (need <= 0) break;
            Inventory inv = c.getInventory();
            for (int i = 0; i < inv.getSize() && need > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || !s.isSimilar(item)) continue;
                int take = Math.min(need, s.getAmount());
                int left = s.getAmount() - take;
                if (left <= 0) {
                    inv.setItem(i, null);
                } else {
                    ItemStack copy = s.clone();
                    copy.setAmount(left);
                    inv.setItem(i, copy);
                }
                need -= take;
            }
        }
        return amount - need;
    }

    /** 往一组容器中放入 item，返回放不下的剩余（全部放下则返回 null）。 */
    public static ItemStack add(List<Container> containers, ItemStack item) {
        ItemStack remaining = item.clone();
        for (Container c : containers) {
            if (remaining == null || remaining.getAmount() <= 0) return null;
            Map<Integer, ItemStack> left = c.getInventory().addItem(remaining);
            if (left.isEmpty()) return null;
            remaining = left.values().iterator().next();
        }
        return (remaining == null || remaining.getAmount() <= 0) ? null : remaining;
    }
}
