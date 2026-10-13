package top.mc100.qdshop.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;

/** Paper 26.2 的公开 Bukkit API 不提供重发交易列表；在原窗口内同步，不重开窗口或移动付款。 */
public final class MerchantSynchronizer {
    private MerchantSynchronizer() {}

    public static void sync(Player player, Merchant merchant) throws ReflectiveOperationException {
        if (!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)
                || inventory.getMerchant() != merchant) return;
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object menu = handle.getClass().getField("containerMenu").get(handle);
        int windowId = menu.getClass().getField("containerId").getInt(menu);
        Object nativeMerchant = merchant.getClass().getMethod("getMerchant").invoke(merchant);
        var getOffers = nativeMerchant.getClass().getMethod("getOffers");
        Object offers = getOffers.invoke(nativeMerchant);
        handle.getClass().getMethod("sendMerchantOffers", int.class, getOffers.getReturnType(),
                int.class, int.class, boolean.class, boolean.class)
                .invoke(handle, windowId, offers, 1, 0, false, false);
        // 重新计算当前选中交易的输出槽，保留两格付款物品。
        inventory.setItem(0, inventory.getItem(0));
        player.updateInventory();
    }
}
