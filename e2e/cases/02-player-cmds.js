// 02-player-cmds.js —— 玩家命令类 E2E（/guide /menu /bot /rank /apply status /config 权限隔离）
// 自包含：专用 bot 账号自动注册 → 测试 → 退服 → 清理白名单
// 用法: 由 e2e/run-all.sh 或技能 wrapper（scripts/e2e-mcsm-wrapper.sh）注入环境运行（见 README）
const { rcon, waitLog } = require('../lib/rcon');
const { spawnBot, waitMessage, quitBot } = require('../lib/bot');

const results = [];
let failed = 0;
const BOT_NAME = `E2EPlayer${Date.now() % 100000}`;
const BOT_PASS = 'E2EPass123';

async function check(name, fn, expect = true) {
  try {
    const out = await fn();
    const ok = expect === true ? !!out : (typeof expect === 'function' ? expect(out) : out.includes(expect));
    results.push(`[${ok ? 'PASS' : 'FAIL'}] ${name}${ok ? '' : `\n    → 输出: ${String(out).slice(0, 300)}`}`);
    if (!ok) failed++;
  } catch (e) {
    results.push(`[FAIL] ${name} → 异常: ${e.message}`);
    failed++;
  }
}

(async () => {
  // 前置 0：加白名单（写操作，结束时还原）
  await check(`前置: \$a 添加白名单 ${BOT_NAME}`, async () => {
    await rcon(`orzdebug $a ${BOT_NAME}`);
    return waitLog(BOT_NAME);
  });

  // bot 进服（自动注册 SimpleLogin）
  let bot = null;
  await check(`bot ${BOT_NAME} 进服+注册+登录`, async () => {
    bot = await spawnBot({ name: BOT_NAME, password: BOT_PASS, autoRegister: true });
    return bot._loggedIn;
  });

  // 首次进服自动发放新手指南书（features.md 9.1）
  await check('首次进服自动获得新手指南', async () => {
    return waitMessage(bot, '获得新手指南');
  });

  // /guide 新手书（openBook 无法读取内容，断言无异常 + 收到使用提示）
  await check('/guide 无异常', async () => {
    bot.chat('/guide');
    await new Promise((r) => setTimeout(r, 1500));
    return !bot._kickReason;
  });

  // /menu 菜单 GUI → 点击 → 功能开发中
  await check('/menu 打开+点击反馈', async () => {
    bot.chat('/menu');
    const win = await new Promise((resolve) => {
      bot.once('windowOpen', (w) => resolve(w));
      setTimeout(() => resolve(null), 4000);
    });
    if (!win) return false;
    bot.clickWindow(0, 0, 0);
    return waitMessage(bot, '功能开发中');
  });

  // /bot 健康状态（输出格式：enabled httpOk wsOk）
  await check('/bot 状态', async () => {
    bot.chat('/bot');
    return waitMessage(bot, 'wsOk');
  });

  // /rank 权限组信息（访客=default）
  await check('/rank 显示权限组', async () => {
    bot.chat('/rank');
    return waitMessage(bot, /当前|权限|访客|成员/);
  });

  // /apply status 申请状态（无申请时提示）
  await check('/apply status 可查询', async () => {
    bot.chat('/apply status');
    return waitMessage(bot, /申请|审核|暂无|没有/);
  });

  // /config 对访客不可见（权限隔离正确性：Unknown = 非管理员看不到管理员命令）
  await check('/config 访客权限隔离（Unknown=正确）', async () => {
    bot.chat('/config get tnt.enable');
    return waitMessage(bot, /Unknown|incomplete|未知/);
  });

  // 清理：退服 + 移除白名单
  quitBot(bot);
  await new Promise((r) => setTimeout(r, 1500));
  await check(`清理: \$r 移除白名单 ${BOT_NAME}`, async () => {
    await rcon(`orzdebug $r ${BOT_NAME}`);
    return waitLog('白名单移除');
  });

  console.log('===== 02-player-cmds 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}`);
  process.exit(failed === 0 ? 0 : 1);
})();
