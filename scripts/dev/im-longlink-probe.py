#!/usr/bin/env python3
"""长连接「入站断流」参考探针（issue #616 实验 C 用，零依赖）。

目的：在**与插件相同的出网路径**上跑一个最小 WS 客户端，判断「收不到入站帧」是插件的问题还是
路径的问题——探针也静默 = 路径问题；探针正常而插件静默 = 回头看插件侧。

用法（在插件容器同一网络命名空间内执行最准）：

    python3 im-longlink-probe.py --url wss://msg-frontier.feishu.cn/ws/v2 --seconds 180
    python3 im-longlink-probe.py --url wss://api.sgroup.qq.com/websocket --text-ping heartbeat --seconds 180

输出为每秒一行的状态摘要（`recv` 累计入站帧、`since` 距上次入站秒数）与结束汇总，
可直接贴进 issue。

说明：
- 只做 TCP/TLS 建连 + WS 握手 + 定时发 ping/pong，不实现任何平台业务（不发鉴权帧）；
- 平台可能在鉴权前就关连接（code 1008/4004 等）——这里只关心**能不能收到任何帧**，
  收到 close 也算「入站通路可用」，会把 code 打出来；
- `--text-ping` 用于某些端点只回文本帧的场景（如 QQ 的 op1/op11 心跳，需配合 `--payload`）。
"""

from __future__ import annotations

import argparse
import base64
import os
import socket
import ssl
import struct
import sys
import time
from urllib.parse import urlparse


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def ws_handshake(sock: socket.socket, host: str, path: str) -> bool:
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET {path or '/'} HTTP/1.1\r\n"
        f"Host: {host}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "User-Agent: orzmc-im-probe/1\r\n\r\n"
    )
    sock.sendall(req.encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            return False
        buf += chunk
        if len(buf) > 65536:
            return False
    head = buf.split(b"\r\n\r\n", 1)[0].decode("latin-1")
    return " 101 " in head.split("\r\n", 1)[0]


def ws_send_ping(sock: socket.socket, payload: bytes = b"orzmc-probe") -> None:
    # 客户端必须用掩码（RFC 6455 §5.3）；opcode 0x9 = ping
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(bytes([0x89, 0x80 | len(payload)]) + mask + masked)


def ws_send_text(sock: socket.socket, text: str) -> None:
    data = text.encode()
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    if len(data) < 126:
        header = bytes([0x81, 0x80 | len(data)])
    else:
        header = bytes([0x81, 0x80 | 126]) + struct.pack(">H", len(data))
    sock.sendall(header + mask + masked)


def read_frames(sock: socket.socket, seconds: float, ping_every: float, text_ping: str | None) -> dict:
    """读帧直到超时；返回统计（任何帧/close 都算「入站通路可用」）。"""
    stats = {"frames": 0, "last": None, "closed": None, "text_ping_sent": 0.0}
    deadline = time.time() + seconds
    sock.settimeout(1.0)
    while time.time() < deadline:
        if ping_every > 0 and time.time() - stats["text_ping_sent"] >= ping_every:
            try:
                if text_ping:
                    ws_send_text(sock, text_ping)
                else:
                    ws_send_ping(sock)
                stats["text_ping_sent"] = time.time()
                log(f"-> keepalive ({'text' if text_ping else 'ping'})")
            except OSError as exc:
                log(f"!! keepalive 发送失败: {exc}")
                stats["closed"] = f"send-fail: {exc}"
                return stats
        try:
            data = sock.recv(4096)
        except socket.timeout:
            if stats["last"] is None:
                log(".. 仍未收到任何入站帧（静默中）")
            else:
                log(f".. 距上次入站 {time.time() - stats['last']:.0f}s")
            continue
        except OSError as exc:
            stats["closed"] = f"recv-error: {exc}"
            break
        if not data:
            stats["closed"] = "peer-closed-tcp"
            break
        stats["frames"] += 1
        stats["last"] = time.time()
        opcode = data[0] & 0x0F
        names = {0x0: "continuation", 0x1: "text", 0x2: "binary", 0x8: "close", 0x9: "ping", 0xA: "pong"}
        log(f"<- 入站帧 #{stats['frames']} opcode=0x{opcode:x}({names.get(opcode, '?')}) len={len(data)}")
        if opcode == 0x8:
            stats["closed"] = "ws-close-frame"
            break
        if opcode == 0x9:  # 对端 ping → 回 pong（opcode 0xA）
            try:
                sock.sendall(bytes([0x8A, 0x80]) + os.urandom(4))
            except OSError:
                pass
    return stats


def main() -> int:
    ap = argparse.ArgumentParser(description="内置 IM 长连接入站断流参考探针（issue #616）")
    ap.add_argument("--url", required=True, help="wss:// 或 ws:// 端点")
    ap.add_argument("--seconds", type=float, default=180.0, help="观察时长（默认 180s）")
    ap.add_argument("--ping-every", type=float, default=30.0, help="keepalive 间隔秒（0 = 不发）")
    ap.add_argument(
        "--text-ping",
        default=None,
        help="改为发送该文本帧作为 keepalive（如 QQ 的 op2 心跳 JSON）；默认发 WS ping 控制帧",
    )
    ap.add_argument("--timeout", type=float, default=15.0, help="TCP/TLS 建连超时秒")
    args = ap.parse_args()

    u = urlparse(args.url)
    host = u.hostname or ""
    port = u.port or (443 if u.scheme == "wss" else 80)
    path = (u.path or "/") + (f"?{u.query}" if u.query else "")

    log(f"目标 {args.url} → {host}:{port}{path}")
    t0 = time.time()
    try:
        raw = socket.create_connection((host, port), timeout=args.timeout)
    except OSError as exc:
        log(f"!! TCP 建连失败: {exc}")
        return 2
    log(f"TCP 已连上（{int((time.time() - t0) * 1000)}ms）")

    sock: socket.socket = raw
    if u.scheme == "wss":
        ctx = ssl.create_default_context()
        try:
            sock = ctx.wrap_socket(raw, server_hostname=host)  # SNI + 主机名校验
        except OSError as exc:
            log(f"!! TLS 握手失败: {exc}")
            return 3
        log(f"TLS 已建立（SNI={host}, 协议={sock.version()}）")

    if not ws_handshake(sock, host, path):
        log("!! WS 握手失败（非 101）")
        return 4
    log("WS 握手成功（101）")

    stats = read_frames(sock, args.seconds, args.ping_every, args.text_ping)
    elapsed = int(time.time() - t0)
    log("---- 汇总 ----")
    log(f"观察 {elapsed}s：入站帧 {stats['frames']} 个；结束原因：{stats['closed'] or '观察窗口结束'}")
    if stats["frames"] == 0:
        log("结论：同一路径上参考客户端也零入站 → 该出网路径对长连接不友好（与插件无关）。")
    else:
        log("结论：该路径入站可用；若插件仍静默，回到插件侧排查（帧构造/读线程），并附本日志到 issue。")
    try:
        sock.close()
    except OSError:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
