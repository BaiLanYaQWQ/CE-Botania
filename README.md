# CeBotania —— 用 CraftEngine 还原植物魔法

> **⚠️ 早期测试阶段（Early Alpha）**
>
> 这是一个**还在开发中**的还原项目，玩法不完整、接口会变、随时可能出 bug。
> 目前可从神秘花一路玩到魔力灌注、符文祭坛、泰拉钢和精灵交易。
> 盖亚守护者及部分工具、饰品和装饰交互仍待补齐。**不建议直接用于正式服务器。**

**语言 / Language** · 中文（本页） | [English](README_EN.md)

📌 **已发布更新日志**：[2026-11-11 更新日志](docs/更新日志-2026-11-11.md) · [English changelog](docs/CHANGELOG-2026-11-11-EN.md)

📚 往期：[2026-11-10](docs/更新日志-2026-11-10.md) · [2026-11-09](docs/更新日志-2026-11-09.md)

---

## 关于 AI 参与制作

本项目**大量使用 AI 辅助开发**：插件代码、CraftEngine 配置生成器、素材与配方提取脚本、
中英文文档均由 AI 起草，再由人工在测试服调试与验证。

上游素材（材质、模型、配方数值）版权归 **Botania 作者 Vazkii 及贡献者**所有，
本项目只做 Forge → Paper 插件 的平台移植，不主张对原素材的任何权利。

## 现在能玩到哪

> 从一朵野花玩到符文、魔钢装备、泰拉钢和精灵交易。

| 环节 | 状态 | 说明 |
|---|---|---|
| 神秘花世界生成 | ✅ | 16 色，在新生成区块的草方块上自然出现 |
| 打花掉花瓣 | ✅ | 2~3 个同色花瓣；剪刀可采集整朵花 |
| 花药台制花 | ✅ | 注水 → 投料 → 产物掉落；**44 条配方**，一种花药台可做全部花 |
| 产魔花 | ✅ | **10 种**，火红莲改为持续燃烧（一块煤 40 秒产 800 魔力） |
| 功能花 | ✅ | **20 种**，消耗与间隔对齐上游（农业/矿石/漏斗/播种/大理石…） |
| 魔力发射器 | ✅ | 六向放置、16 格探测、接力传输、可贴池抽取，四种变体有各自的魔力参数 |
| 魔力池 | ✅ | 空/满两种外观、可悬空、可被发射器抽取 |
| 魔力灌注 | ✅ | **158 条**：铁→魔钢、钻石→魔力钻石、珍珠→魔力珍珠… |
| 符文祭坛 | ✅ | **17 条**配方，投料、注魔、放活石，再用森林法杖完成 |
| 纯洁雏菊 | ✅ | 3×3×1 区域，40 tick 延迟转换（石头→活石、原木→活木…） |
| 魔钢工具护甲 | ✅ | 数值对齐上游，消耗魔力顶耐久 |
| 森林法杖 | ✅ | 查看魔力、显示功能说明、绑定花、旋转发射器 |
| **植物魔法辞典** | ✅ | **990+ 页全中文**，含配方、摆法、功能说明与实现状态标注 |
| 活根 / 红石根 | ✅ | 空手潜行右键泥土「翻土」得活根；红石 + 蕨/草合成红石根 |
| **泰拉钢线** | ✅ | 凝聚板多结构 + 三件泰拉工具（3×3 挖掘 / 连锁砍树 / 剑气） |
| **精灵传送门** | ✅ | 11 块活木框架（双朝向）+ 开关状态 + 14 条精灵交易 |
| **火花系统** | ✅ | 装在池子/祭坛/凝聚板上，16 格圆形接力，只在实际搬运时出粒子 |
| 装饰方块碰撞 | ⚠️ | 台阶/墙/栅栏仍是满格碰撞 |

**分发包内容**：610 个方块配置、1,356 个物品配置（含渲染用物品）、611 条 CraftEngine 配方。插件另加载 61 组标签。

**还没做**：盖亚守护者（Boss 战）、装饰方块的精确碰撞、精灵实体（交易目前直接交换）。


## 管线怎么走

```
神秘花 ──打掉──> 花瓣 ──> 花药台(注水) ──> 功能花/产魔花
                                              │
                                              产魔
                                              ↓
                                          魔力发射器 ──> 魔力池
                                                          │
                              ┌───────────────────────────┤
                              ↓                           ↓
                        魔力灌注                   火花(16格圆形接力)
                    魔力钢/钻石/珍珠                      ↓
                              │                    符文祭坛 ──> 符文
                              ↓                           │
                        泰拉凝聚板 ──> 泰拉钢锭        泰拉钢工具/装备
                              │
                              ↓
                        精灵传送门 ──> 源质钢/梦之木/精灵玻璃
```

> **注意**：只有 **神秘花 / 微光花 / 高花 / 蘑菇** 会掉花瓣，**原版花不掉**。

## 构建与分发

构建需要 JDK 25。服务端使用 Leaves 26.3 或兼容的 Paper 26.3 服务端，搭配 CraftEngine 26.9.2。Minecraft 26.3 客户端接受服务端资源包即可。

在仓库根目录运行：

```bash
(cd plugin && ./gradlew build)
python3 -c 'from shutil import make_archive; make_archive("plugin/build/libs/CE-Botania-pack", "zip", "pack")'
```

分发同一次构建的 `plugin/build/libs/CE-植物魔法-0.1.0.jar` 和 `plugin/build/libs/CE-Botania-pack.zip`。本地分发总包 `dist/ce-botania-latest/CE-Botania-0.1.0.zip` 内含部署说明、插件 JAR 和资源包 ZIP。`dist/` 中的 Alpha 资源包是历史版本。

## 部署

1. 停服，将新插件 JAR 放到服务端的 `plugins/`，并移走旧版 CeBotania JAR。
2. 备份并替换 `plugins/CraftEngine/resources/botania/`。将资源包 ZIP 解压到该目录，或复制源码 `pack/` 内的文件。目标目录应直接包含 `pack.yml`、`configuration/`、`resourcepack/` 和 `blueprint/`。
3. 编辑 CraftEngine 的 `config.yml`，使插件先注册方块行为，再由 CraftEngine 加载资源。

   ```yaml
   misc:
     delay-configuration-load: true
   ```

4. 更新资源包后清理 `plugins/CraftEngine/cache/` 下的旧资源缓存（`*.json` 和 `*.bin`），再启动服务端并让客户端接受新资源包。仅修改资源时可用 `/ce reload all` 重新加载，更新插件 JAR 或方块行为需要重启。

## 验收

使用已初始化的 Leaves 26.3、CraftEngine 26.9.2 测试服作为模板，从仓库根目录运行：

```bash
python3 tools/acceptance/run.py --server-template /path/to/Server
```

脚本构建插件和独立探针，用当前资源包启动隔离副本，覆盖双朝向精灵门交易、防止门外物品被交易、新区块神秘花生成、火花跨区块边界及卸载重载续传。运行日志和生产插件 SHA-256 保存到 `plugin/build/acceptance/run-*/`。2026-11-27 的 Leaves 隔离服实测通过这些场景。详见 [测试服验收](tools/acceptance/README.md)。

提交 `89df10b` 的本地分发包还通过了玩家操作验收。Leaves 测试玩家用 `/botania give livingwood 3` 收到 3 个活木，无权限时背包保持为空。Minecraft 26.3 协议客户端收到了子命令和物品 ID 的补全结果，并手持命令给予的森林法杖右键打开、关闭 X 和 Z 两种朝向的精灵门。每次开启消耗 200,000 魔力并生成 9 个门面，关闭后 9 格全部恢复为空气。

## 命令

```
/botania selfcheck            自检：CE 物品/方块数、配方数、关键方块是否就位
/botania give <id> [数量]      给自己物品，如 /botania give white_petal 16
/botania demo <x> <y> <z>      在指定位置摆一套最小产线（花药台+花+发射器+池子）
/botania inspect <x> <y> <z>   读那个位置的魔力数字
/botania flower               列出内置花型与产魔参数
/botania recipe <物品id>       查灌注配方
```

`/ce` 的物品浏览器里，本包的内容分成 **花与植物 / 工具与饰品 / 材料与符文 / 方块 / 其他** 五个分类
（分类文件是 `pack/configuration/categories.yml`，由生成脚本维护）。

## 工程结构

```
Botania/
├── plugin/                     插件（Java 25）
│   └── src/main/java/net/bailanya/cebotania/
│       ├── CeBotaniaPlugin.java      接线：行为注册、调度、事件、命令
│       ├── core/TickBus.java         自管的逐刻调度（见下文「一个坑」）
│       ├── block/                    方块行为 + 方块实体
│       ├── flower/                   花的参数表与产魔判定
│       ├── recipe/                   配方加载与匹配
│       ├── listener/                 掉花瓣、撒神秘花
│       └── platform/                 物品与消息适配
├── pack/                      CraftEngine 资源包
│   ├── pack.yml               命名空间 botania
│   ├── configuration/
│   │   ├── items/             物品（含"只用于实体渲染"的外观物品）
│   │   ├── blocks/            方块（生成的 + 手写的核心方块）
│   │   ├── categories.yml     物品浏览分类
│   │   └── langs/             中英语言
│   └── resourcepack/assets/botania/    材质 + 模型（直接从上游提取）
├── tools/                     生成脚本（都可重复运行）
│   ├── extract_assets.py      从上游源码提取材质/模型
│   ├── extract_recipes.py     提取上游配方数据
│   ├── extract_flowers.py     提取花的参数
│   ├── gen_content.py         生成物品/方块/分类/语言
│   ├── gen_core_blocks.py     生成核心玩法方块
│   ├── gen_plugin_data.py     配方与标签归一化进插件
│   ├── acceptance/           隔离测试服验收
│   └── rcon.py                给服务器发命令（测试用）
└── docs/                      设计与排错笔记
```

## 改内容的工作流

物品、方块、分类、语言及精灵门的 X 向模型可用仓库内的资源离线重建。使用 Python 3.10 或更新版本，在干净检出中运行以下命令：

```bash
python3 tools/gen_content.py
python3 tools/gen_core_blocks.py
```

给命令加 `--check` 可逐字节核对生成结果，发现缺失或差异时返回非零状态。语言快照在 `tools/content_lang/`。更新上游素材、配方或名称时，再运行对应的提取脚本：

```bash
python3 tools/extract_assets.py /path/to/Botania-src   # 换材质/模型后
python3 tools/fetch_lang.py --update-content           # 更新名称快照
python3 tools/extract_recipes.py                       # 换配方后
python3 tools/extract_flowers.py                       # 换花参数后
python3 tools/gen_plugin_data.py                       # 配方进插件 jar
```

要加**新的核心玩法方块**（带 behavior 的）：改 `tools/gen_core_blocks.py` 的 `CORE` 表 +
`pack/configuration/blocks/_core_overrides.txt`，别直接写 yml。

## 设计上的几个决定（都踩过坑）

### 1. 为什么花用「透明载体 + 实体渲染」

CraftEngine 的自定义方块要挂在一个**原版方块状态**上。一开始所有方块都用
`auto_state: solid`（音符盒），结果：

- 音符盒是满方块，客户端会**按整块剔除它下方方块的顶面** —— 花只占一格中间一小块，
  但它底下的泥土顶面会消失，看起来像天花板漏了；
- 花也因此变成**实心碰撞**，走不过去。

现在的做法按形状分两类：

| 形状 | 载体 | 碰撞 | 外观 |
|---|---|---|---|
| 整块（石头、木头、砖） | 音符盒（`auto_state: solid`） | 满块 | 方块模型 |
| 花（十字模型） | 甘蔗（共享 `transparent` 状态） | **0，可以穿过去** | 实体渲染 |
| 半高机器（池子、发射器、花药台） | 各自一个台阶状态 | 半格，能站上去 | 方块模型 |

台阶载体是给每个核心方块单独分配的（`tools/gen_core_blocks.py` 里的 `SLAB_CARRIERS`），
不用共享 —— 共享视觉状态会让 CE 在替换方块时认不出旧实体，见第 3 条。

### 2. 一个坑：CE 的 `createBlockEntityTicker` 只跑一次

实测 `BlockEntityController#createBlockEntityTicker` 返回的 ticker 只被调用一次就再也不来
（在透明载体的方块上尤其明显）。所以不用它，改成 `TickBus`：控制器在构造和 `onLoad` 时登记，
在 `onUnload` 或 `onRemove` 时注销。Bukkit 主线程任务每刻遍历全部已登记控制器。

### 3. 一个坑：`blockEntity().world` 可能是 null

方块实体被替换/重载的瞬间，`BlockEntity.world` 会短暂为 null，直接解引用会让 tick 永远
跳过这个方块（表现为"魔力一直是 0"）。所以 `ManaBlockController` 在构造期和 `onLoad`
时缓存一份世界与坐标。

### 4. 花瓣标签匹配

上游配方大量用标签（`#botania:petals/lime`），插件在启动时读 `data/tags.json` 把标签
展开成扁平集合，再按多重集合匹配配方（同色花瓣第二次出现要能吃掉第二个）。

## 已知限制

- 装饰台阶、墙和栅栏的碰撞仍是满格。
- 花药台需要手持投入材料。
- 魔力发射器尚未支持棱镜。
- 神秘花在新生成区块自然出现。现有区块保留原来的花卉分布。
- 盖亚守护者与精灵实体尚未实现，精灵交易直接交换物品。

## 许可

上游 Botania 为 **Botania License**（见 `ALTERNATE_LICENSES.txt` / 上游 `LICENSE.txt`），
本项目使用的材质与模型版权归 Botania 作者 Vazkii 及贡献者所有。
