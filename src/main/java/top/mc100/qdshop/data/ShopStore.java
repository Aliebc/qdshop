package top.mc100.qdshop.data;

import top.mc100.qdshop.QdShopPlugin;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 负责商店 + 价格表的持久化（shops.yml）。 */
public class ShopStore {

    private final QdShopPlugin plugin;
    private final File file;
    private final Map<String, Shop> byId = new ConcurrentHashMap<>();

    public ShopStore(QdShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public Shop get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** 找到以该坐标为绑定箱子（取货箱或收货箱）的商店，没有则返回 null。 */
    public Shop findShopByChest(Location loc) {
        if (loc == null) return null;
        for (Shop shop : byId.values()) {
            if (shop.isStockChest(loc) || shop.isIncomeChest(loc)) return shop;
        }
        return null;
    }

    /** 同时匹配双箱两侧，避免绑定另一半绕过保护或跨商店重复绑定。 */
    public Shop findShopByContainer(Location location, Container container) {
        Shop direct = findShopByChest(location);
        if (direct != null) return direct;
        InventoryHolder holder = container.getInventory().getHolder();
        if (holder instanceof DoubleChest chest) {
            Shop left = findShopByHolder(chest.getLeftSide());
            return left != null ? left : findShopByHolder(chest.getRightSide());
        }
        return findShopByHolder(holder);
    }

    private Shop findShopByHolder(InventoryHolder holder) {
        return holder instanceof BlockState state ? findShopByChest(state.getLocation()) : null;
    }

    public void add(Shop shop) {
        byId.put(shop.getId().toLowerCase(Locale.ROOT), shop);
    }

    public void remove(String id) {
        byId.remove(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Shop> all() {
        return byId.values();
    }

    // ---------------- 持久化 ----------------

    public void load() {
        byId.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("shops");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Shop shop = new Shop(id);
            shop.setDisplayName(s.getString("display", id));
            String owner = s.getString("owner");
            if (owner != null) {
                try { shop.setOwner(UUID.fromString(owner)); }
                catch (IllegalArgumentException e) { plugin.getLogger().warning("商店 " + id + " 的主人 UUID 无效，已忽略。"); }
            }
            for (String raw : s.getStringList("stock")) {
                Location l = parseLoc(raw);
                if (l != null) shop.addStockChest(l);
            }
            for (String raw : s.getStringList("income")) {
                Location l = parseLoc(raw);
                if (l != null) shop.addIncomeChest(l);
            }
            // 兼容旧版：单字段 chest（既是取货箱也是收货箱）
            if (shop.getStockChests().isEmpty()) {
                Location legacy = parseLoc(s.getString("chest"));
                if (legacy != null) shop.addStockChest(legacy);
            }
            for (Map<?, ?> t : s.getMapList("trades")) {
                ItemStack cost = (ItemStack) t.get("cost");
                ItemStack result = (ItemStack) t.get("result");
                if (cost != null && result != null) shop.getTrades().add(new Trade(cost, result));
            }
            byId.put(id.toLowerCase(Locale.ROOT), shop);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Shop shop : byId.values()) {
            String base = "shops." + shop.getId() + ".";
            yml.set(base + "display", shop.getDisplayName());
            yml.set(base + "owner", shop.getOwner() == null ? null : shop.getOwner().toString());
            List<String> stock = new ArrayList<>();
            for (Location l : shop.getStockChests()) stock.add(serializeLoc(l));
            yml.set(base + "stock", stock);
            List<String> income = new ArrayList<>();
            for (Location l : shop.getIncomeChests()) income.add(serializeLoc(l));
            yml.set(base + "income", income);
            List<Map<String, Object>> trades = new ArrayList<>();
            for (Trade t : shop.getTrades()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cost", t.getCost());
                m.put("result", t.getResult());
                trades.add(m);
            }
            yml.set(base + "trades", trades);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 shops.yml 失败: " + e.getMessage());
        }
    }

    public static String serializeLoc(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public static Location parseLoc(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
