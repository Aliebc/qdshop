package top.mc100.qdshop;

import top.mc100.qdshop.command.QdShopCommand;
import top.mc100.qdshop.data.ShopStore;
import top.mc100.qdshop.data.TransactionDatabase;
import top.mc100.qdshop.gui.GuiListener;
import top.mc100.qdshop.listener.ChestListener;
import top.mc100.qdshop.perm.Perms;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QdShopPlugin extends JavaPlugin {
    private ShopStore shopStore;
    private Perms perms;
    private GuiListener guiListener;
    private TransactionDatabase transactionDatabase;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            transactionDatabase = new TransactionDatabase(getDataFolder().toPath().resolve("transactions.db"), getLogger());
        } catch (java.io.IOException | java.sql.SQLException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "无法初始化交易数据库，插件停止加载。", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        shopStore = new ShopStore(this);
        shopStore.load();
        perms = new Perms(this);
        Bukkit.getPluginManager().registerEvents(new ChestListener(this), this);
        guiListener = new GuiListener(this);
        Bukkit.getPluginManager().registerEvents(guiListener, this);
        PluginCommand command = getCommand("qdshop");
        if (command == null) throw new IllegalStateException("plugin.yml 未声明 qdshop 命令");
        QdShopCommand handler = new QdShopCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("QIDIAN SHOP 已启用。使用 /qdshop create <id> 创建商店。");
    }

    @Override
    public void onDisable() {
        if (guiListener != null) guiListener.closeAll();
        if (shopStore != null) shopStore.save();
        if (transactionDatabase != null) transactionDatabase.close();
    }

    public void reloadShopConfig() {
        guiListener.closeAll();
        reloadConfig();
        shopStore.load();
    }

    public TransactionDatabase getTransactionDatabase() { return transactionDatabase; }
    public ShopStore getShopStore() { return shopStore; }
    public GuiListener getGuiListener() { return guiListener; }
    public Perms getPerms() { return perms; }
}
