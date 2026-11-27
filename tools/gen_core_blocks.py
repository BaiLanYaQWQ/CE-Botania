#!/usr/bin/env python3
"""生成核心玩法方块的 CraftEngine 配置。

用法：python3 tools/gen_core_blocks.py
产物：pack/configuration/blocks/core_blocks.yml
      pack/resourcepack/assets/botania/models/block/alfheim_gate_x.json

使用 persistent=true、非含水、distance=2~6 的树叶状态作为透明载体。
每个外观独占一个载体，盖上空方块模型，由实体显示实际外观。
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "pack" / "configuration" / "blocks" / "core_blocks.yml"
MODELS = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "models" / "block"

CARRIERS = [
    f"minecraft:{wood}_leaves[distance={distance},persistent=true,waterlogged=false]"
    for wood in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                 "mangrove", "cherry", "pale_oak")
    for distance in range(2, 7)
]

SPREADER_ROTATIONS = {
    "north": "0,180,0",
    "east": "0,90,0",
    "south": None,
    "west": "0,270,0",
    "up": "-90,0,0",
    "down": "90,0,0",
}

CORE = [
    ("mana_pool", "mana_pool"),
    ("mana_spreader", "mana_spreader"),
    ("elven_spreader", "mana_spreader"),
    ("gaia_spreader", "mana_spreader"),
    ("redstone_spreader", "mana_spreader"),
    *((f"apothecary_{variant}", "petal_apothecary")
      for variant in ("default", "default_water", "desert", "forest", "fungal",
                      "mesa", "mountain", "plains", "swamp", "taiga",
                      "livingrock", "mossy", "deepslate")),
    ("runic_altar", "runic_altar"),
    *((block, "mana_pool") for block in ("diluted_pool", "fabulous_pool", "creative_pool")),
]


def display_state(carrier: str, renderer: str | None, indent: int,
                  rotation: str | None = None) -> list[str]:
    lines = [
        f'state: "{carrier}"',
        "model: botania:block/empty",
        "transparent: true",
        "entity_culling: false",
    ]
    if renderer:
        lines.extend([
            "entity_renderer:",
            "  type: item_display",
            f"  item: botania:render/{renderer}",
            "  display_transform: none",
        ])
        if rotation:
            lines.append(f"  rotation: '{rotation}'")
    return [" " * indent + line for line in lines]


def block_yaml(block: str, behavior: str, carriers: list[str]) -> list[str]:
    lines = [
        f"  botania:{block}:",
        "    behavior:",
        f"      type: botania:{behavior}",
    ]
    if behavior == "mana_spreader":
        lines.extend([
            "    states:",
            "      properties:",
            "        facing:",
            "          type: direction",
            "          default: north",
            "      appearances:",
        ])
        for (direction, rotation), carrier in zip(SPREADER_ROTATIONS.items(), carriers, strict=True):
            lines.append(f"        {direction}:")
            if block == "mana_spreader" and direction == "south":
                rotation = "0,0,0"
            lines.extend(display_state(carrier, block, 10, rotation))
        lines.append("      variants:")
        for direction in SPREADER_ROTATIONS:
            lines.extend([f"        'facing={direction}':", f"          appearance: {direction}"])
    else:
        lines.append("    state:")
        # 魔力池本体由插件显示，其他方块由 CraftEngine 负责。
        lines.extend(display_state(carriers[0], None if block == "mana_pool" else block, 6))

    lines.extend(["    settings:", "      support_shape: cobweb"])
    if block == "apothecary_default":
        lines.append("      name: '<lang:botania.block.apothecary_default>'")
    lines.append("      sounds:")
    for event in ("break", "step", "place", "hit", "fall"):
        lines.append(f"        {event}: minecraft:block.stone.{event}")
    hardness, resistance = (2.0, 10.0) if block in ("mana_pool", "runic_altar") else (1.5, 6.0)
    lines.extend([
        f"      hardness: {hardness}",
        f"      resistance: {resistance}",
        "      can_occlude: false",
        "      is_suffocating: false",
        "      is_view_blocking: false",
        "      fluid_state: false",
        "      friction: 0.6",
        f"      item: botania:{block}",
        "    loot:",
        "      pools:",
        "        - rolls: 1",
        "          entries:",
        "            - type: item",
        f"              item: botania:{block}",
    ])
    return lines


def write_alfheim_gate_x() -> None:
    gate = json.loads((MODELS / "alfheim_gate.json").read_text(encoding="utf-8"))
    # 将基准薄面绕方块中心旋转 90 度，北面转到东面。
    element, = gate["elements"]
    min_x, min_y, min_z = element["from"]
    max_x, max_y, max_z = element["to"]
    element["from"] = [16 - max_z, min_y, min_x]
    element["to"] = [16 - min_z, max_y, max_x]
    faces = element["faces"]
    element["faces"] = {"east": faces["north"], "west": faces["south"]}
    output = MODELS / "alfheim_gate_x.json"
    try:
        if json.loads(output.read_text(encoding="utf-8")) == gate:
            return
    except (FileNotFoundError, json.JSONDecodeError):
        pass
    output.write_text(json.dumps(gate, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    lines = [
        "# 核心玩法方块（tools/gen_core_blocks.py 生成，改脚本重跑，勿手改）。",
        # 保留配置中的旧标题，以便逐字节重建现有文件。
        "# 载体规则见脚本头部注释：优先透明满块（玻璃），不够的退回音符盒。",
        "",
        "blocks:",
    ]
    cursor = 0
    for block, behavior in CORE:
        need = len(SPREADER_ROTATIONS) if behavior == "mana_spreader" else 1
        if cursor + need > len(CARRIERS):
            raise SystemExit(f"透明载体不够：需要 {cursor + need} 个")
        lines.extend(block_yaml(block, behavior, CARRIERS[cursor:cursor + need]))
        if behavior == "mana_spreader" and block != "mana_spreader":
            lines.append("")
        cursor += need
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    write_alfheim_gate_x()
    print(f"核心方块 {len(CORE)} 个，占用透明载体 {cursor} / {len(CARRIERS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
