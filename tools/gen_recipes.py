#!/usr/bin/env python3
"""把上游的合成配方转成 CraftEngine 的配方配置。

用法：python3 tools/gen_recipes.py
输入：build/recipes/vanilla_crafting.json（extract_recipes.py 的产物）
输出：pack/configuration/recipes/generated_recipes.yml

能转的：
  minecraft:crafting_shaped / botania:twig_wand      -> type: shaped
  minecraft:crafting_shapeless / botania:mana_upgrade_shapeless -> type: shapeless
转不了的（跳过，日志里报数）：
  botania:nbt_output_wrapper   输出带 NBT 变体，CE 没有对应概念
  botania:armor_upgrade 等     上游自定义配方类型，靠特殊逻辑，得在插件里实现

标签引用统一写成 `#botania:xxx`；物品侧由 gen_content.py 挂上对应的 tags。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "build" / "recipes" / "vanilla_crafting.json"
OUT = ROOT / "pack" / "configuration" / "recipes" / "generated_recipes.yml"

SHAPED_TYPES = {"minecraft:crafting_shaped", "botania:twig_wand"}
SHAPELESS_TYPES = {"minecraft:crafting_shapeless", "botania:mana_upgrade_shapeless"}


def as_ingredient(node) -> str | None:
    """{item:...} / {tag:...} / 列表(多选一) -> CE 的写法。"""
    if isinstance(node, list):
        # 多选一：CE 配方只能写一个，取第一个并注释说明
        return as_ingredient(node[0]) if node else None
    if not isinstance(node, dict):
        return None
    if "item" in node:
        return str(node["item"])
    if "tag" in node:
        return "#" + str(node["tag"])
    return None


def result_of(entry: dict):
    raw = entry.get("result") or entry.get("output")
    if not isinstance(raw, dict):
        return None
    item = raw.get("item") or raw.get("id")
    if not isinstance(item, str):
        return None
    count = int(raw.get("count", 1))
    return item, count


def convert(entry: dict) -> dict | None:
    kind = entry.get("type", "")
    # gog_alternation 是"同一配方在 Garden of Glass 里有另一种写法"，
    # 主配方在 base 里，解开继续走普通流程
    if kind == "botania:gog_alternation" and isinstance(entry.get("base"), dict):
        inner = dict(entry["base"])
        inner.setdefault("id", entry.get("id", ""))
        return convert(inner)
    out = result_of(entry)
    if out is None:
        return None
    item, count = out

    if kind in SHAPED_TYPES:
        pattern = entry.get("pattern")
        key = entry.get("key")
        if not pattern or not key:
            return None
        ingredients = {}
        for symbol, node in key.items():
            value = as_ingredient(node)
            if value:
                ingredients[str(symbol)] = value
        if not ingredients:
            return None
        return {
            "type": "shaped",
            "category": entry.get("category", "misc"),
            "pattern": [str(row) for row in pattern],
            "ingredients": ingredients,
            "result": (item, count),
            "id": entry.get("id", ""),
            "group": entry.get("group"),
        }

    if kind in SHAPELESS_TYPES:
        raw = entry.get("ingredients")
        if not isinstance(raw, list) or not raw:
            return None
        ingredients = {}
        for index, node in enumerate(raw):
            value = as_ingredient(node)
            if not value:
                continue
            ingredients[chr(ord("A") + index)] = value
        if not ingredients:
            return None
        return {
            "type": "shapeless",
            "category": entry.get("category", "misc"),
            "ingredients": ingredients,
            "result": (item, count),
            "id": entry.get("id", ""),
            "group": entry.get("group"),
        }
    return None


def yaml_scalar(text: str) -> str:
    if text.startswith("#") or ":" in text:
        return "'" + text + "'"
    return text


def render(recipes: list[dict]) -> str:
    lines = [
        "# 合成配方（tools/gen_recipes.py 生成，勿手改）。",
        "# 数据来自上游 datagen 的 data/botania/recipe，标签引用由物品的 settings.tags 支撑。",
        "recipes:",
    ]
    for index, recipe in enumerate(recipes):
        rid = recipe["id"] or f"botania:generated_{index}"
        lines.append(f"  {rid}:")
        lines.append(f"    type: {recipe['type']}")
        if recipe.get("category"):
            lines.append(f"    category: {recipe['category']}")
        if recipe.get("group"):
            group = recipe["group"]
            if group.startswith("botania:"):
                group = group.split(":", 1)[1]
            lines.append(f"    group: '{group}'")
        if recipe["type"] == "shaped":
            lines.append("    pattern:")
            for row in recipe["pattern"]:
                lines.append(f"      - '{row}'")
        lines.append("    ingredients:")
        for symbol, value in recipe["ingredients"].items():
            lines.append(f"      '{symbol}': {yaml_scalar(value)}")
        item, count = recipe["result"]
        lines.append("    result:")
        lines.append(f"      id: {item}")
        lines.append(f"      count: {count}")
    return "\n".join(lines) + "\n"


def main() -> int:
    if not SRC.is_file():
        print("先运行 tools/extract_recipes.py", file=sys.stderr)
        return 2
    raw = json.loads(SRC.read_text(encoding="utf-8"))
    converted: list[dict] = []
    skipped: dict[str, int] = {}
    for entry in raw:
        recipe = convert(entry)
        if recipe is None:
            kind = entry.get("type", "?")
            skipped[kind] = skipped.get(kind, 0) + 1
            continue
        converted.append(recipe)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(render(converted), encoding="utf-8")
    print(f"转换 {len(converted)} 条配方 -> {OUT}")
    if skipped:
        print("跳过：", dict(sorted(skipped.items(), key=lambda kv: -kv[1])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
