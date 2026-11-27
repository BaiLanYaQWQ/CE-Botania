#!/usr/bin/env python3
"""Build CeBotania and run in-game checks on a disposable Leaves server."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def prepare(template, server, plugin, probe):
    if not (template / "eula.txt").is_file() or "eula=true" not in (
        template / "eula.txt"
    ).read_text().splitlines():
        raise ValueError("Choose an initialized Leaves server with an accepted EULA.")
    server.mkdir()
    for name in ("server.jar", "eula.txt"):
        shutil.copy2(template / name, server / name)
    for name in ("libraries", "versions", "cache"):
        shutil.copytree(template / name, server / name)
    plugins = server / "plugins"
    plugins.mkdir()
    engines = list((template / "plugins").glob("CraftEngine*.jar"))
    engine, = [p for p in engines if "bridge" not in p.name.lower()]
    shutil.copy2(engine, plugins)
    shutil.copy2(plugin, plugins)
    shutil.copy2(probe, plugins)
    ce = plugins / "CraftEngine"
    original = template / "plugins/CraftEngine"
    shutil.copytree(original / "libs", ce / "libs")
    shutil.copy2(original / "config.yml", ce)
    shutil.copytree(original / "resources", ce / "resources",
                    ignore=lambda path, names: ["botania"] if Path(path) == original / "resources" else [])
    shutil.copytree(ROOT / "pack", ce / "resources/botania")
    terrain = {"biome": "minecraft:plains", "layers": [
        {"block": "minecraft:bedrock", "height": 1},
        {"block": "minecraft:dirt", "height": 2},
        {"block": "minecraft:grass_block", "height": 1},
    ]}
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nenable-rcon=false\nenable-query=false\n"
        "online-mode=false\nlevel-name=acceptance\nlevel-seed=261127\n"
        f"generator-settings={json.dumps(terrain)}\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\n"
        "pause-when-empty-seconds=-1\nview-distance=3\nsimulation-distance=3\n"
    )


def exercise(server, java, log):
    with log.open("w") as writer, log.open(errors="replace") as reader:
        proc = subprocess.Popen([java, "-Xms512m", "-Xmx2G", "-jar", "server.jar", "nogui"],
                                cwd=server, stdin=subprocess.PIPE, stdout=writer,
                                stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 300
            started = False
            while time.monotonic() < deadline:
                line = ANSI.sub("", reader.readline())
                if not line:
                    if proc.poll() is not None:
                        raise RuntimeError(f"server exited ({proc.returncode})")
                    time.sleep(0.05)
                    continue
                if "Failed to run canSurvive" in line:
                    raise RuntimeError(line.strip())
                if not started and re.search(r'\bDone \([\d.]+s\)!', line):
                    proc.stdin.write("ceaccept\n")
                    proc.stdin.flush()
                    started = True
                if started and "CE_ACCEPT" in line:
                    print(line.strip(), flush=True)
                    if "CE_ACCEPT FAIL" in line:
                        raise RuntimeError("probe failed")
                    if line.rstrip().endswith("CE_ACCEPT PASS"):
                        return
            raise TimeoutError("server startup or acceptance checks timed out")
        finally:
            if proc.poll() is None:
                try:
                    proc.stdin.write("stop\n")
                    proc.stdin.flush()
                    proc.wait(timeout=40)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    proc.kill()
                    proc.wait()
            proc.stdin.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-template", type=Path, default=ROOT / "Server")
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    args = parser.parse_args()
    project = ROOT / "plugin"
    subprocess.run([str(project / "gradlew"), *(["--offline"] if args.offline else []),
                    "build", "acceptanceProbe"], cwd=project, check=True)
    plugin, = (project / "build/libs").glob("CE-植物魔法-*.jar")
    output = project / "build/acceptance"
    run = Path(tempfile.mkdtemp(prefix="run-", dir=output))
    log = run / "server.log"
    digest = hashlib.sha256(plugin.read_bytes()).hexdigest()
    (run / "plugin.sha256").write_text(f"{digest}  {plugin.name}\n")
    print(f"Plugin SHA-256 {digest}\nServer log {log}", flush=True)
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
    with tempfile.TemporaryDirectory(prefix="ce-acceptance-") as tmp:
        server = Path(tmp) / "server"
        prepare(args.server_template.resolve(), server, plugin, output / "CeBotaniaAcceptance.jar")
        exercise(server, java, log)
    if "Failed to run canSurvive" in log.read_text(errors="replace"):
        raise RuntimeError("plant survival error in server log")


if __name__ == "__main__":
    main()
