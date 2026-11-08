#!/usr/bin/env python3
"""从 Botania 上游源码提取材质与模型，落到 CE 资源包结构里。

用法：
    python3 tools/extract_assets.py <botania-src> [--clean]

产物：
    pack/resourcepack/assets/botania/textures/...
    pack/resourcepack/assets/botania/models/...
    build/asset_index.json   物品/方块模型清单（给 gen_content.py 用）

说明：
  * 上游模型是 1.20.1 的 models/*.json（标准 MC 格式），CE 与 1.21.4+ 的
    item model definition 依然按 path 引用它们，因此原样复制即可。
  * 方块模型来自 generated/resources（datagen 产物），其中 parent 指向
    botania:block/shapes/* 的共享父模型，那里来自 main/resources。
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/Botania-src")
DST = ROOT / "pack" / "resourcepack" / "assets" / "botania"
INDEX = ROOT / "build" / "asset_index.json"

MAIN = SRC / "Xplat/src/main/resources/assets/botania"
GEN = SRC / "Xplat/src/generated/resources/assets/botania"


def copy_tree(src: Path, dst: Path) -> int:
    if not src.is_dir():
        return 0
    n = 0
    for f in src.rglob("*"):
        if not f.is_file():
            continue
        rel = f.relative_to(src)
        out = dst / rel
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, out)
        n += 1
    return n


def main() -> int:
    if not MAIN.is_dir():
        print(f"找不到上游资源目录：{MAIN}", file=sys.stderr)
        return 2
    if "--clean" in sys.argv and DST.exists():
        shutil.rmtree(DST)

    n_tex = copy_tree(MAIN / "textures", DST / "textures")
    # 主资源里的模型（含 shapes/* 这些共享父模型）先铺一层
    n_model_main = copy_tree(MAIN / "models", DST / "models")
    # datagen 的方块/物品模型覆盖上去（数量多，是最终外观）
    n_model_gen = copy_tree(GEN / "models", DST / "models")
    n_bs = copy_tree(GEN / "blockstates", DST / "blockstates")

    if not DST.is_dir():
        print("上游没有可提取的资源", file=sys.stderr)
        return 3

    # 收集清单：物品模型 / 方块模型 / 方块状态变体
    item_models = sorted(p.stem for p in (DST / "models" / "item").glob("*.json"))
    block_models = sorted(p.stem for p in (DST / "models" / "block").glob("*.json"))

    blockstates: dict[str, dict] = {}
    for p in sorted((DST / "blockstates").glob("*.json")):
        try:
            blockstates[p.stem] = json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

    INDEX.parent.mkdir(parents=True, exist_ok=True)
    INDEX.write_text(
        json.dumps(
            {
                "item_models": item_models,
                "block_models": block_models,
                "blockstates": blockstates,
            },
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )

    print(f"材质 {n_tex} 个")
    print(f"模型：主资源 {n_model_main} / 生成资源 {n_model_gen}")
    print(f"方块状态 {n_bs} 个")
    print(f"物品模型 {len(item_models)} / 方块模型 {len(block_models)}")
    print(f"清单 -> {INDEX}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
