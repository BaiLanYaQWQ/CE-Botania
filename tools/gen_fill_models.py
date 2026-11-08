#!/usr/bin/env python3
"""生成"装了东西"的方块模型（魔力池满的那层魔力、花药台里的水）。

用法：python3 tools/gen_fill_models.py

产物（写进资源包）：
  models/block/shapes/petal_apothecary_water.json   花药台 + 水面
  models/block/apothecary_<变体>_water.json         12 个有水变体
  models/block/mana_pool_full.json 已由上游提供（shapes 里就有），这里只补变体

为什么自己造模型：上游的水面是 BlockEntityRenderer 用代码画的（跟着水位涨落），
我们用的是方块模型，只能烘一个静态水面贴图进去。
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "models" / "block"

APOTHECARY_VARIANTS = [
    "default", "desert", "forest", "fungal", "mesa", "mountain",
    "plains", "swamp", "taiga", "livingrock", "mossy", "deepslate",
]

# 花药台杯腔：外壁 2~14，内壁 3~13，杯底在 y=12，杯口到 16
WATER_PLANE = {
    "name": "water",
    "from": [3, 13.6, 3],
    "to": [13, 13.7, 13],
    "faces": {
        "up": {"texture": "#water"},
        "down": {"texture": "#water"},
    },
}


def main() -> int:
    base_path = MODELS / "shapes" / "petal_apothecary.json"
    if not base_path.is_file():
        raise SystemExit(f"找不到上游模型：{base_path}")
    base = json.loads(base_path.read_text(encoding="utf-8"))
    wet = json.loads(json.dumps(base))
    wet["textures"] = {**base.get("textures", {}), "water": "minecraft:block/water_still"}
    wet["elements"] = list(base.get("elements", [])) + [WATER_PLANE]
    (MODELS / "shapes" / "petal_apothecary_water.json").write_text(
        json.dumps(wet, ensure_ascii=False, indent=2), encoding="utf-8")

    made = 1
    for variant in APOTHECARY_VARIANTS:
        src = MODELS / f"apothecary_{variant}.json"
        if not src.is_file():
            continue
        data = json.loads(src.read_text(encoding="utf-8"))
        data["parent"] = "botania:block/shapes/petal_apothecary_water"
        (MODELS / f"apothecary_{variant}_water.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        made += 1

    print(f"生成 {made} 个模型（花药台水面）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
