package top.mc100.qdshop.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 独立商店，包含显示名称、库存箱子和价格表。 */
public class Shop {

    private final String id;          // 唯一标识（命令中引用）
    private UUID owner;
    private boolean infinite;
    private String displayName;       // 商店显示名（支持颜色/MiniMessage）
    private final List<Location> stockChests = new ArrayList<>();   // 取货箱（可绑多个，从这些箱子取货给顾客）
    private final List<Location> incomeChests = new ArrayList<>();  // 收货箱（顾客付款存入；为空则存入取货箱）
    private final List<Trade> trades = new ArrayList<>();

    public Shop(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String getId() { return id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isInfinite() { return infinite; }
    public void setInfinite(boolean infinite) { this.infinite = infinite; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public boolean isOwner(UUID playerId) { return owner != null && owner.equals(playerId); }

    public void setStockChest(Location location) {
        stockChests.clear();
        addStockChest(location);
    }

    public void setIncomeChest(Location location) {
        incomeChests.clear();
        addIncomeChest(location);
    }

    // ---- 库存箱 ----
    public List<Location> getStockChests() { return stockChests; }
    public List<Location> getIncomeChests() { return incomeChests; }

    /** 添加一个取货箱（去重，按方块坐标）。 */
    public boolean addStockChest(Location loc) {
        return addUnique(stockChests, loc);
    }

    /** 添加一个收货箱（去重，按方块坐标）。 */
    public boolean addIncomeChest(Location loc) {
        return addUnique(incomeChests, loc);
    }

    public void clearChests() {
        stockChests.clear();
        incomeChests.clear();
    }

    /** 至少绑定了一个取货箱才可交易。 */
    public boolean hasStock() { return !stockChests.isEmpty(); }

    /** 顾客付款应存入的箱子：优先收货箱，未设置则退回取货箱。 */
    public List<Location> receiveTargets() {
        return incomeChests.isEmpty() ? stockChests : incomeChests;
    }

    /** 该坐标是否是本商店的取货箱。 */
    public boolean isStockChest(Location loc) { return containsLoc(stockChests, loc); }

    /** 该坐标是否是本商店的收货箱。 */
    public boolean isIncomeChest(Location loc) { return containsLoc(incomeChests, loc); }

    private static boolean containsLoc(List<Location> list, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Location l : list) {
            if (l.getWorld() == loc.getWorld()
                    && l.getBlockX() == loc.getBlockX()
                    && l.getBlockY() == loc.getBlockY()
                    && l.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean addUnique(List<Location> list, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (containsLoc(list, loc)) return false;
        list.add(loc.clone());
        return true;
    }

    public List<Trade> getTrades() { return trades; }
}
