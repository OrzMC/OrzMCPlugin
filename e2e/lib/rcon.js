// e2e/lib/rcon.js —— Promise 化控制台命令客户端（$ 安全，不经 shell）
// 双模式（2026-09-03 迁 MCSM 后本机测试服 = MCSM 实例，走 API 模式；原生 RCON 保留兼容）:
//   [A] HTTP console API 模式（默认）: ORZMC_RCON_MODE=http
//       ORZMC_CONSOLE_URL   控制台命令端点 URL（含固定 query，如
//                           https://mcs.jokerhub.cn/api/protected_instance/command?daemonId=xxx&uuid=xxx）
//       ORZMC_API_KEY       API key（追加为 apikey query，与 MCSM 面板 API 一致）
//       rcon(cmd) = GET {url}&command={enc(cmd)}&apikey={key} → 仅确认已发送（status 200）
//       （命令输出断言走 waitLog 日志文件——实例 logs/latest.log 宿主可见）
//   [B] 原生 RCON 协议模式: ORZMC_RCON_MODE=rcon（默认端口 25575，密码 ORZMC_RCON_PASS）
// 环境变量:
//   ORZMC_LOG_PATH     服务器日志路径（运行入口注入；无默认——本机路径由 wrapper 提供）
// 用法: const { rcon } = require('./lib/rcon');
//   const out = await rcon('list');          // 默认测试服（模式由环境变量决定）
const net = require('net');

const RCON_MODE = process.env.ORZMC_RCON_MODE || 'http';
const DEFAULT_RCON_PORT = parseInt(process.env.ORZMC_RCON_PORT || '25575', 10);
const DEFAULT_RCON_PASS = process.env.ORZMC_RCON_PASS || '';
const CONSOLE_URL = process.env.ORZMC_CONSOLE_URL || '';
const API_KEY = process.env.ORZMC_API_KEY || '';
const DEFAULT_LOG_PATH = process.env.ORZMC_LOG_PATH || '';

function rcon(cmd, port = DEFAULT_RCON_PORT, password = DEFAULT_RCON_PASS, timeoutMs = 15000) {
  if (RCON_MODE === 'http') {
    return rconHttp(cmd, timeoutMs);
  }
  return rconTcp(cmd, port, password, timeoutMs);
}

// [A] HTTP console API（MCSM 面板 /api/protected_instance/command 语义；无同步输出，resolve ''）
function rconHttp(cmd, timeoutMs) {
  return new Promise((resolve, reject) => {
    if (!CONSOLE_URL) return reject(new Error('HTTP 模式缺少 ORZMC_CONSOLE_URL（由 e2e-mcsm-wrapper.sh 注入）'));
    const sep = CONSOLE_URL.includes('?') ? '&' : '?';
    const url = `${CONSOLE_URL}${sep}command=${encodeURIComponent(cmd)}&apikey=${encodeURIComponent(API_KEY)}`;
    const timer = setTimeout(() => reject(new Error(`Console API TIMEOUT: ${cmd}`)), timeoutMs);
    fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
      .then((r) => r.json())
      .then((d) => {
        if (d && (d.status === 200 || d.status === undefined)) { clearTimeout(timer); resolve(''); }
        else { clearTimeout(timer); reject(new Error(`Console API 错误: ${d && d.data ? d.data : JSON.stringify(d).slice(0, 200)}`)); }
      })
      .catch((e) => { clearTimeout(timer); reject(new Error(`Console API error: ${e.message}`)); });
  });
}

// [B] 原生 RCON 协议（旧裸跑/直连场景）
function rconTcp(cmd, port, password, timeoutMs) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(port, '127.0.0.1', () => send(1, 3, password));
    let buffer = Buffer.alloc(0);
    const timer = setTimeout(() => { sock.destroy(); reject(new Error(`RCON TIMEOUT: ${cmd}`)); }, timeoutMs);

    function send(id, type, payload) {
      const body = Buffer.alloc(payload.length + 2);
      body.write(payload, 0, 'utf8');
      const pkt = Buffer.alloc(4 + 4 + 4 + body.length);
      pkt.writeInt32LE(body.length + 8, 0); // length = id+type+payload+2null（少算 8 字节服务器断连）
      pkt.writeInt32LE(id, 4);
      pkt.writeInt32LE(type, 8);
      body.copy(pkt, 12);
      sock.write(pkt);
    }

    sock.on('data', (d) => {
      buffer = Buffer.concat([buffer, d]);
      while (buffer.length >= 4) {
        const len = buffer.readInt32LE(0);
        if (buffer.length < 4 + len) break;
        const id = buffer.readInt32LE(4);
        const type = buffer.readInt32LE(8);
        const payload = buffer.slice(12, 4 + len - 2).toString('utf8');
        buffer = buffer.slice(4 + len);
        if (id === -1) { clearTimeout(timer); sock.destroy(); reject(new Error('RCON AUTH FAIL')); return; }
        if (type === 2 && id === 1) setTimeout(() => send(2, 2, cmd), 100);
        if (type === 0) {
          clearTimeout(timer);
          sock.destroy();
          resolve(payload.replace(/§[0-9a-fk-or]/g, ''));
          return;
        }
      }
    });
    sock.on('error', (e) => { clearTimeout(timer); reject(new Error(`RCON error: ${e.message}`)); });
  });
}

// 等条件满足：poll(fn, timeoutMs) —— fn 返回 truthy 则 resolve
async function waitFor(fn, timeoutMs = 15000, intervalMs = 500, desc = 'condition') {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const v = await fn();
      if (v) return v;
    } catch (e) { /* 继续等 */ }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error(`等待超时: ${desc}`);
}

// 等待服务器日志出现关键字（日志轮转时重读；用于异步链路确认，如 LP 授权、$e 捕获）
// ⚠️ 日志路径无默认（运行入口必须注入 ORZMC_LOG_PATH——MCSM 实例 = InstanceData/<uuid>/logs/latest.log）
// ⚠️ tail 默认 3000：高刷屏环境（命令方块循环）下 200 行窗口会被挤出（BUG-E2E-003 连带）
async function waitLog(pattern, logPath = DEFAULT_LOG_PATH, timeoutMs = 15000, tail = 3000) {
  if (!logPath) throw new Error('waitLog: 未设置日志路径（export ORZMC_LOG_PATH，wrapper 会注入实例日志路径）');
  const fs = require('fs');
  return waitFor(async () => {
    const content = fs.readFileSync(logPath, 'utf8').split('\n').slice(-tail).join('\n');
    return content.includes(pattern) ? content : null;
  }, timeoutMs, 700, `log pattern: ${pattern}`);
}

module.exports = { rcon, waitFor, waitLog, RCON_MODE, DEFAULT_RCON_PORT, DEFAULT_RCON_PASS, DEFAULT_LOG_PATH };
