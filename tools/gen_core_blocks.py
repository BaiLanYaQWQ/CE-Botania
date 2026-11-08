#!/usr/bin/env python3
"""生成核心玩法方块的配置（带 behavior 的那批）。

用法：python3 tools/gen_core_blocks.py
产物：pack/configuration/blocks/core_blocks.yml

## 载体怎么选

CraftEngine 的自定义方块必须挂在一个**原版方块状态**上，这个状态决定了三件事：
碰撞箱、它会不会剔除相邻方块的贴面、以及最多能有多少个自定义方块。

我们的核心方块（魔力池、发射器、花药台）模型都是**半高**的，如果挂在满块载体上，
客户端会按整块剔除它下方方块的顶面 —— 看起来就是"底下有个洞"。

所以要找"整格碰撞 + 不遮挡相邻面"的方块：**玻璃类**。
Minecraft 里玻璃的碰撞是整格，同时 canOcclude=false，两个条件都满足。

可按这种用法的载体只有 20 个左右：glass、tinted_glass、ice、packed_ice，加上 16 色染色玻璃。
（blue_ice、slime_block 这类看着也合适，但 CE 不会给它们生成 blockstates 覆盖，
实测注水后直接显示成蓝冰本体 —— 踩过。）

20 个状态不够 21 个方块用，所以三个冷门的衍生池子（稀释池/神奇池/创造池）
退回 `auto_state: solid`（音符盒），把玻璃让给更常用的花药台。

## 代价

借用的原版状态会被改写模型：玩家自己放玻璃/染色玻璃/冰，看到的是植物魔法的方块。
这是所有 CraftEngine 类插件共有的限制，只能靠"挑不常用的材质"减少撞车。
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "pack" / "configuration" / "blocks" / "core_blocks.yml"

# 透明满块载体池（每个方块独享一个，同一状态不能绑两个模型）
# 只收"透明且不遮挡"的玻璃类。ice 半透明、packed_ice / blue_ice 不透明，
# 用它们当载体会把下方方块的顶面剔掉（表现就是"底下透明了"）。
CARRIERS = [
    "minecraft:glass",
    "minecraft:tinted_glass",
    "minecraft:white_stained_glass",
    "minecraft:orange_stained_glass",
    "minecraft:magenta_stained_glass",
    "minecraft:light_blue_stained_glass",
    "minecraft:yellow_stained_glass",
    "minecraft:lime_stained_glass",
    "minecraft:pink_stained_glass",
    "minecraft:gray_stained_glass",
    "minecraft:light_gray_stained_glass",
    "minecraft:cyan_stained_glass",
    "minecraft:purple_stained_glass",
    "minecraft:blue_stained_glass",
    "minecraft:brown_stained_glass",
    "minecraft:green_stained_glass",
    "minecraft:red_stained_glass",
    "minecraft:black_stained_glass",
]

# (方块 id, 模型, behavior 类型, 硬度, 是否用 solid 载体, 满态模型)
CORE: list[tuple] = [
    # 魔力池做双外观：空 / 有魔力（两个外观各占一个透明载体）
    ("mana_pool", "mana_pool", "botania:mana_pool", 1.5, False, "mana_pool_full"),
    # 主发射器做 4 个朝向外观（每个方向独占一个载体）
    ("mana_spreader", "mana_spreader", "botania:mana_spreader", 1.5, False, "FACING"),
    # 三个衍生发射器降级 solid，把载体让出来
    ("elven_spreader", "elven_spreader", "botania:mana_spreader", 1.5, True, None),
    ("gaia_spreader", "gaia_spreader", "botania:mana_spreader", 1.5, True, None),
    ("redstone_spreader", "redstone_spreader", "botania:mana_spreader", 1.5, True, None),
    ("apothecary_default", "apothecary_default", "botania:petal_apothecary", 1.5, False, None),
    ("apothecary_default_water", "apothecary_default_water",
     "botania:petal_apothecary", 1.5, False, None),
]
for variant in ("desert", "forest", "fungal", "mesa", "mountain",
                "plains", "swamp", "taiga", "livingrock"):
    CORE.append((f"apothecary_{variant}", f"apothecary_{variant}",
                 "botania:petal_apothecary", 1.5, False, None))
# mossy 与 deepslate 变体最少用，降级 solid，把透明载体让给符文祭坛
CORE.append(("apothecary_mossy", "apothecary_mossy",
             "botania:petal_apothecary", 1.5, True, None))
CORE.append(("apothecary_deepslate", "apothecary_deepslate",
             "botania:petal_apothecary", 1.5, True, None))
# 符文祭坛：泰拉钢线的必经环节
CORE.append(("runic_altar", "runic_altar", "botania:runic_altar", 2.5, False, None))
# 三个衍生池子降级成 solid，把透明载体让出来
for variant in ("diluted_pool", "fabulous_pool", "creative_pool"):
    CORE.append((variant, variant, "botania:mana_pool", 1.5, True, None))


def block_yaml(block_id: str, model: str, behavior: str, hardness: float,
               carrier, use_solid: bool, full_model: str | None = None) -> str:
    model_path = model if model.startswith("botania:") else f"botania:block/{model}"
    if full_model == "FACING":
        n, e, so, w = carrier
        return f"""  botania:{block_id}:
    behavior:
      type: {behavior}
    states:
      properties:
        facing:
          type: 4-direction
          default: north
      appearances:
        north:
          state: "{n}"
          model: {model_path}
        east:
          state: "{e}"
          model:
            path: botania:block/mana_spreader
            y: 90
        south:
          state: "{so}"
          model:
            path: botania:block/mana_spreader
            y: 180
        west:
          state: "{w}"
          model:
            path: botania:block/mana_spreader
            y: 270
      variants:
        'facing=north':
          appearance: north
        'facing=east':
          appearance: east
        'facing=south':
          appearance: south
        'facing=west':
          appearance: west
    settings:
      sounds:
        break: minecraft:block.stone.break
        step: minecraft:block.stone.step
        place: minecraft:block.stone.place
        hit: minecraft:block.stone.hit
        fall: minecraft:block.stone.fall
      hardness: {hardness}
      resistance: 6.0
      can_occlude: false
      is_suffocating: false
      is_view_blocking: false
      fluid_state: false
      friction: 0.6
      item: botania:{block_id}
    loot:
      pools:
        - rolls: 1
          entries:
            - type: item
              item: botania:{block_id}
"""
    if full_model:
        dry, wet = carrier
        full_path = f"botania:block/{full_model}"
        return f"""  botania:{block_id}:
    behavior:
      type: {behavior}
    states:
      properties:
        filled:
          type: boolean
          default: false
      appearances:
        empty:
          state: "{dry}"
          model: {model_path}
        full:
          state: "{wet}"
          model: {full_path}
      variants:
        'filled=false':
          appearance: empty
        'filled=true':
          appearance: full
    settings:
      sounds:
        break: minecraft:block.stone.break
        step: minecraft:block.stone.step
        place: minecraft:block.stone.place
        hit: minecraft:block.stone.hit
        fall: minecraft:block.stone.fall
      hardness: {hardness}
      resistance: 6.0
      can_occlude: false
      is_suffocating: false
      is_view_blocking: false
      fluid_state: false
      friction: 0.6
      item: botania:{block_id}
    loot:
      pools:
        - rolls: 1
          entries:
            - type: item
              item: botania:{block_id}
"""
    if use_solid:
        state_block = "      auto_state: solid"
    else:
        state_block = f'      state: "{carrier}"'
    return f"""  botania:{block_id}:
    behavior:
      type: {behavior}
    state:
{state_block}
      model: {model_path}
    settings:
      sounds:
        break: minecraft:block.stone.break
        step: minecraft:block.stone.step
        place: minecraft:block.stone.place
        hit: minecraft:block.stone.hit
        fall: minecraft:block.stone.fall
      hardness: {hardness}
      resistance: 6.0
      can_occlude: false
      is_suffocating: false
      is_view_blocking: false
      fluid_state: false
      friction: 0.6
      item: botania:{block_id}
    loot:
      pools:
        - rolls: 1
          entries:
            - type: item
              item: botania:{block_id}
"""


def main() -> int:
    lines = [
        "# 核心玩法方块（tools/gen_core_blocks.py 生成，改脚本重跑，勿手改）。",
        "# 载体规则见脚本头部注释：优先透明满块（玻璃），不够的退回音符盒。",
        "",
        "blocks:",
    ]
    cursor = 0
    for entry in CORE:
        block_id, model, behavior, hardness, use_solid = entry[:5]
        full_model = entry[5] if len(entry) > 5 else None
        need = 0 if use_solid else (2 if full_model == "DUAL" else 4 if full_model == "FACING"
                                     else (2 if full_model else 1))
        if cursor + need > len(CARRIERS):
            raise SystemExit(f"透明载体不够：需要 {cursor + need} 个")
        if use_solid:
            carrier = None
        elif full_model == "FACING":
            carrier = tuple(CARRIERS[cursor:cursor + 4])
        elif full_model:
            carrier = (CARRIERS[cursor], CARRIERS[cursor + 1])
        else:
            carrier = CARRIERS[cursor]
        cursor += need
        lines.append(block_yaml(block_id, model, behavior, hardness,
                                carrier, use_solid, full_model).rstrip("\n"))
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"核心方块 {len(CORE)} 个，占用透明载体 {cursor} / {len(CARRIERS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
