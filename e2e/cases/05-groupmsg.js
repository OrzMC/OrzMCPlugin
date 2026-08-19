// 05-groupmsg.js —— 群消息发送 E2E（服务器日志断言，不依赖 EasyBot API）
// 场景：whitelist_block 拦截 / player_join 上线 / player_digest 聚合 / player_quit 下线 / ip_blacklist_block 拦截
// 断言源：Notifier.routeEvent 统一群消息日志「[群消息:<key>] <渲染后内容>」+ 占位符残留检查
// 自包含：写操作全部带还原（$a 加白→测→$r 移除；$d 加黑→测→$d -移除）
// 用法: NODE_PATH=~/minecraft-bot/node_modules node cases/05-groupmsg.js
const { rcon, waitLog } = require('../lib/rcon');
const { spawnBot } = require('../lib/bot');

const results = [];
let failed = 0;
const liveBots = [];

async function check(name, fn, expect = true) {
  try {
    const out = await fn();
    const ok = expect === true ? !!out : (typeof expect === 'function' ? expect(out) : String(out).includes(String(expect)));
    results.push(`[${ok ? 'PASS' : 'FAIL'}] ${name}${ok ? '' : `\n    → 输出: ${String(out).slice(0, 300)}`}`);
    if (!ok) failed++;
  } catch (e) {
    results.push(`[FAIL] ${name} → 异常: ${e.message}`);
    failed++;
  }
}

function quitAll() {
  for (const b of liveBots.splice(0)) {
    try { b.quit(); } catch (e) { /* ignore */ }
  }
}

// 从 waitLog 返回的 tail 内容中提取「[群消息:key]」**最后一条**消息行（⏎ 转义后单行完整；
// 历史消息多条时取最新，避免匹配到旧场景记录）
function extractGmsgBlock(content, key) {
  const lines = content.split('\n');
  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes(`[群消息:${key}]`)) idx = i;
  }
  if (idx < 0) return '';
  return lines[idx];
}

// 等「[群消息:key]」出现且包含 mustContain（当前场景玩家名）——历史同 key 记录不算；
// 返回匹配的消息行；超时抛 Error
async function waitGmsgBlock(key, mustContain, timeoutMs = 25000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const content = await waitLog(`[群消息:${key}]`, undefined, 8000, 4000);
      const line = extractGmsgBlock(content, key);
      if (line.includes(mustContain)) return line;
    } catch (e) { /* 未出现继续等 */ }
    await new Promise((r) => setTimeout(r, 1500));
  }
  throw new Error(`等待群消息超时: ${key} 含 ${mustContain}`);
}

(async () => {
  const uniq = Date.now() % 100000;

  await check('前置: 服务器 RCON 在线', async () => {
    await rcon('list');
    return true;
  });

  // ---- 白名单拦截通知 ----
  const blocked = `GMsgBlock${uniq}`;
  await check(`whitelist_block: ${blocked} 未加白名单被拦截`, async () => {
    const outcome = await new Promise((resolve) => {
      const bot = require('mineflayer').createBot({
        host: '127.0.0.1',
        port: parseInt(process.env.ORZMC_TEST_PORT || '25565', 10),
        username: blocked,
        auth: 'offline',
      });
      bot.once('kicked', () => resolve('kicked'));
      bot.once('spawn', () => resolve('spawned'));
      setTimeout(() => resolve('timeout'), 15000);
    });
    if (outcome !== 'kicked') return false;
    const line = await waitGmsgBlock('whitelist_block', blocked, 20000);
    return line.includes('被白名单拦截') && !/{[^}]+}/.test(line);
  });

  // ---- 上线通知（$a 加白名单 → spawn）----
  const joiner = `GMsgJoin${uniq}`;
  const joiner2 = `GMsgJoin${uniq}b`;
  await check(`\$a 添加白名单 ${joiner}`, async () => {
    await rcon(`orzdebug $a ${joiner}`);
    return true;
  });
  await check(`player_join: ${joiner} 上线通知`, async () => {
    const bot1 = await spawnBot({ name: joiner, password: 'GMsgTest123' });
    liveBots.push(bot1);
    const line = await waitGmsgBlock('player_join', joiner, 20000);
    return line.includes('🥰 上线') && !/{[^}]+}/.test(line);
  });

  // ---- 聚合摘要（双 bot 同时下线 → 窗口合并 digest）----
  await check(`player_digest: ${joiner}+${joiner2} 聚合摘要`, async () => {
    await rcon(`orzdebug $a ${joiner2}`);
    const bot2 = await spawnBot({ name: joiner2, password: 'GMsgTest123' });
    liveBots.push(bot2);
    await waitGmsgBlock('player_join', joiner2, 20000);
    quitAll(); // 同时下线 → 聚合窗口合并 → digest「😋 下线(2)：」
    const line = await waitGmsgBlock('player_digest', joiner2, 25000);
    return line.includes('下线(2)') && line.includes(joiner) && !/{[^}]+}/.test(line);
  });

  // ---- 下线通知（单独退出 → 单发 player_quit）----
  await check(`player_quit: ${joiner} 下线通知`, async () => {
    const solo = `GMsgSolo${Date.now() % 100000}`;
    await rcon(`orzdebug $a ${solo}`);
    const botSolo = await spawnBot({ name: solo, password: 'GMsgTest123' });
    await waitGmsgBlock('player_join', solo, 20000);
    botSolo.quit();
    const line = await waitGmsgBlock('player_quit', solo, 25000);
    await rcon(`orzdebug $r ${solo}`); // 还原
    return line.includes('😋 下线') && !/{[^}]+}/.test(line);
  });

  // ---- IP 黑名单拦截通知 ----
  await check(`\$d 添加黑名单 IP（127.0.0.1）`, async () => {
    await rcon(`orzdebug $d 127.0.0.1`);
    return true;
  });
  const badIpName = `GMsgIp${uniq}`;
  await check(`ip_blacklist_block: ${badIpName} 被 IP 黑名单拦截`, async () => {
    const outcome = await new Promise((resolve) => {
      const bot = require('mineflayer').createBot({
        host: '127.0.0.1',
        port: parseInt(process.env.ORZMC_TEST_PORT || '25565', 10),
        username: badIpName,
        auth: 'offline',
      });
      bot.once('kicked', () => resolve('kicked'));
      bot.once('spawn', () => resolve('spawned'));
      setTimeout(() => resolve('timeout'), 15000);
    });
    if (outcome !== 'kicked') return false;
    const line = await waitGmsgBlock('ip_blacklist_block', badIpName, 20000);
    return line.includes('IP 黑名单拦截') && !/{[^}]+}/.test(line);
  });
  await check(`\$d -127.0.0.1 移除黑名单（还原）`, async () => {
    await rcon(`orzdebug $d -127.0.0.1`);
    return true;
  });

  // ---- 还原：移除白名单 ----
  await check(`\$r 移除白名单 ${joiner}（还原）`, async () => {
    await rcon(`orzdebug $r ${joiner}`);
    return true;
  });
  await check(`\$r 移除白名单 ${joiner2}（还原）`, async () => {
    await rcon(`orzdebug $r ${joiner2}`);
    return true;
  });

  console.log('===== 05-groupmsg 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}`);
  process.exit(failed === 0 ? 0 : 1);
})().catch((e) => {
  console.error('FATAL:', e.message);
  process.exit(1);
});
