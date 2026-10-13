# QIDIAN SHOP

作者：AWAFAI, QIDIAN SERVER。

技术标识和 Java 包名为 `top.mc100.qdshop`，游戏内主命令为 `/qdshop`。
运行环境沿用原工程：Paper 26.2、Java 25。

## 构建及安装

执行 `mvn clean package`，将 `target/qdshop-1.0.0.jar` 放入服务器的 `plugins` 目录。
首次启动后生成 `plugins/top.mc100.qdshop/config.yml`，创建商店后生成同目录的 `shops.yml`。

## 命令

- `/qdshop create <id>`：创建商店，显示名称默认为 id。id 不区分大小写，允许文字、数字、下划线和连字符。
- `/qdshop remove <id>`：删除商店并关闭正在浏览此商店的窗口。
- `/qdshop open <id>`：由玩家打开商店，空商店也可以打开。
- `/qdshop name <id> <name>`：设置窗口显示名称，支持空格及 MiniMessage；再次打开时显示新名称。
- `/qdshop help`：显示帮助菜单；直接输入 `/qdshop` 也显示帮助。
- `/qdshop inventory <id>`：将准星对准的存储容器设置为存货箱，目标需在 6 格内。
- `/qdshop inventory <id> <x> <y> <z> <world>`：按整数坐标和已加载世界设置存货箱。
- `/qdshop income <id>` 或 `/qdshop income <id> <x> <y> <z> <world>`：以相同方式设置收入箱。
- `/qdshop owner <id> <player>`：设置商店主人，支持在线或本服已记录的离线玩家；未知玩家需先登录一次。
- `/qdshop add <id>`：主手拿商品，打开 27 格箱子界面，将付款样品放入中间槽并确认上架。

所有命令均提供参数补全，包括商店 ID、坐标、世界和主人玩家名称。
两种容器设置命令均替换对应的旧绑定，支持木箱、铜箱、木桶等 Bukkit 存储容器。
位置无容器、世界不存在、坐标无效或容器属于其他商店时会报错并保留旧绑定。
商品添加界面中，主手商品作为样品复制，付款样品在确认、取消或关闭时退还；实际商品需另行放进存货容器。

管理命令默认需要 OP；装有 LuckPerms 时可通过节点授权。打开和交易默认允许所有玩家。
准星绑定、打开商店和添加商品需要玩家执行；坐标绑定及其他管理命令支持控制台。
使用 LuckPerms 时，可授权 `qdshop.admin` 或 `qdshop.command.create`、
`qdshop.command.remove`、`qdshop.command.name`；交易权限为 `qdshop.use`。
新增管理命令的节点为 `qdshop.command.inventory`、`qdshop.command.income`、`qdshop.command.owner`、`qdshop.command.add`。
帮助节点为 `qdshop.command.help`，默认允许所有人；总权限为 `qdshop.*`。
箱子权限为 `qdshop.chest.open` 或 `qdshop.shop.<id>.chest.open`。
商店主人优先获得本店存货箱、收入箱的打开和破坏权限，即使 LP 明确拒绝开箱节点也允许；这不授予其他管理命令权限。
主人 UUID 保存在 `shops.yml` 的 `owner` 字段，重启后继续生效。
旧版 `top.mc100.qdshop.*` 权限节点已替换，需在 LP 中重新授权 `qdshop.*` 下的相应节点。

## 商店数据

可使用上述命令配置商店，也可手动修改 `shops.yml`。手动修改前请停止服务器，修改后启动加载。
创建的空商店数据如下：

```yaml
shops:
  example:
    display: example
    stock: []
    income: []
    trades: []
```

箱子坐标格式为 `世界名称;x;y;z`，例如 `world;100;64;200`。
`stock` 是取货箱列表，`income` 是收款箱列表；未设置收款箱时付款进入取货箱。
`trades` 每项包含 Bukkit 序列化的 `cost` 和 `result` ItemStack，分别表示付款物品和出售物品。
交易仍然是物换物，库存从实体箱子扣除。界面使用原生村民交易窗口，左侧选择商品，在输入槽付款并领取输出物品，支持 Shift 连续交易。
不生成村民实体。每次成交实时校验库存，缺货时取消成交；不会重复扣取玩家背包或额外发货。
商品数量不再受箱子界面的 45 格限制。单笔付款和出货数量均应不超过该物品的最大堆叠数，超出或无效的配置不会显示。

升级时，请从服务器移除旧插件 JAR。新版本使用独立的数据目录，不自动迁移旧数据。
若需沿用旧价格表和箱子配置，将原数据中的商店记录放到 `shops.yml` 的 `shops` 节点下，
仅保留 `display`、`stock`、`income`、`trades` 字段；本版本新增 `owner` 字段用于保存主人 UUID。旧版已经生成的实体需要在服务器中另行清理。
