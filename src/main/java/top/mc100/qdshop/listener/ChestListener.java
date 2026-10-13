package top.mc100.qdshop.listener;

import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.perm.Perms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ChestListener implements Listener {

    private final QdShopPlugin plugin;

    public ChestListener(QdShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 箱子保护：只有拥有该摊位权限的玩家才能打开商店绑定的箱子。 */
    @EventHandler(ignoreCancelled = true)
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Container container)) return;
        Shop shop = boundShop(block, container);
        if (shop == null) return;

        Player player = event.getPlayer();
        if (plugin.getPerms().canOpenChest(player, shop)) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("你没有权限打开这个箱子（需要 qdshop.chest.open "
                + "或 " + Perms.shopChestNode(shop) + "）。", NamedTextColor.RED));
    }

    /** 箱子保护：阻止无权限玩家破坏商店绑定的箱子。 */
    @EventHandler(ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Container container)) return;
        Shop shop = boundShop(block, container);
        if (shop == null) return;

        Player player = event.getPlayer();
        if (plugin.getPerms().canOpenChest(player, shop)) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("你没有权限破坏这个箱子（需要 qdshop.chest.open "
                + "或 " + Perms.shopChestNode(shop) + "）。", NamedTextColor.RED));
    }

    /** 该方块所属的商店（若箱子保护关闭或不属于任何 商店，则返回 null）。含双箱合并后的另一半。 */
    private Shop boundShop(Block block, Container container) {
        if (!plugin.getConfig().getBoolean("chest-protection.enabled", true)) return null;

        return plugin.getShopStore().findShopByContainer(block.getLocation(), container);
    }
}
