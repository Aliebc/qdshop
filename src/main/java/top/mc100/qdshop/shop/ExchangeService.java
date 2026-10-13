package top.mc100.qdshop.shop;

import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.util.Containers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import java.util.List;

/** 为原生交易结算库存与收款，不扣玩家背包、不额外发货。 */
public final class ExchangeService {
    private ExchangeService() {}

    public static boolean settleMerchantPurchase(Player player, MerchantInventory inventory, Shop shop, Trade trade) {
        ItemStack cost = trade.getCost();
        ItemStack result = trade.getResult();
        // 原生商人允许输入物品交换槽位。只接受足额付款的有效输出操作。
        if (!enough(inventory.getItem(0), cost) && !enough(inventory.getItem(1), cost)) return false;
        List<Container> stock = Containers.resolveAll(shop.getStockChests());
        if (stock.isEmpty()) {
            player.sendMessage(Component.text("商店未绑定有效库存箱，请联系管理员。", NamedTextColor.RED));
            return false;
        }
        if (Containers.count(stock, result) < result.getAmount()) {
            player.sendMessage(Component.text("商店库存不足。", NamedTextColor.RED));
            return false;
        }
        int removed = Containers.remove(stock, result, result.getAmount());
        if (removed != result.getAmount()) {
            // 防止意外的容器变更导致部分扣货。
            if (removed > 0) {
                ItemStack rollback = result.clone();
                rollback.setAmount(removed);
                depositOrDrop(stock, rollback, shop);
            }
            return false;
        }
        List<Container> receive = Containers.resolveAll(shop.receiveTargets());
        if (receive.isEmpty()) receive = stock;
        depositOrDrop(receive, cost.clone(), shop);
        return true;
    }

    private static boolean enough(ItemStack input, ItemStack cost) {
        return input != null && input.isSimilar(cost) && input.getAmount() >= cost.getAmount();
    }

    private static void depositOrDrop(List<Container> targets, ItemStack item, Shop shop) {
        ItemStack leftover = Containers.add(targets, item);
        if (leftover != null) {
            Location dropAt = shop.getStockChests().getFirst().clone().add(0.5, 1, 0.5);
            dropAt.getWorld().dropItemNaturally(dropAt, leftover);
        }
    }
}
