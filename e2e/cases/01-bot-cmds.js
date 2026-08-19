// 01-bot-cmds.js —— Bot 命令类 E2E（$h/$l/$w/$a/$r/$d/$e，写操作带还原）
// 用法: NODE_PATH=~/minecraft-bot/node_modules node cases/01-bot-cmds.js
// 输出: [PASS]/[FAIL] 行；退出码 0=全过 1=有失败（KNOWN-BUG 计入但单独标注）
const { rcon, waitLog } = require('../lib/rcon');

const results = [];
let failed = 0;
let known = 0;

async function check(name, fn, expect = true, isKnown = false) {
  try {
    const out = await fn();
    const ok = expect === true ? !!out : (typeof expect === 'function' ? expect(out) : out.includes(expect));
    results.push(`[${ok ? 'PASS' : 'FAIL'}${isKnown ? '-KNOWN' : ''}] ${name}${ok ? '' : `\n    → 输出: ${String(out).slice(0, 300)}`}`);
    if (!ok) { failed++; if (isKnown) known++; }
  } catch (e) {
    results.push(`[FAIL${isKnown ? '-KNOWN' : ''}] ${name} → 异常: ${e.message}`);
    if (isKnown) known++; else failed++;
  }
}

(async () => {
  // 前置：服务器 RCON 在线
  await check('前置: 服务器 RCON 在线', async () => (await rcon('list')).length >= 0);

  // $h 帮助（改版样式：🤖 标题）
  await check('$h 帮助输出', async () => {
    await rcon('orzdebug $h');
    return waitLog('🤖 OrzMC 群指令帮助');
  });

  // $l 在线
  await check('$l 在线列表', async () => {
    await rcon('orzdebug $l');
    return waitLog('当前在线');
  });

  // $w 白名单分页 —— 已知 bug（BUG-E2E-001: Folia i=0 delay=0 异常），照常断言
  await check('$w 白名单列表', async () => {
    await rcon('orzdebug $w');
    return waitLog('当前白名单玩家');
  }, true, true);

  // $d 黑名单查询（只读）
  await check('$d 黑名单查询', async () => {
    await rcon('orzdebug $d');
    return waitLog('黑名单');
  });

  // $e 控制台命令（say → 日志应出现广播）
  await check('$e 控制台命令 say 生效', async () => {
    const tag = `e2e-say-${Date.now() % 10000}`;
    await rcon(`orzdebug $e say ${tag}`);
    return waitLog(tag);
  });

  // $a 加白 + $r 还原（写操作，测完必须移除）
  const testName = `E2E_Test_${Date.now() % 100000}`;
  await check(`$a 添加白名单 ${testName}`, async () => {
    await rcon(`orzdebug $a ${testName}`);
    return waitLog(testName);
  });
  await check(`$r 移除白名单 ${testName}（还原）`, async () => {
    await rcon(`orzdebug $r ${testName}`);
    return waitLog('白名单移除');
  });

  // 汇总
  console.log('===== 01-bot-cmds 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}（KNOWN-BUG ${known} 项）`);
  process.exit(failed === 0 ? 0 : 1);
})();
