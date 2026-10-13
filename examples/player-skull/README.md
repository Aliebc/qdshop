# player_skull 拓展示例

独立产物为 `player-skull-1.0.0.jar`。复制到 `plugins/qdshop/extensions/`，执行 `/qdshop extension reload` 加载。

用法：`/qdshop addex <id> player_skull [数量]`，数量默认为 1，允许 1-64。打开价格窗口设置一至两种付款，确认后样品退还。
商品按每位打开商店的玩家的 PlayerProfile 生成头颅，因此不会固定成添加商品的管理员的头颅。

## 编译

在仓库根目录执行：

```bash
mvn install
mvn -f examples/player-skull/pom.xml package
```

主插件和 Paper API 都使用 `provided` 依赖，不打进拓展 JAR。

## API

源码目录：`src/main/java/top/mc100/qdshop/extension/`。
API 位于主插件 JAR 中，其他插件使用 `top.mc100:qdshop:1.0.0` 的 provided 依赖。

- `ExtensionItem`：拓展基础类，构造时提供唯一 `id`、`description`、`author`。
- `ExtensionContext`：包含当前打开商店的 `viewer` 和 `shopId`；添加预览时 viewer 为设置者。
- `ExtensionRegistry`：注册、查询、生成物品、扫描及重载 JAR 的接口。
- `QdShopPlugin#getExtensionRegistry()`：其他插件获取注册器的公开接口；依赖插件可用 `depend: [qdshop]` 保证加载顺序。

实现 `public` 拓展类并提供 `public` 无参构造器，继承 `ExtensionItem`：

```java
public MyExtension() {
    super("my_item", "物品描述", "作者");
}

@Override
public ItemStack createItem(ExtensionContext context, List<String> arguments) {
    // 根据 context.viewer() 生成商品，返回一份有效 ItemStack。
}
```

JAR 中必须包含 UTF-8 服务文件 `META-INF/services/top.mc100.qdshop.extension.ExtensionItem`，每行一个实现类完整名称。
本示例内容为：

```text
top.mc100.qdshop.example.PlayerSkullExtension
```

一个 JAR 可以提供多个拓展 ID。注册器拒绝重复 ID，ID 格式为小写字母、数字、下划线和连字符。
`createItem()`、注册和生命周期调用均在服务器主线程进行，返回物品数量须在最大堆叠数范围内。
参数验证可抛出 `IllegalArgumentException`，其消息会显示给设置者。`completeArguments()` 为 addex 参数提供补全。
`requiresStock()` 默认 false，动态供货无需库存；覆盖返回 true 时按生成的实际物品扣除库存。
`onEnable()`、`onDisable()` 为可选生命周期钩子，资源、监听器或任务由实现者在卸载时清理。

其他已经安装的插件也可直接调用 `getExtensionRegistry().register(new MyExtension())` 注册。
通过 API 注册的项目保留至插件关闭；`extension reload` 只卸载和重新加载目录中的 JAR。

## 动态交易 API

`add <id>` 只添加普通主手物品；拓展使用 `addex <id> <extension> [arguments...]`，权限为 `qdshop.command.addex`。

拓展的完整调用流程为：

1. `createItem(ExtensionContext, arguments)`：按当前玩家生成商品，添加时生成设置者预览，打开时生成买家实际物品。
2. `onShopOpen(TradeContext)`：窗口打开后，每个拓展商品调用一次。
3. `quote(TradeContext)`：打开、界面刷新、每次购买前调用。决定是否可交易、付款物品和下一次刷新时间。应无副作用，不在此处扣款、计数或设置冷却。
4. `onTradeCommitted(TradeCompletion)`：成功成交确认后立即调用，库存和收入已结算，原生玩家扣款、出货尚在完成；可立即更新涨价或限购状态。不要在此阶段改背包或关闭窗口。
5. `onTradeCompleted(TradeCompletion)`：原生扣款、出货完成后的下一 tick 调用一次，可读买家最新物品栏并设置冷却。取消或失败不调用这两个成功钩子。

`TradeContext` 提供玩家、商店 ID、持久化商品 UUID、拓展参数、当前商品、基础价格和当前时间。
`baseCosts()` 永远是设置者配置的基础价格，不是上一次动态涨价后的价格。
`TradeCompletion` 提供上述上下文、唯一成交 UUID、实际商品、实际付款、成交时间快照和买家成交位置。
ItemStack、价格列表和位置均为防御性副本，修改返回值不会修改商店配置或已记录的交易。

`TradeQuote.allow(costs)` 允许成交；`TradeQuote.deny(costs, reason, retryAt)` 禁止成交。
报价必须包含一至两份有效付款物品，数量不得超过物品最大堆叠数。
禁止成交显示原生红叉；返回 `refreshAt`/`retryAt` 后插件会自动在该时间刷新，无需买家重开窗口。
每次购买前重新报价，价格变化时取消旧报价请求，再刷新界面；不会静默按新价扣款。
同一玩家同一商品的完成回调执行前暂不允许下一笔成交，避免仅在完成钩子设置的冷却被同 tick 的 Shift 绕过。
重载拓展、重载配置或关闭插件时会先交付已确认的完成回调，再卸载；不会重复调用。

例如在自定义 `ExtensionItem` 类中加入以下成员和方法即可实现“每次成交涨价 + 五秒冷却”：

```java
private final Map<String, Integer> purchases = new HashMap<>();
private final Map<String, Instant> cooldowns = new HashMap<>();

private String key(TradeContext c) {
    return c.player().getUniqueId() + ":" + c.tradeId();
}

@Override
public TradeQuote quote(TradeContext c) {
    List<ItemStack> price = c.baseCosts(); // 可独立修改的价格副本
    int count = purchases.getOrDefault(key(c), 0);
    ItemStack first = price.getFirst();
    first.setAmount((int) Math.min(first.getMaxStackSize(), (long) first.getAmount() + count));
    Instant until = cooldowns.get(key(c));
    if (until != null && c.now().isBefore(until)) {
        return TradeQuote.deny(price, "交易冷却中，请稍后再试。", until);
    }
    return TradeQuote.allow(price);
}

@Override
public void onTradeCommitted(TradeCompletion t) {
    purchases.merge(key(t.context()), 1, Integer::sum);
}

@Override
public void onTradeCompleted(TradeCompletion t) {
    cooldowns.put(key(t.context()), Instant.now().plusSeconds(5));
}
```

此示例按玩家和商品独立计数，状态保存在内存中。若希望服务器重启或拓展重载后保留状态，由拓展使用商品 UUID 自行持久化。
