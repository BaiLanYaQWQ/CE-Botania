#!/usr/bin/env python3
"""下载上游语言文件，可同时刷新 gen_content.py 的物品与方块名称快照。

用法：python3 tools/fetch_lang.py [--update-content]
"""
from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "upstream_lang"
CONTENT_LANG = ROOT / "tools" / "content_lang"
BASE = ("https://raw.githubusercontent.com/VazkiiMods/Botania/HEAD/"
        "Xplat/src/main/resources/assets/botania/lang/{}.json")


def main() -> int:
    parser = argparse.ArgumentParser(description="下载 Botania 上游语言文件")
    parser.add_argument("--update-content", action="store_true", help="同时更新仓库中的物品与方块名称")
    args = parser.parse_args()
    snapshots = {}
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("zh_cn", "en_us"):
        try:
            with urllib.request.urlopen(BASE.format(name), timeout=60) as response:
                data = response.read()
            if args.update_content:
                translations = {key: value for key, value in json.loads(data).items()
                                if key.startswith(("item.botania.", "block.botania."))}
                if not translations:
                    raise ValueError("缺少物品与方块名称")
                snapshots[name] = json.dumps(translations, ensure_ascii=False, indent=2, sort_keys=True)
        except Exception as exc:
            print(f"下载 {name} 失败：{exc}")
            return 1
        (OUT / f"{name}.json").write_bytes(data)
        print(f"{name}.json: {len(data)} 字节")

    if snapshots:
        CONTENT_LANG.mkdir(parents=True, exist_ok=True)
        for name, snapshot in snapshots.items():
            (CONTENT_LANG / f"{name}.json").write_bytes((snapshot + "\n").encode("utf-8"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
