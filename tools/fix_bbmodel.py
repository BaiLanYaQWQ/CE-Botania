#!/usr/bin/env python3
"""把 MiMo 转出来的 bbmodel 修成 CE 官方工具能吃的结构。

用法：python3 tools/fix_bbmodel.py <输入.bbmodel> [输出.bbmodel]

## 修什么

1. **元素归属**：原文件 38 个 element 全挂在顶层、43 个分组全是空的，
   工具报"204 个面未标记身体部位"。这里按元素坐标把它们分到
   头 / 身 / 左右手 / 左右腿六个标准部位。
2. **只留一个 head**：原来 head / hat / head_outer / helm* 一堆分组都算"头部"，
   CE 工具和 Iris 只认第一个。现在把装饰组降级成标准部位的子分组。
3. **2D 回退纹理**：工具要求嵌入名为 `<盔甲名>_humanoid` 与
   `<盔甲名>_humanoid_leggings` 的纹理（第一视角/低配时的贴图回退）。

坐标分类依据：Botania 的盔甲模型是标准人形骨架
（head 在 y=24~32，body 12~24，腿 0~12，手臂 |x|≥4）。
"""
from __future__ import annotations

import base64
import json
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ARMOR_TEX = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "textures" / "entity" / "equipment"

# 装饰组 → 它该归属的标准部位
PART_MAP = {
    "head": "head", "hat": "head", "head_outer": "head",
    "helm": "head", "helm_front": "head",
    "body": "body", "belt": "body", "body_outer": "body",
    "body_top": "body", "body_bottom": "body",
    "left_arm": "left_arm", "left_arm_outer": "left_arm",
    "left_arm_main": "left_arm", "left_arm_pauldron": "left_arm",
    "right_arm": "right_arm", "right_arm_outer": "right_arm",
    "right_arm_main": "right_arm", "right_arm_pauldron": "right_arm",
    "left_leg": "left_leg", "left_leg_outer": "left_leg",
    "left_boot_top": "left_leg",
    "right_leg": "right_leg", "right_leg_outer": "right_leg",
    "right_boot_top": "right_leg",
}

PARTS = ("head", "body", "left_arm", "right_arm", "left_leg", "right_leg")


def part_of_prefix(name: str) -> str | None:
    """按名字前缀判断装饰组属于哪个部位。"""
    if name in PART_MAP:
        return PART_MAP[name]
    for prefix in ("helm", "hat"):
        if name.startswith(prefix):
            return "head"
    for prefix, part in (("left_arm", "left_arm"), ("right_arm", "right_arm"),
                         ("left_leg", "left_leg"), ("right_leg", "right_leg"),
                         ("left_boot", "left_leg"), ("right_boot", "right_leg"),
                         ("body", "body")):
        if name.startswith(prefix):
            return part
    return None


# 标准人形骨架下各部位"重心"（Botania 原版模型的分组原点）
PART_ANCHORS = {
    "head":      (0.0, 26.0),
    "body":      (0.0, 18.0),
    "left_arm":  (-5.5, 18.0),
    "right_arm": (5.5, 18.0),
    "left_leg":  (-2.0, 6.0),
    "right_leg": (2.0, 6.0),
}


def classify_element(element: dict, origin_offset: tuple[float, float] = (0.0, 0.0)) -> str:
    """按元素包围盒中心离哪个部位最近来归类。

    不用固定阈值 —— 这套模型被整体抬高了 12px（作者为了让模型浮在网格上），
    硬编码的 y 分界会把腿全部分进身体里。用"最近重心"更稳。
    """
    box_from = element.get("from") or [0, 0, 0]
    box_to = element.get("to") or [0, 0, 0]
    x = (float(box_from[0]) + float(box_to[0])) / 2 + origin_offset[0]
    y = (float(box_from[1]) + float(box_to[1])) / 2 + origin_offset[1]
    best, best_distance = "body", float("inf")
    for part, (ax, ay) in PART_ANCHORS.items():
        distance = (x - ax) ** 2 + (y - ay) ** 2
        if distance < best_distance:
            best_distance = distance
            best = part
    return best


def texture_b64(path: Path) -> str | None:
    if not path.is_file():
        return None
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else src.with_name(src.stem + "_fixed.bbmodel")
    data = json.loads(src.read_text(encoding="utf-8"))
    armor = src.stem.replace("botania_", "").replace("_armor", "")

    elements = data.get("elements", [])
    old_groups = data.get("groups", [])

    # 1) 元素按坐标归类
    buckets: dict[str, list[str]] = {part: [] for part in PARTS}
    for element in elements:
        part = classify_element(element)
        buckets[part].append(element.get("uuid") or element.get("name"))

    # 2) 建标准部位分组，装饰组作为它的子分组
    def make_group(name: str) -> dict:
        return {
            "name": name,
            "uuid": str(uuid.uuid4()),
            "export": True,
            "locked": False,
            "scope": 0,
            "selected": False,
            "visibility": True,
            "_static": {"properties": {}, "temp_data": {}},
            "origin": [0, 24.42857, 0],
            "rotation": [0, 0, 0],
            "color": 0,
            "children": [],
            "reset": False,
            "shade": True,
            "mirror_uv": False,
            "autouv": 0,
            "isOpen": True,
            "primary_selected": False,
        }

    part_groups = {part: make_group(part) for part in PARTS}
    used: set[str] = set()
    for group in old_groups:
        name = group.get("name", "")
        part = part_of_prefix(name)
        if part is None:
            continue
        if name == part:
            used.add(name)
            continue
        # 装饰分组：清空它自己的元素（元素统一归标准部位），挂成子分组
        child = make_group(name)
        child["elements"] = []
        part_groups[part]["children"].append(child)
        used.add(name)

    # 3) 写入元素归属
    for part, ids in buckets.items():
        part_groups[part]["elements"] = ids

    data["groups"] = [part_groups[part] for part in PARTS]

    # 4) 补 2D 回退纹理（<盔甲>_humanoid / <盔甲>_humanoid_leggings）
    textures = data.setdefault("textures", [])
    existing = {t.get("name") for t in textures}
    def as_int(value, default=0):
        try:
            return int(value)
        except (TypeError, ValueError):
            return default

    next_id = max([as_int(t.get("id")) for t in textures], default=-1) + 1
    for suffix, folder in (("humanoid", "humanoid"), ("humanoid_leggings", "humanoid_leggings")):
        name = f"{armor}_{suffix}"
        if name in existing:
            continue
        path = ARMOR_TEX / folder / f"{armor}.png"
        source = texture_b64(path)
        if source is None:
            print(f"  跳过 {name}：找不到 {path}")
            continue
        textures.append({
            "name": name,
            "id": str(next_id),
            "group": "",
            "namespace": "",
            "folder": "entity/equipment/" + folder,
            "width": 64,
            "height": 64,
            "uv_width": 64,
            "uv_height": 64,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "uuid": str(uuid.uuid4()),
            "relative_path": "",
            "source": source,
        })
        next_id += 1

    dst.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"修复完成 -> {dst}")
    for part in PARTS:
        print(f"  {part:10s} 元素 {len(part_groups[part]['elements']):>3d}  子分组 {len(part_groups[part]['children']):>2d}")
    print(f"  纹理 {len(textures)} 张：{[t.get('name') for t in textures]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
