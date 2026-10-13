package top.mc100.qdshop.gui;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.data.ShopStore;
import top.mc100.qdshop.data.TradeLogEntry;
import top.mc100.qdshop.data.TransactionDatabase;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.perm.Perms;
import top.mc100.qdshop.shop.ExchangeService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseLoggingTest {
    ItemStack item(String type, int amount) {
        ItemStack item = mock(ItemStack.class);
        Material material = mock(Material.class);
        when(material.name()).thenReturn(type);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.serializeAsBytes()).thenReturn(new byte[]{(byte) amount});
        return item;
    }

    @Test void acceptedPurchaseRecordsBothIncomesAndOnlyOnce() {
        runPurchase(false, true);
    }

    @Test void cancelledPurchaseRestoresReservationWithoutLoggingOrCollectingIncome() {
        runPurchase(true, true);
    }

    @Test void failedStockReservationDoesNotCreateTransactionRecord() {
        runPurchase(false, false);
    }

    void runPurchase(boolean cancelled, boolean inStock) {
        QdShopPlugin plugin = mock(QdShopPlugin.class);
        ShopStore store = mock(ShopStore.class);
        Perms perms = mock(Perms.class);
        TransactionDatabase database = mock(TransactionDatabase.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(plugin.getShopStore()).thenReturn(store);
        when(plugin.getPerms()).thenReturn(perms);
        when(plugin.getTransactionDatabase()).thenReturn(database);
        Player player = mock(Player.class);
        UUID buyer = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(buyer);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world"); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 10.5, 64, -20.25));
        Shop shop = new Shop("demo");
        when(store.get("demo")).thenReturn(shop); when(perms.canTrade(player)).thenReturn(true);
        Trade trade = new Trade(item("EMERALD", 2), item("GOLD_INGOT", 3), item("DIAMOND", 1));
        Merchant merchant = mock(Merchant.class);
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(merchant);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory); when(player.getOpenInventory()).thenReturn(view);
        MerchantRecipe recipe = mock(MerchantRecipe.class);
        when(recipe.getResult()).thenReturn(trade.getResult()); when(recipe.getIngredients()).thenReturn(trade.getCosts());
        when(recipe.shouldIgnoreDiscounts()).thenReturn(true);
        PlayerPurchaseEvent event = mock(PlayerPurchaseEvent.class);
        when(event.getPlayer()).thenReturn(player); when(event.getMerchant()).thenReturn(merchant);
        when(event.getTrade()).thenReturn(recipe);
        ExchangeService.Reservation reservation = mock(ExchangeService.Reservation.class);
        try (var construction = mockConstruction(ShopGui.class, (gui, context) -> {
            when(gui.getMerchant()).thenReturn(merchant); when(gui.getShop()).thenReturn(shop);
            when(gui.getTrade(0)).thenReturn(trade);
        }); var exchange = mockStatic(ExchangeService.class); var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            exchange.when(() -> ExchangeService.reserve(player, inventory, shop, trade)).thenReturn(inStock ? reservation : null);
            GuiListener listener = new GuiListener(plugin);
            listener.openShop(player, shop);
            listener.onPurchase(event);
            when(event.isCancelled()).thenReturn(cancelled || !inStock);
            listener.onPurchaseFinished(event);
            listener.onPurchaseFinished(event);
            if (!inStock) {
                verify(event).setCancelled(true);
                verifyNoInteractions(reservation, database);
            } else if (cancelled) {
                verify(reservation, times(1)).rollback(); verify(reservation, never()).commit();
                verifyNoInteractions(database);
            } else {
                verify(reservation, times(1)).commit();
                var capture = org.mockito.ArgumentCaptor.forClass(TradeLogEntry.class);
                verify(database, times(1)).record(capture.capture());
                var entry = capture.getValue();
                assertEquals(buyer, entry.buyer()); assertEquals("demo", entry.shopId());
                assertEquals("world", entry.world()); assertEquals(10.5, entry.x()); assertEquals(-20.25, entry.z());
                assertEquals("DIAMOND", entry.item().type());
                assertEquals(2, entry.income1().amount()); assertEquals(3, entry.income2().amount());
                assertArrayEquals(new byte[]{3}, entry.income2().data());
            }
        }
    }
}
