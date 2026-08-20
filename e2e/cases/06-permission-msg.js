// 06-permission-msg.js —— 权限/审核群消息 E2E（服务器日志断言）
// 场景：review_submitted 申请发起 / review_approved 审核通过 / rank_promoted 晋升 / review_rejected 拒绝 / review_cancelled 撤回
// 自包含：$a 加白名单 → lp 设组/op 设管理员 → 测试 → 清理（$r + deop）
// ⚠️ LP 设组必须 parent set + group set 双发（parent 自动创建用户；group set 设主组=track 判定源）
// ⚠️ 注册后 SimpleLogin 自动登录有延迟：spawn 后 sleep 2.5s 再发命令
// 用法: NODE_PATH=~/minecraft-bot/node_modules node cases/06-permission-msg.js
const { rcon, waitLog } = require('../lib/rcon');
const { spawnBot } = require('../lib/bot');

const results = [];
let failed = 0;

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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 登录节流：login_rate_limit（5 次/分钟）+ LoginSecurity 登录冷却（实测 13s+）——
// 登录间隔不足 20s 则等待（双端安全：Folia SimpleLogin / Paper LoginSecurity）
let lastLoginAt = 0;
async function throttledSpawn(opts) {
  const wait = 20000 - (Date.now() - lastLoginAt);
  if (wait > 0) await sleep(wait);
  const b = await spawnBot(opts);
  lastLoginAt = Date.now();
  return b;
}

// 申请人前置：先进服创建 LP 用户（default）→ 退出 → parent+主组设 member
// （LP 对不存在用户 parent set 创建的虚拟用户会被首登覆盖，必须先建后设）
async function prepMember(name) {
  const b = await throttledSpawn({ name, password: 'GMsgTest123', autoRegister: true });
  await sleep(2500);
  try { b.quit(); } catch (e) { /* ignore */ }
  await sleep(1000);
  await rcon(`lp user ${name} parent set member`);
  await rcon(`lp user ${name} group set member`);
  return true;
}

// 等「[群消息:key]」出现且包含 mustContain（当前场景玩家名）——历史同 key 记录不算
async function waitGmsgBlock(key, mustContain, timeoutMs = 25000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const content = await waitLog(`[群消息:${key}]`, undefined, 8000, 4000);
      const lines = content.split('\n');
      let idx = -1;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes(`[群消息:${key}]`)) idx = i;
      }
      const line = idx < 0 ? '' : lines[idx];
      if (line.includes(mustContain)) return line;
    } catch (e) { /* 未出现继续等 */ }
    await sleep(1500);
  }
  throw new Error(`等待群消息超时: ${key} 含 ${mustContain}`);
}

(async () => {
  const uniq = Date.now() % 100000;
  const app = `GMsgApp${uniq}`;
  const admin = `GMsgAdm${uniq}`;
  const app2 = `GMsgApp${uniq}b`;
  const app3 = `GMsgApp${uniq}c`;

  await check('前置: 服务器 RCON 在线', async () => {
    await rcon('list');
    return true;
  });

  // ---- 前置准备 ----
  for (const n of [app, admin, app2, app3]) {
    await check(`\$a 添加白名单 ${n}`, async () => { await rcon(`orzdebug $a ${n}`); return true; });
  }
  await check(`lp 设 ${app} 为 member（先建后设）`, () => prepMember(app));
  await check(`lp 设 ${app2} 为 member（先建后设）`, () => prepMember(app2));
  await check(`lp 设 ${app3} 为 member（先建后设）`, () => prepMember(app3));
  await check(`op ${admin}（审核人权限）`, async () => { await rcon(`op ${admin}`); return true; });

  // ---- 场景 1：申请发起 ----
  await check(`review_submitted: ${app} /apply builder`, async () => {
    let bot;
    try {
      bot = await throttledSpawn({ name: app, password: 'GMsgTest123', autoRegister: true });
      await sleep(2500);
      bot.chat('/apply builder 权限E2E测试');
      const line = await waitGmsgBlock('review_submitted', app, 25000);
      return line.includes('申请发起') && !/{[^}]+}/.test(line);
    } finally {
      if (bot) { try { bot.quit(); } catch (e) { /* ignore */ } }
    }
  });

  // ---- 场景 2：审核通过 + 晋升 ----
  await check(`review_approved: ${admin} 审核通过 ${app}`, async () => {
    let bot;
    try {
      bot = await throttledSpawn({ name: admin, password: 'GMsgTest123', autoRegister: true });
      await sleep(2500);
      bot.chat(`/review approve ${app}`);
      const line = await waitGmsgBlock('review_approved', app, 25000);
      return line.includes('申请通过') && line.includes(admin) && !/{[^}]+}/.test(line);
    } finally {
      if (bot) { try { bot.quit(); } catch (e) { /* ignore */ } }
    }
  });
  await check(`rank_promoted: ${app} 权限已升级为 builder`, async () => {
    const line = await waitGmsgBlock('rank_promoted', app, 25000);
    return line.includes('权限已升级') && (line.includes('建造者') || line.includes('builder')) && !/{[^}]+}/.test(line);
  });

  // ---- 场景 3：审核拒绝 ----
  await check(`review_rejected: ${app2} 被拒绝`, async () => {
    let bot;
    let adm;
    try {
      bot = await throttledSpawn({ name: app2, password: 'GMsgTest123', autoRegister: true });
      await sleep(2500);
      bot.chat('/apply builder 权限E2E测试');
      await waitGmsgBlock('review_submitted', app2, 25000);
      try { bot.quit(); } catch (e) { /* ignore */ }
      adm = await throttledSpawn({ name: admin, password: 'GMsgTest123', autoRegister: true });
      await sleep(2500);
      adm.chat(`/review reject ${app2}`);
      const line = await waitGmsgBlock('review_rejected', app2, 25000);
      return line.includes('申请拒绝') && !/{[^}]+}/.test(line);
    } finally {
      for (const b of [bot, adm]) { if (b) { try { b.quit(); } catch (e) { /* ignore */ } } }
    }
  });

  // ---- 场景 4：申请撤回 ----
  await check(`review_cancelled: ${app3} 撤回申请`, async () => {
    let bot;
    try {
      bot = await throttledSpawn({ name: app3, password: 'GMsgTest123', autoRegister: true });
      await sleep(2500);
      bot.chat('/apply builder 权限E2E测试');
      await waitGmsgBlock('review_submitted', app3, 25000);
      bot.chat('/apply cancel builder');
      const line = await waitGmsgBlock('review_cancelled', app3, 25000);
      return line.includes('申请撤回') && !/{[^}]+}/.test(line);
    } finally {
      if (bot) { try { bot.quit(); } catch (e) { /* ignore */ } }
    }
  });

  // ---- 清理还原 ----
  await check(`deop ${admin}（还原）`, async () => { await rcon(`deop ${admin}`); return true; });
  for (const n of [app, admin, app2, app3]) {
    await check(`\$r 移除白名单 ${n}（还原）`, async () => { await rcon(`orzdebug $r ${n}`); return true; });
  }

  console.log('===== 06-permission-msg 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}`);
  process.exit(failed === 0 ? 0 : 1);
})().catch((e) => {
  console.error('FATAL:', e.message);
  process.exit(1);
});
