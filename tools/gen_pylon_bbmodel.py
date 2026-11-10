#!/usr/bin/env python3
"""按 6 面截图生成魔力水晶的 .bbmodel（CE 家具/方块用）。

结构（从截图判读）：
  · 底座：4 块深色薄板围成方框（前/后/左/右）
  · 核心：中心一个深色小方柱
  · 水晶：4 根绿色柱体，位于四角，高度参差（后右最高）
坐标：BlockBench 方块模型惯例 —— 原点在方块底部中心，y 向上，x/z ∈ [-8,8]
"""
from __future__ import annotations

import base64
import io
import json
import struct
import zlib
from pathlib import Path

OUT_DIR = Path("pack/blueprint/botania")

# 色板（从截图取样）
PALETTE = [
    ("dark",  (0x3A, 0x1A, 0x12)),   # 底座深棕
    ("dark2", (0x2A, 0x10, 0x0A)),   # 底座更暗
    ("green", (0x4C, 0xF2, 0x3C)),   # 水晶亮绿
    ("green2",(0x2E, 0xD0, 0x28)),   # 水晶暗绿
    ("lime",  (0xB8, 0xF0, 0x8A)),   # 水晶浅黄绿
    ("white", (0xFF, 0xFF, 0xFF)),   # 高光
]

def make_texture() -> tuple[str, dict[str, int]]:
    """16x16 纹理：横向 6 个 2px 宽色块。返回 base64 与色块索引。"""
    w = h = 16
    px = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]
    slots = {}
    for i, (name, rgb) in enumerate(PALETTE):
        x0 = i * 2
        slots[name] = x0
        for y in range(h):
            for x in range(x0, x0 + 2):
                px[y][x] = (*rgb, 255)
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in px)
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    return "data:image/png;base64," + base64.b64encode(png).decode(), slots


def face_uv(slot_x: int) -> dict:
    """给所有面用同一个 2x2 色块。"""
    quad = [slot_x, 0, slot_x + 2, 16]
    return {face: {"uv": quad, "texture": 0}
            for face in ("north", "east", "south", "west", "up", "down")}


def main() -> int:
    tex_b64, slots = make_texture()
    elements = []
    outliner = []

    def cube(name: str, fx, fy, fz, tx, ty, tz, color: str):
        element = {
            "name": name,
            "from": [fx, fy, fz],
            "to": [tx, ty, tz],
            "faces": face_uv(slots[color]),
        }
        elements.append(element)
        outliner.append(len(elements) - 1)

    # —— 底座：4 块深色板（围成方框，高 6）——
    cube("base_front", -7, 0, -8,  7, 6, -5, "dark")
    cube("base_back",  -7, 0,  5,  7, 6,  8, "dark")
    cube("base_left",  -8, 0, -5, -5, 6,  5, "dark2")
    cube("base_right",  5, 0, -5,  8, 6,  5, "dark2")

    # —— 中心核心（深色小柱）——
    cube("core", -3, 0, -3, 3, 8, 3, "dark2")

    # —— 四根水晶柱（对角四角，高度参差；后右最高）——
    cube("crystal_fl", -6, 6, -6, -2, 18, -2, "green")
    cube("crystal_fr",  2, 6, -6,  6, 14, -2, "lime")
    cube("crystal_bl", -6, 6,  2, -2, 12,  6, "lime")
    cube("crystal_br",  2, 6,  2,  6, 21,  6, "green")

    # —— 顶部小高光块 ——
    cube("tip_br", 3, 21, 3, 5, 23, 5, "white")
    cube("tip_fl", -5, 18, -5, -3, 20, -3, "white")

    model = {
        "meta": {"format_version": "4.10", "model_format": "java_block"},
        "name": "mana_pylon",
        "model_identifier": "mana_pylon",
        "resolution": {"width": 16, "height": 16},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "name": "mana_pylon.png",
            "id": "0",
            "width": 16,
            "height": 16,
            "uv_width": 16,
            "uv_height": 16,
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "source": tex_b64,
        }],
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / "mana_pylon.bbmodel"
    out.write_text(json.dumps(model, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"生成 {out}（{len(elements)} 个立方体）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
