#!/usr/bin/env python3
"""#616 实验 D：区分「链路整体不通」vs「已建立的长连接被掐」。

每 N 秒做两件事，输出到同一时间线：
1. 对插件当前使用的远端 IP 做一次**全新** TCP+TLS 建连（带耗时）；
2. 读插件日志里新增的静默/断连事件（由调用方用 docker logs 增量喂进来，见 orchestrator）。

判读：
- 静默发生时**新连接仍然秒通** → 掐的是「已建立的流」（中间设备/NAT/DPI 对长连接的策略）；
- 新连接也一起失败/超时 → 是真的路径中断（家宽/路由/运营商）。

用法：
    python3 flow_monitor.py --ips 111.161.67.196 116.136.49.177 --interval 30 --minutes 60
"""

from __future__ import annotations

import argparse
import socket
import ssl
import time
from datetime import datetime


def log(msg: str) -> None:
    print(f"{datetime.now().strftime('%H:%M:%S')} {msg}", flush=True)


def probe(host: str, port: int = 443, timeout: float = 6.0) -> str:
    t0 = time.time()
    try:
        raw = socket.create_connection((host, port), timeout=timeout)
        tcp_ms = int((time.time() - t0) * 1000)
        # 只测「能不能建立 TCP+TLS」，不做主机名校验（目标给的是 IP，证书 CN 是域名）
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        t1 = time.time()
        with ctx.wrap_socket(raw, server_hostname=host) as tls:
            tls_ms = int((time.time() - t1) * 1000)
            ver = tls.version()
        return f"OK tcp={tcp_ms}ms tls={tls_ms}ms {ver}"
    except Exception as exc:  # noqa: BLE001 - 探测脚本，任何异常都只是「失败」
        return f"FAIL {int((time.time() - t0) * 1000)}ms {type(exc).__name__}: {exc}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ips", nargs="+", required=True)
    ap.add_argument("--port", type=int, default=443)
    ap.add_argument("--interval", type=float, default=30.0)
    ap.add_argument("--minutes", type=float, default=60.0)
    args = ap.parse_args()

    deadline = time.time() + args.minutes * 60
    log(f"开始：目标={','.join(args.ips)} 间隔={args.interval}s 时长={args.minutes}min")
    while time.time() < deadline:
        for ip in args.ips:
            log(f"fresh {ip}:{args.port} → {probe(ip, args.port)}")
        time.sleep(args.interval)
    log("结束")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
