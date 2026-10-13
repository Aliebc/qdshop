package top.mc100.qdshop.gui;

import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
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

    public ShopGui(Shop shop) {
        this.shop = shop;
        this.merchant = Bukkit.createMerchant();
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Trade trade : shop.getTrades()) {
            if (!valid(trade)) continue;
            // 复制配置，窗口中的报价不随外部修改而变化。
            Trade snapshot = new Trade(trade.getCost().clone(),
                    trade.getSecondCost() == null ? null : trade.getSecondCost().clone(), trade.getResult().clone());
            trades.add(snapshot);
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
        var stock = shop.isInfinite() ? List.<org.bukkit.block.Container>of() : Containers.resolveAll(shop.getStockChests());
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            int available = shop.isInfinite() ? Integer.MAX_VALUE
                    : Math.max(0, Containers.count(stock, trade.getResult()) / trade.getResult().getAmount());
            MerchantRecipe recipe = merchant.getRecipe(i);
            recipe.setUses(0);
            recipe.setMaxUses(available);
            merchant.setRecipe(i, recipe);
        }
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
