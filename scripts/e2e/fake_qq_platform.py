#!/usr/bin/env python3
"""假 QQ 开放平台（本地自举 IM 联调 / L1.5 E2E 用）。

目的：让 **builtin QQ 通道**在没有真实平台、没有凭据的情况下跑通全链路——
token 换取 → `/gateway/bot` → WS hello/identify/READY/心跳 → 注入群消息 → 机器人命令 → 出站发消息。

它只实现插件实际用到的最小协议面（不是通用 QQ 模拟器）：

HTTP
    POST /app/getAppAccessToken            → {"access_token": "...", "expires_in": 7200}
    GET  /gateway/bot                      → {"url": "ws://127.0.0.1:<port>/ws"}
    POST /v2/groups/{group_openid}/messages → 记录出站内容，返回 {"id","timestamp"}
    POST /v2/users/{openid}/messages        → 同上（私聊/主动消息）
控制（供测试脚本驱动）
    GET  /control/health                   → {"ok":true,"ws_clients":N,"sent":N}
    GET  /control/sent                     → 出站记录（路径 + JSON body）
    POST /control/message                  → 广播一条入站群消息事件
         body: {"content":"$h","group_openid":"G1","member_openid":"U1","role":"owner"}

WS（同端口 /ws）
    服务端：连上即发 op10 hello（heartbeat_interval）
    客户端：op2 identify / op1 心跳（回 op11）/ op6 resume（回 READY）
    测试：`POST /control/message` → op0 + t=GROUP_AT_MESSAGE_CREATE

用法：
    python3 scripts/e2e/fake_qq_platform.py --port 18080 [--log build/fake-qq.jsonl]
    python3 scripts/e2e/fake_qq_platform.py --self-test     # 自检：起服务 + 走一遍协议

依赖：仅标准库（不引入 websockets 等第三方包，CI 零安装）。
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import socket
import struct
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
STATE_LOCK = threading.Lock()
WS_CLIENTS: list[socket.socket] = []
SENT: list[dict] = []
SEQ = {"event": 0}


def log_line(path: str, payload: str) -> None:
    if LOG_PATH:
        with open(LOG_PATH, "a", encoding="utf-8") as fh:
            fh.write(json.dumps({"ts": time.time(), "path": path, "payload": payload}, ensure_ascii=False) + "\n")


def ws_send(sock: socket.socket, text: str) -> None:
    data = text.encode("utf-8")
    header = bytearray([0x81])
    length = len(data)
    if length < 126:
        header.append(length)
    elif length < (1 << 16):
        header.append(126)
        header += struct.pack(">H", length)
    else:
        header.append(127)
        header += struct.pack(">Q", length)
    try:
        sock.sendall(bytes(header) + data)
    except OSError:
        pass


def ws_read(sock: socket.socket) -> str | None:
    """读一帧文本；返回文本，连接关闭返回 None。"""
    try:
        head = _recv_exact(sock, 2)
        if not head:
            return None
        opcode = head[0] & 0x0F
        masked = bool(head[1] & 0x80)
        length = head[1] & 0x7F
        if length == 126:
            length = struct.unpack(">H", _recv_exact(sock, 2))[0]
        elif length == 127:
            length = struct.unpack(">Q", _recv_exact(sock, 8))[0]
        mask = _recv_exact(sock, 4) if masked else None
        payload = _recv_exact(sock, length) if length else b""
        if opcode == 0x8:  # close
            return None
        if opcode == 0x9:  # ping → pong
            sock.sendall(b"\x8a\x00")
            return ""
        if masked and mask:
            payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        return payload.decode("utf-8", errors="replace")
    except OSError:
        return None


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return b""
        buf += chunk
    return buf


def broadcast_event(payload: dict) -> int:
    with STATE_LOCK:
        targets = list(WS_CLIENTS)
    sent = 0
    for client in targets:
        ws_send(client, json.dumps(payload, ensure_ascii=False))
        sent += 1
    return sent


def next_seq() -> int:
    SEQ["event"] += 1
    return SEQ["event"]


def group_event(content: str, group_openid: str, member_openid: str, role: str) -> dict:
    return {
        "op": 0,
        "s": next_seq(),
        "t": "GROUP_AT_MESSAGE_CREATE",
        "d": {
            "id": f"in-{int(time.time() * 1000)}",
            "content": content,
            "timestamp": str(int(time.time())),
            "group_openid": group_openid,
            "author": {"member_openid": member_openid, "member_role": role, "username": "e2e-tester"},
        },
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:  # 安静：避免污染 Gradle 输出
        if VERBOSE:
            sys.stderr.write("[fake-qq] " + (fmt % args) + "\n")

    # ---------- HTTP ----------
    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def do_GET(self) -> None:
        if self.path.startswith("/ws"):
            self._upgrade_ws()
            return
        if self.path == "/gateway/bot":
            host = self.headers.get("Host") or f"127.0.0.1:{PORT}"
            self._json(200, {"url": f"ws://{host}/ws"})
            return
        if self.path == "/control/health":
            with STATE_LOCK:
                self._json(200, {"ok": True, "ws_clients": len(WS_CLIENTS), "sent": len(SENT)})
            return
        if self.path == "/control/sent":
            with STATE_LOCK:
                self._json(200, {"sent": list(SENT)})
            return
        self._json(404, {"error": "not found", "path": self.path})

    def do_POST(self) -> None:
        if self.path == "/app/getAppAccessToken":
            body = self._read_body()
            log_line(self.path, json.dumps(body))
            self._json(200, {"access_token": "fake-access-token", "expires_in": 7200})
            return
        if self.path == "/control/message":
            body = self._read_body()
            event = group_event(
                body.get("content", ""),
                body.get("group_openid", "G1"),
                body.get("member_openid", "U1"),
                body.get("role", "owner"),
            )
            delivered = broadcast_event(event)
            self._json(200, {"ok": True, "delivered": delivered, "event": event})
            return
        if self.path.startswith("/v2/"):
            body = self._read_body()
            with STATE_LOCK:
                SENT.append({"path": self.path, "body": body})
                index = len(SENT)
            log_line(self.path, json.dumps(body, ensure_ascii=False))
            self._json(200, {"id": f"out-{index}", "timestamp": str(int(time.time()))})
            return
        self._json(404, {"error": "not found", "path": self.path})

    # ---------- WebSocket ----------
    def _upgrade_ws(self) -> None:
        key = self.headers.get("Sec-WebSocket-Key")
        if not key:
            self._json(400, {"error": "missing Sec-WebSocket-Key"})
            return
        accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
        self.send_response(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.wfile.flush()
        sock = self.connection
        self.close_connection = True
        with STATE_LOCK:
            WS_CLIENTS.append(sock)
        try:
            ws_send(sock, json.dumps({"op": 10, "d": {"heartbeat_interval": 30000}}))
            while True:
                frame = ws_read(sock)
                if frame is None:
                    break
                if not frame:
                    continue
                try:
                    msg = json.loads(frame)
                except json.JSONDecodeError:
                    continue
                op = msg.get("op")
                if op == 1:  # 心跳 → ack
                    ws_send(sock, json.dumps({"op": 11}))
                elif op == 2:  # identify
                    ws_send(
                        sock,
                        json.dumps(
                            {
                                "op": 0,
                                "s": next_seq(),
                                "t": "READY",
                                "d": {"session_id": "fake-session", "user": {"id": "bot"}},
                            }
                        ),
                    )
                elif op == 6:  # resume
                    ws_send(sock, json.dumps({"op": 0, "s": next_seq(), "t": "RESUMED", "d": {}}))
        finally:
            with STATE_LOCK:
                if sock in WS_CLIENTS:
                    WS_CLIENTS.remove(sock)
            try:
                sock.close()
            except OSError:
                pass


def serve(port: int) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


# ---------------- 自检（--self-test） ----------------
def self_test() -> int:
    """起服务 → HTTP + WS 全流程各走一遍，全部符合预期才返回 0。"""
    import urllib.request

    server = serve(PORT)
    base = f"http://127.0.0.1:{PORT}"
    failures: list[str] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        print(("[PASS] " if ok else "[FAIL] ") + name + ("" if ok else f" → {detail}"))
        if not ok:
            failures.append(name)

    def http_json(path: str, body: dict | None = None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base + path, data=data, method="POST" if data else "GET")
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read().decode())

    token = http_json("/app/getAppAccessToken", {"app_id": "a", "client_secret": "s"})
    check("token 换取", token.get("access_token") == "fake-access-token", str(token))

    gateway = http_json("/gateway/bot")
    check("gateway 返回 ws url", str(gateway.get("url", "")).startswith("ws://127.0.0.1:"), str(gateway))

    # 最小 WS 客户端：握手 → hello → identify → READY → 注入消息 → 收到事件 → 心跳 ack
    import ssl  # noqa: F401  (保持 stdlib 依赖声明明确)

    sock = socket.create_connection(("127.0.0.1", PORT), timeout=5)
    key = base64.b64encode(b"0123456789abcdef").decode()
    sock.sendall(
        (
            "GET /ws HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        ).encode()
    )
    handshake = sock.recv(4096).decode("utf-8", errors="replace")
    check("WS 握手 101", "101" in handshake.split("\r\n")[0], handshake.split("\r\n")[0])

    hello = json.loads(ws_read(sock) or "{}")
    check("收到 hello(op10)", hello.get("op") == 10, str(hello))

    ws_send(sock, json.dumps({"op": 2, "d": {"token": "QQBot fake", "intents": 0}}))
    ready = json.loads(ws_read(sock) or "{}")
    check("identify 后 READY", ready.get("t") == "READY", str(ready))

    injected = http_json("/control/message", {"content": "$h", "group_openid": "G1", "member_openid": "U1"})
    check("控制面注入事件", injected.get("delivered") == 1, str(injected))
    event = json.loads(ws_read(sock) or "{}")
    check(
        "WS 收到群消息事件",
        event.get("t") == "GROUP_AT_MESSAGE_CREATE" and event.get("d", {}).get("content") == "$h",
        str(event),
    )

    ws_send(sock, json.dumps({"op": 1, "d": 1}))
    ack = json.loads(ws_read(sock) or "{}")
    check("心跳回 op11", ack.get("op") == 11, str(ack))

    import urllib.request as ur

    req = ur.Request(base + "/v2/groups/G1/messages", data=json.dumps({"content": "hi"}).encode(), method="POST")
    with ur.urlopen(req, timeout=5) as resp:
        sent_resp = json.loads(resp.read().decode())
    check("出站消息被记录", sent_resp.get("id") == "out-1", str(sent_resp))
    sent = http_json("/control/sent")
    check("control/sent 可读", sent.get("sent", [{}])[0].get("body", {}).get("content") == "hi", str(sent))

    sock.close()
    server.shutdown()
    print(f"通过 {sum(1 for _ in [0]) and ''}{len(failures)} 个失败 / 10 项检查")
    return 0 if not failures else 1


VERBOSE = False
LOG_PATH: str | None = None
PORT = 18080


def main() -> None:
    global VERBOSE, LOG_PATH, PORT
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--log", default=None, help="出站/入站 JSONL 记录文件（可选）")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    PORT = args.port
    LOG_PATH = args.log
    VERBOSE = args.verbose
    if args.log:
        open(args.log, "w", encoding="utf-8").close()

    if args.self_test:
        sys.exit(self_test())

    server = serve(PORT)
    print(f"假 QQ 平台已启动: http://127.0.0.1:{PORT} （WS: ws://127.0.0.1:{PORT}/ws）", flush=True)
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
