#!/usr/bin/env python3
"""把 Botania 的盔甲模型（Java 的 MeshDefinition）转成 Blockbench 的 .bbmodel。

用法：
    python3 tools/botania_to_bbmodel.py [源码目录]

默认从 build/upstream_models/ 读（tools/fetch_armor_models.py 会下载），
输出到 export/bbmodel/<模型名>.bbmodel，直接拖进 Blockbench 打开。

为什么需要它：Botania 的盔甲不是资源包里的 JSON 模型，而是
`client/model/armor/*ArmorModel.java` 里用 CubeListBuilder 一行行搭出来的
MeshDefinition。想改外观只能改代码；转成 bbmodel 之后就能在 Blockbench 里
可视化编辑，再导出成 CraftEngine 能吃的 3D 模型。

坐标系换算：
  * Minecraft 的 ModelPart 空间里 y 向下为正，Blockbench 里 y 向上为正 → y 取反
  * PartPose 的 offset / offsetAndRotation 逐层累加到子部件
  * cube 的 from/to = 父级累计偏移 + addBox 的坐标
  * 每层骨骼的枢轴 = 父级累计偏移（取反后作为 Blockbench 的 origin）
"""
from __future__ import annotations

import json
import math
import re
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "upstream_models"
OUT = ROOT / "export" / "bbmodel"

# 模型文件 → (输出名, 纹理名)
MODELS = {
    "ManasteelArmorModel": ("manasteel_armor", "armor_manasteel.png"),
    "ElementiumArmorModel": ("elementium_armor", "armor_elementium.png"),
    "TerrasteelArmorModel": ("terrasteel_armor", "armor_terrasteel.png"),
    "ManaweaveRobeModel": ("manaweave_armor", "armor_manaweave.png"),
}

# 允许在表达式里出现的常量
ENV = {"Mth.PI": math.pi, "Math.PI": math.pi, "PI": math.pi}


def evaluate(expr: str) -> float:
    """算 4.5F / -Mth.PI / 12 这类简单表达式。"""
    text = expr.strip().rstrip("FfDd")
    text = text.replace("Mth.PI", repr(math.pi)).replace("Math.PI", repr(math.pi))
    text = text.replace("Mth.HALF_PI", repr(math.pi / 2))
    text = re.sub(r"(?<![\w.])-", " -", text)
    # 只保留数字、运算符、括号，防止执行任意代码
    if not re.fullmatch(r"[\d\s\.\+\-\*/\(\)eE]+", text):
        return 0.0
    try:
        return float(eval(text, {"__builtins__": {}}, {}))
    except Exception:
        return 0.0


def matching_paren(text: str, start: int) -> int:
    """从 text[start] == '(' 开始，返回匹配的 ')' 下标。"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_args(text: str) -> list[str]:
    """按顶层逗号切分参数。"""
    parts, depth, current = [], 0, ""
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current)
    return [p.strip() for p in parts]


def parse_pose(expr: str):
    """解析 PartPose.ZERO / offset(x,y,z) / offsetAndRotation(...)。"""
    expr = expr.strip()
    if "offsetAndRotation" in expr:
        start = expr.index("offsetAndRotation") + len("offsetAndRotation")
        end = matching_paren(expr, start)
        args = split_args(expr[start + 1:end])
        values = [evaluate(a) for a in args[:6]] + [0.0] * (6 - min(6, len(args)))
        return values[0], values[1], values[2], values[3], values[4], values[5]
    if "offset" in expr:
        start = expr.index("offset") + len("offset")
        end = matching_paren(expr, start)
        args = split_args(expr[start + 1:end])
        values = [evaluate(a) for a in args[:3]] + [0.0] * (3 - min(3, len(args)))
        return values[0], values[1], values[2], 0.0, 0.0, 0.0
    return 0.0, 0.0, 0.0, 0.0, 0.0, 0.0


def parse_model(source: str) -> list[dict]:
    """把一份 Java 源码里的 all addOrReplaceChild 解析成扁平部件列表。"""
    parts: list[dict] = []
    # 变量名 → 累计偏移 (x, y, z)
    accum: dict[str, tuple[float, float, float]] = {"root": (0.0, 0.0, 0.0)}

    pattern = re.compile(r"(?:var\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\s*\(")
    for match in pattern.finditer(source):
        var_name = match.group(1)
        parent = match.group(2)
        open_paren = match.end() - 1
        close_paren = matching_paren(source, open_paren)
        if close_paren < 0:
            continue
        inner = source[open_paren + 1:close_paren]
        args = split_args(inner)
        if len(args) < 3:
            continue
        name = args[0].strip().strip('"')
        builder = args[1]
        pose_expr = args[2]

        pose = parse_pose(pose_expr)
        parent_offset = accum.get(parent, (0.0, 0.0, 0.0))
        offset = (parent_offset[0] + pose[0],
                  parent_offset[1] + pose[1],
                  parent_offset[2] + pose[2])
        if var_name:
            accum[var_name] = offset

        # 提取所有 addBox
        boxes = []
        uv = (0.0, 0.0)
        uv_match = re.search(r"texOffs\s*\(\s*([^,]+),\s*([^)]+)\)", builder)
        if uv_match:
            uv = (evaluate(uv_match.group(1)), evaluate(uv_match.group(2)))
        for box_match in re.finditer(r"addBox\s*\(", builder):
            bp = box_match.end() - 1
            be = matching_paren(builder, bp)
            box_args = split_args(builder[bp + 1:be])
            if len(box_args) < 6:
                continue
            values = [evaluate(a) for a in box_args[:6]]
            boxes.append(values)

        parts.append({
            "name": name,
            "var": var_name,
            "parent_var": parent,
            "offset": offset,
            "rotation": (pose[3], pose[4], pose[5]),
            "uv": uv,
            "boxes": boxes,
        })
    return parts


def to_bbmodel(parts: list[dict], model_name: str, texture: str) -> dict:
    """组装成 Blockbench 的 .bbmodel 结构（Java Block/Item 格式）。"""
    elements = []
    outliner: dict[str, dict] = {}

    for part in parts:
        ox, oy, oz = part["offset"]
        rx, ry, rz = part["rotation"]
        # y 轴翻转：MC 的 y 向下 → Blockbench 的 y 向上
        bone = outliner.setdefault(part["name"], {
            "name": part["name"],
            "origin": [ox, -oy, oz],
            "rotation": [math.degrees(rx), math.degrees(ry), math.degrees(rz)],
            "uuid": str(uuid.uuid4()),
            "children": [],
        })
        for box in part["boxes"]:
            x, y, z, w, h, d = box
            if w <= 0 or h <= 0 or d <= 0:
                continue
            # Minecraft 用 2x2x2 的小方块当"锚点"，本身不可见；Blockbench 里看到会碍事
            if (w, h, d) == (2, 2, 2):
                continue
            u, v = part["uv"]
            element = {
                "name": f'{part["name"]}_cube{len(elements)}',
                "box_uv": False,
                "rescale": False,
                "locked": False,
                "light_emission": 0,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": [ox + x, -(oy + y + h), oz + z],
                "to": [ox + x + w, -(oy + y), oz + z + d],
                "autouv": 0,
                "color": 0,
                "origin": [ox, -oy, oz],
                "faces": {},
                "type": "cube",
                "uuid": str(uuid.uuid4()),
            }
            # 六个面按 MC 的 box UV 展开规则给矩形
            faces = {
                "north": [u + d, v + d, u + d + w, v + d + h],
                "east": [u, v + d, u + d, v + d + h],
                "south": [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
                "west": [u + d + w, v + d, u + d + w + d, v + d + h],
                "up": [u + d, v, u + d + w, v + d],
                "down": [u + d + w, v, u + d + w + w, v + d],
            }
            for face_name, rect in faces.items():
                element["faces"][face_name] = {
                    "uv": [round(c, 3) for c in rect],
                    "texture": 0,
                }
            elements.append(element)
            bone["children"].append(element["uuid"])

    # 父子关系：外层用变量名（bodyTop），部件名是 body_top —— 先建变量名 → 部件名映射
    var_to_name = {p["var"]: p["name"] for p in parts if p.get("var")}
    parent_of = {p["name"]: var_to_name.get(p["parent_var"], p["parent_var"]) for p in parts}
    roots = []
    for name, bone in outliner.items():
        parent = parent_of.get(name, "root")
        if parent in outliner and parent != name and parent_of.get(parent) != name:
            outliner[parent]["children"].append(bone)
        else:
            roots.append(bone)

    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "java_block",
            "box_uv": False,
        },
        "name": model_name,
        "model_identifier": model_name,
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 64, "height": 128},
        "elements": elements,
        "outliner": roots,
        "textures": [{
            "path": "",
            "name": texture,
            "folder": "entity",
            "namespace": "",
            "id": "0",
            "group": "",
            "width": 64,
            "height": 128,
            "uv_width": 64,
            "uv_height": 128,
            "particle": True,
            "use_as_default": False,
            "layers_enabled": False,
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": False,
            "uuid": str(uuid.uuid4()),
            "relative_path": "",
            "source": "",
        }],
    }


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    made = 0
    for class_name, (model_name, texture) in MODELS.items():
        src = SRC / f"{class_name}.java"
        if not src.is_file():
            print(f"跳过 {class_name}：找不到 {src}")
            continue
        source = src.read_text(encoding="utf-8")
        parts = parse_model(source)
        if not parts:
            print(f"{class_name}: 没解析出任何部件，检查源码格式")
            continue
        data = to_bbmodel(parts, model_name, texture)
        target = OUT / f"{model_name}.bbmodel"
        target.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{class_name} -> {target.name}  "
              f"({len(data['elements'])} 个方块, {len(parts)} 个部件)")
        made += 1
    print(f"完成 {made} 个模型，输出目录 {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
