# CeBotania —— 用 CraftEngine 还原植物魔法

> **⚠️ 早期测试阶段（Early Alpha）**
>
> 这是一个**还在开发中**的还原项目，玩法不完整、接口会变、随时可能出 bug。
> 目前能跑通「神秘花 → 花瓣 → 花药台 → 产魔花 → 发射器 → 魔力池 → 灌注 / 符文祭坛」这条主线，
> 花功能、工具饰品、精灵线等大量内容还没做。**不建议直接用于正式服务器。**

在 **Paper/Leaves 26.3 + CraftEngine 26.9.2** 上复刻 Botania 的核心玩法。
玩家只需要装资源包，**不需要安装任何 mod**。

## 现在能玩什么

| 环节 | 状态 | 说明 |
|---|---|---|
| 16 色神秘花自然生成 | ✅ | 新区块生成时在草方块上撒花（每个区块 0~3 朵） |
| 打花掉花瓣 | ✅ | 神秘花掉 2~3 个同色花瓣；原版小花、矮草、蕨也认 |
| 花药台制花 | ✅ | 水桶注水 → 手持花瓣/种子投入（空手可退回）→ 凑齐配方后花以掉落物弹出台子 |
| 43 条花药台配方 | ✅ | 全部来自上游 datagen，含标签匹配（如 `#botania:petals/lime`） |
| 产魔花 | ✅ | 萤火之焰（烧燃料）、水绣球、热百合、爆爆花、蛋糕花、美食花等 10 种 |
| 魔力发射器 | ✅ | 有朝向，沿朝向直线找目标（魔力池 / 符文祭坛），从附近花吸魔力 |
| 魔力池 | ✅ | 容量 1,000,000，右键看储量 |
| 魔力灌注 | ✅ | 158 条配方：铁→魔力钢、钻石→魔力钻石、珍珠→魔力珍珠……（需催化剂会检查） |
| 符文祭坛 | ✅ | 投料 + 魔力（发射器灌注或法杖点火）→ 出符文，17 条配方 |
| 泰拉钢 / 精灵传送门 | ⚠️ | 数据已提取，交互未实现 |
| 工具 / 护甲 / 饰品 | ⚠️ | 物品已注册可用，功能未实现 |

> ⚠️ = 内容齐全但玩法没做全，见文末「下一步」。

## 管线怎么走

```
神秘花 ──打掉──> 花瓣 ──┐
                       ├─ 花药台(注水) ──> 功能花 ──产魔──> 发射器 ──> 魔力池
原版小花 ──打掉──> 花瓣 ─┘                                          │
                                                                   └─ 灌注 ──> 魔力钢/钻石/珍珠
```

## 构建

需要 JDK 25（`paper-api 26.3` 是 Java 25 字节码）。Gradle Wrapper 已带。

```bash
cd plugin
./gradlew build
# 产物：plugin/build/libs/CE-植物魔法-0.1.0.jar
```

## 部署

```bash
# 1) 资源包放进 CE 的 resources 目录（目录名就是命名空间）
cp -r pack Server/plugins/CraftEngine/resources/botania

# 2) 插件
cp plugin/build/libs/CE-植物魔法-0.1.0.jar Server/plugins/

# 3) 重启服务器（首次必须重启；之后改资源包可以用 /ce reload all）
```

前置条件（CraftEngine 的 `config.yml`）：

```yaml
misc:
  delay-configuration-load: true   # 必须：插件要抢在 CE 载包之前注册方块行为
```

## 服务端设置

默认**不改**世界的时间与天气。本地调试时想要固定正午/晴天，可以在 `config.yml` 里打开：

```yaml
world:
  always-day: false     # 改成 true：关掉昼夜循环，时间钉在 6000
  always-clear: false   # 改成 true：关掉天气循环
```

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
│   └── rcon.py                给服务器发命令（测试用）
└── docs/                      设计与排错笔记
```

## 改内容的工作流

内容全部由脚本从上游生成，**不要手改生成出来的 yml**：

```bash
python3 tools/extract_assets.py /path/to/Botania-src   # 换材质/模型后
python3 tools/extract_recipes.py                       # 换配方后
python3 tools/extract_flowers.py                       # 换花参数后
python3 tools/gen_content.py                           # 重新生成物品/方块/分类
python3 tools/gen_core_blocks.py                       # 重新生成核心方块
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
（在透明载体的方块上尤其明显）。所以不用它，改成 `TickBus`：控制器在 `onLoad` 里登记、
`onUnload`/`onRemove` 里注销，一个 Bukkit 任务逐刻遍历。和 ce-mekanism 的做法一致。

### 3. 一个坑：`blockEntity().world` 可能是 null

方块实体被替换/重载的瞬间，`BlockEntity.world` 会短暂为 null，直接解引用会让 tick 永远
跳过这个方块（表现为"魔力一直是 0"）。所以 `ManaBlockController` 在构造期和 `onLoad`
时缓存一份世界与坐标。

### 4. 花瓣标签匹配

上游配方大量用标签（`#botania:petals/lime`），插件在启动时读 `data/tags.json` 把标签
展开成扁平集合，再按多重集合匹配配方（同色花瓣第二次出现要能吃掉第二个）。

## 已知限制

- **装饰方块（台阶、墙、栅栏）目前用音符盒载体**：碰撞是满块，和原版形状不一致。
  要修就得给它们接 CE 的 `stairs_block` / `slab_block` / `fence_block` / `wall_block` 行为。
- 花药台目前只认**手持投入**，还不认"把物品丢在地上"。
- 魔力发射器没有棱镜，也没有精灵/盖亚/红石发射器的差异化能力。
- 花的功能花（农业花、治疗花等）只在参数表里，效果逻辑还没实现。
- 神秘花只在新生成的区块出现，老世界不会补。

## 下一步

按优先级：

1. **符文祭坛**：方块行为 + 投料 + 注魔（17 条配方已就绪）
2. **泰拉凝聚板**：泰拉钢合成线
3. **装饰方块形状**：接 CE 的 stairs/slab/fence/wall 行为
4. **功能花**：农业花、治疗花、骨粉花先做，验证"消耗魔力"这条链路
5. **精灵传送门**：活木/梦之木 → 精灵交易

## 许可

上游 Botania 为 **Botania License**（见 `ALTERNATE_LICENSES.txt` / 上游 `LICENSE.txt`），
本项目使用的材质与模型版权归 Botania 作者 Vazkii 及贡献者所有。
