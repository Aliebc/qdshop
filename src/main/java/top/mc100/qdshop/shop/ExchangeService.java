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

/** 原生界面负责玩家扣款和发货；插件预留箱子库存，事件通过后收款。 */
public final class ExchangeService {
    private ExchangeService() {}

    public static Reservation reserve(Player player, MerchantInventory inventory, Shop shop, Trade trade) {
        ItemStack first = inventory.getItem(0), second = inventory.getItem(1);
        ItemStack cost = trade.getCost(), cost2 = trade.getSecondCost();
        if (cost2 == null) {
            if (!enough(first, cost) && !enough(second, cost)) return null;
        } else if (!((enough(first, cost) && enough(second, cost2))
                || (enough(first, cost2) && enough(second, cost)))) return null;
        if (shop.isInfinite()) return new Reservation(shop, trade, List.of(), false);
        ItemStack result = trade.getResult();
        List<Container> stock = Containers.resolveAll(shop.getStockChests());
        if (stock.isEmpty()) {
            player.sendMessage(Component.text("商店未绑定有效库存箱，请联系管理员。", NamedTextColor.RED));
            return null;
        }
        if (Containers.count(stock, result) < result.getAmount()) {
            player.sendMessage(Component.text("商店库存不足。", NamedTextColor.RED));
            return null;
        }
        int removed = Containers.remove(stock, result, result.getAmount());
        if (removed != result.getAmount()) {
            if (removed > 0) {
                ItemStack rollback = result.clone();
                rollback.setAmount(removed);
                depositOrDrop(stock, rollback, shop);
            }
            return null;
        }
        return new Reservation(shop, trade, stock, true);
    }

    public static boolean settleMerchantPurchase(Player player, MerchantInventory inventory, Shop shop, Trade trade) {
        Reservation reservation = reserve(player, inventory, shop, trade);
        if (reservation == null) return false;
        reservation.commit();
        return true;
    }

    public static final class Reservation {
        private final Shop shop;
        private final Trade trade;
        private final List<Container> stock;
        private final boolean reservedStock;
        private boolean finished;
        private Reservation(Shop shop, Trade trade, List<Container> stock, boolean reservedStock) {
            this.shop = shop; this.trade = trade; this.stock = stock; this.reservedStock = reservedStock;
        }
        public void commit() {
            if (finished) return;
            finished = true;
            List<Container> receive = Containers.resolveAll(shop.receiveTargets());
            if (receive.isEmpty()) receive = stock;
            // 没有任何收款容器时，原生界面仍扣付款；虚拟收入由交易日志保存。
            if (receive.isEmpty()) return;
            for (ItemStack cost : trade.getCosts()) depositOrDrop(receive, cost.clone(), shop);
        }
        public void rollback() {
            if (finished) return;
            finished = true;
            if (reservedStock) depositOrDrop(stock, trade.getResult().clone(), shop);
        }
    }

    private static boolean enough(ItemStack input, ItemStack cost) {
        return input != null && input.isSimilar(cost) && input.getAmount() >= cost.getAmount();
    }
    private static void depositOrDrop(List<Container> targets, ItemStack item, Shop shop) {
        ItemStack leftover = Containers.add(targets, item);
        if (leftover != null) {
            Location dropAt = targets.getFirst().getLocation().clone().add(0.5, 1, 0.5);
            dropAt.getWorld().dropItemNaturally(dropAt, leftover);
        }
    }
}
