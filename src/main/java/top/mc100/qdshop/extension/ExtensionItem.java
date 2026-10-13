package top.mc100.qdshop.extension;

import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Objects;

/** JAR 拓展实现此基础类，并提供 public 无参构造器。 */
public abstract class ExtensionItem {
    private final String id, description, author;
    protected ExtensionItem(String id, String description, String author) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("拓展 id 只能包含小写字母、数字、下划线和连字符");
        this.id = id;
        this.description = Objects.requireNonNull(description);
        this.author = Objects.requireNonNull(author);
        if (description.isBlank() || author.isBlank()) throw new IllegalArgumentException("description 和 author 不能为空");
    }
    public final String getId() { return id; }
    public final String getDescription() { return description; }
    public final String getAuthor() { return author; }
    /** 为当前玩家生成实际商品，可用 IllegalArgumentException 报告参数错误。 */
    public abstract ItemStack createItem(ExtensionContext context, List<String> arguments);
    /** 默认是动态生成商品，无需库存；返回 true 则按生成物品的完整数据匹配箱子库存。 */
    /** 商店实际打开后调用，每个拓展商品调用一次。 */
    public void onShopOpen(TradeContext context) {}
    /** 打开、刷新及每次购买前调用。用于价格、冷却和限购；请保持此方法无副作用。 */
    public TradeQuote quote(TradeContext context) { return TradeQuote.allow(context.baseCosts()); }
    /** 成交已确认时立即调用，可更新冷却/涨价状态以阻止同 tick 的 Shift 重复购买；原生扣款尚在完成。 */
    public void onTradeCommitted(TradeCompletion completion) {}
    /** 原生扣款及发货完成后的下一 tick 调用一次，取消或失败不调用。 */
    public void onTradeCompleted(TradeCompletion completion) {}
    public boolean requiresStock() { return false; }
    public List<String> completeArguments(CommandSender sender, List<String> arguments) { return List.of(); }
    public void onEnable() {}
    public void onDisable() {}
}
