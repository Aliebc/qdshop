package top.mc100.qdshop.command;

import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.data.ShopStore;
import top.mc100.qdshop.gui.CostSetupGui;
import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.perm.Perms;
import top.mc100.qdshop.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class QdShopCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("help", "create", "remove", "open", "name", "inventory", "income", "owner", "add");
    private final QdShopPlugin plugin;

    public QdShopCommand(QdShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) { help(sender); return true; }
        if (!allowed(sender, sub)) { error(sender, "你没有权限执行这个命令。"); return true; }
        if (sub.equals("help")) { help(sender); return true; }
        boolean valid = switch (sub) {
            case "name" -> args.length >= 3;
            case "owner" -> args.length == 3;
            case "inventory", "income" -> args.length == 2 || args.length == 6;
            default -> args.length == 2;
        };
        if (!valid) { usage(sender, sub); return true; }
        ShopStore store = plugin.getShopStore();
        String id = args[1];
        if (sub.equals("create")) {
            if (!id.matches("[\\p{L}\\p{N}_-]+")) {
                error(sender, "商店 id 只能包含文字、数字、下划线和连字符。");
                return true;
            }
            if (store.get(id) != null) { error(sender, "该商店 id 已存在。"); return true; }
            store.add(new Shop(id));
            store.save();
            success(sender, "已创建商店：" + id + "，显示名称默认为 " + id);
            return true;
        }
        Shop shop = store.get(id);
        if (shop == null) { error(sender, "找不到商店：" + id); return true; }
        switch (sub) {
            case "remove" -> {
                plugin.getGuiListener().closeViewers(shop);
                store.remove(shop.getId());
                store.save();
                success(sender, "已删除商店：" + shop.getId());
            }
            case "name" -> {
                shop.setDisplayName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                plugin.getGuiListener().closeViewers(shop);
                store.save();
                success(sender, "已修改商店显示名称。");
            }
            case "open" -> {
                if (!(sender instanceof Player player)) { error(sender, "打开商店需要由玩家执行。"); return true; }
                plugin.getGuiListener().openShop(player, shop);
            }
            case "inventory", "income" -> bindContainer(sender, args, shop, sub.equals("inventory"));
            case "owner" -> {
                OfflinePlayer owner = Bukkit.getPlayerExact(args[2]);
                if (owner == null) owner = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (owner == null) {
                    owner = Arrays.stream(Bukkit.getOfflinePlayers())
                            .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(args[2]))
                            .findFirst().orElse(null);
                }
                if (owner == null) { error(sender, "找不到玩家：" + args[2] + "，请让该玩家先登录一次。"); return true; }
                shop.setOwner(owner.getUniqueId());
                store.save();
                success(sender, "已设置商店主人：" + (owner.getName() == null ? args[2] : owner.getName()));
            }
            case "add" -> {
                if (!(sender instanceof Player player)) { error(sender, "添加商品需要由玩家执行。"); return true; }
                ItemStack product = player.getInventory().getItemInMainHand();
                if (product.getType().isAir()) { error(sender, "请把要出售的商品拿在主手。"); return true; }
                if (product.getAmount() > product.getMaxStackSize()) { error(sender, "商品数量不能超过最大堆叠数。"); return true; }
                new CostSetupGui(shop, product, player.getUniqueId()).open(player);
            }
            default -> { }
        }
        return true;
    }

    private void bindContainer(CommandSender sender, String[] args, Shop shop, boolean stock) {
        Block block;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) { error(sender, "控制台请使用坐标和世界名称绑定容器。"); return; }
            block = player.getTargetBlockExact(6);
            if (block == null) { error(sender, "请对准 6 格范围内的存储容器。"); return; }
        } else {
            World world = Bukkit.getWorld(args[5]);
            if (world == null) { error(sender, "世界不存在或未加载：" + args[5]); return; }
            int x, y, z;
            try {
                x = Integer.parseInt(args[2]); y = Integer.parseInt(args[3]); z = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) { error(sender, "坐标必须是整数。"); return; }
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) { error(sender, "Y 坐标超出世界高度范围。"); return; }
            block = world.getBlockAt(x, y, z);
        }
        if (!(block.getState() instanceof Container container)) { error(sender, "该位置不存在可用的存储容器。"); return; }
        Shop bound = plugin.getShopStore().findShopByContainer(block.getLocation(), container);
        if (bound != null && bound != shop) { error(sender, "该容器已绑定其他商店：" + bound.getId()); return; }
        if (stock) shop.setStockChest(block.getLocation()); else shop.setIncomeChest(block.getLocation());
        plugin.getShopStore().save();
        Location location = block.getLocation();
        success(sender, "已设置" + (stock ? "存货" : "收入") + "容器：" + location.getWorld().getName()
                + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
    }

    private boolean allowed(CommandSender sender, String sub) {
        if (sub.equals("help")) return sender.hasPermission(Perms.CMD + "help");
        if (sender instanceof Player player) {
            return sub.equals("open") ? plugin.getPerms().canTrade(player) : plugin.getPerms().admin(player, sub);
        }
        return sub.equals("open") ? sender.hasPermission(Perms.USE)
                : sender.hasPermission(Perms.ADMIN) || sender.hasPermission(Perms.CMD + sub);
    }

    private void usage(CommandSender sender, String sub) {
        String tail = switch (sub) {
            case "name" -> " <name>";
            case "owner" -> " <player>";
            case "inventory", "income" -> " [<x> <y> <z> <world>]";
            default -> "";
        };
        error(sender, "用法：/qdshop " + sub + " <id>" + tail);
    }

    private void error(CommandSender sender, String text) { sender.sendMessage(Component.text(text, NamedTextColor.RED)); }
    private void success(CommandSender sender, String text) { sender.sendMessage(Component.text(text, NamedTextColor.GREEN)); }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.mm("<gold>===== QIDIAN SHOP ====="));
        sender.sendMessage(Component.text("/qdshop help 显示帮助菜单"));
        sender.sendMessage(Component.text("/qdshop create <id> 创建商店"));
        sender.sendMessage(Component.text("/qdshop remove <id> 删除商店"));
        sender.sendMessage(Component.text("/qdshop open <id> 打开商店"));
        sender.sendMessage(Component.text("/qdshop name <id> <name> 设置商店显示名称"));
        sender.sendMessage(Component.text("/qdshop inventory <id> [<x> <y> <z> <world>] 设置存货容器（省略坐标则使用准星目标）"));
        sender.sendMessage(Component.text("/qdshop income <id> [<x> <y> <z> <world>] 设置收入容器（省略坐标则使用准星目标）"));
        sender.sendMessage(Component.text("/qdshop owner <id> <player> 设置商店主人"));
        sender.sendMessage(Component.text("/qdshop add <id> 主手商品上架，打开箱子界面设置价格"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) return matches(SUBCOMMANDS.stream().filter(s -> allowed(sender, s)).toList(), args[0]);
        if (!SUBCOMMANDS.contains(sub) || !allowed(sender, sub) || sub.equals("help")) return List.of();
        if (args.length == 2) {
            if (sub.equals("create")) return matches(List.of("<id>"), args[1]);
            return matches(plugin.getShopStore().all().stream().map(Shop::getId).toList(), args[1]);
        }
        if (sub.equals("owner") && args.length == 3) {
            TreeSet<String> names = new TreeSet<>();
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) if (p.getName() != null) names.add(p.getName());
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return matches(List.copyOf(names), args[2]);
        }
        if (sub.equals("name") && args.length == 3) {
            Shop shop = plugin.getShopStore().get(args[1]);
            return matches(shop == null ? List.of("<name>") : List.of(shop.getDisplayName()), args[2]);
        }
        if (sub.equals("inventory") || sub.equals("income")) {
            if (args.length >= 3 && args.length <= 5) {
                if (sender instanceof Player player) {
                    Block target = player.getTargetBlockExact(6);
                    Location loc = target == null ? player.getLocation() : target.getLocation();
                    int coordinate = switch (args.length) { case 3 -> loc.getBlockX(); case 4 -> loc.getBlockY(); default -> loc.getBlockZ(); };
                    return matches(List.of(String.valueOf(coordinate)), args[args.length - 1]);
                }
                return matches(List.of("0"), args[args.length - 1]);
            }
            if (args.length == 6) return matches(Bukkit.getWorlds().stream().map(World::getName).toList(), args[5]);
        }
        return List.of();
    }

    private List<String> matches(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().sorted().toList();
    }
}
