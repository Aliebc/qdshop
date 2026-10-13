package top.mc100.qdshop.gui;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.data.*;
import top.mc100.qdshop.extension.*;
import top.mc100.qdshop.model.*;
import top.mc100.qdshop.perm.Perms;
import top.mc100.qdshop.shop.ExchangeService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionCallbacksTest {
    ItemStack item(String type, int amount) {
        var item = mock(ItemStack.class); var material = mock(Material.class);
        when(material.name()).thenReturn(type); when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount); when(item.getMaxStackSize()).thenReturn(64);
        when(item.clone()).thenReturn(item); when(item.serializeAsBytes()).thenReturn(new byte[]{1}); return item;
    }
    @Test void successCallsCommittedImmediatelyAndCompletedAfterNativeSettlementExactlyOnce() { run(false, false); }
    @Test void cancellationNeverCallsEitherSuccessHook() { run(true, false); }
    @Test void outdatedPriceIsRejectedBeforeStockPaymentAndCallbacks() { run(false, true); }

    void run(boolean cancelled, boolean changedPrice) {
        var plugin = mock(QdShopPlugin.class); when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        var store = mock(ShopStore.class); var perms = mock(Perms.class); var registry = mock(ExtensionRegistry.class);
        var database = mock(TransactionDatabase.class);
        when(plugin.getShopStore()).thenReturn(store); when(plugin.getPerms()).thenReturn(perms);
        when(plugin.getExtensionRegistry()).thenReturn(registry); when(plugin.getTransactionDatabase()).thenReturn(database);
        var player = mock(Player.class); UUID playerId = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(playerId);
        var world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getName()).thenReturn("world");
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 2));
        Shop shop = new Shop("demo"); when(store.get("demo")).thenReturn(shop); when(perms.canTrade(player)).thenReturn(true);
        var cost = item("EMERALD", 2); var result = item("DIAMOND", 1);
        Trade configured = new Trade(cost, null, result, "rules", List.of());
        Trade trade = configured.resolve(result, false, true);
        var context = new TradeContext(player, "demo", trade.getTradeId(), List.of(), result, List.of(cost), Instant.now());
        var committed = new AtomicInteger(); var completed = new AtomicInteger();
        var extension = new ExtensionItem("rules", "Description", "Author") {
            public ItemStack createItem(ExtensionContext c, List<String> args) { return result; }
            public void onTradeCommitted(TradeCompletion c) { committed.incrementAndGet(); assertEquals(List.of(cost), c.paidCosts()); }
            public void onTradeCompleted(TradeCompletion c) { completed.incrementAndGet(); assertEquals(playerId, c.context().player().getUniqueId()); }
        };
        when(registry.get("rules")).thenReturn(extension);
        var merchant = mock(Merchant.class); var inventory = mock(MerchantInventory.class); var view = mock(InventoryView.class);
        when(inventory.getMerchant()).thenReturn(merchant); when(view.getTopInventory()).thenReturn(inventory); when(player.getOpenInventory()).thenReturn(view);
        var recipe = mock(MerchantRecipe.class); when(recipe.getResult()).thenReturn(result); when(recipe.getIngredients()).thenReturn(List.of(cost));
        when(recipe.shouldIgnoreDiscounts()).thenReturn(true);
        var event = mock(PlayerPurchaseEvent.class); when(event.getPlayer()).thenReturn(player); when(event.getMerchant()).thenReturn(merchant); when(event.getTrade()).thenReturn(recipe);
        var reservation = mock(ExchangeService.Reservation.class);
        List<Runnable> tasks = new ArrayList<>(); var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return mock(org.bukkit.scheduler.BukkitTask.class); });
        Trade latest = changedPrice ? trade.reprice(List.of(item("EMERALD", 5)), true, "") : trade;
        try (var constructors = mockConstruction(ShopGui.class, (gui, ignored) -> {
            when(gui.getMerchant()).thenReturn(merchant); when(gui.getShop()).thenReturn(shop); when(gui.getTrade(0)).thenReturn(trade);
            when(gui.context(0)).thenReturn(context);
            when(gui.evaluateTrade(0)).thenReturn(latest);
        }); var bukkit = mockStatic(Bukkit.class); var exchange = mockStatic(ExchangeService.class); var sync = mockStatic(MerchantSynchronizer.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler); bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            exchange.when(() -> ExchangeService.reserve(player, inventory, shop, trade)).thenReturn(reservation);
            var listener = new GuiListener(plugin); listener.openShop(player, shop); listener.onPurchase(event);
            if (changedPrice) {
                verify(event).setCancelled(true); listener.onPurchaseFinished(event);
                verifyNoInteractions(reservation, database); assertEquals(0, committed.get()); assertEquals(0, completed.get()); return;
            }
            when(event.isCancelled()).thenReturn(cancelled); listener.onPurchaseFinished(event);
            if (cancelled) {
                verify(reservation).rollback(); verifyNoInteractions(database);
                assertEquals(0, committed.get()); assertEquals(0, completed.get()); return;
            }
            assertEquals(1, committed.get()); assertEquals(0, completed.get());
            // 完成回调前同一商品不能再次成交，防止仅在完成回调设置冷却时被 Shift 绕过。
            var repeated = mock(PlayerPurchaseEvent.class); when(repeated.getPlayer()).thenReturn(player); when(repeated.getMerchant()).thenReturn(merchant);
            listener.onPurchase(repeated); verify(repeated).setCancelled(true);
            assertEquals(2, tasks.size()); tasks.get(1).run(); tasks.get(1).run();
            assertEquals(1, completed.get()); listener.closeAll(); assertEquals(1, completed.get());
            verify(database, times(1)).record(any());
        }
    }
}
