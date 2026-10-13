package top.mc100.qdshop.gui;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.shop.ExchangeService;
import top.mc100.qdshop.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiListener implements Listener {
    private final QdShopPlugin plugin;
    private final Map<UUID, ShopGui> sessions = new HashMap<>();

    public GuiListener(QdShopPlugin plugin) { this.plugin = plugin; }

    public void openShop(Player player, Shop shop) {
        ShopGui gui = new ShopGui(shop);
        gui.open(player);
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == gui.getMerchant()) {
            sessions.put(player.getUniqueId(), gui);
        }
    }

    /** 原生界面负责扣交易槽物品和发货；插件只处理箱子库存、收款。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        Player player = event.getPlayer();
        ShopGui gui = sessions.get(player.getUniqueId());
        if (gui == null || event.getMerchant() != gui.getMerchant()) return;
        if (!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)
                || inventory.getMerchant() != gui.getMerchant()) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getShopStore().get(gui.getShop().getId()) != gui.getShop()
                || !plugin.getPerms().canTrade(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("商店已失效或你没有交易权限。", NamedTextColor.RED));
            return;
        }
        Trade trade = gui.getTrade(inventory.getSelectedRecipeIndex());
        MerchantRecipe recipe = event.getTrade();
        if (trade == null || !recipe.getResult().equals(trade.getResult())
                || recipe.getIngredients().size() != 1
                || !recipe.getIngredients().getFirst().equals(trade.getCost())
                || recipe.getDemand() != 0 || recipe.getSpecialPrice() != 0
                || recipe.getPriceMultiplier() != 0f || !recipe.shouldIgnoreDiscounts()) {
            event.setCancelled(true);
            return;
        }
        if (!ExchangeService.settleMerchantPurchase(player, inventory, gui.getShop(), trade)) {
            event.setCancelled(true);
            return;
        }
        event.setRewardExp(false);
        event.setIncreaseTradeUses(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSetupClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CostSetupGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !gui.getEditor().equals(player.getUniqueId()) || gui.isFinishing()) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.isShiftClick()
                || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.MIDDLE || event.getAction() == InventoryAction.UNKNOWN) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        if (event.getRawSlot() == CostSetupGui.COST_SLOT) return;
        event.setCancelled(true);
        if (event.getRawSlot() == CostSetupGui.CANCEL_SLOT) {
            finishSetup(player, gui);
        } else if (event.getRawSlot() == CostSetupGui.CONFIRM_SLOT) {
            if (plugin.getShopStore().get(gui.getShop().getId()) != gui.getShop()
                    || !plugin.getPerms().admin(player, "add")) {
                player.sendMessage(Component.text("商店已失效或你没有添加商品权限。", NamedTextColor.RED));
                finishSetup(player, gui);
                return;
            }
            ItemStack cost = gui.getInventory().getItem(CostSetupGui.COST_SLOT);
            if (cost == null || cost.getType().isAir() || cost.getAmount() <= 0
                    || cost.getAmount() > cost.getMaxStackSize()) {
                player.sendMessage(Component.text("请在中间槽位放入有效的付款物品，数量不能超过最大堆叠数。", NamedTextColor.RED));
                return;
            }
            gui.getShop().getTrades().add(new Trade(cost.clone(), gui.getProduct()));
            plugin.getShopStore().save();
            player.sendMessage(Component.text("商品已上架，请向存货容器放入足够库存。", NamedTextColor.GREEN));
            finishSetup(player, gui);
        }
    }

    private void finishSetup(Player player, CostSetupGui gui) {
        gui.setFinishing();
        // 点击事件处理中不能直接关闭窗口；下一 tick 关闭并退还付款样品。
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == gui) player.closeInventory();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSetupDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CostSetupGui gui)) return;
        if (!gui.getEditor().equals(event.getWhoClicked().getUniqueId()) || gui.isFinishing()
                || event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize()
                    && slot != CostSetupGui.COST_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CostSetupGui gui) {
            ItemStack cost = gui.getInventory().getItem(CostSetupGui.COST_SLOT);
            gui.getInventory().setItem(CostSetupGui.COST_SLOT, null);
            gui.setFinishing();
            if (cost != null && !cost.getType().isAir() && event.getPlayer() instanceof Player player) {
                Items.giveOrDrop(player, cost);
            }
        }
        ShopGui gui = sessions.get(event.getPlayer().getUniqueId());
        if (gui != null && event.getInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == gui.getMerchant()) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    public void closeViewers(Shop shop) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ShopGui gui = sessions.get(player.getUniqueId());
            if ((gui != null && gui.getShop() == shop)
                    || (player.getOpenInventory().getTopInventory().getHolder() instanceof CostSetupGui setup
                        && setup.getShop() == shop)) player.closeInventory();
        }
    }

    public void closeAll() {
        for (UUID id : java.util.List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.closeInventory();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CostSetupGui) player.closeInventory();
        }
        sessions.clear();
    }
}
