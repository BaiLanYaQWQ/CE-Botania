#!/usr/bin/env python3
"""把 Botania 上游清单生成为 CraftEngine 的物品 / 方块 / 语言配置。

用法：
    python3 tools/gen_content.py

输入：build/asset_index.json（extract_assets.py 的产物）
      /tmp/Botania-src 的 lang/en_us.json、zh_cn.json
输出：pack/configuration/items/generated_items.yml
      pack/configuration/blocks/generated_blocks.yml
      pack/configuration/langs/zh_cn.yml、en_us.yml

约定：
  * 命名空间固定 botania，id 与上游一致，便于对照上游代码与配方。
  * 纯物品走 `model: minecraft:model path: botania:item/<id>`，保留上游图标。
  * 方块走 `blocks:` 段：单状态 solid；多状态按 variant 取默认外观，
    由 core_blocks.yml（手写）覆盖需要精确物理行为的核心方块。
  * 语言文件用 botania.item.<id> / botania.block.<id> 作为键。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path("/tmp/Botania-src")
INDEX = ROOT / "build" / "asset_index.json"
CFG = ROOT / "pack" / "configuration"

# 上游物品名特殊字符清理：把 "Botania" 前缀之类剥掉不影响，保留原文。
NAMESPACE = "botania"


def load_lang(name: str) -> dict[str, str]:
    p = SRC / f"Xplat/src/main/resources/assets/botania/lang/{name}.json"
    if not p.is_file():
        return {}
    return json.loads(p.read_text(encoding="utf-8"))


def clean_name(raw: str) -> str:
    # 上游名称可能带颜色代码或格式说明，去掉 & 开头两位的样式码
    return re.sub(r"[&\u00a7].", "", raw).strip()


def yaml_str(s: str) -> str:
    if s == "":
        return "''"
    if re.fullmatch(r"[A-Za-z0-9_./\-]+", s):
        return s
    return "'" + s.replace("'", "''") + "'"


class Index:
    def __init__(self) -> None:
        data = json.loads(INDEX.read_text(encoding="utf-8"))
        self.item_models: list[str] = data["item_models"]
        self.block_models: list[str] = data["block_models"]
        self.blockstates: dict[str, dict] = data["blockstates"]
        self.item_set = set(self.item_models)
        self.block_set = set(self.blockstates)

    def default_model(self, block: str) -> str | None:
        """取方块的默认外观模型路径（不带 botania: 前缀）。"""
        d = self.blockstates.get(block)
        if not d:
            return None
        if "variants" in d:
            variants = d["variants"]
            # 优先空键，其次 normal，再次第一个
            for key in ("", "normal"):
                if key in variants:
                    return self._model_of(variants[key])
            return self._model_of(next(iter(variants.values())))
        if "multipart" in d:
            for part in d["multipart"]:
                m = self._model_of(part.get("apply"))
                if m:
                    return m
        return None

    @staticmethod
    def _model_of(v) -> str | None:
        if isinstance(v, list):
            for item in v:
                m = Index._model_of(item)
                if m:
                    return m
            return None
        if isinstance(v, dict):
            return v.get("model")
        return None



MODELS_DIR = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "models"

# 满方块常见的原版父模型；命中就按"整块"处理
FULL_PARENTS = ("minecraft:block/cube", "minecraft:block/block")


def resolve_model(path: str, depth: int = 0):
    """递归解析模型 parent 链，返回 (elements, vanilla_parent)。"""
    if depth > 12 or not path:
        return None, None
    ns, _, rel = path.partition(":")
    if ns == "minecraft":
        return None, path
    f = MODELS_DIR / (rel + ".json")
    if not f.is_file():
        return None, None
    try:
        d = json.loads(f.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None, None
    if "elements" in d:
        return d["elements"], None
    parent = d.get("parent")
    if not parent:
        return None, None
    return resolve_model(parent, depth + 1)


def _chain_has_cross(path: str, depth: int = 0) -> bool:
    """沿 parent 链找 shapes/cross —— 花类模型的父模型都是它。"""
    if depth > 12 or not path:
        return False
    rel = path.replace("botania:", "")
    tail = rel.split("/")[-1]
    if tail in ("cross", "tinted_cross"):
        return True
    f = MODELS_DIR / (rel + ".json")
    if not f.is_file():
        return False
    try:
        d = json.loads(f.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False
    return _chain_has_cross(d.get("parent", ""), depth + 1)


def geometry_of(idx: Index, name: str) -> str:
    """返回 full / cross / partial。"""
    model = idx.default_model(name)
    if not model:
        return "partial"
    if _chain_has_cross(model):
        return "cross"
    elements, vanilla = resolve_model(model)
    if vanilla:
        return "full" if vanilla.startswith(FULL_PARENTS) else "partial"
    if elements is None:
        return "partial"
    if len(elements) != 1:
        return "partial"
    e = elements[0]
    if e.get("from") == [0, 0, 0] and e.get("to") == [16, 16, 16] and not e.get("rotation"):
        return "full"
    # 只比"矮"的方块走实体渲染：整块宽度、高度不到 16 的（池子、台子、平台）
    to = e.get("to") or [0, 0, 0]
    if to[1] < 16:
        return "partial"
    return "full"


def is_cross_model(model: str | None, idx: Index) -> bool:
    """花这类十字模型：条目 parent 指向 shapes/cross 或本身叫 cross。"""
    if not model:
        return False
    name = model.split("/")[-1]
    if name == "cross":
        return True
    p = ROOT / "pack/resourcepack/assets/botania/models" / f"{model.split(':', 1)[-1]}.json"
    if not p.is_file():
        return False
    try:
        d = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False
    parent = d.get("parent", "")
    return parent.endswith("shapes/cross") or parent.endswith("block/cross")


# 需要非满方块物理的方块：花、花药台、发射器、池子等由 core_blocks.yml 手写覆盖，
# 这里仅记录集合供生成器跳过。
CORE_OVERRIDES_FILE = CFG / "blocks" / "_core_overrides.txt"
FLOWER_IDS_FILE = CFG / "blocks" / "_flower_ids.txt"


def flower_ids() -> set[str]:
    """要有方块实体的花（插件里挂 botania:flower 行为）。"""
    if not FLOWER_IDS_FILE.is_file():
        return set()
    out = set()
    for line in FLOWER_IDS_FILE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            # 清单里写的是带命名空间的 id（便于人读），生成器里比对的是裸名字
            out.add(line.split(":", 1)[-1])
    return out


def core_overrides() -> set[str]:
    if not CORE_OVERRIDES_FILE.is_file():
        return set()
    out = set()
    for line in CORE_OVERRIDES_FILE.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            # 清单里写的是带命名空间的 id（便于人读），生成器里比对的是裸名字
            out.add(line.split(":", 1)[-1])
    return out


TAGS_FILE = ROOT / "plugin" / "src" / "main" / "resources" / "data" / "tags.json"


def item_tags() -> dict[str, list[str]]:
    """tag -> 成员 反过来：每个物品挂上它所属的标签。

    配方里大量用 `#botania:livingwood_logs` 这种引用，物品没挂 tag 就匹配不上。
    """
    if not TAGS_FILE.is_file():
        return {}
    raw = json.loads(TAGS_FILE.read_text(encoding="utf-8"))

    def expand(tag: str, depth: int = 0) -> list[str]:
        """把标签递归展开成实际物品：上游有 botania:petals -> botania:petals/white 这种嵌套。"""
        if depth > 8:
            return []
        out: list[str] = []
        for member in raw.get(tag, []):
            if not isinstance(member, str):
                continue
            name = member[1:] if member.startswith("#") else member
            if name in raw:                      # 子标签：继续往下钻
                out.extend(expand(name, depth + 1))
            else:
                out.append(member)
        return out

    reverse: dict[str, list[str]] = {}
    for tag in raw:
        for item in expand(tag):
            if isinstance(item, str) and not item.startswith("#"):
                reverse.setdefault(item, []).append(tag)
    return reverse


def gen_items(idx: Index, en: dict[str, str], zh: dict[str, str], skip: set[str]) -> tuple[str, dict, dict]:
    lines: list[str] = [
        "# 纯物品（tools/gen_content.py 生成，勿手改）。",
        "# 图标直接引用上游 models/item/*.json，保留原版分层与手持模型。",
        "items:",
    ]
    en_out: dict[str, str] = {}
    zh_out: dict[str, str] = {}
    tags = item_tags()
    for name in idx.item_models:
        if name.endswith("_active"):
            continue  # 激活态是渲染中间产物，不作为独立物品
        if name in idx.block_set:
            continue  # 方块物品由方块段生成
        if name in skip:
            continue
        key_en = en.get(f"item.botania.{name}") or en.get(f"block.botania.{name}") or name
        key_zh = zh.get(f"item.botania.{name}") or zh.get(f"block.botania.{name}") or key_en
        en_out[f"{NAMESPACE}.item.{name}"] = clean_name(key_en)
        zh_out[f"{NAMESPACE}.item.{name}"] = clean_name(key_zh)

        lines.append(f"  {NAMESPACE}:{name}:")
        lines.append(f"    material: {base_material(name)}")
        lines.append("    model:")
        lines.append("      type: minecraft:model")
        lines.append(f"      path: {NAMESPACE}:item/{name}")
        lines.append("    data:")
        lines.append(
            f"      item_name: '<!i><white><lang:{NAMESPACE}.item.{name}>'"
        )
        if unstackable(name):
            lines.append("      max_stack_size: 1")
        stats = tool_stats(name) if name.startswith("manasteel_") else None
        if stats is not None:
            # 耐久与附魔能力按上游表
            lines.append(f"      max_damage: {stats[1]}")
            lines.append("      enchantable:")
            lines.append(f"        value: {stats[2]}")
            armor_slot = ARMOR_SLOT.get(next((suffix for suffix in ARMOR_SLOT
                                              if name.endswith(suffix)), ""), None)
            if armor_slot is not None:
                # 外观用 Botania 1.7.10 的 2D 盔甲贴图（那会儿还是标准 64x32 布局，
                # 正好能贴到 CE 的 64x64 humanoid / humanoid_leggings 上）。
                asset = (name.replace("_helmet", "").replace("_chestplate", "")
                         .replace("_leggings", "").replace("_boots", ""))
                lines.append("      equippable:")
                lines.append(f"        slot: {armor_slot}")
                if asset in ("manasteel", "elementium", "terrasteel", "manaweave"):
                    lines.append(f"        asset_id: botania:{asset}")
            digging = tool_digging(name)
            if digging is not None:
                # 挖掘速度 6.2（铁 6.0）+ 钻石级挖掘等级，跟上游 ManasteelTier 一致
                blocks, speed = digging
                lines.append("      tool:")
                lines.append(f"        default_mining_speed: {speed}")
                lines.append("        damage_per_block_break: 1")
                lines.append("        rules:")
                lines.append("          - blocks: '" + blocks + "'")
                lines.append(f"            speed: {speed}")
                lines.append("            correct_for_drops: true")
        owned = tags.get(f"{NAMESPACE}:{name}")
        if owned:
            lines.append("    settings:")
            lines.append("      tags:")
            for tag in sorted(owned):
                lines.append(f"        - {tag}")
    return "\n".join(lines) + "\n", en_out, zh_out



def gen_render_items(names: list[str], idx: Index) -> str:
    """非满方块要建实体来渲染，这里给每个方块造一个"只用来显示"的物品。

    物品 id 是 botania:render/<方块名>，模型直接指向方块模型，
    玩家不会在背包里见到它（不进分类）。
    """
    lines = [
        "# 实体渲染用的外观物品（tools/gen_content.py 生成，勿手改）。",
        "items:",
    ]
    for name in names:
        lines.append(f"  {NAMESPACE}:render/{name}:")
        lines.append("    material: paper")
        lines.append("    model:")
        lines.append("      type: minecraft:model")
        model = idx.default_model(name) or ""
        lines.append(f"      path: {model}")
        lines.append("    data:")
        lines.append(f"      item_name: '<!i><white><lang:{NAMESPACE}.block.{name}>'")
    return "\n".join(lines) + "\n"



def gen_block_items(idx: Index, en: dict, zh: dict, skip: set[str]) -> str:
    """给每个方块注册配套的物品（block_item 行为）。

    `blocks:` 段里的 `settings.item` 只说明"这个方块掉落哪个物品"，
    并不会创建物品 —— 物品必须在 `items:` 段里自己定义，否则合成配方里
    引用 `botania:livingrock` 会被判成"物品不存在"。
    """
    tags = item_tags()
    lines = [
        "# 方块物品（tools/gen_content.py 生成，勿手改）。",
        "# behavior: block_item 把物品和 blocks: 段里的方块绑在一起。",
        "items:",
    ]
    names = sorted(set(idx.block_set) | set(skip))
    for name in names:
        if name not in idx.block_set:
            continue
        key_en = en.get(f"block.botania.{name}") or name
        key_zh = zh.get(f"block.botania.{name}") or key_en
        lines.append(f"  {NAMESPACE}:{name}:")
        lines.append(f"    material: {base_material(name)}")
        # 优先用上游的物品模型；没有就退回方块模型
        if name in idx.item_set:
            lines.append("    model:")
            lines.append("      type: minecraft:model")
            lines.append(f"      path: {NAMESPACE}:item/{name}")
        else:
            model = idx.default_model(name) or f"botania:block/{name}"
            lines.append("    model:")
            lines.append("      type: minecraft:model")
            lines.append(f"      path: {model}")
        lines.append("    data:")
        lines.append(f"      item_name: '<!i><white><lang:{NAMESPACE}.block.{name}>'")
        lines.append("    behavior:")
        lines.append("      type: block_item")
        lines.append(f"      block: {NAMESPACE}:{name}")
        owned = tags.get(f"{NAMESPACE}:{name}")
        if owned:
            lines.append("    settings:")
            lines.append("      tags:")
            for tag in sorted(owned):
                lines.append(f"        - {tag}")
        del key_en, key_zh
    return "\n".join(lines) + "\n"


def carrier_plan(idx: Index, flowers: set[str]) -> dict[str, str]:
    """暂不给花分配原版方块状态。

    曾经试过占用原版小花/树苗/甘蔗来让花"用方块模型渲染"，结果和 CraftEngine
    自带默认资源包（fairy_flower 等）抢状态，还把玩家种的原版花变成 Botania 花。
    现在统一回到"共享透明载体 + 实体渲染"，宁可渲染上妥协，也不劫持原版方块。
    """
    return {}


def gen_blocks(idx: Index, en: dict[str, str], zh: dict[str, str], skip: set[str]) -> tuple[str, dict, dict]:
    flowers = flower_ids()
    plant_carriers = carrier_plan(idx, flowers)
    lines: list[str] = [
        "# 方块（tools/gen_content.py 生成，勿手改）。",
        "# 整块方块走 solid + 方块模型；小模型方块走透明载体 + 实体渲染",
        "# （用音符盒当载体的话，客户端会按整块剔除它下方方块的顶面）。",
        "blocks:",
    ]
    en_out: dict[str, str] = {}
    zh_out: dict[str, str] = {}
    block_tags = item_tags()
    for name in sorted(idx.block_set):
        if name in skip:
            # 手写方块（core_blocks.yml）不生成方块配置，但**物品名还是要的**，
            # 否则方块物品会显示成翻译键原文 botania.block.xxx
            key_en = en.get(f"block.botania.{name}") or name
            key_zh = zh.get(f"block.botania.{name}") or key_en
            en_out[f"{NAMESPACE}.block.{name}"] = clean_name(key_en)
            zh_out[f"{NAMESPACE}.block.{name}"] = clean_name(key_zh)
            continue
        geometry = geometry_of(idx, name)
        model = idx.default_model(name)
        if not model:
            continue
        model = model.replace("botania:", "")
        key_en = en.get(f"block.botania.{name}") or name
        key_zh = zh.get(f"block.botania.{name}") or key_en
        en_out[f"{NAMESPACE}.block.{name}"] = clean_name(key_en)
        zh_out[f"{NAMESPACE}.block.{name}"] = clean_name(key_zh)

        lines.append(f"  {NAMESPACE}:{name}:")
        if name == "pure_daisy":
            # 纯洁雏菊自己有方块实体（净化相邻方块），不走普通产魔花那套
            lines.append("    behavior:")
            lines.append("      type: botania:pure_daisy")
        elif name in flowers:
            lines.append("    behavior:")
            lines.append("      type: botania:flower")
        elif geometry == "cross":
            # 野花（神秘花、发光花、蘑菇…）：只做"能不能站住"的判定，不建方块实体
            lines.append("    behavior:")
            lines.append("      type: botania:ground_plant")
        lines.append("    state:")
        if geometry == "cross" and name in plant_carriers:
            # 有专属 0 碰撞载体：直接盖方块模型，材质完整、能穿过去、不遮挡
            lines.append(f"      state: \"{plant_carriers[name]}\"")
            lines.append(f"      model: {NAMESPACE}:{model}")
        elif geometry == "full":
            lines.append("      auto_state: solid")
            lines.append(f"      model: {NAMESPACE}:{model}")
        else:
            lines.append("      auto_state:")
            lines.append("        type: sugar_cane")
            lines.append("        id: transparent")
            lines.append("      transparent: true")
            lines.append("      entity_culling: false")
            lines.append("      entity_renderer:")
            lines.append("        type: item_display")
            lines.append(f"        item: {NAMESPACE}:render/{name}")
            lines.append("        display_transform: none")
            lines.append("        scale: 1.0")
            lines.append("        translation: '0,0,0'")
            lines.append("        rotation: '0,180,0'")
        tool_tags = []
        lines.append("    settings:")
        if geometry == "cross" or name in flowers:
            # 和原版花一样：一碰就碎、不需要工具、音效用草方块那套
            lines.append("      sounds:")
            for event in ("break", "step", "place", "hit", "fall"):
                lines.append(f"        {event}: minecraft:block.grass.{event}")
            lines.append("      hardness: 0.0")
            lines.append("      resistance: 0.0")
            lines.append("      require_correct_tools: false")
        else:
            kind = tool_kind(name)
            # 音效按材质走：不设的话 CE 会沿用载体方块（音符盒/玻璃）的声音，
            # 挖石头听起来像敲玻璃，甚至没声。
            sound_group = "wood" if kind == "axe" else "stone"
            lines.append("      sounds:")
            for event, suffix in (("break", "break"), ("step", "step"),
                                  ("place", "place"), ("hit", "hit"), ("fall", "fall")):
                lines.append(f"        {event}: minecraft:block.{sound_group}.{suffix}")
            if kind == "pickaxe":
                # 和石头一样：要镐子，空手打不下来
                lines.append("      hardness: 1.5")
                lines.append("      resistance: 6.0")
                lines.append("      require_correct_tools: true")
                lines.append("      correct_tools:")
                lines.append("        - '#minecraft:pickaxes'")
                tool_tags = ["minecraft:mineable/pickaxe", "minecraft:needs_stone_tool"]
            elif kind == "axe":
                # 和木头一样：空手也能掉，但斧子更快
                lines.append("      hardness: 2.0")
                lines.append("      resistance: 3.0")
                lines.append("      require_correct_tools: false")
                tool_tags = ["minecraft:mineable/axe"]
            else:
                lines.append("      hardness: 1.5")
                lines.append("      resistance: 3.0")
                lines.append("      require_correct_tools: false")
                tool_tags = []
        if geometry != "full":
            lines.append("      can_occlude: false")
            lines.append("      is_suffocating: false")
            lines.append("      is_view_blocking: false")
            lines.append("      fluid_state: false")
        owned = list(block_tags.get(f"{NAMESPACE}:{name}", []))
        # 工具标签 + 物品标签合并写一个 tags 段（写两个会后者覆盖前者）
        all_tags = sorted(set(tool_tags if geometry != "cross" and name not in flowers else [])
                          | set(owned))
        if all_tags:
            lines.append("      tags:")
            for tag in all_tags:
                lines.append(f"        - {tag}")
        lines.append(f"      item: {NAMESPACE}:{name}")
        if wild_flower(name):
            # 野花不掉方块：原版是打掉直接掉花瓣（插件里的 PetalDropListener 负责）
            lines.append("    loot: {}")
        else:
            lines.append("    loot:")
            lines.append("      pools:")
            lines.append("        - rolls: 1")
            lines.append("          entries:")
            lines.append("            - type: item")
            lines.append(f"              item: {NAMESPACE}:{name}")
    return "\n".join(lines) + "\n", en_out, zh_out



# 0 碰撞 + 不遮挡的原版方块状态池。
# 花用它们做载体，就能"用方块模型渲染"（材质完整）同时保留"能穿过去"的手感。
# 每个状态只能绑一个模型，所以一个花一个格子，不够的才退回共享甘蔗 + 实体渲染。
PLANT_CARRIERS: list[str] = [
    # 原版小花（16）
    "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
    "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
    "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
    "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
    "minecraft:wither_rose", "minecraft:torchflower",
    "minecraft:closed_eyeblossom", "minecraft:open_eyeblossom",
    # 树苗（8）
    "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling",
    "minecraft:jungle_sapling", "minecraft:acacia_sapling",
    "minecraft:dark_oak_sapling", "minecraft:cherry_sapling",
    "minecraft:pale_oak_sapling",
    # 短草 / 蕨 / 灌木（4）
    "minecraft:short_grass", "minecraft:fern",
    "minecraft:dead_bush", "minecraft:torch",
    # 甘蔗（16 个 age）
] + [f"minecraft:sugar_cane[age={i}]" for i in range(16)] + [
    # 海泡菜（8）
] + [f"minecraft:sea_pickle[pickles={p},waterlogged=false]" for p in (1, 2, 3, 4)] + [
    # 小麦（8 个 age）
] + [f"minecraft:wheat[age={i}]" for i in range(8)] + [
    # 甜菜（4 个 age）
] + [f"minecraft:beetroots[age={i}]" for i in range(4)]


# 不可堆叠的东西：法杖、饰品类。上游这些物品的 maxStackSize 都是 1。
UNSTACKABLE_HINTS = (
    "wand", "twig_wand", "rod", "ring", "pendant", "belt", "charm", "cloak",
    "halo", "talisman", "monocle", "lexicon", "tablet", "flask", "vial",
    "slingshot", "astrolabe", "sextant", "clip", "cacophonium", "dice",
    "obedience_stick", "incense_stick", "flower_bag", "bauble_box",
    "spark_changer", "temperance_stone", "diva_charm", "black_hole_talisman",
    "third_eye", "monocle", "crafting_halo", "auto_crafting_halo",
    "world_seed", "laputa_shard", "goddess_charm", "keep_ivy", "itemfinder",
    "astrolabe", "sextant", "clip", "open_bucket", "slime_bottle",
    "thorn_chakram", "flare_chakram", "ender_dagger", "crystal_bow",
    "livingwood_bow", "mana_gun", "star_sword", "thunder_sword",
    "terra_sword", "mana_mirror", "brew_flask", "brew_vial",
)


def unstackable(name: str) -> bool:
    return any(hint in name for hint in UNSTACKABLE_HINTS)


TOOL_HINTS = ("sword", "pick", "axe", "shovel", "hoe", "shears", "bow", "helmet",
              "chestplate", "leggings", "boots", "rod", "wand", "lens", "ring",
              "pendant", "belt", "charm", "cloak", "halo", "talisman", "flask",
              "vial", "brew", "spark", "divining", "sextant", "astrolabe", "slingshot",
              "dice", "monocle", "clip", "twig_wand", "cacophonium", "horn_", "drum_")

MATERIAL_HINTS = ("petal", "rune", "dust", "ingot", "quartz", "diamond", "pearl",
                  "seed", "nugget", "cloth", "pattern", "dye", "mana_powder",
                  "manaweave", "spark_upgrade", "will", "lotus", "mantle")

FLOWER_HINTS = ("mystical_flower", "shiny_flower", "double_flower", "mushroom",
                "floating_flower", "floating_", "_chibi")


# 物品的"底层材质"：工具/护甲直接用原版同类物品当底，
# 这样挖掘速度、攻击力、护甲值、耐久全都是原版的正确行为，只需要换外观和名字。
MATERIAL_MAP = {
    "_pick": "iron_pickaxe",
    "_pickaxe": "iron_pickaxe",
    "_sword": "iron_sword",
    "_axe": "iron_axe",
    "_shovel": "iron_shovel",
    "_hoe": "iron_hoe",
    "_shears": "shears",
    "_helmet": "iron_helmet",
    "_chestplate": "iron_chestplate",
    "_leggings": "iron_leggings",
    "_boots": "iron_boots",
}


# 上游 BotaniaAPIImpl.java 里的真实数值：
#   ManasteelTier(300, 6.2F, 2, 3, 20)  —— 耐久/挖掘速度/攻击加成/挖掘等级/附魔能力
#   铁的是      (250, 6.0F, 2, 2, 14)，所以魔钢是"耐久更高、挖掘快一点、钻石级挖掘等级"
TOOL_STATS = {
    "_pick": ("iron_pickaxe", 300),
    "_sword": ("iron_sword", 300),
    "_axe": ("iron_axe", 300),
    "_shovel": ("iron_shovel", 300),
    "_hoe": ("iron_hoe", 300),
    "_shears": ("shears", 238),
}

# 魔钢护甲：护甲值同铁（靴2 腿5 胸6 头2），耐久倍率 18（铁是 15）
ARMOR_STATS = {
    "_helmet": ("iron_helmet", 198),
    "_chestplate": ("iron_chestplate", 288),
    "_leggings": ("iron_leggings", 270),
    "_boots": ("iron_boots", 234),
}


def tool_stats(name: str):
    """返回 (底层材质, 耐久上限, 附魔能力)。"""
    for suffix, (material, durability) in TOOL_STATS.items():
        if name.endswith(suffix):
            return material, durability, 20
    for suffix, (material, durability) in ARMOR_STATS.items():
        if name.endswith(suffix):
            return material, durability, 18
    return None


# 各类工具的"能挖什么"和速度（上游 ManasteelTier: efficiency 6.2）
DIGGING = {
    "_pick": ("#minecraft:mineable/pickaxe", 6.2),
    "_axe": ("#minecraft:mineable/axe", 6.2),
    "_shovel": ("#minecraft:mineable/shovel", 6.2),
    "_hoe": ("#minecraft:mineable/hoe", 6.2),
}


ARMOR_SLOT = {
    "_helmet": "head",
    "_chestplate": "chest",
    "_leggings": "legs",
    "_boots": "feet",
}


def tool_digging(name: str):
    for suffix, value in DIGGING.items():
        if name.endswith(suffix):
            return value
    return None


def base_material(name: str) -> str:
    for suffix, material in MATERIAL_MAP.items():
        if name.endswith(suffix):
            return material
    return "paper"


# 采集工具归类：和原版同类方块一致
WOOD_HINTS = ("wood", "log", "plank", "stripped", "twig", "frame")
STONE_HINTS = ("rock", "stone", "brick", "quartz", "pavement", "azulejo",
               "metamorphic", "shimmer", "slate", "deepslate", "prismarine")


def tool_kind(name: str) -> str:
    """返回 pickaxe / axe / none。"""
    if any(hint in name for hint in WOOD_HINTS):
        return "axe"
    if any(hint in name for hint in STONE_HINTS):
        return "pickaxe"
    return "none"


def wild_flower(name: str) -> bool:
    """野外自然长的花：打掉掉花瓣，而不是掉方块自己。"""
    return (name.endswith("_mystical_flower") or name.endswith("_shiny_flower")
            or name.endswith("_double_flower") or name.endswith("_mushroom"))


def is_flower(name: str, flowers: set[str]) -> bool:
    if name in flowers:
        return True
    return any(hint in name for hint in FLOWER_HINTS) and name not in ("flower_bag",)


def gen_categories(idx: Index, flowers: set[str], skip: set[str]) -> str:
    """把物品按功能分组，让 /ce 的浏览菜单能看见它们（CE 的浏览器只列分类里的物品）。"""
    groups: dict[str, list[str]] = {
        "flowers": [], "tools": [], "materials": [], "blocks": [], "misc": [],
    }
    for name in idx.item_models:
        if name.endswith("_active") or name in skip:
            continue
        full = f"{NAMESPACE}:{name}"
        if is_flower(name, flowers):
            groups["flowers"].append(full)
        elif name in idx.block_set:
            groups["blocks"].append(full)
        elif any(hint in name for hint in TOOL_HINTS):
            groups["tools"].append(full)
        elif any(hint in name for hint in MATERIAL_HINTS):
            groups["materials"].append(full)
        else:
            groups["misc"].append(full)
    for name in sorted(idx.block_set):
        if name in skip:
            continue
        full = f"{NAMESPACE}:{name}"
        if is_flower(name, flowers):
            groups["flowers"].append(full)
        elif full not in groups["blocks"]:
            groups["blocks"].append(full)

    titles = {
        "flowers": ("花与植物", 10, "botania:white_mystical_flower"),
        "tools": ("工具与饰品", 20, "botania:manasteel_pick"),
        "materials": ("材料与符文", 30, "botania:manasteel_ingot"),
        "blocks": ("方块", 40, "botania:livingwood"),
        "misc": ("其他", 50, "botania:lexicon"),
    }
    lines = [
        "# 物品浏览分类（tools/gen_content.py 生成，勿手改）。",
        "# CE 的 /ce 菜单只列这里登记过的物品。",
        "categories:",
    ]
    for key, items in groups.items():
        if not items:
            continue
        zh, priority, icon = titles[key]
        lines.append(f"  {NAMESPACE}:{key}:")
        lines.append(f"    name: '<!i><white><lang:{NAMESPACE}.category.{key}>'")
        lines.append(f"    icon: {icon}")
        lines.append(f"    priority: {priority}")
        lines.append("    list:")
        for item in sorted(set(items)):
            lines.append(f"      - {item}")
    return "\n".join(lines) + "\n"


def category_lang() -> dict[str, str]:
    return {
        "botania.category.flowers": "花与植物",
        "botania.category.tools": "工具与饰品",
        "botania.category.materials": "材料与符文",
        "botania.category.blocks": "方块",
        "botania.category.misc": "其他",
    }


def gen_lang(en_out: dict[str, str], zh_out: dict[str, str]) -> tuple[str, str]:
    def build(title: str, data: dict[str, str]) -> str:
        body = [
            f"# {title}（tools/gen_content.py 生成，勿手改）",
            "lang:",
            f"  {title}:",
        ]
        for k in sorted(data):
            body.append(f"    {k}: {yaml_str(data[k])}")
        return "\n".join(body) + "\n"

    return build("zh_cn", zh_out), build("en_us", en_out)


def write_block_items(idx: Index, block_entries: list[str], en: dict, zh: dict) -> None:
    """方块物品在 blocks 段里用 settings.item 绑定，CE 会一并注册为物品。"""


def main() -> int:
    if not INDEX.is_file():
        print("先运行 tools/extract_assets.py", file=sys.stderr)
        return 2
    idx = Index()
    en = load_lang("en_us")
    zh = load_lang("zh_cn")
    skip = core_overrides()

    flowers = flower_ids()
    items_txt, en1, zh1 = gen_items(idx, en, zh, skip)
    blocks_txt, en2, zh2 = gen_blocks(idx, en, zh, skip)
    # 核心方块（被 _core_overrides 跳过的那批）也要外观物品：它们同样走实体渲染
    carrier_names = set(carrier_plan(idx, flowers))
    render_names = [n for n in sorted(idx.block_set)
                    if geometry_of(idx, n) != "full" and n not in carrier_names]
    render_txt = gen_render_items(render_names, idx)
    block_items_txt = gen_block_items(idx, en, zh, skip)
    categories_txt = gen_categories(idx, flowers, skip)
    (CFG / "items" / "generated_render.yml").write_text(render_txt, encoding="utf-8")
    (CFG / "items" / "generated_block_items.yml").write_text(block_items_txt, encoding="utf-8")
    zh1.update(category_lang())
    en1.update(category_lang())
    zh_txt, en_txt = gen_lang({**en1, **en2}, {**zh1, **zh2})

    (CFG / "items").mkdir(parents=True, exist_ok=True)
    (CFG / "blocks").mkdir(parents=True, exist_ok=True)
    (CFG / "langs").mkdir(parents=True, exist_ok=True)
    (CFG / "items" / "generated_items.yml").write_text(items_txt, encoding="utf-8")
    (CFG / "blocks" / "generated_blocks.yml").write_text(blocks_txt, encoding="utf-8")
    (CFG / "categories.yml").write_text(categories_txt, encoding="utf-8")
    (CFG / "langs" / "zh_cn.yml").write_text(zh_txt, encoding="utf-8")
    (CFG / "langs" / "en_us.yml").write_text(en_txt, encoding="utf-8")

    print(f"物品 {items_txt.count(chr(10) + '  botania:')} 条")
    print(f"方块 {blocks_txt.count(chr(10) + '  botania:')} 条")
    print(f"语言键 {len(zh1) + len(zh2)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
