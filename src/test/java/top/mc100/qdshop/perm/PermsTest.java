package top.mc100.qdshop.perm;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermsTest {
    @Test void ownerBypassesDeniedLuckPermsNodesButOtherPlayersDoNot() {
        QdShopPlugin plugin = mock(QdShopPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        PluginManager manager = mock(PluginManager.class);
        when(manager.getPlugin("LuckPerms")).thenReturn(mock(Plugin.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            Perms perms = new Perms(plugin);
            Shop shop = new Shop("demo");
            UUID ownerId = UUID.randomUUID();
            shop.setOwner(ownerId);
            Player owner = mock(Player.class);
            when(owner.getUniqueId()).thenReturn(ownerId);
            assertTrue(perms.canOpenChest(owner, shop));
            for (String sub : java.util.List.of("add", "remove", "inventory", "income")) {
                assertTrue(perms.canManage(owner, shop, sub));
                assertFalse(perms.canManage(owner, new Shop("other"), sub));
            }
            assertFalse(perms.canManage(owner, shop, "destroy"));
            assertFalse(perms.canManage(owner, shop, "addex"));

            Player other = mock(Player.class);
            when(other.getUniqueId()).thenReturn(UUID.randomUUID());
            assertFalse(perms.canOpenChest(other, shop));
            verify(other).hasPermission("qdshop.chest.open");
            verify(other).hasPermission("qdshop.shop.demo.chest.open");
        }
    }
}
