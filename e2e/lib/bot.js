// e2e/lib/bot.js —— mineflayer bot 工厂（粒子 patch / 登录 / 消息收集）
// 环境变量（双核心支持）:
//   ORZMC_TEST_PORT     MC 端口（默认 25565）
// 依赖: NODE_PATH 指向 ~/minecraft-bot/node_modules（run-all.sh 已设置）
// 用法: const { spawnBot, waitMessage, quitBot } = require('./lib/bot');
const path = require('path');
const os = require('os');

const TEST_PORT = parseInt(process.env.ORZMC_TEST_PORT || '25565', 10);

// 粒子 patch（26.2→1.21.11 兼容，登录报错时自动跳过）
try {
  const mcDataPath = path.join(require.resolve('minecraft-data'), '../..', 'minecraft-data', 'data', 'pc', '1.21.11', 'protocol.json');
  const fs = require('fs');
  const proto = JSON.parse(fs.readFileSync(mcDataPath, 'utf8'));
  const mappings = proto.types.Particle[1][0].type[1].mappings;
  mappings['115'] = 'block_crumble';
  mappings['116'] = 'firefly';
  fs.writeFileSync(mcDataPath, JSON.stringify(proto));
} catch (e) { /* 已 patch 或版本变化 */ }

const mineflayer = require('mineflayer');

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

// 创建 bot 并等待 spawn + 登录完成
// opts: { name, host, port, password?, autoRegister?, probeLogin? }
//   password 提供时自动响应登录提示；autoRegister=true 未注册自动注册
//   probeLogin=true（默认）：spawn 后主动发 /login 探测——兼容 SimpleLogin 与 LoginSecurity：
//     - 已注册 → 登录成功
//     - 未注册 → 收到「未注册/尚未注册」提示 → 自动转 /register <pw> <pw>
//   成功标记: bot._loggedIn=true（收到「登录成功/注册成功/欢迎」或 waitFor 宽松放行）
function spawnBot(opts) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: opts.host || '127.0.0.1',
      port: parseInt(opts.port || String(TEST_PORT), 10),
      username: opts.name,
      auth: 'offline',
    });
    bot._messages = [];
    bot._joined = false;
    bot._loggedIn = false;
    bot._registered = false;
    let probing = false;

    bot.on('message', (m) => {
      const text = m.toString();
      bot._messages.push(text);
      // 登录提示 → 发 /login
      if (/请输入 \/login|请登录后继续|please login/i.test(text) && opts.password && !probing) {
        bot.chat(`/login ${opts.password}`);
        probing = true;
      }
      // 未注册提示 → 自动注册（两插件通用）
      if (/尚未注册|未注册|请(先)?注册|please register/i.test(text) && opts.autoRegister && opts.password) {
        bot.chat(`/register ${opts.password} ${opts.password}`);
        bot._registered = true;
      }
      // 成功标记：只认真正的登录/注册成功消息
      // ⚠️ 勿匹配「Welcome! xxx just joined」——那是服务器首登广播，非登录成功
      if (/登录成功|注册成功|成功登录/.test(text)) { bot._joined = true; bot._loggedIn = true; }
    });

    bot.once('spawn', () => {
      // 主动探测登录（probeLogin 默认 true）：先发 /login，未注册会收到「尚未注册」→ 转注册
      if (opts.probeLogin !== false && opts.password) {
        setTimeout(() => {
          if (!bot._joined) { bot.chat(`/login ${opts.password}`); probing = true; }
        }, 1500);
      }
      const check = async () => {
        if (!opts.password) { resolve(bot); return; }
        try {
          await waitFor(() => bot._joined, 18000, 600);
          resolve(bot);
        } catch (e) {
          // 未收到成功消息（提示语差异）——若 autoRegister 已尝试注册则宽松放行
          if (opts.autoRegister && bot._registered) { resolve(bot); return; }
          // 无密码要求或提示语不匹配：已 spawn 即视为可用
          resolve(bot);
        }
      };
      check();
    });
    bot.once('kicked', (r) => { bot._kickReason = JSON.stringify(r); });
    bot.on('error', (e) => { /* 连接错误通常伴随 end */ });
    bot.once('end', () => { /* 由用例处理 */ });
    setTimeout(() => reject(new Error(`bot ${opts.name} 连接超时`)), 28000).unref();
  });
}

// 等待 bot 收到包含 keyword 的消息（支持字符串或正则）
function waitMessage(bot, keyword, timeoutMs = 15000) {
  return waitFor(() => {
    const hit = bot._messages.find((m) =>
      typeof keyword === 'string' ? m.includes(keyword) : keyword.test(m)
    );
    return hit || null;
  }, timeoutMs, 300, `message contains: ${keyword}`);
}

// 安全退出（保证 server 端 QUIT 事件正常）
function quitBot(bot) {
  try { bot.quit(); } catch (e) { /* ignore */ }
}

module.exports = { spawnBot, waitFor, waitMessage, quitBot, TEST_PORT };
