#!/usr/bin/env python3
"""把上游配方与标签规整成插件运行时直接读的紧凑格式。

用法：
    python3 tools/gen_plugin_data.py

输入：
    build/recipes/*.json               （extract_recipes.py 的产物）
    /tmp/Botania-src/.../data/botania/tags/items/**
输出：
    plugin/src/main/resources/data/recipes/<type>.json
    plugin/src/main/resources/data/tags.json
    plugin/src/main/resources/data/meta.json

为什么要这一步：上游 JSON 字段不统一（output / result 混用、elven_trade 的
output 是数组、pure_daisy 用 name 而不是 item），插件侧不应该背着这些历史包袱。
这里把它们归一成：
    {"id": "...", "mana": 1234, "ingredients": [{"item": "..."}|{"tag": "..."}], "output": {"item": "...", "count": 1}}
只保留插件真的会用的配方类型，其余类型不进 jar。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECIPES = ROOT / "build" / "recipes"
SRC_TAGS = Path("/tmp/Botania-src/Xplat/src/generated/resources/data/botania/tags/items")
OUT = ROOT / "plugin" / "src" / "main" / "resources" / "data"

# 插件实现里会用到的配方类型 → 源文件名
WANTED = {
    "petal_apothecary": "petal_apothecary.json",
    "mana_infusion": "mana_infusion.json",
    "runic_altar": "runic_altar.json",
    "elven_trade": "elven_trade.json",
    "pure_daisy": "pure_daisy.json",
}


def norm_ingredient(raw) -> dict | None:
    """上游 {item:..} / {tag:..} / "#tag" / {id:"#tag"} 统一成 {item|tag}."""
    if isinstance(raw, str):
        if raw.startswith("#"):
            return {"tag": raw[1:]}
        return {"item": raw}
    if not isinstance(raw, dict):
        return None
    if "item" in raw:
        return {"item": raw["item"]}
    if "tag" in raw:
        return {"tag": raw["tag"]}
    ident = raw.get("id")
    if isinstance(ident, str):
        if ident.startswith("#"):
            return {"tag": ident[1:]}
        return {"item": ident}
    if "block" in raw:  # orechid 之类，保留给后续阶段
        return {"block": raw["block"]}
    if "name" in raw:
        return {"block": raw["name"]}
    return None


def norm_output(raw) -> dict | None:
    if isinstance(raw, list):
        return norm_output(raw[0]) if raw else None
    if isinstance(raw, str):
        return {"item": raw, "count": 1}
    if not isinstance(raw, dict):
        return None
    if "item" in raw:
        return {"item": raw["item"], "count": int(raw.get("count", 1))}
    if "id" in raw:
        return {"item": raw["id"], "count": int(raw.get("count", 1))}
    if "name" in raw:
        return {"block": raw["name"], "count": int(raw.get("count", 1))}
    if "block" in raw:
        return {"block": raw["block"], "count": int(raw.get("count", 1))}
    return None


def normalize(entry: dict) -> dict | None:
    out = norm_output(entry.get("output") if "output" in entry else entry.get("result"))
    if out is None:
        return None
    ing: list[dict] = []
    for key in ("ingredients",):
        for raw in entry.get(key, []) or []:
            norm = norm_ingredient(raw)
            if norm is not None:
                ing.append(norm)
    if not ing:
        # 上游有些配方把"多选一"写成 input 数组：每个元素都能独立合成一次，
        # 这里由调用方拆成多条（见 normalize_all）。
        raw_input = entry.get("input")
        if isinstance(raw_input, list):
            for item in raw_input:
                norm = norm_ingredient(item)
                if norm is not None:
                    ing.append(norm)
        else:
            single = norm_ingredient(raw_input)
            if single is not None:
                ing.append(single)
    recipe = {"id": entry.get("id", ""), "ingredients": ing, "output": out}
    if "mana" in entry:
        recipe["mana"] = int(entry["mana"])
    if "catalyst" in entry:
        cat = entry["catalyst"]
        if isinstance(cat, dict) and "block" in cat:
            recipe["catalyst_block"] = cat["block"]
    if entry.get("reagent"):
        reagent = norm_ingredient(entry["reagent"])
        if reagent:
            recipe["reagent"] = reagent
    return recipe


def normalize_all(entries: list[dict]) -> list[dict]:
    """把"多选一"的输入拆成多条独立配方，其余原样。"""
    out: list[dict] = []
    for entry in entries:
        raw_input = entry.get("input")
        multi = (not entry.get("ingredients")) and isinstance(raw_input, list) and len(raw_input) > 1
        if multi:
            base = normalize(entry)
            if base is None:
                continue
            for index, item in enumerate(raw_input):
                norm = norm_ingredient(item)
                if norm is None:
                    continue
                out.append({
                    "id": f"{entry.get('id', '')}#{index}",
                    "ingredients": [norm],
                    "output": base["output"],
                    **({"mana": base["mana"]} if "mana" in base else {}),
                })
        else:
            normalized = normalize(entry)
            if normalized is not None:
                out.append(normalized)
    return out


def collect_tags() -> dict[str, list[str]]:
    tags: dict[str, list[str]] = {}
    if not SRC_TAGS.is_dir():
        return tags
    for path in sorted(SRC_TAGS.rglob("*.json")):
        rel = path.relative_to(SRC_TAGS).with_suffix("")
        tag_id = "botania:" + "/".join(rel.parts)
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        values = data.get("values", [])
        members: list[str] = []
        for v in values:
            if isinstance(v, str):
                if v.startswith("#"):
                    members.append(v[1:])
                else:
                    members.append(v)
            elif isinstance(v, dict):
                ident = v.get("id")
                if isinstance(ident, str):
                    members.append(ident[1:] if ident.startswith("#") else ident)
        tags[tag_id] = members
    return tags


def main() -> int:
    if not RECIPES.is_dir():
        print("先运行 tools/extract_recipes.py", file=sys.stderr)
        return 2
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "recipes").mkdir(parents=True, exist_ok=True)

    meta: dict[str, int] = {}
    total = 0
    for name, filename in WANTED.items():
        src = RECIPES / filename
        if not src.is_file():
            meta[name] = 0
            continue
        raw = json.loads(src.read_text(encoding="utf-8"))
        normalized = normalize_all(raw)
        (OUT / "recipes" / f"{name}.json").write_text(
            json.dumps(normalized, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        meta[name] = len(normalized)
        total += len(normalized)

    tags = collect_tags()
    (OUT / "tags.json").write_text(
        json.dumps(tags, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    (OUT / "meta.json").write_text(
        json.dumps({"recipes": meta, "total": total, "tags": len(tags)}, ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    print("配方:", meta)
    print("标签:", len(tags), "组")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
