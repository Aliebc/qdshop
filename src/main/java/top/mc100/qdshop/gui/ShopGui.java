package top.mc100.qdshop.gui;

import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.extension.ExtensionRegistry;
import top.mc100.qdshop.extension.ExtensionContext;
import top.mc100.qdshop.extension.TradeContext;
import java.time.Instant;
import top.mc100.qdshop.util.Msg;
import top.mc100.qdshop.util.Containers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/** 每位玩家独立的原生村民交易窗口，不生成实体。 */
public final class ShopGui {
    private final Shop shop;
    private final Merchant merchant;
    private final List<Trade> trades = new ArrayList<>();
    private final List<Trade> baseTrades = new ArrayList<>(), products = new ArrayList<>();
    private final Player viewer;
    private final ExtensionRegistry registry;
    private Instant nextRefreshAt;

    public ShopGui(Shop shop) {
        this(shop, null, null);
    }
    public ShopGui(Shop shop, Player viewer, ExtensionRegistry registry) {
        this.shop = shop; this.viewer = viewer; this.registry = registry;
        this.merchant = Bukkit.createMerchant();
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Trade trade : shop.getTrades()) {
            if (!valid(trade)) continue;
            // 复制配置，窗口中的报价不随外部修改而变化。
            Trade snapshot;
            if (trade.getExtensionId() == null) snapshot = trade.resolve(trade.getResult(), true, true);
            else {
                try {
                    var extension = registry == null ? null : registry.get(trade.getExtensionId());
                    if (extension == null || viewer == null) throw new IllegalArgumentException("拓展未安装");
                    var result = registry.createItem(trade.getExtensionId(), new ExtensionContext(viewer, shop.getId()), trade.getExtensionArguments());
                    snapshot = trade.resolve(result, extension.requiresStock(), true);
                } catch (Exception | LinkageError e) {
                    // 保留原商品位置，但禁止缺失或失败的拓展出售之前保存的预览物品。
                    snapshot = trade.resolve(trade.getResult(), false, false);
                    if (viewer != null) viewer.sendMessage(net.kyori.adventure.text.Component.text("拓展商品不可用：" + trade.getExtensionId(),
                            net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
            baseTrades.add(trade); products.add(snapshot); trades.add(snapshot);
            MerchantRecipe recipe = new MerchantRecipe(snapshot.getResult().clone(),
                    0, Integer.MAX_VALUE, false, 0, 0f, 0, 0, true);
            for (var cost : snapshot.getCosts()) recipe.addIngredient(cost.clone());
            recipes.add(recipe);
        }
        merchant.setRecipes(recipes);
        refreshStock();
    }

    /** 使用次数达到上限时，原生界面显示需要补货的红叉。 */
    public void refreshStock() {
        nextRefreshAt = null;
        var stock = shop.isInfinite() ? List.<org.bukkit.block.Container>of() : Containers.resolveAll(shop.getStockChests());
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = evaluateTrade(i);
            trades.set(i, trade);
            int available = !trade.isAvailable() ? 0 : (shop.isInfinite() || !trade.requiresStock()) ? Integer.MAX_VALUE
                    : Math.max(0, Containers.count(stock, trade.getResult()) / trade.getResult().getAmount());
            MerchantRecipe recipe = merchant.getRecipe(i);
            recipe.setIngredients(trade.getCosts());
            recipe.setUses(0);
            recipe.setMaxUses(available);
            merchant.setRecipe(i, recipe);
        }
    }

    public TradeContext context(int index) {
        Trade base = baseTrades.get(index), product = products.get(index);
        return new TradeContext(viewer, shop.getId(), base.getTradeId(), base.getExtensionArguments(),
                product.getResult(), base.getCosts(), Instant.now());
    }

    /** 每次购买前重新报价；不允许以窗口中的旧价格成交。 */
    public Trade evaluateTrade(int index) {
        if (index < 0 || index >= products.size()) return null;
        Trade product = products.get(index);
        if (product.getExtensionId() == null || !product.isAvailable()) return product;
        try {
            var extension = registry.get(product.getExtensionId());
            if (extension == null) return product.reprice(product.getCosts(), false, "拓展已卸载");
            var quote = extension.quote(context(index));
            Instant retry = quote.refreshAt();
            if (retry != null && (nextRefreshAt == null || retry.isBefore(nextRefreshAt))) nextRefreshAt = retry;
            return product.reprice(quote.costs(), quote.allowed(), quote.reason());
        } catch (Exception | LinkageError e) {
            return product.reprice(product.getCosts(), false, "拓展报价失败");
        }
    }

    public Instant getNextRefreshAt() { return nextRefreshAt; }
    public void onOpened() {
        for (int i = 0; i < products.size(); i++) {
            var product = products.get(i);
            if (product.getExtensionId() != null && product.isAvailable()) {
                var extension = registry.get(product.getExtensionId());
                if (extension != null) {
                    try { extension.onShopOpen(context(i)); }
                    catch (Exception | LinkageError e) {
                        products.set(i, product.reprice(product.getCosts(), false, "拓展打开回调失败"));
                    }
                }
            }
        }
        refreshStock();
    }

    private static boolean valid(Trade trade) {
        return trade != null && Trade.validItem(trade.getCost()) && Trade.validItem(trade.getResult())
                && (trade.getSecondCost() == null || Trade.validItem(trade.getSecondCost()));
    }

    public void open(Player player) {
        player.openInventory(MenuType.MERCHANT.builder()
                .merchant(merchant)
                .title(Msg.mm("<dark_green>商店: <white>" + shop.getDisplayName()))
                .checkReachable(false)
                .build(player));
    }

    public Trade getTrade(int index) {
        return index >= 0 && index < trades.size() ? trades.get(index) : null;
    }

    public Shop getShop() { return shop; }
    public Merchant getMerchant() { return merchant; }
}
