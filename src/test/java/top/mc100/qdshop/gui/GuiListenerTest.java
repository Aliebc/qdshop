package top.mc100.qdshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.data.ShopStore;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.perm.Perms;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuiListenerTest {
    final QdShopPlugin plugin = mock(QdShopPlugin.class);
    final ShopStore store = mock(ShopStore.class);
    final Perms perms = mock(Perms.class);
    final Player player = mock(Player.class);
    final CostSetupGui gui = mock(CostSetupGui.class);
    final Inventory inventory = mock(Inventory.class);
    final ItemStack cost = mock(ItemStack.class);
    final Shop shop = new Shop("demo");
    final GuiListener listener;

    GuiListenerTest() {
        UUID editor = UUID.randomUUID();
        when(plugin.getShopStore()).thenReturn(store);
        when(plugin.getPerms()).thenReturn(perms);
        when(player.getUniqueId()).thenReturn(editor);
        when(gui.getEditor()).thenReturn(editor);
        when(gui.getInventory()).thenReturn(inventory);
        when(gui.getShop()).thenReturn(shop);
        when(inventory.getHolder()).thenReturn(gui);
        when(inventory.getSize()).thenReturn(27);
        when(cost.getType()).thenReturn(mock(Material.class));
        when(cost.getAmount()).thenReturn(2);
        when(cost.getMaxStackSize()).thenReturn(64);
        when(cost.clone()).thenReturn(cost);
        when(store.get("demo")).thenReturn(shop);
        when(perms.admin(player, "add")).thenReturn(true);
        listener = new GuiListener(plugin);
    }

    @Test void closeReturnsSampleExactlyOnce() {
        AtomicReference<ItemStack> slot = new AtomicReference<>(cost);
        when(inventory.getItem(CostSetupGui.COST_SLOT)).thenAnswer(call -> slot.get());
        doAnswer(call -> { slot.set(call.getArgument(1)); return null; })
                .when(inventory).setItem(eq(CostSetupGui.COST_SLOT), nullable(ItemStack.class));
        PlayerInventory backpack = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(backpack);
        when(backpack.addItem(cost)).thenReturn(new HashMap<>());
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getPlayer()).thenReturn(player);
        listener.onClose(event);
        listener.onClose(event);
        verify(backpack, times(1)).addItem(cost);
        assertNull(slot.get());
    }

    @Test void rapidConfirmOnlyAddsOneTradeAndKeepsSampleForCloseRefund() {
        AtomicBoolean finishing = new AtomicBoolean();
        when(gui.isFinishing()).thenAnswer(call -> finishing.get());
        doAnswer(call -> { finishing.set(true); return null; }).when(gui).setFinishing();
        when(inventory.getItem(CostSetupGui.COST_SLOT)).thenReturn(cost);
        ItemStack product = mock(ItemStack.class);
        when(gui.getProduct()).thenReturn(product);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(CostSetupGui.CONFIRM_SLOT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        try (var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            listener.onSetupClick(event);
            listener.onSetupClick(event);
            assertEquals(1, shop.getTrades().size());
            assertSame(product, shop.getTrades().getFirst().getResult());
            verify(store, times(1)).save();
            verify(scheduler, times(1)).runTask(eq(plugin), any(Runnable.class));
            verify(inventory, never()).setItem(eq(CostSetupGui.COST_SLOT), nullable(ItemStack.class));
        }
    }

    @Test void draggingIntoDecorationSlotsIsBlockedWhileCostSlotIsAllowed() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlots()).thenReturn(Set.of(11, 13));
        listener.onSetupDrag(event);
        verify(event).setCancelled(true);
        reset(event);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlots()).thenReturn(Set.of(13, 30));
        listener.onSetupDrag(event);
        verify(event, never()).setCancelled(true);
    }
}
