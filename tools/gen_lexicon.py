#!/usr/bin/env python3
"""把上游 Patchouli 格式的植物魔法辞典转成插件能直接读的资源。

用法：
    python3 tools/gen_lexicon.py [源码目录]

默认从 build/upstream_lexicon/ 读（tools/fetch_lexicon.py 会稀疏克隆上游），
正文文本来自 build/upstream_lang/zh_cn.json。

产物：
    plugin/src/main/resources/data/lexicon/lexicon.json

结构：
    {
      "categories": [
        {"id": "basics", "name": "入门", "icon": "botania:lexicon", "sortnum": 0,
         "entries": [
            {"id": "welcome", "title": "欢迎来到植物魔法世界", "icon": "botania:...",
             "pages": [{"type": "text", "text": "..."},
                       {"type": "recipe", "station": "crafting",
                        "lines": ["活石 ×5", "魔力珍珠 ×1"], "result": "魔力池 ×1"}]}
         ]}
      ]
    }

正文里的 Patchouli 标记会在这里清洗掉 —— 成书只认 § 格式码，不认 $(...)。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "upstream_lexicon"
LANG = ROOT / "build" / "upstream_lang" / "zh_cn.json"
RECIPES = ROOT / "build" / "recipes"
MC_LANG = ROOT / "build" / "upstream_lang" / "minecraft_zh_cn.json"
OUT = ROOT / "plugin" / "src" / "main" / "resources" / "data" / "lexicon"

# 成书单页上限（字符）；超了就拆页，末尾标"（续）"
BOOK_LINES = 13       # 成书一页 14 行，留一行给"（续）"
LINE_CHAR_LIMIT = 19  # 中文每行约 19 字不会溢出

# 章节显示顺序（上游 sortnum 之外再兜个底）
CATEGORY_ORDER = [
    "basics", "mana", "generating_flowers", "functional_flowers",
    "tools", "devices", "baubles", "ender", "alfhomancy", "challenges", "misc",
]


def clean_patchouli(text: str) -> str:
    """把 $(...) 标记清成成书能显示的纯文本 + § 格式码。"""
    if not text:
        return ""
    # $(l:path)显示文本$() → 显示文本
    text = re.sub(r"\$\(l:[^)]*\)(.*?)\$\(\)", r"\1", text)
    # $(item)铁锭$() / $(thing)…$() → 文本本身
    text = re.sub(r"\$\((?:item|thing|bold|italic|underlined|strike|obfuscated)\)(.*?)\$\(\)",
                  r"\1", text)
    # 颜色 $(0)..$(f) → 去掉
    text = re.sub(r"\$\([0-9a-fklmnor]\)", "", text)
    # 段落 / 换行
    text = text.replace("$(p)", "\n").replace("$(br2)", "\n").replace("$(br)", "\n")
    # 列表项
    text = re.sub(r"\$\(li\)", "· ", text)
    # 剩下的 $( 开头的标记一律清掉
    text = re.sub(r"\$\([^)]*\)", "", text)
    # 清理多余空行
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def wrap_lines(text: str) -> list[str]:
    """按成书的行宽硬折行（中文按字宽算，一个汉字占 1 格）。"""
    import unicodedata
    lines: list[str] = []
    for paragraph in text.split("\n"):
        if not paragraph:
            lines.append("")
            continue
        current = ""
        width = 0.0
        for ch in paragraph:
            w = 1.0 if unicodedata.east_asian_width(ch) in ("W", "F") else 0.55
            if width + w > LINE_CHAR_LIMIT:
                lines.append(current)
                current, width = ch, w
            else:
                current += ch
                width += w
        if current:
            lines.append(current)
    return lines


def split_pages(text: str) -> list[str]:
    """折行后按 13 行一页切，保证成书里不会被截断。"""
    lines = wrap_lines(text)
    if not lines:
        return [""]
    pages: list[str] = []
    for start in range(0, len(lines), BOOK_LINES):
        chunk = lines[start:start + BOOK_LINES]
        if start + BOOK_LINES < len(lines):
            chunk = chunk[:-1] + [chunk[-1] + "（续）"] if chunk else chunk
        pages.append("\n".join(chunk))
    return pages


# 配方库：id -> 配方对象（从 build/recipes 里的上游 datagen 数据加载）
RECIPE_INDEX: dict[str, dict] = {}


def load_recipes() -> None:
    for name in ("vanilla_crafting", "mana_infusion", "petal_apothecary",
                 "runic_altar", "elven_trade", "pure_daisy"):
        path = RECIPES / f"{name}.json"
        if not path.is_file():
            continue
        try:
            for entry in json.loads(path.read_text(encoding="utf-8")):
                rid = entry.get("id", "")
                if rid:
                    RECIPE_INDEX[rid] = entry
                    RECIPE_INDEX[rid.split("/")[-1]] = entry
        except Exception:
            continue


MC_NAMES: dict[str, str] = {}


def item_name(node, lang: dict[str, str]) -> str:
    """{item:...} / {tag:...} / 字符串 → 中文名。"""
    is_tag = False
    if isinstance(node, str):
        ident = node
        is_tag = node.startswith("#")
    elif isinstance(node, dict):
        if node.get("tag"):
            is_tag = True
            ident = "#" + str(node["tag"]).lstrip("#")
        else:
            ident = node.get("item") or node.get("name") or ""
            if isinstance(ident, dict):
                ident = ident.get("id", "")
    else:
        return "?"
    if not ident:
        return "?"
    if ident.startswith("#"):
        tag = ident[1:]
        pretty = {
            "botania:petals": "任意神秘花瓣",
            "botania:mana_dusts": "任意魔力粉尘",
            "botania:manasteel_ingots": "魔钢锭",
            "botania:livingwood_logs": "任意活木",
            "botania:livingrock": "活石",
            "botania:terrasteel_ingots": "泰拉钢锭",
            "minecraft:planks": "任意木板",
            "minecraft:logs": "任意原木",
            "minecraft:wool": "任意羊毛",
            "minecraft:coals": "任意煤炭",
        }.get(tag)
        return pretty if pretty else tag.split(":")[-1]
    path = ident.split(":", 1)[-1]
    ns = ident.split(":", 1)[0] if ":" in ident else "minecraft"
    if ns == "minecraft":
        for key in (f"block.minecraft.{path}", f"item.minecraft.{path}",
                    f"entity.minecraft.{path}"):
            if key in MC_NAMES:
                return MC_NAMES[key]
    for key in (f"{ns}.item.{path}", f"{ns}.block.{path}",
                f"item.{ns}.{path}", f"block.{ns}.{path}"):
        if key in lang and lang[key]:
            text = lang[key]
            if not text.startswith("botania.") and not text.startswith("item."):
                return text
    return path


def describe_recipe(recipe_id: str, lang: dict[str, str]) -> list[str]:
    """把一条配方翻成几行可读文本。"""
    entry = RECIPE_INDEX.get(recipe_id) or RECIPE_INDEX.get(recipe_id.split("/")[-1])
    if entry is None:
        return [f"（找不到配方数据：{recipe_id}）"]
    lines: list[str] = []
    kind = entry.get("type", "")

    # 有序合成：摆法 + 材料
    if "crafting_shaped" in kind and entry.get("pattern"):
        lines.append("摆法：")
        lines.extend("  " + str(row).replace(" ", "·") for row in entry["pattern"])
        key = entry.get("key", {})
        if key:
            lines.append("材料：")
            for symbol, node in key.items():
                lines.append(f"  {symbol} = {item_name(node, lang)}")
    # 无序合成
    elif "crafting_shapeless" in kind and entry.get("ingredients"):
        lines.append("材料（无序）：")
        for node in entry["ingredients"]:
            lines.append("  " + item_name(node, lang))
    elif entry.get("ingredients") or entry.get("input"):
        lines.append("材料：")
        for node in (entry.get("ingredients") or [entry["input"]]):
            lines.append("  " + item_name(node, lang))

    if entry.get("mana"):
        lines.append(f"魔力：{entry['mana']}")
    catalyst = entry.get("catalyst")
    if isinstance(catalyst, dict) and catalyst.get("block"):
        lines.append("催化剂：" + item_name(catalyst["block"], lang))
    if entry.get("reagent"):
        lines.append("种子：" + item_name(entry["reagent"], lang))

    result = entry.get("result") or entry.get("output")
    if isinstance(result, list):
        result = result[0] if result else {}
    if isinstance(result, dict):
        rid = result.get("item") or result.get("id") or result.get("name")
        if rid:
            count = int(result.get("count", 1))
            lines.append("→ " + item_name(rid, lang) + (f" ×{count}" if count > 1 else ""))
    return lines


def strip_ns(key: str) -> str:
    return key.split(":", 1)[-1] if ":" in key else key


def convert_page(page: dict, lang: dict[str, str]) -> list[dict]:
    """上游一页 → 插件侧的一到多条渲染指令。"""
    kind = page.get("type", "text")

    if kind == "text":
        raw = lang.get(page.get("text", ""), "")
        text = clean_patchouli(raw)
        if not text:
            return []
        return [{"type": "text", "text": chunk} for chunk in split_pages(text)]

    if kind == "lore_page":
        chunks = []
        for key in ("text", "title"):
            value = lang.get(page.get(key, ""), "")
            if value:
                chunks.append(clean_patchouli(value))
        text = "\n".join(chunks)
        return [{"type": "text", "text": c} for c in split_pages(text)] if text else []

    # 配方类页面：从配方库查出详情，文字化成"材料 → 成品"
    recipe_id = page.get("recipe") or page.get("recipe_id")
    if kind in ("crafting", "crafting_multi") or kind.startswith("botania:"):
        lines: list[str] = []
        title = clean_patchouli(lang.get(page.get("title", ""), ""))
        if title:
            lines.append(title)
        if recipe_id:
            lines.extend(describe_recipe(str(recipe_id), lang))
        # 页面自带的说明文字也带上
        body = clean_patchouli(lang.get(page.get("text", ""), ""))
        if body:
            if lines:
                lines.append("")
            lines.append(body)
        if not lines:
            lines.append("[本页配方数据缺失]")
        return [{"type": "text", "text": c} for c in split_pages("\n".join(lines))]

    # 插图 / 进度这类：保留说明文字
    label = {"image": "插图", "quest": "进度"}.get(kind, kind)
    title = lang.get(page.get("title", ""), "")
    body = clean_patchouli(lang.get(page.get("text", ""), ""))
    text = (clean_patchouli(title) + "\n" if title else "") + body
    if not text.strip():
        text = f"[{label}]"
    return [{"type": "text", "text": c} for c in split_pages(text)]


def main() -> int:
    if not SRC.is_dir():
        print(f"找不到上游辞典目录：{SRC}（先跑 tools/fetch_lexicon.py）", file=sys.stderr)
        return 2
    load_recipes()
    if MC_LANG.is_file():
        MC_NAMES.update(json.loads(MC_LANG.read_text(encoding="utf-8")))
    lang = json.loads(LANG.read_text(encoding="utf-8")) if LANG.is_file() else {}
    if not lang:
        print(f"警告：语言文件缺失 {LANG}，词条正文会是空的", file=sys.stderr)

    base = SRC / "en_us"
    entries_dir = base / "entries"
    categories_dir = base / "categories"

    # 章节
    categories: dict[str, dict] = {}
    for path in sorted(categories_dir.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        cid = strip_ns(for_category_id(path.stem))
        categories[cid] = {
            "id": cid,
            "name": clean_patchouli(lang.get(data.get("name", ""), path.stem)),
            "icon": data.get("icon", "botania:lexicon"),
            "sortnum": data.get("sortnum", 100),
            "entries": [],
        }

    # 词条
    total_pages = 0
    for path in sorted(entries_dir.rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        raw_category = strip_ns(data.get("category", ""))
        category = categories.get(raw_category)
        if category is None:
            continue
        title = clean_patchouli(lang.get(data.get("name", ""), path.stem))
        pages: list[dict] = []
        for page in data.get("pages", []):
            pages.extend(convert_page(page, lang))
        if not pages:
            continue
        entry_id = path.relative_to(entries_dir).with_suffix("").as_posix().replace("/", "_")
        category["entries"].append({
            "id": entry_id,
            "title": title or entry_id,
            "icon": data.get("icon", "botania:lexicon"),
            # 上游用 sortnum 排顺序，priority 的排最前
            "_priority": 0 if data.get("priority") else 1,
            "_sortnum": data.get("sortnum", 100),
            "pages": pages,
        })
        total_pages += len(pages)

    # 词条排序：priority 优先，其次 sortnum
    for category in categories.values():
        category["entries"].sort(key=lambda e: (e.pop("_priority"), e.pop("_sortnum"), e["id"]))

    ordered = []
    for cid in CATEGORY_ORDER:
        if cid in categories:
            ordered.append(categories.pop(cid))
    ordered.extend(sorted(categories.values(), key=lambda c: (c["sortnum"], c["id"])))

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"categories": ordered}
    (OUT / "lexicon.json").write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    entries_count = sum(len(c["entries"]) for c in ordered)
    print(f"章节 {len(ordered)} 个 / 词条 {entries_count} 个 / 页面 {total_pages} 页")
    for c in ordered:
        print(f"  {c['id']:22s} {len(c['entries']):3d} 词条  {c['name']}")
    print(f"输出 -> {OUT / 'lexicon.json'}")
    return 0


def for_category_id(stem: str) -> str:
    return stem


if __name__ == "__main__":
    raise SystemExit(main())
