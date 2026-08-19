// e2e/lib/rcon.js —— Promise 化 RCON 客户端（$ 安全，不经 shell）
// 环境变量（双核心支持）:
//   ORZMC_RCON_PORT    RCON 端口（默认 25575）
//   ORZMC_RCON_PASS    RCON 密码（默认 orztest2026）
//   ORZMC_LOG_PATH     服务器日志路径（默认 ~/folia-test/logs/latest.log）
// 用法: const { rcon } = require('./lib/rcon');
//   const out = await rcon('list');          // 默认测试服
//   const out = await rcon('orzdebug $v l', 25575, 'orztest2026');
const net = require('net');
const os = require('os');
const path = require('path');

const DEFAULT_RCON_PORT = parseInt(process.env.ORZMC_RCON_PORT || '25575', 10);
const DEFAULT_RCON_PASS = process.env.ORZMC_RCON_PASS || 'orztest2026';
const DEFAULT_LOG_PATH = process.env.ORZMC_LOG_PATH
  || path.join(os.homedir(), 'folia-test', 'logs', 'latest.log');

function rcon(cmd, port = DEFAULT_RCON_PORT, password = DEFAULT_RCON_PASS, timeoutMs = 10000) {
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
// ⚠️ tail 默认 3000：高刷屏环境（命令方块循环）下 200 行窗口会被挤出（BUG-E2E-003 连带）
async function waitLog(pattern, logPath = DEFAULT_LOG_PATH, timeoutMs = 15000, tail = 3000) {
  const fs = require('fs');
  return waitFor(async () => {
    const content = fs.readFileSync(logPath, 'utf8').split('\n').slice(-tail).join('\n');
    return content.includes(pattern) ? content : null;
  }, timeoutMs, 700, `log pattern: ${pattern}`);
}

module.exports = { rcon, waitFor, waitLog, DEFAULT_RCON_PORT, DEFAULT_RCON_PASS, DEFAULT_LOG_PATH };
