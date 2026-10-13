package top.mc100.qdshop.gui;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.data.TradeLogEntry;
import top.mc100.qdshop.extension.ExtensionItem;
import top.mc100.qdshop.extension.TradeCompletion;
import java.time.Instant;
import org.bukkit.scheduler.BukkitTask;
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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiListener implements Listener {
    private final QdShopPlugin plugin;
    private boolean closing;
    private final Map<UUID, ShopGui> sessions = new HashMap<>();
    private final java.util.Set<Shop> refreshQueued = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, CostSetupGui> setups = new HashMap<>();
    private final Map<PlayerPurchaseEvent, PendingPurchase> pending = new IdentityHashMap<>();
    private record PendingPurchase(ExchangeService.Reservation reservation, TradeLogEntry entry, Shop shop,
                                   ExtensionItem extension, TradeCompletion completion) {}
    private record CompletionCall(ExtensionItem extension, TradeCompletion completion, Shop shop) {}
    private final java.util.List<CompletionCall> completions = new java.util.ArrayList<>();
    private final java.util.Set<String> completing = new java.util.HashSet<>();
    private final Map<UUID, BukkitTask> quoteTimers = new HashMap<>();
    private String completionKey(TradeCompletion c) { return c.context().player().getUniqueId() + ":" + c.context().tradeId(); }

    public GuiListener(QdShopPlugin plugin) { this.plugin = plugin; }

    public void openShop(Player player, Shop shop) {
        ShopGui gui = new ShopGui(shop, player, plugin.getExtensionRegistry());
        gui.open(player);
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == gui.getMerchant()) {
            sessions.put(player.getUniqueId(), gui);
            try { gui.onOpened(); MerchantSynchronizer.sync(player, gui.getMerchant()); }
            catch (Exception | LinkageError e) { plugin.getLogger().log(java.util.logging.Level.WARNING, "拓展打开商店回调失败", e); }
            scheduleQuoteRefresh(player, gui);
        }
    }

    public void openSetup(Player player, Shop shop, ItemStack product) {
        openSetup(player, shop, product, null, java.util.List.of());
    }
    public void openSetup(Player player, Shop shop, ItemStack product, String extensionId, java.util.List<String> arguments) {
        CostSetupGui gui = new CostSetupGui(shop, product, player.getUniqueId(), extensionId, arguments);
        gui.open(player);
        if (player.getOpenInventory().getTopInventory().getHolder() == gui) setups.put(player.getUniqueId(), gui);
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
        int index = inventory.getSelectedRecipeIndex();
        Trade trade = gui.getTrade(index);
        if (trade != null && trade.getExtensionId() != null) {
            if (completing.contains(player.getUniqueId() + ":" + trade.getTradeId())) {
                event.setCancelled(true); return;
            }
            Trade latest = gui.evaluateTrade(index);
            if (latest == null || !latest.isAvailable() || !latest.getCosts().equals(trade.getCosts())) {
                event.setCancelled(true);
                player.sendMessage(Component.text(latest != null && !latest.getUnavailableReason().isBlank()
                        ? latest.getUnavailableReason() : "交易条件或价格已变化，请使用更新后的报价。", NamedTextColor.RED));
                queueStockRefresh(gui.getShop()); return;
            }
            trade = latest;
        }
        MerchantRecipe recipe = event.getTrade();
        if (trade == null || !trade.isAvailable() || !recipe.getResult().equals(trade.getResult())
                || !recipe.getIngredients().equals(trade.getCosts())
                || recipe.getDemand() != 0 || recipe.getSpecialPrice() != 0
                || recipe.getPriceMultiplier() != 0f || !recipe.shouldIgnoreDiscounts()) {
            event.setCancelled(true);
            return;
        }
        TradeLogEntry entry;
        try { entry = TradeLogEntry.capture(player, gui.getShop(), trade); }
        catch (RuntimeException e) {
            event.setCancelled(true);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "无法捕获交易记录，已取消成交。", e);
            return;
        }
        ExchangeService.Reservation reservation = ExchangeService.reserve(player, inventory, gui.getShop(), trade);
        if (reservation == null) {
            queueStockRefresh(gui.getShop());
            event.setCancelled(true);
            return;
        }
        ExtensionItem extension = trade.getExtensionId() == null ? null : plugin.getExtensionRegistry().get(trade.getExtensionId());
        TradeCompletion completion = extension == null ? null : new TradeCompletion(gui.context(index), UUID.randomUUID(),
                trade.getResult(), trade.getCosts(), Instant.now(), player.getLocation());
        pending.put(event, new PendingPurchase(reservation, entry, gui.getShop(), extension, completion));
        event.setRewardExp(false);
        event.setIncreaseTradeUses(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchaseFinished(PlayerPurchaseEvent event) {
        PendingPurchase purchase = pending.remove(event);
        if (purchase == null) return;
        queueStockRefresh(purchase.shop());
        if (event.isCancelled()) {
            purchase.reservation().rollback();
            return;
        }
        purchase.reservation().commit();
        plugin.getTransactionDatabase().record(purchase.entry());
        if (purchase.extension() != null) {
            CompletionCall call = new CompletionCall(purchase.extension(), purchase.completion(), purchase.shop());
            completions.add(call); completing.add(completionKey(call.completion()));
            try { call.extension().onTradeCommitted(call.completion()); }
            catch (Exception | LinkageError e) { plugin.getLogger().log(java.util.logging.Level.WARNING, "拓展成交确认回调失败", e); }
            Bukkit.getScheduler().runTask(plugin, () -> finishCompletion(call));
        }
    }

    private void finishCompletion(CompletionCall call) {
        if (!completions.remove(call)) return;
        try { call.extension().onTradeCompleted(call.completion()); }
        catch (Exception | LinkageError e) { plugin.getLogger().log(java.util.logging.Level.WARNING, "拓展交易完成回调失败", e); }
        finally { completing.remove(completionKey(call.completion())); queueStockRefresh(call.shop()); }
    }

    private void scheduleQuoteRefresh(Player player, ShopGui gui) {
        BukkitTask old = quoteTimers.remove(player.getUniqueId());
        if (old != null) old.cancel();
        Instant time = gui.getNextRefreshAt();
        if (time == null) return;
        long ticks = Math.max(1, (java.time.Duration.between(Instant.now(), time).toMillis() + 49) / 50);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            quoteTimers.remove(player.getUniqueId());
            if (sessions.get(player.getUniqueId()) == gui) queueStockRefresh(gui.getShop());
        }, ticks);
        quoteTimers.put(player.getUniqueId(), task);
    }

    /** 合并同一 tick 内的连续购买，成交完成后的下一 tick 同步所有正在浏览本店的窗口。 */
    public void queueStockRefresh(Shop shop) {
        if (closing) return;
        if (!refreshQueued.add(shop)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!refreshQueued.remove(shop)) return;
            for (var entry : java.util.List.copyOf(sessions.entrySet())) {
                ShopGui gui = entry.getValue();
                if (gui.getShop() != shop) continue;
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)
                        || inventory.getMerchant() != gui.getMerchant()) continue;
                gui.refreshStock();
                scheduleQuoteRefresh(player, gui);
                try { MerchantSynchronizer.sync(player, gui.getMerchant()); }
                catch (ReflectiveOperationException e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "同步商店交易列表失败（Paper 26.2）", e);
                }
            }
        });
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
        if (CostSetupGui.isCostSlot(event.getRawSlot())) return;
        event.setCancelled(true);
        if (event.getRawSlot() == CostSetupGui.CANCEL_SLOT) {
            finishSetup(player, gui);
        } else if (event.getRawSlot() == CostSetupGui.CONFIRM_SLOT) {
            if (plugin.getShopStore().get(gui.getShop().getId()) != gui.getShop()
                    || !plugin.getPerms().canManage(player, gui.getShop(), gui.getExtensionId() == null ? "add" : "addex")) {
                player.sendMessage(Component.text("商店已失效或你没有添加商品权限。", NamedTextColor.RED));
                finishSetup(player, gui);
                return;
            }
            ItemStack cost = gui.getInventory().getItem(CostSetupGui.COST_SLOT);
            ItemStack secondCost = gui.getInventory().getItem(CostSetupGui.SECOND_COST_SLOT);
            if (cost != null && cost.getType().isAir()) cost = null;
            if (secondCost != null && secondCost.getType().isAir()) secondCost = null;
            if ((cost == null && secondCost == null) || (cost != null && !Trade.validItem(cost))
                    || (secondCost != null && !Trade.validItem(secondCost))) {
                player.sendMessage(Component.text("请放入一至两种有效付款物品，数量不能超过最大堆叠数。", NamedTextColor.RED));
                return;
            }
            // 仅填第二槽也视为一种付款，配置中不保存空的第一种付款。
            if (cost == null) { cost = secondCost; secondCost = null; }
            gui.getShop().getTrades().add(new Trade(cost.clone(), secondCost == null ? null : secondCost.clone(), gui.getProduct(), gui.getExtensionId(), gui.getArguments()));
            plugin.getShopStore().save();
            player.sendMessage(Component.text(gui.getExtensionId() != null ? "拓展商品已上架。" : gui.getShop().isInfinite() ? "商品已上架，当前商店为无限供货。" : "商品已上架，请向存货容器放入足够库存。", NamedTextColor.GREEN));
            finishSetup(player, gui);
        }
    }

    private void finishSetup(Player player, CostSetupGui gui) {
        gui.setFinishing();
        // 点击事件处理中不能直接关闭窗口；下一 tick 关闭并退还付款样品。
        Bukkit.getScheduler().runTask(plugin, () -> {
            refundSetup(player, gui);
            if (player.getOpenInventory().getTopInventory().getHolder() == gui) player.closeInventory();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSetupDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CostSetupGui gui)) return;
        if (!gui.getEditor().equals(event.getWhoClicked().getUniqueId()) || gui.isFinishing()
                || event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize()
                    && !CostSetupGui.isCostSlot(slot))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CostSetupGui gui) {
            gui.setFinishing();
            if (event.getPlayer() instanceof Player player) refundSetup(player, gui);
            setups.remove(event.getPlayer().getUniqueId(), gui);
        }
        ShopGui gui = sessions.get(event.getPlayer().getUniqueId());
        if (gui != null && event.getInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() == gui.getMerchant()) {
            sessions.remove(event.getPlayer().getUniqueId());
            BukkitTask timer = quoteTimers.remove(event.getPlayer().getUniqueId());
            if (timer != null) timer.cancel();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        BukkitTask timer = quoteTimers.remove(event.getPlayer().getUniqueId());
        if (timer != null) timer.cancel();
        CostSetupGui gui = setups.remove(event.getPlayer().getUniqueId());
        if (gui != null) refundSetup(event.getPlayer(), gui);
    }

    private void refundSetup(Player player, CostSetupGui gui) {
        if (!player.getUniqueId().equals(gui.getEditor())) return;
        // 先取出并清空两个样品槽，再退还；确认任务、关闭事件和重载重复调用也不会复制物品。
        ItemStack first = gui.getInventory().getItem(CostSetupGui.COST_SLOT);
        ItemStack second = gui.getInventory().getItem(CostSetupGui.SECOND_COST_SLOT);
        gui.getInventory().setItem(CostSetupGui.COST_SLOT, null);
        gui.getInventory().setItem(CostSetupGui.SECOND_COST_SLOT, null);
        if (first != null && !first.getType().isAir()) Items.giveOrDrop(player, first);
        if (second != null && !second.getType().isAir()) Items.giveOrDrop(player, second);
    }

    public void closeViewers(Shop shop) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ShopGui gui = sessions.get(player.getUniqueId());
            if ((gui != null && gui.getShop() == shop)
                    || (player.getOpenInventory().getTopInventory().getHolder() instanceof CostSetupGui setup
                        && setup.getShop() == shop)) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof CostSetupGui setup) refundSetup(player, setup);
                player.closeInventory();
            }
        }
    }

    public void closeAll() {
        closing = true;
        // 先交付已完成成交的回调，再卸载拓展/重载配置，防止回调丢失或访问已卸载类。
        for (CompletionCall call : java.util.List.copyOf(completions)) finishCompletion(call);
        quoteTimers.values().forEach(BukkitTask::cancel); quoteTimers.clear();
        for (UUID id : java.util.List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.closeInventory();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CostSetupGui gui) {
                refundSetup(player, gui);
                player.closeInventory();
            }
        }
        for (var entry : java.util.List.copyOf(setups.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) refundSetup(player, entry.getValue());
        }
        refreshQueued.clear();
        setups.clear();
        sessions.clear();
        for (PendingPurchase purchase : pending.values()) purchase.reservation().rollback();
        pending.clear();
        closing = false;
    }
}
