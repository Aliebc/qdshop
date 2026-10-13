package top.mc100.qdshop.command;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.data.ShopStore;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.perm.Perms;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QdShopCommandTest {
    private final QdShopPlugin plugin = mock(QdShopPlugin.class);
    private final ShopStore store = mock(ShopStore.class);
    private final Perms perms = mock(Perms.class);
    private final Player player = mock(Player.class);
    private final Shop shop = new Shop("demo");
    private final World world = mock(World.class);
    private final Block block = mock(Block.class);
    private final QdShopCommand command;

    QdShopCommandTest() {
        when(plugin.getShopStore()).thenReturn(store);
        when(plugin.getPerms()).thenReturn(perms);
        when(perms.canManage(eq(player), any(), anyString())).thenAnswer(call -> perms.admin(player, call.getArgument(2)));
        when(store.get("demo")).thenReturn(shop);
        when(store.all()).thenReturn(List.of(shop));
        when(perms.admin(eq(player), anyString())).thenReturn(true);
        when(player.hasPermission("qdshop.command.help")).thenReturn(true);
        when(perms.canTrade(player)).thenReturn(true);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        when(block.getState()).thenReturn(mock(Container.class));
        when(player.getTargetBlockExact(6)).thenReturn(block);
        command = new QdShopCommand(plugin);
    }

    private void execute(String... args) { command.onCommand(player, null, "qdshop", args); }

    @Test void versionIsPublicForPlayersAndConsoleAndUsesPluginMetadata() {
        var metadata = mock(io.papermc.paper.plugin.configuration.PluginMeta.class);
        when(plugin.getPluginMeta()).thenReturn(metadata);
        when(metadata.getVersion()).thenReturn("2.3.4");
        when(player.hasPermission(anyString())).thenReturn(false);
        when(perms.admin(eq(player), anyString())).thenReturn(false);
        when(perms.canTrade(player)).thenReturn(false);
        var console = mock(org.bukkit.command.ConsoleCommandSender.class);
        for (org.bukkit.command.CommandSender sender : List.of(player, console)) {
            assertTrue(command.onCommand(sender, null, "qdshop", new String[]{"version"}));
            var messages = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify(sender, times(4)).sendMessage(messages.capture());
            assertEquals(List.of("QIDIAN SHOP 起点商店交易系统", "版本: 2.3.4",
                            "提供MC服务器交易系统，由起点服务器开发", "(C) 2026 MC100.TOP"),
                    messages.getAllValues().stream().map(message ->
                            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message)).toList());
            assertEquals(List.of("version"), command.onTabComplete(sender, null, "qdshop", new String[]{"ver"}));
            assertTrue(command.onTabComplete(sender, null, "qdshop", new String[]{"version", ""}).isEmpty());
        }
    }

    @Test void targetBindingReplacesPriorInventoryContainer() {
        shop.addStockChest(new Location(world, 1, 64, 1));
        execute("inventory", "demo");
        assertEquals(List.of(block.getLocation()), shop.getStockChests());
        verify(store).save();
    }

    @Test void nonexistentContainerDoesNotOverwriteBinding() {
        Location old = new Location(world, 1, 64, 1);
        shop.addStockChest(old);
        when(block.getState()).thenReturn(mock(BlockState.class));
        execute("inventory", "demo");
        assertEquals(List.of(old), shop.getStockChests());
        verify(store, never()).save();
    }

    @Test void manualCoordinatesBindIncomeAndRejectMissingWorld() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            when(world.getBlockAt(10, 64, 20)).thenReturn(block);
            execute("income", "demo", "10", "64", "20", "world");
            assertEquals(List.of(block.getLocation()), shop.getIncomeChests());
            execute("inventory", "demo", "10", "64", "20", "missing");
            assertTrue(shop.getStockChests().isEmpty());
            verify(store, times(1)).save();
        }
    }

    @Test void foreignShopBindingCannotBeReused() {
        when(store.findShopByContainer(eq(block.getLocation()), any(Container.class))).thenReturn(new Shop("other"));
        execute("inventory", "demo");
        assertTrue(shop.getStockChests().isEmpty());
        verify(store, never()).save();
    }

    @Test void ownerUsesOfflinePlayersUuid() {
        OfflinePlayer owner = mock(OfflinePlayer.class);
        UUID id = UUID.randomUUID();
        when(owner.getName()).thenReturn("Owner");
        when(owner.getUniqueId()).thenReturn(id);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Owner")).thenReturn(owner);
            execute("owner", "demo", "Owner");
            assertEquals(id, shop.getOwner());
            verify(store).save();
        }
    }

    @Test void completionsIncludeCommandsIdsCoordinatesWorldsAndPlayerNames() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);
            when(player.getName()).thenReturn("Owner");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            assertEquals(15, command.onTabComplete(player, null, "qdshop", new String[]{""}).size());
            assertEquals(List.of("demo"), command.onTabComplete(player, null, "qdshop", new String[]{"add", "d"}));
            assertEquals(List.of("10"), command.onTabComplete(player, null, "qdshop", new String[]{"inventory", "demo", ""}));
            assertEquals(List.of("64"), command.onTabComplete(player, null, "qdshop", new String[]{"income", "demo", "10", ""}));
            assertEquals(List.of("20"), command.onTabComplete(player, null, "qdshop", new String[]{"income", "demo", "10", "64", ""}));
            assertEquals(List.of("world"), command.onTabComplete(player, null, "qdshop", new String[]{"income", "demo", "10", "64", "20", "w"}));
            assertEquals(List.of("Owner"), command.onTabComplete(player, null, "qdshop", new String[]{"owner", "demo", "O"}));
        }
    }
    @Test void removeUsesOneBasedProductNumberAndKeepsShop() {
        top.mc100.qdshop.gui.GuiListener guiListener = mock(top.mc100.qdshop.gui.GuiListener.class);
        when(plugin.getGuiListener()).thenReturn(guiListener);
        top.mc100.qdshop.model.Trade first = mock(top.mc100.qdshop.model.Trade.class);
        top.mc100.qdshop.model.Trade second = mock(top.mc100.qdshop.model.Trade.class);
        shop.getTrades().add(first); shop.getTrades().add(second);
        execute("remove", "demo", "0");
        execute("remove", "demo", "3");
        assertEquals(2, shop.getTrades().size());
        execute("remove", "demo", "1");
        assertEquals(List.of(second), shop.getTrades());
        verify(store, never()).remove(anyString());
        verify(store, times(1)).save();
        assertEquals(List.of("1"), command.onTabComplete(player, null, "qdshop", new String[]{"remove", "demo", ""}));
    }

    @Test void destroyDeletesWholeShopAndReloadHasNoIdParameter() {
        when(plugin.getGuiListener()).thenReturn(mock(top.mc100.qdshop.gui.GuiListener.class));
        execute("destroy", "demo");
        verify(store).remove("demo");
        execute("reload");
        execute("reload", "unexpected");
        verify(plugin, times(1)).reloadShopConfig();
        assertTrue(command.onTabComplete(player, null, "qdshop", new String[]{"reload", ""}).isEmpty());
    }
    @Test void infiniteCommandPersistsFlagAndRejectsInvalidBoolean() {
        var gui = mock(top.mc100.qdshop.gui.GuiListener.class);
        when(plugin.getGuiListener()).thenReturn(gui);
        execute("infinite", "demo", "true");
        assertTrue(shop.isInfinite());
        execute("infinite", "demo", "bad");
        assertTrue(shop.isInfinite());
        execute("infinite", "demo", "false");
        assertFalse(shop.isInfinite());
        verify(store, times(2)).save();
        verify(gui, times(2)).queueStockRefresh(shop);
        assertEquals(List.of("false", "true"), command.onTabComplete(player, null, "qdshop", new String[]{"infinite", "demo", ""}));
    }
    @Test void extensionAddPassesIdAndArgumentsWithoutRequiringMainHandItem() {
        var registry = mock(top.mc100.qdshop.extension.ExtensionRegistry.class);
        var gui = mock(top.mc100.qdshop.gui.GuiListener.class);
        var item = mock(org.bukkit.inventory.ItemStack.class);
        when(plugin.getExtensionRegistry()).thenReturn(registry); when(plugin.getGuiListener()).thenReturn(gui);
        when(registry.createItem(eq("player_skull"), any(), eq(List.of("2")))).thenReturn(item);
        execute("addex", "demo", "player_skull", "2");
        verify(gui).openSetup(player, shop, item, "player_skull", List.of("2"));
        verify(player, never()).getInventory();
        assertEquals(List.of("list", "reload"), command.onTabComplete(player, null, "qdshop", new String[]{"extension", ""}));
    }

    @Test void extensionReloadClosesWindowsBeforeRescanningJars() throws Exception {
        var registry = mock(top.mc100.qdshop.extension.ExtensionRegistry.class);
        var gui = mock(top.mc100.qdshop.gui.GuiListener.class);
        when(plugin.getExtensionRegistry()).thenReturn(registry); when(plugin.getGuiListener()).thenReturn(gui);
        execute("extension", "reload");
        var ordered = inOrder(gui, registry); ordered.verify(gui).closeAll(); ordered.verify(registry).reload();
    }
    @Test void addRejectsExtensionArgumentsAndAddexHasIndependentPermission() {
        when(perms.admin(player, "addex")).thenReturn(false);
        execute("add", "demo", "player_skull");
        execute("addex", "demo", "player_skull");
        verify(player, never()).getInventory();
        verify(plugin, never()).getExtensionRegistry();
        verify(perms).admin(player, "addex");
    }
    @Test void ownerCompletionsAndExecutionAreLimitedToOwnShop() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner); shop.setOwner(owner);
        Shop other = new Shop("other"); when(store.get("other")).thenReturn(other); when(store.all()).thenReturn(List.of(shop, other));
        when(perms.admin(eq(player), anyString())).thenReturn(false);
        when(perms.canManage(eq(player), any(), anyString())).thenAnswer(call -> {
            Shop target = call.getArgument(1); String sub = call.getArgument(2);
            return Perms.isOwnerCommand(sub) && target != null && target.isOwner(owner);
        });
        assertEquals(List.of("demo"), command.onTabComplete(player, null, "qdshop", new String[]{"inventory", ""}));
        var subcommands = command.onTabComplete(player, null, "qdshop", new String[]{""});
        assertTrue(subcommands.containsAll(List.of("add", "remove", "inventory", "income")));
        assertFalse(subcommands.contains("destroy")); assertFalse(subcommands.contains("addex"));
        execute("inventory", "other"); assertTrue(other.getStockChests().isEmpty()); verify(store, never()).save();
        execute("inventory", "demo"); assertEquals(List.of(block.getLocation()), shop.getStockChests()); verify(store).save();
    }
}
