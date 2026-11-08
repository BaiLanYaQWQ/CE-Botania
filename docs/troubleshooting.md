# 排错笔记

## 魔力一直是 0

按这个顺序查：

1. **看自检读数**。启动日志里的 `[自检 15s]` 那行：
   - `花=0(tick 0)` → 方块实体没被调度，见下一条
   - `花=0(tick 48)` → 花在跑，但没吃到燃料 / 环境不满足
   - `发射器=600 池子=600` → 管线通了，只是花产得慢（萤火之焰 1 mana/tick）

2. **方块实体没被调度**（tick 一直是 0）：
   - 确认 `config.yml` 里 `misc.delay-configuration-load: true`
   - 确认插件加载顺序：`[CeBotania] 植物配方 218 条` 必须在 `[CraftEngine] 已加载blocks` 之前
   - 看日志有没有 `方块实体 tick 失败，已从调度里摘除`

3. **花不产魔**：
   - 萤火之焰要吃**掉落物**（丢煤在它旁边，不是放在手上右键）
   - 水绣球要贴着**水**，热百合要贴着**岩浆**
   - 右键花看它当前攒了多少

## 玩家能看到方块，但 `/ce` 菜单里没有

CE 的物品浏览器**只显示登记在 `categories.yml` 里的物品**。加内容后要重跑
`python3 tools/gen_content.py`（它会重新生成分类文件），然后 `/ce reload all`。

## 方块外观对，但碰撞/遮挡不对

三种载体对应三种物理：

| 载体 | 碰撞 | 适用 |
|---|---|---|
| `auto_state: solid`（音符盒） | 满块，会剔除下方方块顶面 | 只有整块模型能用 |
| 甘蔗（共享 `transparent`） | 0，能穿过 | 花（十字模型） |
| 台阶（每个方块一个状态） | 半格，能站 | 池子、发射器、花药台 |

判断某个方块该用哪种：`tools/gen_content.py` 里的 `geometry_of()` 会解析模型
（满 16×16×16 → full；高度 < 16 → partial；cross → cross）。

**改完一定要重新生成**，手改 yml 会在下次生成时被覆盖。

## 重启后魔力数字变了 / 方块实体行为异常

同一位置反复 `place` / `remove` 时，CE 可能留下旧的方块实体在调度池里。
自检脚本已经用 `CraftEngineBlocks.remove()` 清场（`setType(AIR)` 不触发 `onRemove`）。
如果你自己写测试脚本，注意这一点。

## 花药台投了料没反应

- 台子必须**有水**（手持水桶右键）
- 配方要求**先有种子**（`#botania:seed_apothecary_reagent`，小麦/甜菜/西瓜/南瓜种子都算）
- 投料数不对会一直显示"已投入 N 样"，没有产出 —— 用
  `pack/configuration/recipes/petal_apothecary.json` 对一下数量

## 资源包相关

- 材质/模型改了之后要重跑 `tools/extract_assets.py` + `tools/gen_content.py`，
  再把 `pack/` 重新拷到 `plugins/CraftEngine/resources/botania/`
- 首次装载必须**重启服务器**（CE 的行为注册发生在插件 enable 时，早于 `/ce reload`）
- 客户端需要接受服务器下发的资源包，否则看到的是原版音符盒/台阶

## 待修清单

- [ ] 装饰方块（台阶/墙/栅栏）还是满块碰撞
- [ ] 花药台不认"丢在地上的物品"
- [ ] 发射器没有朝向与棱镜
- [ ] 功能花（农业花等）只有参数没逻辑
- [ ] 老世界不补神秘花
