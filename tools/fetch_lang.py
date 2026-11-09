#!/usr/bin/env python3
"""下载上游语言文件到 build/upstream_lang/（gen_content.py 依赖它取中文名）。

用法：python3 tools/fetch_lang.py
"""
from __future__ import annotations

import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "upstream_lang"
BASE = ("https://raw.githubusercontent.com/VazkiiMods/Botania/HEAD/"
        "Xplat/src/main/resources/assets/botania/lang/{}.json")


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("zh_cn", "en_us"):
        target = OUT / f"{name}.json"
        try:
            data = urllib.request.urlopen(BASE.format(name), timeout=60).read()
        except Exception as exc:
            print(f"下载 {name} 失败：{exc}")
            return 1
        target.write_bytes(data)
        print(f"{name}.json: {len(data)} 字节")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
