package top.mc100.qdshop.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopStoreTest {
    @TempDir Path dir;
    @Test void ownerAndDisplayNameSurviveReload() {
        QdShopPlugin plugin = mock(QdShopPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        ShopStore store = new ShopStore(plugin);
        Shop shop = new Shop("demo");
        UUID owner = UUID.randomUUID();
        shop.setOwner(owner);
        shop.setInfinite(true);
        shop.setDisplayName("起点商店");
        store.add(shop);
        store.save();
        store.load();
        assertEquals(owner, store.get("DEMO").getOwner());
        assertTrue(store.get("demo").isInfinite());
        assertEquals("起点商店", store.get("demo").getDisplayName());
    }
    @Test void unboundHalfOfDoubleChestResolvesTheBoundShop() {
        QdShopPlugin plugin = mock(QdShopPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        ShopStore store = new ShopStore(plugin);
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        org.bukkit.Location bound = new org.bukkit.Location(world, 1, 64, 1);
        org.bukkit.Location otherHalf = new org.bukkit.Location(world, 2, 64, 1);
        Shop shop = new Shop("demo");
        shop.addStockChest(bound);
        store.add(shop);
        org.bukkit.block.Container container = mock(org.bukkit.block.Container.class);
        org.bukkit.inventory.Inventory inventory = mock(org.bukkit.inventory.Inventory.class);
        org.bukkit.block.DoubleChest doubleChest = mock(org.bukkit.block.DoubleChest.class);
        org.bukkit.block.Container left = mock(org.bukkit.block.Container.class);
        when(left.getLocation()).thenReturn(bound);
        when(container.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(doubleChest);
        when(doubleChest.getLeftSide()).thenReturn(left);
        assertNull(store.findShopByChest(otherHalf));
        assertSame(shop, store.findShopByContainer(otherHalf, container));
    }
}
