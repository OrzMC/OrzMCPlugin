#!/usr/bin/env node
// e2e/ci/player-probe.js —— 玩家侧自举探测（CI 用；依赖：mineflayer）
//
// 覆盖此前只能真人点的路径：加入服务器、`/menu` 箱子 GUI、`/guide` 发新手书、非管理员命令被拒（权限隔离）。
//
// 协议说明（2026-11 实测）：
//   · mineflayer（minecraft-data 3.113.2）的最高协议是 1.21.11，服务端是 26.2；
//   · 只装 ViaVersion 或只用协议补丁都连不上（服务端回 "Outdated client"）；
//   · **ViaVersion + ViaBackwards**（新客户端 → 旧服务端方向）后，1.21.11 客户端可正常登录。
//   因此本探测要求服务端已装 ViaVersion + ViaBackwards（Gradle 任务会下载并放入 plugins/）。
//
// 环境变量：
//   MC_HOST / MC_PORT / MC_VERSION / MC_ADMIN_USER / MC_GUEST_USER
// 输出：[PASS]/[FAIL] 行；退出码 0=全过，1=有失败。

const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.MC_PORT || '25586', 10);
const VERSION = process.env.MC_VERSION || '1.21.11';
const ADMIN = process.env.MC_ADMIN_USER || 'E2EAdmin';
const GUEST = process.env.MC_GUEST_USER || 'E2EGuest';

const REJECT = /Unknown or incomplete command|需要管理员权限|没有权限|no permission/i;
let failed = 0;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function pass(name) {
  console.log(`[PASS] ${name}`);
}

function fail(name, detail) {
  failed++;
  console.log(`[FAIL] ${name}${detail ? ` → ${detail}` : ''}`);
}

async function waitFor(name, predicate, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      if (await predicate()) return true;
    } catch (e) {
      /* 继续等 */
    }
    await sleep(200);
  }
  console.log(`  (超时等待: ${name})`);
  return false;
}

/** 连一个玩家并执行 steps(bot, ctx)；返回是否成功 spawn。 */
async function withBot(username, steps) {
  const chats = [];
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: VERSION });
  let connectError = null;
  bot.on('error', (e) => {
    connectError = e;
  });
  bot.on('kicked', (reason) => {
    connectError = new Error(`被踢出: ${JSON.stringify(reason)}`);
  });
  bot.on('message', (m) => chats.push(m.toString()));

  const spawned = await waitFor(`${username} spawn`, () => bot.entity != null, 90000);
  if (!spawned) {
    fail(`${username} 加入服务器`, connectError ? connectError.message : `最近消息: ${chats.slice(-3).join(' | ')}`);
    try {
      bot.quit();
    } catch (e) {}
    return false;
  }
  pass(`${username} 加入服务器`);
  try {
    await steps(bot, chats);
  } catch (e) {
    fail(`${username} 探测异常`, e && e.message ? e.message : String(e));
  } finally {
    try {
      bot.quit();
    } catch (e) {}
  }
  return true;
}

// `/guide` 是「打开书」而不是「放进背包」：服务端发 open_book 包，mineflayer 没有高层事件，听底层包
function watchOpenBook(bot) {
  const state = { opened: false };
  try {
    bot._client.on('open_book', () => {
      state.opened = true;
    });
  } catch (e) {
    /* 协议名变化时退化为只看聊天 */
  }
  return state;
}

(async () => {
  console.log(`连接 ${HOST}:${PORT}（client ${VERSION}）`);

  // ① 管理员玩家：GUI + 新手书
  await withBot(ADMIN, async (bot, chats) => {
    let opened = null;
    bot.on('windowOpen', (win) => {
      opened = win;
    });
    bot.chat('/menu');
    if (await waitFor('windowOpen', () => opened != null, 10000)) {
      pass('`/menu` 打开箱子 GUI');
      bot.closeWindow(opened);
    } else {
      fail('`/menu` 打开箱子 GUI', `最近消息: ${chats.slice(-3).join(' | ')}`);
    }

    const book = watchOpenBook(bot);
    chats.length = 0;
    bot.chat('/guide');
    if (await waitFor('open_book', () => book.opened, 10000)) {
      pass('`/guide` 打开新手书（open_book 包）');
    } else {
      fail('`/guide` 打开新手书', `最近消息: ${chats.slice(-3).join(' | ')}`);
    }
  });

  // ② 非管理员玩家：/config 必须被拒（权限隔离）
  await withBot(GUEST, async (bot, chats) => {
    chats.length = 0;
    bot.chat('/config');
    if (await waitFor('config rejected', () => chats.some((line) => REJECT.test(line)), 8000)) {
      pass('非管理员执行 `/config` 被拒');
    } else {
      fail('非管理员执行 `/config` 被拒', `收到: ${chats.slice(-3).join(' | ')}`);
    }
  });

  console.log(`失败 ${failed} 项`);
  process.exit(failed === 0 ? 0 : 1);
})();
