package top.mc100.qdshop.perm;

import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 权限判定中心。
 *
 * <p>设计原则：插件只<b>声明</b>权限节点，不打包、不硬编码 LuckPerms。
 * 运行时按环境自动适配：
 * <ul>
 *   <li>已安装 LuckPerms —— 完全交给 LuckPerms 判定（支持 {@code qdshop.*} 通配与组继承）；</li>
 *   <li>未安装 LuckPerms —— 管理权限退化为 {@link Player#isOp()}；玩家权限默认放行。</li>
 * </ul>
 *
 * <p>可选：config.yml 的 {@code permissions.op-always-admin}（默认 false）设为 true 时，
 * 无论是否装 LuckPerms，OP 都始终拥有管理权限——用于避免在 LuckPerms 服上把自己锁在外面。
 */
public final class Perms {

    /** 与商店 交互（打开商店、成交）。 */
    public static final String USE = "qdshop.use";
    /** 管理员总节点，等价于全部 {@code qdshop.command.*}。 */
    public static final String ADMIN = "qdshop.admin";
    /** 子命令节点前缀。 */
    public static final String CMD = "qdshop.command.";
    /** 打开/破坏被商店 绑定的箱子。 */
    public static final String CHEST_OPEN = "qdshop.chest.open";

    private final QdShopPlugin plugin;
    private final boolean luckPerms;

    public Perms(QdShopPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    /** 是否检测到 LuckPerms。 */
    public boolean hasLuckPerms() { return luckPerms; }

    private boolean opAlwaysAdmin() {
        return plugin.getConfig().getBoolean("permissions.op-always-admin", false);
    }

    /** 玩家能否与商店 交互。装 LuckPerms 时按节点（默认 true）；未装时默认放行。 */
    public boolean canTrade(Player p) {
        if (p.hasPermission(USE)) return true;
        return !luckPerms;
    }

    /**
     * 管理类子命令权限。
     * @param sub 子命令名，如 create / remove / name
     */
    public boolean admin(Player p, String sub) {
        if (opAlwaysAdmin() && p.isOp()) return true;
        if (luckPerms) return p.hasPermission(ADMIN) || p.hasPermission(CMD + sub);
        return p.isOp();                      // 无 LuckPerms：退化为 OP 判断
    }

    /** 某个摊位专属的"开箱"权限节点：qdshop.shop.&lt;id&gt;.chest.open。 */
    public static String shopChestNode(Shop shop) {
        return "qdshop.shop." + shop.getId() + ".chest.open";
    }

    /**
     * 能否打开/破坏某个商店绑定的箱子。
     * <p>判定优先级：全局 {@link #CHEST_OPEN} / {@link #ADMIN} → 摊位专属 {@code qdshop.shop.<id>.chest.open}。
     * <p>装了 LuckPerms 按其节点判定（支持 qdshop.shop.*.chest.open 之类的通配）；未装退化为 OP。
     */
    public boolean canOpenChest(Player p, Shop shop) {
        if (shop != null && shop.isOwner(p.getUniqueId())) return true;
        if (opAlwaysAdmin() && p.isOp()) return true;
        if (luckPerms) {
            return p.hasPermission(CHEST_OPEN)
                    || p.hasPermission(ADMIN)
                    || (shop != null && p.hasPermission(shopChestNode(shop)));
        }
        return p.isOp();
    }
}
