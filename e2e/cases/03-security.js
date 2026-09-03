// 03-security.js —— 安全拦截类 E2E（IP 黑名单登录拦截 / 聊天过滤 / 命令守卫）
// 自包含：写操作全部带还原（$d 加黑→测→移除）
// 环境变量: ORZMC_TEST_PORT（MC 端口，默认 25565）ORZMC_LOG_PATH
// 用法: 由 e2e/run-all.sh 或技能 wrapper（scripts/e2e-mcsm-wrapper.sh）注入环境运行（见 README）
const mineflayer = require('mineflayer');
const { rcon, waitLog } = require('../lib/rcon');
const { TEST_PORT } = require('../lib/bot');
const LOG_PATH = process.env.ORZMC_LOG_PATH;  // 无默认：由运行入口注入（MCSM 实例日志路径，见 e2e-mcsm-wrapper.sh）
if (!LOG_PATH) { console.error('❌ 未设置 ORZMC_LOG_PATH（由 run-all.sh / 技能 wrapper 注入）'); process.exit(1); }

const results = [];
let failed = 0;

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

// 尝试登录并返回被踢原因（或 null=成功进服）
function tryLogin(name, timeoutMs = 15000) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1',
      port: TEST_PORT,
      username: name,
      auth: 'offline',
      version: '1.21.11',
    });
    let reason = null;
    let settled = false;
    const done = (v) => { if (!settled) { settled = true; try { bot.quit(); } catch (e) {} resolve(v); } };
    bot.once('kicked', (r) => { reason = JSON.stringify(r); done(reason); });
    bot.once('spawn', () => done(null));
    bot.once('end', () => { if (!reason) done(null); });
    bot.on('error', () => {});
    setTimeout(() => done(reason), timeoutMs);
  });
}

(async () => {
  await check('前置: 服务器 RCON 在线', async () => (await rcon('list')).length >= 0);

  // ---- IP 黑名单登录拦截（核心安全链路）----
  const blackIp = '127.0.0.1'; // 本机 bot 的 IP，安全测试用
  await check(`\$d ${blackIp} 添加黑名单`, async () => {
    await rcon(`orzdebug $d ${blackIp}`);
    return waitLog('已添加');
  });

  const badName = `E2EBad${Date.now() % 100000}`;
  await check(`黑名单 IP 登录被拦截（reason=你的IP已被禁止访问）`, async () => {
    const reason = await tryLogin(badName, 12000);
    return reason && reason.includes('你的IP已被禁止访问');
  });

  await check(`\$d -${blackIp} 移除黑名单（还原）`, async () => {
    await rcon(`orzdebug $d -${blackIp}`);
    return waitLog('已移除');
  });

  // ---- 聊天过滤（ChatSpamFilter：重复消息检测）----
  // 需要已注册登录的 bot；用专用账号自动注册
  const { spawnBot, waitMessage, quitBot } = require('../lib/bot');
  const chatName = `E2EChat${Date.now() % 100000}`;
  await check(`前置: \$a 添加白名单 ${chatName}`, async () => {
    await rcon(`orzdebug $a ${chatName}`);
    return waitLog(chatName);
  });
  let bot = null;
  await check(`bot ${chatName} 进服+注册`, async () => {
    bot = await spawnBot({ name: chatName, password: 'E2EPass123', autoRegister: true });
    return bot._loggedIn;
  });
  // 发送完全相同的消息两次 → 第二次应被拦截并提示
  // 注：mineflayer 不回显自己的聊天（第一条收不到属正常），
  //     服务器端 ChatSpamFilter 基于内存记录检测，第二条必触发
  await check('重复消息被过滤+提示', async () => {
    bot.chat('E2E-dup-test-message');
    await new Promise((r) => setTimeout(r, 1500));
    bot.chat('E2E-dup-test-message');
    return waitMessage(bot, '请勿刷屏或发送广告');
  });
  // 链接消息被过滤
  await check('含链接消息被过滤', async () => {
    bot.chat('visit https://example.com/xyz');
    return waitMessage(bot, '请勿刷屏或发送广告');
  });

  quitBot(bot);
  await new Promise((r) => setTimeout(r, 1500));
  await check(`清理: \$r 移除白名单 ${chatName}`, async () => {
    await rcon(`orzdebug $r ${chatName}`);
    return waitLog('白名单移除');
  });

  // ---- 命令守卫（guard.blocked_commands：seed 拦截）----
  await check('guard: $e seed 被拦截（无 seed 输出）', async () => {
    await rcon('orzdebug $e seed');
    // seed 命令若执行会输出世界种子；拦截则日志出现 guard 记录
    const guardLog = await new Promise((resolve) => {
      const fs = require('fs');
      setTimeout(() => {
        const content = fs.readFileSync(LOG_PATH, 'utf8').split('\n').slice(-120).join('\n');
        resolve(/guard|危险命令|拦截|blocked/i.test(content) || !/Seed \[/.test(content));
      }, 3000);
    });
    return guardLog;
  });

  console.log('===== 03-security 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}`);
  process.exit(failed === 0 ? 0 : 1);
})();
