package top.mc100.qdshop.gui;

import top.mc100.qdshop.model.Shop;
import top.mc100.qdshop.model.Trade;
import top.mc100.qdshop.util.Msg;
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
            Trade snapshot = new Trade(trade.getCost().clone(), trade.getResult().clone());
            trades.add(snapshot);
            MerchantRecipe recipe = new MerchantRecipe(snapshot.getResult().clone(),
                    0, Integer.MAX_VALUE, false, 0, 0f, 0, 0, true);
            recipe.addIngredient(snapshot.getCost().clone());
            recipes.add(recipe);
        }
        merchant.setRecipes(recipes);
    }

    private static boolean valid(Trade trade) {
        return trade != null && trade.getCost() != null && trade.getResult() != null
                && !trade.getCost().getType().isAir() && !trade.getResult().getType().isAir()
                && trade.getCost().getAmount() > 0 && trade.getResult().getAmount() > 0
                && trade.getCost().getAmount() <= trade.getCost().getMaxStackSize()
                && trade.getResult().getAmount() <= trade.getResult().getMaxStackSize();
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
