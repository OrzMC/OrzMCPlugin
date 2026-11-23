#!/usr/bin/env node
/**
 * 长连接「入站断流」参考探针（Node 版，issue #616 实验 C 用，零依赖）。
 *
 * 为什么有 Python 版还要一个 Node 版：容器里常常只有 Node（如 MCSManager 面板镜像）没有 python，
 * 而实验 C 的关键是「**在插件同一网络命名空间里**跑一个别的客户端」——用容器里现成的运行时最省事。
 * Node 22+ 自带全局 `WebSocket`（undici），无需 npm install。
 *
 * 用法：
 *   docker run --rm --network orzmc_default -v "$PWD/scripts/dev/im-longlink-probe.mjs:/probe.mjs:ro" \
 *     githubyumao/mcsmanager-web:latest node /probe.mjs wss://api.sgroup.qq.com/websocket 60
 *
 * 输出：每秒一行状态 + 结束汇总；零入站 = 该出网路径对长连接不友好（与插件无关）。
 */

const url = process.argv[2];
const seconds = Number(process.argv[3] ?? 60);

if (!url) {
    console.error("用法: node im-longlink-probe.mjs <wss://…> [观察秒数]");
    process.exit(2);
}

const t0 = Date.now();
let frames = 0;
let lastAt = null;
let done = false;
const ts = () => new Date().toISOString().slice(11, 19);
const log = (m) => console.log(`[${ts()}] ${m}`);

log(`目标 ${url}`);

let ws;
try {
    ws = new WebSocket(url);
} catch (e) {
    log(`!! 建连失败: ${e}`);
    process.exit(3);
}

ws.addEventListener("open", () => log(`WS 已连接（${Date.now() - t0}ms）`));
ws.addEventListener("message", (ev) => {
    frames += 1;
    lastAt = Date.now();
    const data = typeof ev.data === "string" ? ev.data : `<binary ${ev.data?.byteLength ?? "?"}B>`;
    log(`<- 入站帧 #${frames}: ${String(data).slice(0, 120)}`);
});
ws.addEventListener("error", (ev) => log(`!! WS error: ${ev?.message ?? ev?.error?.message ?? "unknown"}`));
ws.addEventListener("close", (ev) => {
    log(`!! WS close: code=${ev.code} reason=${ev.reason || "-"} clean=${ev.wasClean}`);
    done = true;
});

const ticker = setInterval(() => {
    if (done) return;
    const silent = lastAt === null ? "-" : `${Math.round((Date.now() - lastAt) / 1000)}s`;
    log(`.. 入站累计 ${frames} 帧，距上次入站 ${silent}`);
}, 1000);

setTimeout(() => {
    clearInterval(ticker);
    log("---- 汇总 ----");
    log(`观察 ${Math.round((Date.now() - t0) / 1000)}s：入站帧 ${frames} 个`);
    if (frames === 0) {
        log("结论：同一路径上参考客户端也零入站 → 该出网路径对长连接不友好（与插件无关）。");
    } else {
        log("结论：该路径入站可用；若插件仍静默，回到插件侧排查（帧构造/读线程）。");
    }
    try {
        ws.close();
    } catch {
        /* ignore */
    }
    process.exit(0);
}, seconds * 1000);
