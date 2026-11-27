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
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("zh_cn", "en_us"):
        target = OUT / f"{name}.json"
        try:
            data = urllib.request.urlopen(BASE.format(name), timeout=60).read()
        except Exception as exc:
            print(f"下载 {name} 失败：{exc}")
            return 1
        target.write_bytes(data)
        if args.update_content:
            translations = {key: value for key, value in json.loads(data).items()
                            if key.startswith(("item.botania.", "block.botania."))}
            CONTENT_LANG.mkdir(parents=True, exist_ok=True)
            snapshot = json.dumps(translations, ensure_ascii=False, indent=2, sort_keys=True)
            (CONTENT_LANG / f"{name}.json").write_text(snapshot + "\n", encoding="utf-8")
        print(f"{name}.json: {len(data)} 字节")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
