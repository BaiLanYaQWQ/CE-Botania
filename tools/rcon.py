#!/usr/bin/env python3
"""极简 RCON 客户端：给测试脚本注入服务器命令。

用法：
    python3 tools/rcon.py "list" "botania selfcheck"
"""
from __future__ import annotations

import socket
import struct
import sys

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "botania"


class Rcon:
    def __init__(self, host: str = HOST, port: int = PORT, password: str = PASSWORD):
        self.sock = socket.create_connection((host, port), timeout=10)
        self._send(3, password)
        self._recv()

    def _send(self, kind: int, body: str) -> None:
        payload = struct.pack("<ii", 0, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv(self) -> str:
        raw = self.sock.recv(4)
        if len(raw) < 4:
            return ""
        length = struct.unpack("<i", raw)[0]
        data = b""
        while len(data) < length:
            chunk = self.sock.recv(length - len(data))
            if not chunk:
                break
            data += chunk
        return data[8:-2].decode("utf-8", "ignore")

    def command(self, text: str) -> str:
        self._send(2, text)
        return self._recv()

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    rcon = Rcon()
    try:
        for cmd in sys.argv[1:]:
            print(f"> {cmd}")
            print(rcon.command(cmd))
    finally:
        rcon.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
