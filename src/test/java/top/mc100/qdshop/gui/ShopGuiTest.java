package top.mc100.qdshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.util.Containers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopGuiTest {
    ItemStack item(int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(amount); when(item.getMaxStackSize()).thenReturn(64);
        when(item.clone()).thenReturn(item);
        return item;
    }

    @Test void openingMarksMissingStockSoldOutAndRefreshTracksDepletionAndRestocking() {
        Shop shop = new Shop("demo");
        ItemStack product = item(3);
        shop.getTrades().add(new Trade(item(2), product));
        AtomicInteger count = new AtomicInteger();
        Merchant merchant = mock(Merchant.class);
        List<MerchantRecipe> recipes = new ArrayList<>();
        doAnswer(call -> { recipes.addAll(call.getArgument(0)); return null; }).when(merchant).setRecipes(anyList());
        when(merchant.getRecipe(anyInt())).thenAnswer(call -> recipes.get(call.getArgument(0)));
        try (var bukkit = mockStatic(Bukkit.class); var containers = mockStatic(Containers.class)) {
            bukkit.when(Bukkit::createMerchant).thenReturn(merchant);
            containers.when(() -> Containers.resolveAll(shop.getStockChests())).thenReturn(List.of());
            containers.when(() -> Containers.count(List.of(), product)).thenAnswer(call -> count.get());
            ShopGui gui = new ShopGui(shop);
            assertEquals(0, recipes.getFirst().getMaxUses());
            assertTrue(recipes.getFirst().getUses() >= recipes.getFirst().getMaxUses());
            count.set(8); gui.refreshStock();
            assertEquals(2, recipes.getFirst().getMaxUses());
            count.set(2); gui.refreshStock();
            assertEquals(0, recipes.getFirst().getMaxUses());
            shop.setInfinite(true); gui.refreshStock();
            assertEquals(Integer.MAX_VALUE, recipes.getFirst().getMaxUses());
            shop.setInfinite(false); gui.refreshStock();
            assertEquals(0, recipes.getFirst().getMaxUses());
        }
    }
}
