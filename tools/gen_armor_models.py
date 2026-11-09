#!/usr/bin/env python3
"""程序化生成 3D 盔甲模型（BlockBench 物品模型格式）+ 贴图。

用法：python3 tools/gen_armor_models.py

为什么要自己生成：Botania 原版的盔甲是代码构建的 3D 网格（client/model/armor/
ModelArmorManasteel），不是能用资源包直接还原的东西。而 CraftEngine 的自定义装备
只能把 2D 纹理贴到标准玩家模型上，做不出"肩甲、裙甲"这些凸起。

所以这里走另一条路 —— 和森罗物语的草帽同一套机制：
  1. 物品 equippable 只给 slot，不给 asset_id（客户端就用物品模型渲染）
  2. 模型用 minecraft:select + display_context，head 场合用 3D 模型
  3. 3D 模型是普通物品模型 JSON，靠 display.head 的变换摆到头/身上

产物：
  pack/resourcepack/assets/botania/models/item/armor/<材质>_<部位>.json
  pack/resourcepack/assets/botania/textures/item/armor/<材质>_<部位>.png
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "models" / "item" / "armor"
TEXTURES = ROOT / "pack" / "resourcepack" / "assets" / "botania" / "textures" / "item" / "armor"

# 各材质的配色（主色, 亮部, 暗部）
PALETTES = {
    "manasteel": ((0, 66, 197), (86, 150, 255), (0, 28, 96)),
    "elementium": ((233, 0, 129), (255, 128, 196), (120, 0, 66)),
    "terrasteel": ((63, 200, 0), (156, 255, 96), (26, 100, 0)),
    "manaweave": ((0, 150, 165), (96, 226, 236), (0, 72, 84)),
}

# display 变换：照森罗物语草帽的数值，先把 head 对齐
DISPLAY = {
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0.25, -2, 0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0.25, -2, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.95, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.95, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 3, 0], "scale": [0.7, 0.7, 0.7]},
    "gui": {"rotation": [30, -135, 0], "translation": [0, 2, 0], "scale": [0.78, 0.78, 0.78]},
    "head": {"rotation": [0, 0, 0], "translation": [-0.25, 10, 0.75], "scale": [1.95, 1.95, 1.95]},
    "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -7.75], "scale": [2, 2, 2]},
}


def box(x1, y1, z1, x2, y2, z2, uv_origin=(0, 0)):
    """一个长方体元素；六个面共用贴图的一块区域（简单起见做纯色感）。"""
    u, v = uv_origin
    # 每个面按尺寸给一个 uv 矩形，看起来像有分块
    def face(w, h, ox=0, oy=0):
        return {"uv": [u + ox, v + oy, u + ox + w, v + oy + h], "texture": "#0"}

    return {
        "from": [x1, y1, z1],
        "to": [x2, y2, z2],
        "faces": {
            "north": face(min(16, x2 - x1), min(16, y2 - y1)),
            "south": face(min(16, x2 - x1), min(16, y2 - y1)),
            "east": face(min(16, z2 - z1), min(16, y2 - y1)),
            "west": face(min(16, z2 - z1), min(16, y2 - y1)),
            "up": face(min(16, x2 - x1), min(16, z2 - z1)),
            "down": face(min(16, x2 - x1), min(16, z2 - z1)),
        },
    }


def build(part: str) -> list[dict]:
    """按部位拼几何。坐标是 BlockBench 的 0~16 方块空间，原点在方块底中心。"""
    if part == "helmet":
        return [
            # 头壳（比玩家头略大，形成厚度感）
            box(3.2, 3.2, 3.2, 12.8, 12.8, 12.8),
            # 面罩：正前方一条
            box(3.0, 6.0, 2.6, 13.0, 8.0, 3.3),
            # 两侧"翼"：Botania 魔钢头盔的标志性装饰
            box(1.6, 8.5, 6.5, 3.4, 11.5, 9.5),
            box(12.6, 8.5, 6.5, 14.4, 11.5, 9.5),
            # 顶部尖角
            box(6.5, 12.8, 6.5, 9.5, 15.0, 9.5),
        ]
    if part == "chestplate":
        return [
            # 胸壳
            box(3.0, 3.0, 3.4, 13.0, 12.5, 12.6),
            # 肩甲：两块往外凸的圆角板（这就是 2D 纹理做不到的部分）
            box(0.6, 9.5, 4.6, 3.2, 13.2, 11.4),
            box(12.8, 9.5, 4.6, 15.4, 13.2, 11.4),
            # 腰裙
            box(2.6, 1.0, 3.0, 13.4, 3.4, 13.0),
        ]
    if part == "leggings":
        return [
            # 两条腿的护甲
            box(3.4, 0.5, 4.0, 7.6, 8.5, 12.0),
            box(8.4, 0.5, 4.0, 12.6, 8.5, 12.0),
            # 膝甲
            box(3.2, 6.0, 3.6, 7.8, 9.0, 4.4),
            box(8.2, 6.0, 3.6, 12.8, 9.0, 4.4),
        ]
    if part == "boots":
        return [
            # 脚踝 + 鞋体
            box(3.4, 0.0, 3.6, 7.6, 4.5, 12.4),
            box(8.4, 0.0, 3.6, 12.6, 4.5, 12.4),
            # 护胫
            box(3.6, 4.0, 4.0, 7.4, 6.4, 8.0),
            box(8.6, 4.0, 4.0, 12.4, 6.4, 8.0),
        ]
    return []


def write_texture(path: Path, palette) -> None:
    """16x16 的小贴图：中间亮、边缘暗，充当盔甲表面的明暗。"""
    from PIL import Image
    base, light, dark = palette
    im = Image.new("RGBA", (16, 16), base + (255,))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = dark + (255,)
            elif (x + y) % 7 == 0:
                px[x, y] = light + (255,)
    im.save(path)


def main() -> int:
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    made = 0
    for material, palette in PALETTES.items():
        for part in ("helmet", "chestplate", "leggings", "boots"):
            name = f"{material}_{part}"
            tex_rel = f"botania:item/armor/{name}"
            write_texture(TEXTURES / f"{name}.png", palette)
            model = {
                "credit": "generated by tools/gen_armor_models.py",
                "texture_size": [16, 16],
                "textures": {"0": tex_rel, "particle": tex_rel},
                "elements": build(part),
                "display": DISPLAY,
            }
            (MODELS / f"{name}.json").write_text(
                json.dumps(model, ensure_ascii=False, indent=1), encoding="utf-8")
            made += 1
    print(f"生成 {made} 个 3D 盔甲模型 + 贴图")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
