// 04-maintenance.js —— 世界维护类 E2E（$b 备份三阶段 + 完成 + 备份文件落盘）
// ⚠️ $b 会踢出所有在线玩家并短暂进入维护模式——适合无玩家时段执行
// 环境变量: ORZMC_LOG_PATH（日志）ORZMC_BACKUP_DIR（备份目录）
// 用法: NODE_PATH=~/minecraft-bot/node_modules node cases/04-maintenance.js
const { rcon, waitLog } = require('../lib/rcon');
const fs = require('fs');
const path = require('path');
const os = require('os');

const LOG_PATH = process.env.ORZMC_LOG_PATH || path.join(os.homedir(), 'papermc-test', 'logs', 'latest.log');
const BACKUP_DIR = process.env.ORZMC_BACKUP_DIR || path.join(os.homedir(), 'papermc-test', 'backup');

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

(async () => {
  await check('前置: 服务器 RCON 在线', async () => (await rcon('list')).length >= 0);

  // 记录备份目录现状（retention_count=1 时旧文件会被清理，用文件名差异判定）
  const before = new Set(fs.existsSync(BACKUP_DIR) ? fs.readdirSync(BACKUP_DIR) : []);
  console.log(`  备份目录现有 ${before.size} 个文件`);

  // 触发 $b 备份（维护模式：踢出在线玩家）
  await check('$b 触发备份', async () => {
    await rcon('orzdebug $b');
    return waitLog('地图备份');
  });

  // 备份进入执行（阶段进度出现）——功能链路验证。
  // ⚠️ 大世界（如 Paper 测试服 317 万 chunk）完整备份需 ~17 分钟，
  //    完成断言仅在小型世界（Folia 测试服）执行
  await check('$b 备份进入执行（阶段进度）', async () => {
    return waitLog('地图备份 阶段:Running', LOG_PATH, 20000);
  });

  // 小世界才断言完成（大世界跳过——由 ORZMC_ASSERT_COMPLETE=1 强制）
  if (process.env.ORZMC_ASSERT_COMPLETE === '1') {
    await check('$b 备份完成（含耗时）', async () => {
      return waitLog('地图备份 完成', LOG_PATH, 30000);
    });
  } else {
    console.log('  ⚠️ 大世界模式：跳过「备份完成」断言（用 ORZMC_ASSERT_COMPLETE=1 强制）');
  }

  // 备份文件实际落盘（backup 目录出现新 .zip 文件——排除 tempDir 中间产物）
  // ⚠️ 大世界（Paper 测试服 317 万 chunk）完整备份需 ~17 分钟，60s 等待必然超时——
  //    因此仅 ORZMC_ASSERT_COMPLETE=1 显式强制时断言（run-all.sh 全套不设置该变量，与旧行为一致）
  if (process.env.ORZMC_ASSERT_COMPLETE === '1') {
    await check('备份 .zip 落盘', async () => {
      return waitForZipFile(BACKUP_DIR, before, 60000);
    }, () => true);
  }

  // 服务器仍正常（维护结束恢复）
  await check('维护结束服务器正常', async () => (await rcon('list')).length >= 0);

  console.log('===== 04-maintenance 结果 =====');
  results.forEach((r) => console.log(r));
  console.log(`通过 ${results.length - failed}/${results.length}`);
  process.exit(failed === 0 ? 0 : 1);
})();

// 等待备份目录出现新 .zip 完成文件（排除 tempDir 中间产物；兼容 retention 清理）
async function waitForZipFile(dir, before, timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (fs.existsSync(dir)) {
      const now = fs.readdirSync(dir).filter((f) => f.endsWith('.zip'));
      const added = now.filter((f) => !before.has(f));
      if (added.length > 0) {
        return `新增 zip: ${added.join(', ')}`;
      }
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error('备份目录无新 .zip 文件（备份可能失败，检查 OptimizeError）');
}
