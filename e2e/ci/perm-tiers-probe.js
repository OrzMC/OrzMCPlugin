#!/usr/bin/env node
// e2e/ci/perm-tiers-probe.js —— 权限三档矩阵探测（CI 用；依赖：mineflayer）
//
// ## 为什么要做这一层
// 技能侧脚本 `perm-tier-test.js` 只把三个账号的命令回显打到日志里、不做任何断言——
// 权限配错/`orzmc.admin` 节点被误删都不会让它变红，等于没有回归价值。本脚本把它改造成
// **有断言、有正负对照、失败即非 0 退出**的自举 E2E，且只依赖仓库内可下载的插件
// （Paper + LuckPerms + OrzMC 自己），不再依赖测试服上的 Essentials/WorldEdit。
//
// ## 断言设计（为什么这么断言）
// 1. **锚定真实节点 + 实测文案**：OrzMC 的权限判定只有两处真实实现
//    · Brigadier `.requires(...)`（`BrigadierSupport.requirement` 只取 `AdminOnlyInterceptor`）：
//      非管理员**看不到节点**，直接执行只会得到原版文案「Unknown or incomplete command, see below for error」
//      ——这就是下面断言的拒绝文案（2026-11 本机实测抓的，见 REJECT 常量处注释）；
//    · `CommandPermissionService#requireAdmin`：`player.isOp() || hasPermission("orzmc.admin")`。
//    注意：本例**不**用「没报错就算通过」这种模糊判定——每条拒绝都要求出现上述文案，
//    且必须**不出现**成功文案（`expectDeny` 双向检查）。
// 2. **三档必须形成「逐档放行更多」的差异**，否则矩阵没有信息量：
//    · default：OrzMC 管理命令全拒；`/rank` 显示 default 组；`/apply` 无可申请类型；
//    · member ：管理命令仍全拒（**证明 LP 继承不会顺带把 `orzmc.admin` 漏下来**），
//      但 `/apply` 列出 `/apply builder`（资格预检读当前 LP 组，证明它真的是 member 组）；
//    · builder：挂上 `orzmc.admin` 节点后 `/portal` 放行、`/orzdebug` 也放行
//      （硬编码 admin 命令判定是 `isOp() || hasPermission("orzmc.admin")`），
//      `/apply` 列出 `admin` 档（比 member 多一档申请资格）；
//    · op     ：全放行对照（`/orzdebug` 等硬编码 admin 命令）——证明「拒绝」来自权限判定
//      而不是命令本身坏了（否则「全拒」也会让前两档假绿）。
// 3. **每档都有一条「正向」断言**（`/rank` 或 `/apply`）：若服务器没起来/命令层挂了，
//    所有「被拒」断言都会因为等不到文案而失败，不会出现「一条 message 都没收到 = 通过」。
// 4. `/rank` 输出里的组代码（`default`/`member`/`builder`）是 LuckPerms 组是否真的挂上的
//    唯一可信证据：`lp user <name> parent set <group>` 只影响下次登录，必须靠它验证生效。
// 5. `/bot http` **只做「未被拒绝」断言，不当作放行证据**（2026-11 评审）：任何错误回显
//    （参数错、状态查询失败、内部异常）都含 `HTTP`，按 `/HTTP/` 判放行会把错误当成功。
//    真正的放行证据由 `/rank` 组名、`/apply` 列表、builder 的 `/portal` 用法回显、
//    `/orzdebug` 受理承担——这三档的差异已足以证明「拒绝来自 orzmc.admin 判定」。
//
// ## 两阶段（register / assert）
// LuckPerms 按**名字**解析离线玩家，且只解析「登录过」的玩家（实测：对没登录过的名字执行
// `lp user X parent set member` 只会得到 `[LP] A user for X could not be found.`）。
// 因此 Gradle 侧分两趟调用本脚本：
//   · `MC_PERM_PHASE=register` —— 四个账号各登录一次，把名字写进 usercache/playerdata；
//   · Gradle 下发 `lp creategroup/parent set/permission set`，等 LP 输出落地；
//   · `MC_PERM_PHASE=assert`   —— 本矩阵断言。
//
// ## 超时预算（为什么是这个数）
// 全局超时按「各步上限之和 × 1.5 + 30s」计算，慢 runner 上先由本探针自己超时退出，
// **报出卡在哪一步/哪个账号**，而不是被父进程一刀砍掉（那样既不知道哪条断言失败，也来不及
// 断开连接，服务器侧会残留「玩家仍在登录中」）。spawn 上限收到 30s（旧值 90s × 4 账号 = 360s，
// 已经超过旧的总超时 170s）：本地/CI 登录远快于此，真慢就让它明确失败。
//   · register：4 × spawn 30s + 收尾 10s = 130s → 130×1.5+30 ≈ 225s
//   · assert  ：4 × spawn 30s + 13 条断言 × 8s + 4 个「未拒绝」窗口 × 3s + 收尾 ≈ 238s
//               → 238×1.5+30 ≈ 390s（Gradle 侧给到 460s）
//
// ## 环境变量
//   MC_HOST / MC_PORT / MC_VERSION / MC_PERM_PHASE
// 输出：[PASS]/[FAIL] 行；退出码 0=全过，1=有失败。

const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.MC_PORT || '25587', 10);
// 客户端版本必须 ≤1.21.4（本探针用 1.20.4）：1.21.x 客户端经 ViaBackwards 连 26.3 时，
// mineflayer 上报的移动包会被服务端判为 `Invalid move player packet received` 踢人，
// 断言就全打在死连接上（实测见 e2e/ci/entity-portal-probe.js 文件头）
const VERSION = process.env.MC_VERSION || '1.20.4';
const PHASE = process.env.MC_PERM_PHASE || 'assert';

// 账号 → 期望的 LP 组。op 账号不进 LP 组（对照组）。
const ACCOUNTS = [
  { name: 'E2EDefault', group: 'default' },
  { name: 'E2EMember', group: 'member' },
  { name: 'E2EBuilder', group: 'builder' },
  { name: 'E2EOp', group: 'op' },
];

// ---- 各步上限（改任何一个都要重算下面的总预算，见文件头「超时预算」）----
const SPAWN_TIMEOUT_MS = 30000;
const ASK_TIMEOUT_MS = 8000;
const NO_DENY_WINDOW_MS = 3000;
/** assert 阶段的 ask 次数（default 4 / member 3 / builder 4 / op 2）。 */
const ASSERT_ASK_COUNT = 13;
/** assert 阶段「未拒绝」窗口数（四档各一条 /bot http）。 */
const ASSERT_NO_DENY_COUNT = 4;

const REGISTER_STEP_SUM_MS = ACCOUNTS.length * SPAWN_TIMEOUT_MS + 10000;
const ASSERT_STEP_SUM_MS =
  ACCOUNTS.length * SPAWN_TIMEOUT_MS +
  ASSERT_ASK_COUNT * ASK_TIMEOUT_MS +
  ASSERT_NO_DENY_COUNT * NO_DENY_WINDOW_MS +
  ACCOUNTS.length * 1000;
/** 各步之和 × 1.5 + 30s（取整到秒）。 */
const budget = (stepSumMs) => Math.round((stepSumMs * 1.5 + 30000) / 1000) * 1000;
const TOTAL_TIMEOUT_MS = budget(PHASE === 'register' ? REGISTER_STEP_SUM_MS : ASSERT_STEP_SUM_MS);

// ---- 真实拒绝文案（2026-11 本机实测抓取） ----
// OrzMC 的 admin-only 命令全部在 Brigadier `.requires()` 阶段就被过滤
// （`BrigadierSupport.requirement` 只取 `AdminOnlyInterceptor`），非管理员**看不到节点**，
// 直接执行时服务端回的是原版文案：
//   「Unknown or incomplete command, see below for error」（en 客户端）
//   「未知或不完整的命令」（zh 客户端）
// 自定义文案 `common.admin_required`（「需要管理员权限」/「Administrator permission required.」）
// 只在「命令可见、执行时被拦截」时才出现，而 OrzMC 目前没有这样的玩家可达命令
// （`/bot http` 的 adminOnly 取自 command_policies，默认 false → 本来就该放行，见下方断言）。
// 因此这里按实测文案断言：**被拒**必须看到原版未知命令 + 不得出现成功文案。
const REJECT =
  /Unknown or incomplete command|未知或不完整的命令|需要管理员权限|requires administrator permissions|Administrator permission required|no permission|没有权限/i;
// 组代码断言：zh 用全角括号、en 用半角括号，两种语言都接受（bot 的系统消息用默认语言，
// 玩家消息按 client locale，探针不假定一定落在哪一种）。
const GROUP_CODE = (g) => new RegExp(`[（(]${g}[）)]`);
const PORTAL_USAGE = /\/portal <host>/; // zh「用法: /portal <host> …」/ en「Usage: /portal <host> …」
const ORZDEBUG_ACCEPTED = /debug 已受理|Debug command accepted/i;

let failed = 0;
/** 当前步骤（账号 + 断言名）：总超时/异常时必须能说出「卡在哪一步」。 */
let currentStep = `阶段 ${PHASE} 启动`;
/** 已连接但未断开的 bot：总超时时统一 quit，避免服务器侧残留登录中会话。 */
const openBots = new Set();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function step(name) {
  currentStep = name;
  console.log(`\n--- ${name} ---`);
}

function pass(name) {
  console.log(`[PASS] ${name}`);
}

function fail(name, detail) {
  failed++;
  console.log(`[FAIL] ${name}${detail ? ` → ${detail}` : ''}`);
}

/** 去掉 Minecraft 颜色码，避免断言字符串被 §x 拆断。 */
const plain = (s) => String(s).replace(/§[0-9a-fk-or]/gi, '');

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

/** 断开所有仍连着的 bot（finally 与总超时路径共用）。 */
function quitAll() {
  for (const bot of openBots) {
    try {
      bot.quit();
    } catch (e) {}
  }
  openBots.clear();
}

/** 连接一个账号并执行 steps(bot, ctx)；返回是否成功 spawn。 */
async function withBot(username, steps) {
  const chats = [];
  step(`${username} 登录`);
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: VERSION });
  openBots.add(bot);
  let connectError = null;
  bot.on('error', (e) => {
    connectError = e;
    // 断线原因必须打出来：否则只会看到「没有回复」，排查成本极高
    console.log(`  [error] ${e && e.message ? e.message : e}`);
  });
  bot.on('end', (reason) => {
    openBots.delete(bot);
    console.log(`  [end] 连接断开（${reason || '未知'}）`);
  });
  bot.on('kicked', (reason) => {
    connectError = new Error(`被踢出: ${JSON.stringify(reason)}`);
  });
  bot.on('message', (m) => {
    const text = plain(m.toString());
    if (text.trim()) {
      chats.push(text);
      // 全量回显：断言失败时要能直接看到服务端到底回了什么（排查成本远低于重跑一次）
      console.log(`  [msg:${username}] ${text}`);
    }
  });

  const spawned = await waitFor(`${username} spawn`, () => bot.entity != null, SPAWN_TIMEOUT_MS);
  if (!spawned) {
    fail(`${username} 加入服务器`, connectError ? connectError.message : `最近消息: ${chats.slice(-3).join(' | ')}`);
    try {
      bot.quit();
    } catch (e) {}
    openBots.delete(bot);
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
    openBots.delete(bot);
    await sleep(600);
  }
  return true;
}

/**
 * 执行一条命令并等「本次执行之后」的回复（先清空聊天缓冲，避免拿到上一条命令的残留）。
 * 返回 { ok, lines }：ok = 收到了满足 predicate 的文案。
 */
async function ask(bot, chats, command, predicate, timeoutMs = ASK_TIMEOUT_MS) {
  chats.length = 0;
  bot.chat(command);
  const ok = await waitFor(`回复: ${command}`, () => chats.some((line) => predicate.test(line)), timeoutMs);
  return { ok, lines: chats.slice(-4) };
}

/** 正向断言：命令必须执行成功（收到期望文案）。 */
async function expectAllow(bot, chats, command, predicate, label) {
  step(label);
  const r = await ask(bot, chats, command, predicate);
  if (r.ok) {
    pass(label);
  } else {
    fail(label, `未收到期望回复，实际: ${r.lines.join(' | ') || '(无消息)'}`);
  }
}

/**
 * 「未被拒绝」断言：只证明**没有收到拒绝文案**。
 *
 * <p>这**不是**放行证据：任何错误回显（参数错误、状态查询失败、内部异常）都含 `HTTP`，
 * 按 `/HTTP/` 判放行会把错误当成功。放行证据见文件头第 5 条（`/rank` 组名、`/apply` 列表、
 * builder 的 `/portal` 用法回显、`/orzdebug` 受理）。</p>
 */
async function expectNotDenied(bot, chats, command, denyRe, label) {
  step(label);
  chats.length = 0;
  bot.chat(command);
  await sleep(NO_DENY_WINDOW_MS);
  const denied = chats.find((line) => denyRe.test(line));
  if (denied) {
    fail(label, `收到拒绝文案: ${denied}`);
    return;
  }
  pass(label);
}

/** 负向断言：命令必须被拒绝（收到真实拒绝文案），且**不能**出现成功文案。 */
async function expectDeny(bot, chats, command, denyRe, forbidden, label) {
  step(label);
  const r = await ask(bot, chats, command, denyRe);
  if (!r.ok) {
    fail(label, `未收到拒绝文案，实际: ${r.lines.join(' | ') || '(无消息)'}`);
    return;
  }
  if (forbidden && r.lines.some((line) => forbidden.test(line))) {
    fail(label, `既收到拒绝文案又出现成功文案: ${r.lines.join(' | ')}`);
    return;
  }
  pass(label);
}

// ---------------------------------------------------------------- 阶段 1：登记
async function phaseRegister() {
  console.log('阶段 register：让 LuckPerms 能按名字解析四个测试账号（各登录一次即可）');
  for (const acc of ACCOUNTS) {
    await withBot(acc.name, async () => {
      /* 只登录，不做断言：目的是把名字写进 usercache，供 Gradle 侧的 lp user 命令解析 */
    });
  }
}

// ---------------------------------------------------------------- 阶段 2：矩阵
async function phaseAssert() {
  // ① default（访客）：管理命令全拒 + 无晋升资格
  await withBot('E2EDefault', async (bot, chats) => {
    await expectAllow(bot, chats, '/rank', GROUP_CODE('default'), 'default 组 /rank 显示访客组（LP 挂组已生效）');
    await expectAllow(
      bot,
      chats,
      '/apply',
      /没有可申请的审核类型|no review types/i,
      'default 组 /apply 无可申请类型（不满足晋升资格）',
    );
    // 只断言「未被拒绝」：`/bot http` 的 admin_only 默认 false，本来就该放行；
    // 但错误回显同样含 HTTP，因此这里不把它当放行证据（见文件头第 5 条）。
    await expectNotDenied(bot, chats, '/bot http', REJECT, 'default 组 /bot http 未被拒绝（非放行证据）');
    await expectDeny(bot, chats, '/portal', REJECT, PORTAL_USAGE, 'default 组 /portal 被 requires 过滤（不可见）');
    await expectDeny(bot, chats, '/orzdebug $h', REJECT, ORZDEBUG_ACCEPTED, 'default 组 /orzdebug 被 requires 过滤');
  });

  // ② member（成员）：管理命令仍拒（继承不越权），但获得晋升建造者的申请资格
  await withBot('E2EMember', async (bot, chats) => {
    await expectAllow(bot, chats, '/rank', GROUP_CODE('member'), 'member 组 /rank 显示成员组');
    await expectAllow(
      bot,
      chats,
      '/apply',
      /\/apply builder\b/,
      'member 组 /apply 列出「晋升建造者」（/apply builder，资格预检读当前 LP 组）',
    );
    await expectNotDenied(
      bot,
      chats,
      '/bot http',
      REJECT,
      'member 组 /bot http 未被拒绝（继承链未把 orzmc.admin 带出来）',
    );
    await expectDeny(bot, chats, '/portal', REJECT, PORTAL_USAGE, 'member 组 /portal 仍不可见');
  });

  // ③ builder（建造者 + orzmc.admin 节点）：管理命令放行
  await withBot('E2EBuilder', async (bot, chats) => {
    await expectAllow(bot, chats, '/rank', GROUP_CODE('builder'), 'builder 组 /rank 显示建造者组');
    await expectAllow(
      bot,
      chats,
      '/apply',
      /\/apply admin\b/,
      'builder 组 /apply 列出「晋升管理员」（/apply admin，比 member 档多一档申请资格）',
    );
    await expectNotDenied(bot, chats, '/bot http', REJECT, 'builder 组 /bot http 未被拒绝（非放行证据）');
    await expectAllow(bot, chats, '/portal', PORTAL_USAGE, 'builder 组 /portal 放行并返回用法');
    // 同一条节点也解锁硬编码 admin 命令（requires 阶段判 isOp || orzmc.admin），
    // 说明 default/member 档的拒绝确实来自「没有这个节点」，而不是命令本身不可用。
    await expectAllow(
      bot,
      chats,
      '/orzdebug $h',
      ORZDEBUG_ACCEPTED,
      'builder 组（挂 orzmc.admin）/orzdebug 受理（硬编码 admin 判定同样认该节点）',
    );
  });

  // ④ op（对照组）：硬编码 admin 命令（requires 阶段就判 isOp）必须全放行
  await withBot('E2EOp', async (bot, chats) => {
    await expectAllow(bot, chats, '/orzdebug $h', ORZDEBUG_ACCEPTED, 'op 账号 /orzdebug 受理（requires 对 op 放行）');
    await expectAllow(bot, chats, '/portal', PORTAL_USAGE, 'op 账号 /portal 放行');
    await expectNotDenied(bot, chats, '/bot http', REJECT, 'op 账号 /bot http 未被拒绝（非放行证据）');
  });
}

// ---------------------------------------------------------------- 入口
const totalTimer = setTimeout(async () => {
  // 超时同样要说明「卡在哪一步」，并断开连接（否则服务器侧残留登录中会话，下一次运行会挤）
  console.log(`[FAIL] 总超时 ${TOTAL_TIMEOUT_MS}ms（卡在步骤：${currentStep}）`);
  try {
    quitAll();
  } catch (e) {}
  await sleep(500);
  process.exit(1);
}, TOTAL_TIMEOUT_MS);

(async () => {
  console.log(`连接 ${HOST}:${PORT}（client ${VERSION}，阶段 ${PHASE}，上限 ${TOTAL_TIMEOUT_MS / 1000}s）`);
  if (PHASE === 'register') {
    await phaseRegister();
  } else {
    await phaseAssert();
  }
  clearTimeout(totalTimer);
  quitAll();
  console.log(`失败 ${failed} 项（阶段 ${PHASE}）`);
  process.exit(failed === 0 ? 0 : 1);
})().catch((e) => {
  clearTimeout(totalTimer);
  quitAll();
  console.log(`[FAIL] 探测脚本异常 → ${e && e.stack ? e.stack : e}`);
  process.exit(1);
});
