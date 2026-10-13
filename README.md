# QIDIAN SHOP

作者：AWAFAI, QIDIAN SERVER。Java 包名为 `top.mc100.qdshop`，主命令为 `/qdshop`。
运行环境：Paper 26.2、Java 25。

## 构建及安装

执行 `mvn clean package`，将 `target/qdshop-1.0.0.jar` 放入服务器的 `plugins` 目录。
SQLite 驱动及其原生库已包含在插件 JAR 中，不需要额外安装数据库服务或 sqlite3 命令行程序。
当前 `plugin.yml` 的技术名称为 `qdshop`，数据目录为 `plugins/qdshop/`。
其中 `config.yml` 保存插件设置，`shops.yml` 保存商店，`transactions.db` 保存交易记录。

## 命令

| 命令 | 功能 |
| --- | --- |
| `/qdshop version` | 显示插件版本，所有人均可使用 |
| `/qdshop help` | 显示帮助，直接输入 `/qdshop` 也显示帮助 |
| `/qdshop create <id>` | 创建商店，默认显示名称为 id |
| `/qdshop destroy <id>` | 删除整个商店 |
| `/qdshop remove <id> <number>` | 删除第 number 个商品，编号从 1 开始，对应交易列表顺序 |
| `/qdshop open <id>` | 打开原生村民交易界面 |
| `/qdshop name <id> <name>` | 设置显示名称，支持空格和 MiniMessage |
| `/qdshop inventory <id>` | 设置准星对准的容器为存货箱，目标需在 6 格内 |
| `/qdshop inventory <id> <x> <y> <z> <world>` | 按整数坐标和已加载世界设置存货箱 |
| `/qdshop income <id>` | 设置准星对准的容器为收入箱 |
| `/qdshop income <id> <x> <y> <z> <world>` | 按坐标设置收入箱 |
| `/qdshop owner <id> <player>` | 设置商店主人，支持在线或本服已记录的离线玩家 |
| `/qdshop add <id>` | 添加主手普通商品，设置一至两种付款 |
| `/qdshop addex <id> <extension> [<arguments>...]` | 按拓展 ID 和参数生成商品预览，再设置一至两种付款 |
| `/qdshop extension list` | 列出已加载拓展，绿色为成功，红色为加载失败的 JAR，悬停查看描述和作者 |
| `/qdshop extension reload` | 关闭商店及添加窗口，重新扫描 `extensions` 中的 JAR 并加载 |
| `/qdshop infinite <id> true\|false` | 设置指定商店无限供货，默认 false |
| `/qdshop reload` | 重载 `config.yml` 和 `shops.yml`，关闭商店及添加窗口并退还样品，保留数据库历史 |

所有命令都有参数补全。`remove` 补全有效商品编号，容器命令补全坐标和世界，`owner` 补全玩家名称。
商店 id 不区分大小写，允许文字、数字、下划线和连字符。
两种容器设置命令均替换对应的旧绑定，支持木箱、铜箱、木桶等 Bukkit 存储容器。
无容器、世界不存在、坐标无效或容器属于其他商店时会报错并保留旧绑定。
打开、准星绑定和添加商品由玩家执行；其余命令也支持控制台。
删除商品后剩余商品按当前顺序重新编号，已有交易窗口会关闭。

## 商品设置和交易

主手物品作为出售样品复制，不消耗主手物品。
添加窗口中的两个空槽（内部槽号 12、14）用于付款样品，至少填一个，也可以只填第二槽。
确认后复制两份付款配置，并主动退还两个槽中的实物；取消、关闭、退出和重载也会退还。
先清空样品槽再退还，避免确认任务与关闭事件重复退还。背包满时，退还物品掉在设置者脚下。
实际出售库存需另行放入存货容器。

顾客使用原生村民交易窗口，选择商品，在一至两个输入槽付款并领取输出物品，支持 Shift 连续交易。
打开商店和每次成交后均检测库存，按库存计算每种商品还能交易多少份。
不足一份的项目显示原生村民交易的补货红叉；成交后所有正在浏览本店的窗口一起更新，保持原窗口和付款槽。
补充库存后重新打开商店，或本店其他商品完成交易时，会重新检测并恢复有货的项目。
每次成交仍实时检查两个付款槽及箱子库存；付款不足、缺货或交易被取消时不成交。
取消事件会退回预留库存，也不会写交易记录。玩家扣款及出货由原生界面处理，插件只处理库存和收入。
没有收入箱时付款进入存货箱；收入容器放不下的物品掉在接收容器旁。
商店数据中的 `infinite: true` 启用无限供货，不需要存货箱，也不读取或扣除库存；默认 `infinite: false`。
无限供货仍需要足额付款，仍记录交易和收入。有收入箱时正常存入收入箱；没有收入箱但绑定了存货箱时存入存货箱。
没有任何接收容器时，付款由原生交易消耗，收入保存在数据库记录中。
`/qdshop infinite <id> true|false` 支持补全并保存配置，切换后会更新本店已打开的交易窗口。
单笔付款和出售数量均不能超过物品最大堆叠数。商品配置使用 `costs` 列表，长度为 1 或 2；`result` 为出售物品。

## 权限

总权限为 `qdshop.*`，管理员权限为 `qdshop.admin`，交易权限为 `qdshop.use`。
管理命令节点为 `qdshop.command.<子命令>`，包括 `create`、`destroy`、`remove`、`reload`、`name`、`inventory`、`income`、`owner`、`add`、`addex`、`infinite`。
帮助节点为 `qdshop.command.help`。帮助和交易默认所有人可用，其余默认 OP；装有 LuckPerms 时可通过节点授权。
箱子权限为 `qdshop.chest.open` 或 `qdshop.shop.<id>.chest.open`。
主人 UUID 保存在商店 `owner` 字段，优先获得本店存货、收入容器的打开和破坏权限，即使 LP 明确拒绝开箱也允许。
主人自动拥有本店 `add`、`remove`、`inventory`、`income` 权限，主人身份优先于 LP 拒绝；不能据此管理其他商店。
`addex`、`destroy`、`owner`、`name`、`infinite` 等操作仍需相应 LP/管理员权限。

## SQLite 交易记录

`transactions.db` 的 `transactions` 表每次成交记录一行，Shift 连续购买的每份成交也分别记录。
字段包括：

- `occurred_at`：UTC ISO 时间；`occurred_at_ms`：Unix 毫秒时间。
- `world`、`x`、`y`、`z`：买家成交时的位置。
- `shop_id`、`buyer_uuid`：商店 ID 和买家 UUID。
- `item_type`、`item_amount`、`item_data`：出售物品的类型、数量及完整序列化数据。
- `income1_type`、`income1_amount`、`income1_data`：第一份收入。
- `income2_type`、`income2_amount`、`income2_data`：第二份收入，一种付款时为 NULL。

`*_data` 是 Bukkit ItemStack 序列化的二进制数据，保留物品名称、附魔及其他组件。
日志在独立线程按顺序写入，插件正常关闭时等待队列写完并关闭数据库。
数据库采用 WAL 模式，运行时可能存在同目录的 `transactions.db-wal`、`transactions.db-shm` 文件。
重载、删除商品和删除商店不会清除交易历史。

例如，用 sqlite3 查看最近 20 笔交易，时间显示为北京时间：

```sql
SELECT datetime(occurred_at_ms / 1000, 'unixepoch', '+8 hours') AS time_cn,
       shop_id, buyer_uuid, world, x, y, z,
       item_type, item_amount, income1_type, income1_amount,
       income2_type, income2_amount
FROM transactions ORDER BY id DESC LIMIT 20;
```


## 拓展物品

插件启动时创建并扫描 `plugins/qdshop/extensions/`。
将独立示例 `player-skull-1.0.0.jar` 放入此目录，执行 `/qdshop extension reload`，再用 `/qdshop extension list` 检查。
示例不内置在主插件中，也不是 Paper 插件，不要直接放入服务器 `plugins/` 根目录。

```text
/qdshop addex example player_skull
/qdshop addex example player_skull 2
```

以上命令无需主手物品，会打开价格设置窗口。可选参数为头颅数量，范围 1-64。
每位玩家打开商店时重新生成该玩家自己的头颅，使用其当前 PlayerProfile 皮肤。
拓展默认动态生成供货，不需要库存，也不要求整个商店启用 infinite；付款、收入与 SQLite 记录仍正常处理。
如果拓展显式要求库存，则按生成物品的完整数据匹配库存。
拓展缺失或生成失败时保留商品位置并显示不可交易状态，不出售保存的预览物品。
商品配置额外保存 `extension` ID 和 `arguments` 字符串列表。

拓展重载会执行旧拓展的 `onDisable()`，关闭旧 JAR 类加载器，然后重新扫描。
重复 ID 或损坏 JAR 不会阻止其他有效 JAR 加载；API 直接注册的拓展会保留。
权限为 `qdshop.command.extension.list` 和 `qdshop.command.extension.reload`，默认 OP，均包含在 `qdshop.admin` 中。
API 和示例开发说明见 [examples/player-skull/README.md](examples/player-skull/README.md)。

拓展还可通过 `quote(TradeContext)` 动态决定是否允许成交及实际两格价格，返回恢复时间后会自动刷新冷却状态。
`onShopOpen` 收到打开界面的玩家；`onTradeCommitted` 可立即更新价格/限购；`onTradeCompleted` 在原生结算结束后收到实际商品、实际价格与买家信息。
商品 UUID 持久化到 `shops.yml` 的商品 `id` 字段，便于拓展稳定识别商品。冷却和涨价实现示例见拓展开发说明。
