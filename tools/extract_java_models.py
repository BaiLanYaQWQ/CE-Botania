#!/usr/bin/env python3
"""从 Botania 的 Java 模型类提取几何体，生成 Minecraft block model。

上游有些方块（水晶、沙漏、风箱、酿造台、晴天娃娃）的几何体写在
`client/model/*.java` 的 `CubeListBuilder.addBox(...)` 里，资源包里只有
一个 `builtin/entity` 占位。这里解析那些 addBox，转成 block model 的 elements。

用法：python3 tools/extract_java_models.py <botania-repo 路径>
"""
from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "pack/resourcepack/assets/botania/models/block"

# 要提取的模型：方块名 → (Java 文件, 纹理键)
TARGETS = {
    "mana_pylon":   ("ManaPylonModel.java",   "botania:model/pylon_mana"),
    "natura_pylon": ("NaturaPylonModel.java", "botania:model/pylon_natura"),
    "gaia_pylon":   ("GaiaPylonModel.java",   "botania:model/pylon_gaia"),
    "hourglass":    ("HourglassModel.java",   "botania:model/hourglass"),
    "bellows":      ("BellowsModel.java",     "botania:model/bellows"),
    "brewery":      ("BotanicalBreweryModel.java", "botania:model/brewery"),
}

BOX_RE = re.compile(
    r'(?P<child>\w+)",\s*CubeListBuilder\.create\(\)\.texOffs\((?P<u>\d+),\s*(?P<v>\d+)\)\s*'
    r'\.addBox\((?P<x>[-\d.]+)F?,\s*(?P<y>[-\d.]+)F?,\s*(?P<z>[-\d.]+)F?,\s*'
    r'(?P<w>[-\d.]+)F?,\s*(?P<h>[-\d.]+)F?,\s*(?P<d>[-\d.]+)F?\)\s*,\s*'
    r'PartPose\.(?P<pose>offset|offsetAndRotation)\((?P<ox>[-\d.]+)F?,\s*(?P<oy>[-\d.]+)F?,\s*(?P<oz>[-\d.]+)F?'
    r'(?P<extra>[^)]*)\)',
    re.S,
)

def _num(value: str) -> float:
    return float(value.replace("F", ""))

def _face_uv(u, v, w, h, d):
    """MC 标准盒子 UV 展开。"""
    return {
        "down":  [u + d, v, u + d + w, v + d],
        "up":    [u + d + w, v + d, u + d + 2 * w, v + 2 * d],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west":  [u, v + d, u + d, v + d + h],
        "east":  [u + d + w, v + d, u + d + w + d, v + d + h],
    }

def texture_size(texture: str) -> tuple[int, int]:
    """拿到纹理的真实像素尺寸，UV 要按它归一化。"""
    from PIL import Image
    path = ROOT / "pack/resourcepack/assets/botania/textures" / (texture.split(":")[1] + ".png")
    if path.is_file():
        with Image.open(path) as im:
            return im.size
    return 64, 64


def extract(java: Path, texture: str) -> dict | None:
    text = java.read_text(encoding="utf-8")
    tex_w, tex_h = texture_size(texture)
    # 26.1+ 的模型 UV 是 0~16 的归一化空间，像素坐标要除以 (尺寸/16)
    uv_scale_x = 16.0 / tex_w
    uv_scale_y = 16.0 / tex_h
    elements = []
    for m in BOX_RE.finditer(text):
        x, y, z = _num(m["x"]), _num(m["y"]), _num(m["z"])
        w, h, d = _num(m["w"]), _num(m["h"]), _num(m["d"])
        ox, oy, oz = _num(m["ox"]), _num(m["oy"]), _num(m["oz"])
        # 模型坐标（中心原点、y 向下）→ 方块坐标（角原点、y 向下）
        bx = x + ox + 8
        by = 16 - (y + oy)
        bz = z + oz + 8
        uv = _face_uv(int(m["u"]), int(m["v"]), int(w), int(h), int(d))
        faces = {face: {"texture": "#all",
                        "uv": [round(quad[0] * uv_scale_x, 3), round(quad[1] * uv_scale_y, 3),
                               round(quad[2] * uv_scale_x, 3), round(quad[3] * uv_scale_y, 3)]}
                 for face, quad in uv.items()}
        element = {
            "from": [round(bx, 2), round(by, 2), round(bz, 2)],
            "to": [round(bx + w, 2), round(by + h, 2), round(bz + d, 2)],
            "faces": faces,
        }
        # 绕 y 轴旋转的盒子：block model 不支持任意旋转，直接把立方体
        # 旋转后的等效包围盒算出来（90°/180°/270° 都是轴对齐的，能精确表达）
        extra = m["extra"] or ""
        yaw = 0
        if "Rotation" in m["pose"]:
            if "-Mth.PI / 2" in extra:
                yaw = -90
            elif "Mth.PI / 2" in extra:
                yaw = 90
            elif "Mth.PI" in extra:
                yaw = 180
        if yaw and "PI" in extra:
            # 先用未翻转的 model 坐标算，再转方块坐标
            x0, x1 = x + ox, x + w + ox
            z0, z1 = z + oz, z + d + oz
            if yaw == 180:
                x0, x1 = -x1, -x0
                z0, z1 = -z1, -z0
            elif yaw == 90:
                x0, x1, z0, z1 = -z1, -z0, x0, x1
            elif yaw == -90:
                x0, x1, z0, z1 = z0, z1, -x1, -x0
            bx, bz = x0 + 8, z0 + 8
            element = {
                "from": [round(bx, 2), round(by, 2), round(bz, 2)],
                "to": [round(x1 + 8, 2), round(by + h, 2), round(z1 + 8, 2)],
                "faces": {face: {"texture": "#all",
                                 "uv": [round(quad[0] * uv_scale_x, 3), round(quad[1] * uv_scale_y, 3),
                                        round(quad[2] * uv_scale_x, 3), round(quad[3] * uv_scale_y, 3)]}
                          for face, quad in uv.items()},
            }
        elements.append(element)
    if not elements:
        return None
    return {
        "textures": {"particle": texture, "all": texture},
        "elements": elements,
    }

def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    model_dir = Path(sys.argv[1]) / "Xplat/src/main/java/vazkii/botania/client/model"
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (java_name, texture) in TARGETS.items():
        java = model_dir / java_name
        if not java.is_file():
            print(f"  跳过 {name}：找不到 {java_name}")
            continue
        model = extract(java, texture)
        if model is None:
            print(f"  跳过 {name}：没解析到 addBox")
            continue
        (OUT / f"{name}.json").write_text(
            json.dumps(model, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  {name}: {len(model['elements'])} 个立方体")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
