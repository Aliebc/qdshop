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
| `/qdshop add <id>` | 主手拿商品，打开箱子界面设置一至两种付款物品 |
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
管理命令节点为 `qdshop.command.<子命令>`，包括 `create`、`destroy`、`remove`、`reload`、`name`、`inventory`、`income`、`owner`、`add`、`infinite`。
帮助节点为 `qdshop.command.help`。帮助和交易默认所有人可用，其余默认 OP；装有 LuckPerms 时可通过节点授权。
箱子权限为 `qdshop.chest.open` 或 `qdshop.shop.<id>.chest.open`。
主人 UUID 保存在商店 `owner` 字段，优先获得本店存货、收入容器的打开和破坏权限，即使 LP 明确拒绝开箱也允许。
主人身份不授予其他管理命令权限。

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
