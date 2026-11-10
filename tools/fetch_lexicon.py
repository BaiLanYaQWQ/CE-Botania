#!/usr/bin/env python3
"""把上游 Patchouli 格式的辞典数据拉到 build/upstream_lexicon/。

用法：python3 tools/fetch_lexicon.py

只稀疏克隆 patchouli_books 那一块，不下载整个仓库。
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "upstream_lexicon"
REPO = "https://github.com/VazkiiMods/Botania"


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        target = Path(tmp) / "botania"
        subprocess.run(["git", "clone", "--depth", "1", "--filter=blob:none",
                        "--sparse", REPO, str(target)], check=True)
        subprocess.run(["git", "sparse-checkout", "set",
                        "Xplat/src/main/resources/assets/botania/patchouli_books"],
                       cwd=target, check=True)
        src = target / "Xplat/src/main/resources/assets/botania/patchouli_books/lexicon"
        if not src.is_dir():
            print(f"克隆成功但找不到 {src}")
            return 1
        if OUT.exists():
            shutil.rmtree(OUT)
        shutil.copytree(src, OUT)
        entries = len(list((OUT / "en_us" / "entries").rglob("*.json")))
        print(f"辞典数据已就位：{OUT}（{entries} 个词条文件）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
