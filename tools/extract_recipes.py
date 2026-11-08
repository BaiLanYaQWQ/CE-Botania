#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 Botania 源码的 generated recipes 目录提取配方数据，产出结构化 JSON。

输出（默认 /home/BaiLanYa/1/Botania/build/recipes/）:
  petal_apothecary.json    花药台（花瓣+种子 -> 花）
  mana_infusion.json       魔力池注魔
  runic_altar.json         符文祭坛
  elven_trade.json         精灵交易
  pure_daisy.json          纯洁雏菊转化
  orechid.json             矿石雏菊
  marimorphosis.json       大理石雏菊
  conversions.json         其他转化
  vanilla_crafting.json    顶层合成配方（crafting_shaped / shapeless / smelting 等）
  _summary.json            各类型条数汇总
  _unparsed.json           解析失败的条目

原则:
  * 上游 JSON 原样保留（所有字段、所有嵌套结构），只在外层加 "id"
  * "id" = "botania:" + 相对 recipes/ 的路径去掉 .json，例如
    petal_apothecary/endoflame -> botania:petal_apothecary/endoflame
    mana_ring                 -> botania:mana_ring
  * item / tag id 一律不改写
  * 目录不存在时跳过并记 0，不报错退出
  * 只读源码目录，不修改 /tmp/Botania-src

用法:
  python3 tools/extract_recipes.py
  python3 tools/extract_recipes.py --src /path/to/recipes --out /path/to/out
"""

import argparse
import json
import os
import sys

DEFAULT_SRC = "/tmp/Botania-src/Xplat/src/generated/resources/data/botania/recipes"
DEFAULT_OUT = "/home/BaiLanYa/1/Botania/build/recipes"

# 目录名 -> 输出文件名（均不含 .json）
SUBDIR_TARGETS = [
    "petal_apothecary",
    "mana_infusion",
    "runic_altar",
    "elven_trade",
    "pure_daisy",
    "orechid",
    "marimorphosis",
    "conversions",
]

VANILLA_TARGET = "vanilla_crafting"

# vanilla_crafting 只收顶层这些 type（含 botania 对原版合成的扩展变体，
# 它们本质仍是 crafting_shaped / shapeless 语义）
VANILLA_TYPES = {
    "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless",
    "minecraft:crafting_special_armordye",
    "minecraft:crafting_special_bannerduplicate",
    "minecraft:crafting_special_bookcloning",
    "minecraft:crafting_special_firework_rocket",
    "minecraft:crafting_special_firework_star",
    "minecraft:crafting_special_firework_star_fade",
    "minecraft:crafting_special_mapcloning",
    "minecraft:crafting_special_mapextending",
    "minecraft:crafting_special_repairitem",
    "minecraft:crafting_special_shielddecoration",
    "minecraft:crafting_special_shulkerboxcoloring",
    "minecraft:crafting_special_suspiciousstew",
    "minecraft:crafting_special_tippedarrow",
    "minecraft:crafting_transmute",
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
    "minecraft:stonecutting",
    "minecraft:smithing_transform",
    "minecraft:smithing_trim",
    "botania:nbt_output_wrapper",
    "botania:gog_alternation",
    "botania:armor_upgrade",
    "botania:mana_upgrade",
    "botania:mana_upgrade_shapeless",
    "botania:twig_wand",
    "botania:water_bottle_matching_shaped",
}


def recipe_id(rel_path_no_ext):
    """把相对路径转成配方的资源 id。"""
    return "botania:" + rel_path_no_ext.replace(os.sep, "/")


def load_recipe(abs_path, rel_no_ext):
    """读取单个配方文件，返回 (entry, None) 或 (None, 失败原因)。"""
    try:
        with open(abs_path, "r", encoding="utf-8") as fh:
            data = json.load(fh)
    except json.JSONDecodeError as exc:
        return None, "JSON 解析失败: %s" % exc
    except OSError as exc:
        return None, "读取失败: %s" % exc

    if not isinstance(data, dict):
        return None, "顶层不是 JSON 对象（实际为 %s）" % type(data).__name__
    if "type" not in data:
        return None, "缺少 type 字段"

    # 原样保留所有字段，只在最外层加 id
    entry = {"id": recipe_id(rel_no_ext)}
    entry.update(data)
    return entry, None


def collect_from_dir(root, subdir):
    """收集某个子目录下所有 *.json。返回 (entries, unparsed)。"""
    entries, unparsed = [], []
    abs_dir = os.path.join(root, subdir)
    if not os.path.isdir(abs_dir):
        return entries, unparsed  # 目录不存在：跳过，summary 记 0

    for name in sorted(os.listdir(abs_dir)):
        if not name.endswith(".json") or not os.path.isfile(os.path.join(abs_dir, name)):
            continue
        rel_no_ext = subdir + "/" + name[: -len(".json")]
        entry, err = load_recipe(os.path.join(abs_dir, name), rel_no_ext)
        if err:
            unparsed.append({"file": rel_no_ext + ".json", "path": os.path.join(abs_dir, name), "reason": err})
        else:
            entries.append(entry)
    return entries, unparsed


def collect_top_level(root):
    """收集顶层配方（不在任何上述子目录里的 *.json）。"""
    entries, unparsed = [], []
    if not os.path.isdir(root):
        return entries, unparsed

    # 注意: 顶层存在与子目录同名的单文件（如 runic_altar.json 与 runic_altar/ 并存），
    # 它是合成符文祭坛方块的普通配方，必须收集，不能按子目录名过滤掉。
    for name in sorted(os.listdir(root)):
        full = os.path.join(root, name)
        if not name.endswith(".json") or not os.path.isfile(full):
            continue
        rel_no_ext = name[: -len(".json")]
        entry, err = load_recipe(full, rel_no_ext)
        if err:
            unparsed.append({"file": name, "path": full, "reason": err})
            continue
        if entry.get("type") in VANILLA_TYPES:
            entries.append(entry)
        else:
            # 未知顶层类型：仍然收进 vanilla_crafting，避免数据丢失，
            # 但记录到 _unparsed.json 便于人工复核
            entries.append(entry)
            unparsed.append({
                "file": name,
                "path": full,
                "reason": "顶层非预期 type '%s'，已收入 %s.json 供复核"
                          % (entry.get("type"), VANILLA_TARGET),
            })
    return entries, unparsed


def write_json(path, payload):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    os.replace(tmp, path)


def main():
    ap = argparse.ArgumentParser(description="提取 Botania 配方数据为结构化 JSON")
    ap.add_argument("--src", default=os.environ.get("BOTANIA_RECIPES_SRC", DEFAULT_SRC),
                    help="源码 recipes 目录（默认 %s）" % DEFAULT_SRC)
    ap.add_argument("--out", default=os.environ.get("BOTANIA_RECIPES_OUT", DEFAULT_OUT),
                    help="输出目录（默认 %s）" % DEFAULT_OUT)
    args = ap.parse_args()

    src, out = os.path.abspath(args.src), os.path.abspath(args.out)
    if not os.path.isdir(src):
        print("错误: 源码目录不存在: %s" % src, file=sys.stderr)
        return 2
    os.makedirs(out, exist_ok=True)

    summary = {}
    unparsed_all = []
    written = []

    # 子目录
    for subdir in SUBDIR_TARGETS:
        entries, unparsed = collect_from_dir(src, subdir)
        entries.sort(key=lambda e: e["id"])
        write_json(os.path.join(out, subdir + ".json"), entries)
        summary[subdir] = len(entries)
        unparsed_all.extend(unparsed)
        written.append((subdir + ".json", len(entries)))

    # 顶层合成
    entries, unparsed = collect_top_level(src)
    entries.sort(key=lambda e: e["id"])
    write_json(os.path.join(out, VANILLA_TARGET + ".json"), entries)
    summary[VANILLA_TARGET] = len(entries)
    unparsed_all.extend(unparsed)
    written.append((VANILLA_TARGET + ".json", len(entries)))

    write_json(os.path.join(out, "_summary.json"), summary)
    write_json(os.path.join(out, "_unparsed.json"), unparsed_all)

    total = sum(summary.values())
    print("输出目录: %s" % out)
    for name, count in written:
        print("  %-24s %4d 条" % (name, count))
    print("  总计 %d 条配方，解析失败/待复核 %d 条" % (total, len(unparsed_all)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
