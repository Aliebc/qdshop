package top.mc100.qdshop.shop;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExchangeServiceTest {
    private final Map<ItemStack, String> types = new IdentityHashMap<>();

    private ItemStack item(String type, int amount) {
        AtomicInteger count = new AtomicInteger(amount);
        ItemStack item = mock(ItemStack.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getAmount" -> count.get();
            case "setAmount" -> { count.set(invocation.getArgument(0)); yield null; }
            case "clone" -> item(type, count.get());
            case "isSimilar" -> type.equals(types.get(invocation.getArgument(0)));
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
        types.put(item, type);
        return item;
    }

    private class Fixture {
        final Player player = mock(Player.class);
        final MerchantInventory inputs = mock(MerchantInventory.class);
        final Shop shop = mock(Shop.class);
        final ItemStack cost = item("payment", 2);
        final ItemStack product = item("product", 3);
        final Trade trade = new Trade(cost, product);
        final ItemStack[] contents = { item("product", 6) };
        final AtomicInteger received = new AtomicInteger();

        Fixture() {
            Location stockLocation = location(false);
            Location incomeLocation = location(true);
            when(shop.getStockChests()).thenReturn(List.of(stockLocation));
            when(shop.receiveTargets()).thenReturn(List.of(incomeLocation));
            when(inputs.getItem(0)).thenReturn(item("payment", 2));
        }

        private Location location(boolean income) {
            Location location = mock(Location.class);
            Block block = mock(Block.class);
            Container container = mock(Container.class);
            Inventory inventory = mock(Inventory.class);
            when(location.getWorld()).thenReturn(mock(World.class));
            when(location.getBlock()).thenReturn(block);
            when(block.getState()).thenReturn(container);
            when(container.getInventory()).thenReturn(inventory);
            if (income) {
                when(inventory.addItem(any(ItemStack.class))).thenAnswer(call -> {
                    received.addAndGet(((ItemStack) call.getArgument(0)).getAmount());
                    return new java.util.HashMap<Integer, ItemStack>();
                });
            } else {
                when(inventory.getContents()).thenAnswer(call -> contents.clone());
                when(inventory.getSize()).thenReturn(1);
                when(inventory.getItem(0)).thenAnswer(call -> contents[0]);
                doAnswer(call -> { contents[0] = call.getArgument(1); return null; })
                        .when(inventory).setItem(eq(0), nullable(ItemStack.class));
            }
            return location;
        }

        boolean buy() { return ExchangeService.settleMerchantPurchase(player, inputs, shop, trade); }
    }

    @Test
    void consecutivePurchasesStopAtSharedStockLimitWithoutDuplicatingNativeSettlement() {
        Fixture f = new Fixture();
        assertTrue(f.buy());
        assertEquals(3, f.contents[0].getAmount());
        assertEquals(2, f.received.get());
        assertTrue(f.buy());
        assertNull(f.contents[0]);
        assertFalse(f.buy());
        assertEquals(4, f.received.get());
        // 玩家交易槽和背包由服务器结算，本服务不得再次扣款或发货。
        verify(f.player, never()).getInventory();
        verify(f.inputs, never()).setItem(anyInt(), nullable(ItemStack.class));
        assertEquals(2, f.inputs.getItem(0).getAmount());
    }

    @Test
    void insufficientPaymentDoesNotConsumeStockOrDepositMoney() {
        Fixture f = new Fixture();
        when(f.inputs.getItem(0)).thenReturn(item("payment", 1));
        assertFalse(f.buy());
        assertEquals(6, f.contents[0].getAmount());
        assertEquals(0, f.received.get());
    }

    @Test
    void paymentInSecondNativeInputSlotIsAccepted() {
        Fixture f = new Fixture();
        when(f.inputs.getItem(0)).thenReturn(null);
        when(f.inputs.getItem(1)).thenReturn(item("payment", 2));
        assertTrue(f.buy());
        assertEquals(3, f.contents[0].getAmount());
        assertEquals(2, f.received.get());
    }
}
