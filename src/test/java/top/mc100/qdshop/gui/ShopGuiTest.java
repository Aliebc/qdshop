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
    @Test void extensionResultIsPerViewerAndMissingExtensionRemainsSoldOutEvenInInfiniteShop() throws Exception {
        Shop shop = new Shop("demo");
        ItemStack preview = item(1), firstHead = item(1), secondHead = item(1);
        shop.getTrades().add(new Trade(item(2), null, preview, "dynamic", List.of("arg")));
        org.bukkit.entity.Player first = mock(org.bukkit.entity.Player.class), second = mock(org.bukkit.entity.Player.class);
        Merchant merchant = mock(Merchant.class);
        List<MerchantRecipe> recipes = new ArrayList<>();
        doAnswer(call -> { recipes.clear(); recipes.addAll(call.getArgument(0)); return null; }).when(merchant).setRecipes(anyList());
        when(merchant.getRecipe(anyInt())).thenAnswer(call -> recipes.get(call.getArgument(0)));
        try (var registry = new top.mc100.qdshop.extension.ExtensionRegistry(java.nio.file.Files.createTempDirectory("qdshop-ext-test"), mock(java.util.logging.Logger.class));
             var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::createMerchant).thenReturn(merchant);
            registry.register(new top.mc100.qdshop.extension.ExtensionItem("dynamic", "Description", "Author") {
                public ItemStack createItem(top.mc100.qdshop.extension.ExtensionContext c, List<String> args) {
                    assertEquals(List.of("arg"), args); return c.viewer() == first ? firstHead : secondHead;
                }
            });
            ShopGui firstGui = new ShopGui(shop, first, registry);
            ShopGui secondGui = new ShopGui(shop, second, registry);
            assertSame(firstHead, firstGui.getTrade(0).getResult()); assertSame(secondHead, secondGui.getTrade(0).getResult());
            assertFalse(firstGui.getTrade(0).requiresStock());
            assertEquals(Integer.MAX_VALUE, recipes.getFirst().getMaxUses());
            shop.setInfinite(true);
            registry.close();
            ShopGui unavailable = new ShopGui(shop, first, registry);
            assertFalse(unavailable.getTrade(0).isAvailable());
            assertEquals(0, recipes.getFirst().getMaxUses());
        }
    }
    @Test void quotesCanChangeBothPricesDenyForCooldownAndRestoreWithoutChangingBasePrice() throws Exception {
        Shop shop = new Shop("demo");
        ItemStack original = item(2), raised = item(5), secondPrice = item(3), product = item(1);
        Trade stored = new Trade(original, null, product, "rules", List.of("test")); shop.getTrades().add(stored);
        var viewer = mock(org.bukkit.entity.Player.class);
        var merchant = mock(Merchant.class);
        List<MerchantRecipe> recipes = new ArrayList<>();
        doAnswer(call -> { recipes.clear(); recipes.addAll(call.getArgument(0)); return null; }).when(merchant).setRecipes(anyList());
        when(merchant.getRecipe(anyInt())).thenAnswer(call -> recipes.get(call.getArgument(0)));
        var quote = new java.util.concurrent.atomic.AtomicReference<>(top.mc100.qdshop.extension.TradeQuote.allow(List.of(original)));
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        try (var registry = new top.mc100.qdshop.extension.ExtensionRegistry(java.nio.file.Files.createTempDirectory("qdshop-quotes"), mock(java.util.logging.Logger.class));
             var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::createMerchant).thenReturn(merchant);
            registry.register(new top.mc100.qdshop.extension.ExtensionItem("rules", "Description", "Author") {
                public ItemStack createItem(top.mc100.qdshop.extension.ExtensionContext c, List<String> args) { return product; }
                public void onShopOpen(top.mc100.qdshop.extension.TradeContext c) { opened.incrementAndGet(); }
                public top.mc100.qdshop.extension.TradeQuote quote(top.mc100.qdshop.extension.TradeContext c) {
                    assertSame(viewer, c.player()); assertEquals(stored.getTradeId(), c.tradeId());
                    assertEquals(List.of(original), c.baseCosts()); return quote.get();
                }
            });
            var gui = new ShopGui(shop, viewer, registry); gui.onOpened(); assertEquals(1, opened.get());
            quote.set(top.mc100.qdshop.extension.TradeQuote.allow(List.of(raised, secondPrice)));
            assertEquals(List.of(raised, secondPrice), gui.evaluateTrade(0).getCosts());
            gui.refreshStock(); assertEquals(List.of(raised, secondPrice), recipes.getFirst().getIngredients());
            var until = java.time.Instant.now().plusSeconds(60);
            quote.set(top.mc100.qdshop.extension.TradeQuote.deny(List.of(raised, secondPrice), "冷却中", until));
            gui.refreshStock(); assertFalse(gui.getTrade(0).isAvailable()); assertEquals(0, recipes.getFirst().getMaxUses());
            assertEquals(until, gui.getNextRefreshAt());
            quote.set(top.mc100.qdshop.extension.TradeQuote.allow(List.of(original)));
            gui.refreshStock(); assertTrue(gui.getTrade(0).isAvailable()); assertEquals(Integer.MAX_VALUE, recipes.getFirst().getMaxUses());
            assertEquals(List.of(original), stored.getCosts());
        }
    }
}
