#!/usr/bin/env python3
"""把 bbmodel 的坐标归位到标准人形骨架。

用法：python3 tools/normalize_bbmodel.py <输入.bbmodel> [输出.bbmodel] [--shift-y 数值]

标准骨架：脚底 y=0，腿 0~12，身 12~24，头 24~32。
如果模型整体偏高/偏低，用 --shift-y -5.4 这样把整体挪回去。
不带参数时会先打印当前范围和预测结果，不做修改。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# 标准骨架各部位重心（用于判断归位目标）
ANCHORS = {
    "head": (0.0, 26.0), "body": (0.0, 18.0),
    "left_arm": (-5.5, 18.0), "right_arm": (5.5, 18.0),
    "left_leg": (-2.0, 6.0), "right_leg": (2.0, 6.0),
}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    src = Path(sys.argv[1])
    shift = 0.0
    if "--shift-y" in sys.argv:
        shift = float(sys.argv[sys.argv.index("--shift-y") + 1])
    dst = src
    for index, arg in enumerate(sys.argv[2:], start=2):
        if not arg.startswith("--") and index != sys.argv.index("--shift-y") + 1:
            dst = Path(arg)
            break

    data = json.loads(src.read_text(encoding="utf-8"))
    elements = data.get("elements", [])
    lows = [min(e["from"][1], e["to"][1]) for e in elements]
    highs = [max(e["from"][1], e["to"][1]) for e in elements]
    print(f"当前 y 范围: {min(lows):.2f} ~ {max(highs):.2f}")
    print(f"标准骨架:   0.00 ~ 32.00")

    if shift == 0.0:
        suggested = -min(lows)
        print(f"\n如果只是整体偏移，建议 --shift-y {suggested:.2f}（把脚底对齐到 0）")
        print("注意：这只挪位置，补不出缺失的腿部几何。")
        return 0

    for e in elements:
        e["from"][1] = round(e["from"][1] + shift, 5)
        e["to"][1] = round(e["to"][1] + shift, 5)
        if "origin" in e:
            e["origin"][1] = round(e["origin"][1] + shift, 5)
    for g in data.get("groups", []):
        if "origin" in g:
            g["origin"][1] = round(g["origin"][1] + shift, 5)

    # 重新归类到最近重心
    parts = {name: [] for name in ANCHORS}
    for e in elements:
        x = (e["from"][0] + e["to"][0]) / 2
        y = (e["from"][1] + e["to"][1]) / 2
        best, best_d = "body", float("inf")
        for name, (ax, ay) in ANCHORS.items():
            d = (x - ax) ** 2 + (y - ay) ** 2
            if d < best_d:
                best_d, best = d, name
        parts[best].append(e.get("uuid") or e.get("name"))

    for g in data.get("groups", []):
        if g.get("name") in parts:
            g["elements"] = parts[g["name"]]

    dst.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已平移 {shift:+.2f} 并重新归类 -> {dst}")
    for name in ANCHORS:
        print(f"  {name:10s} {len(parts[name]):>3d} 个元素")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
