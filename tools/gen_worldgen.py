#!/usr/bin/env python3
"""生成神秘花的自然生成配置。

格式抄 CraftEngine 自带的 `default_feature_populator` 包（plugins/CraftEngine/
resources/default_feature_populator/subpacks/26_3/configuration/features.yml）：
`to_place` 必须用 `minecraft:rule_based`，普通的 `weighted_state_provider`
会被判成"无效的已配置的地物"。

用法：python3 tools/gen_worldgen.py
产物：pack/configuration/worldgen.yml
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "pack" / "configuration" / "worldgen.yml"

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]


def main() -> int:
    entries = "\n".join(
        f"""                - weight: 1
                  data:
                    id: 'botania:{c}_mystical_flower'""" for c in COLORS)

    yaml = f"""# 神秘花的自然生成（tools/gen_worldgen.py 生成，勿手改）。
# 只在草方块正上方、且该格是空气时替换掉空气——不会挖穿地形，也不会长到水里。
placed_features:
  botania:mystical_flowers:
    dimension_type:
      - minecraft:overworld
    feature:
      type: 'minecraft:simple_block'
      to_place:
        type: 'minecraft:rule_based'
        rules:
          - if_true:
              type: 'minecraft:all_of'
              predicates:
                - type: 'minecraft:matching_blocks'
                  blocks: 'minecraft:air'
                - type: 'minecraft:matching_blocks'
                  blocks: 'minecraft:grass_block'
                  offset:
                    - 0
                    - -1
                    - 0
            then:
              type: 'minecraft:weighted'
              entries:
{entries}
    placement:
      # 原版 placement 的顺序有讲究：count 决定尝试次数，必须排在最前
      - type: 'minecraft:count'
        count: 10
      - type: 'minecraft:in_square'
      - type: 'minecraft:rarity_filter'
        chance: 5
      - type: 'minecraft:heightmap'
        heightmap: MOTION_BLOCKING
      - type: 'minecraft:biome'
"""
    OUT.write_text(yaml, encoding="utf-8")
    print(f"神秘花地物 -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
