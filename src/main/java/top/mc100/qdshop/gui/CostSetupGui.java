package top.mc100.qdshop.gui;

import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;
import java.util.List;

/** 管理员用箱子界面设置付款样品；商品只复制主手样品。 */
public final class CostSetupGui implements InventoryHolder {
    public static final int COST_SLOT = 12;
    public static final int SECOND_COST_SLOT = 14;
    public static final int CONFIRM_SLOT = 22;
    public static final int CANCEL_SLOT = 26;
    private final Shop shop;
    private final ItemStack product;
    private final UUID editor;
    private final String extensionId;
    private final List<String> arguments;
    private final Inventory inventory;
    private boolean finishing;

    public CostSetupGui(Shop shop, ItemStack product, UUID editor) {
        this(shop, product, editor, null, List.of());
    }
    public CostSetupGui(Shop shop, ItemStack product, UUID editor, String extensionId, List<String> arguments) {
        this.extensionId = extensionId;
        this.arguments = List.copyOf(arguments);
        this.shop = shop;
        this.product = product.clone();
        this.editor = editor;
        inventory = Bukkit.createInventory(this, 27, Msg.mm("<dark_green>添加商品: <white>" + shop.getDisplayName()));
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, "<gray>将付款物品放入两个空槽，第二槽可留空");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        inventory.setItem(COST_SLOT, null);
        inventory.setItem(SECOND_COST_SLOT, null);
        inventory.setItem(10, this.product.clone());
        inventory.setItem(CONFIRM_SLOT, button(Material.LIME_WOOL, "<green>确认上架（付款样品会退还）"));
        inventory.setItem(CANCEL_SLOT, button(Material.RED_WOOL, "<red>取消"));
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.mm(name));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCostSlot(int slot) { return slot == COST_SLOT || slot == SECOND_COST_SLOT; }
    public void open(Player player) { player.openInventory(inventory); }
    public Shop getShop() { return shop; }
    public ItemStack getProduct() { return product.clone(); }
    public String getExtensionId() { return extensionId; }
    public List<String> getArguments() { return arguments; }
    public UUID getEditor() { return editor; }
    public boolean isFinishing() { return finishing; }
    public void setFinishing() { finishing = true; }
    @Override public Inventory getInventory() { return inventory; }
}
