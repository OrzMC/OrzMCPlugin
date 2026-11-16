#!/usr/bin/env node
// e2e/ci/entity-portal-probe.js —— 村民穿越下界传送门探测（CI 用；依赖：mineflayer）
//
// ## 为什么做这一层
// OrzMC 的实体传送策略（`EntityTeleportPolicyService` + `OrzTPEvent`）会拦「命令/插件触发」的
// 实体传送，但**必须放行下界传送门穿越**——`EntityPortalEvent` 是 `EntityTeleportEvent` 的子类，
// 代码里刻意提前 return。这条边界此前只在真实测试服由真人脚本 `villager_portal_test.js` 手工跑过，
// 逻辑没有进 PR 门禁：一旦有人把 `EntityPortalEvent` 也纳入禁用策略，村民会被原地卡住而
// 单测/MockBukkit 全绿。本探针把那个脚本搬进 CI 自举层，去掉测试服账号依赖，并补上断言
// （源脚本只打印、不判定，等于没有回归价值）。
//
// ## 覆盖的链路
// 玩家 bot（op）用**原生命令**建标准 4×5 下界门（内 2×3）→ 召唤村民进门 → 原版门判定
// → `EntityPortalEvent`（插件放行）→ 村民消失并出现在地狱维度。
//
// ## 断言（为什么这么断言）
// 1. **门结构合法**：村民没过去也可能是门没点亮，所以用客户端区块数据（`bot.blockAt`）
//    逐一核对 4 角 + 4 条边是黑曜石、内层 2×3 是 `nether_portal`。没有这条，
//    「村民消失」的结论随时可能是「门根本没建成」。
// 2. **过门前村民必须在场**（前置守卫）：**先建门框、先召唤、后点亮内层**——门没点亮时村民
//    不会传送，这条时间窗让「summon 成功」可以被确定地断言。否则「summon 失败 →
//    扫描到 0 只 → 判定传送成功」会无条件假绿。
// 3. **过门后村民必须在原维度消失**：用服务端条件命令
//    `execute positioned <门坐标> unless entity @e[type=villager,distance=..20] run say <标记>`
//    断言（结果以 `say` 聊天回到探针），不依赖客户端实体追踪（实测 mineflayer 在 Via 转译下
//    `bot.entities` 拿不到生物）。**实体判定一律锚定到门坐标**（`execute positioned`）：
//    裸 `@e[type=villager,distance=..20]` 的原点是「命令源」（主世界共享出生点），
//    与门坐标只是靠隐含假设相等——换世界/换出生点就会静默失效。
// 4. **村民确实到了地狱维度**：`execute in minecraft:the_nether positioned <地狱侧坐标>
//    if entity @e[type=villager,distance=..64] run say <标记>`——「消失」也可能是被别的机制删掉，
//    只有这条能区分「穿越」和「销毁」。
// 5. 「插件没拦」由 3+4 的行为断言覆盖：若被 `EntityPortalEvent`/`OrzTPEvent` 拦下，村民
//    根本不会过去（探针直接失败）。**因此不再**做「日志里不得出现『实体传送被禁用』」的文本守卫
//    ——那类守卫按实体名裸串匹配，大小写/命名空间一变就恒绿，扫 offset 0 还会被上一轮日志污染
//    （2026-11-16 CI 曾因同句里无关实体 `Crimson Roots` 误红）。
//
// ## 两条实测踩坑（2026-11 本机 + 真实 Paper 26.3 跑出来的）
// · **客户端版本必须 ≤ 1.21.4（本探针用 1.20.4）**：1.21.9/1.21.11 客户端经 ViaBackwards
//   连 26.3 时，mineflayer 上报的移动包会被服务端判为 `Invalid move player packet received`
//   并踢人（登录后约 1 秒），且区块数据拿不到（0 个 chunk）→ 读方块/看实体全废。
//   1.20.4 客户端经 ViaVersion 转译后一切正常（能读方块、能收 say、长时间连接稳定）。
// · **别 tp 探针 bot**：服务端 tp 后客户端回的是旧位置包，同样会被上面那条判定踢掉。
//   门就建在 bot 站位旁边（相对坐标），全程不移动客户端。
//
// ## 超时预算（为什么是这个数）
// 全局超时按「各步上限之和 × 1.5 + 30s」计算，覆盖最坏情况：这样慢 runner 上先由本探针
// 自己超时退出，**报出卡在哪一步**；而不是被父进程（Gradle）一刀砍掉——那种情况下既看不到
// 哪条断言失败，也来不及清理（两个维度留村民、门框残留）。
//   各步之和 = spawn 30 + 清场 20 + 建门 3 + 门框同步 15 + 在场 8 + 点亮 15 + 过门 45
//              + 地狱 10 + 清理 10 = 156s → 156×1.5+30 ≈ 264s（Gradle 侧给到 300s）。
// spawn 上限收到 30s：本机/CI 起服 + 登录远快于此，真慢就让它明确失败（90s 的旧上限会把
// 「插件拒绝登录」拖成 90s 的沉默）。
//
// ## 清理
// 开头清上一轮遗留（锚定门坐标的村民 + 门区域回填空气），结尾在 **finally** 里再清一次并
// 断言清理生效——超时/断言失败/异常也会执行，保证任务可重复跑、世界里不留测试产物。
//
// 环境变量：MC_HOST / MC_PORT / MC_VERSION / MC_PORTAL_USER
// 输出：[PASS]/[FAIL] 行；退出码 0=全过，1=有失败。

const mineflayer = require('mineflayer');
const vec3 = require('vec3');

const HOST = process.env.MC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.MC_PORT || '25588', 10);
// 见文件头「实测踩坑」：1.21.x 客户端在 ViaBackwards 下会被服务端判为非法移动而踢人
const VERSION = process.env.MC_VERSION || '1.20.4';
const USER = process.env.MC_PORTAL_USER || 'E2EPortal';

/** 村民判定半径（以**门坐标**为原点，村民在门槛里，正常距离 1~3 格）。 */
const SCAN_RADIUS = 20;
/** 地狱侧判定半径（原版建目的地门时最多在目标坐标 16 格内搜索/新建，留到 64 格仍锚定得够紧）。 */
const NETHER_SCAN_RADIUS = 64;

// ---- 各步上限（改任何一个都要重算下面的总预算，否则最坏情况会超总超时）----
const SPAWN_TIMEOUT_MS = 30000;
const CLEAN_TIMEOUT_MS = 20000;
const BUILD_SETTLE_MS = 3000;
const FRAME_TIMEOUT_MS = 15000;
const PRESENT_TIMEOUT_MS = 8000;
const LIGHT_TIMEOUT_MS = 15000;
const TRAVEL_TIMEOUT_MS = 45000;
const NETHER_TIMEOUT_MS = 10000;
const CLEANUP_TIMEOUT_MS = 10000;

const STEP_SUM_MS =
  SPAWN_TIMEOUT_MS +
  CLEAN_TIMEOUT_MS +
  BUILD_SETTLE_MS +
  FRAME_TIMEOUT_MS +
  PRESENT_TIMEOUT_MS +
  LIGHT_TIMEOUT_MS +
  TRAVEL_TIMEOUT_MS +
  NETHER_TIMEOUT_MS +
  CLEANUP_TIMEOUT_MS;
/** 各步之和 × 1.5 + 30s（取整到秒）——见文件头「超时预算」。 */
const TOTAL_TIMEOUT_MS = Math.round((STEP_SUM_MS * 1.5 + 30000) / 1000) * 1000;

let failed = 0;
/** 当前步骤名：总超时/异常时必须知道「卡在哪一步」，否则只剩一个超时数字。 */
let currentStep = '连接服务器';
/** 总超时时主动清理的钩子（由主流程注入；未注入时只是打印）。 */
const cleanupHook = { run: null };

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

const plain = (s) => String(s).replace(/§[0-9a-fk-or]/gi, '');

async function waitFor(name, predicate, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      if (await predicate()) return true;
    } catch (e) {
      /* 继续等 */
    }
    await sleep(250);
  }
  console.log(`  (超时等待: ${name})`);
  return false;
}

(async () => {
  console.log(`连接 ${HOST}:${PORT}（client ${VERSION}）`);

  const chats = [];
  /** 服务端用 `say <TAG>` 回写的条件标记（命令结果与语言无关，断言稳定）。 */
  const markers = new Set();
  let connectError = null;
  let bot = null;
  /** 门坐标（清理也靠它锚定；未算出之前清理是空操作）。 */
  let anchor = null;

  /** 发一条原生命令（探针 bot 是 op，不需要 $e 通道；同一条连接上的命令由服务端按序执行）。 */
  async function cmd(command, settleMs = 400) {
    if (!bot || !bot.entity) return;
    bot.chat(command);
    await sleep(settleMs);
  }

  const blockAt = (x, y, z) => {
    const b = bot && bot.entity ? bot.blockAt(vec3(x, y, z)) : null;
    return b ? b.name : '(未加载)';
  };

  const cleanArea = () => {
    const { area } = anchor || {};
    if (!area) return Promise.resolve();
    return cmd(`/fill ${area.minX} ${area.minY} ${area.minZ} ${area.maxX} ${area.maxY} ${area.maxZ} minecraft:air`);
  };

  /**
   * 清两个维度（锚定门坐标）的村民 + 门区域回填空气。
   * @param {boolean} verify 是否再断言「清理生效」（首轮前置清理不需要）
   */
  async function cleanupProducts(verify) {
    if (!anchor) return;
    const { X, Y, Z } = anchor;
    // 原版只把 **X/Z** 按坐标比例（8）换算，**Y 原样带过去**——把 Y 也除以 8 会锚到
    // 地狱的另一个高度（实测差出 60+ 格，断言必假红）。
    const nx = Math.round(X / 8);
    const ny = Y;
    const nz = Math.round(Z / 8);
    await cmd(`/execute positioned ${X} ${Y} ${Z} run kill @e[type=villager,distance=..${SCAN_RADIUS}]`, 500);
    await cmd(
      `/execute in minecraft:the_nether positioned ${nx} ${ny} ${nz} run kill @e[type=villager,distance=..${NETHER_SCAN_RADIUS}]`,
      500,
    );
    await cleanArea();
    if (!verify) return;
    await cmd(
      `/execute positioned ${X} ${Y} ${Z} unless entity @e[type=villager,distance=..${SCAN_RADIUS}] ` +
        `unless block ${X} ${Y} ${Z} minecraft:obsidian run say TAG_CLEAN`,
      900,
    );
    const cleaned = await waitFor('清理生效标记', () => markers.has('TAG_CLEAN'), CLEANUP_TIMEOUT_MS);
    if (cleaned) {
      pass('测试产物已清理（两个维度的村民清空 + 门区域回填空气）');
    } else {
      fail(
        '测试产物已清理（两个维度的村民清空 + 门区域回填空气）',
        `锚点 (${X},${Y},${Z}) 仍有村民或门框残余 [${X},${Y},${Z}]=${blockAt(X, Y, Z)}；` +
          `最近消息: ${chats.slice(-3).join(' | ') || '(无)'}`,
      );
    }
  }

  bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USER,
    auth: 'offline',
    version: VERSION,
  });
  bot.on('error', (e) => {
    connectError = e;
    // 断线原因必须打出来：否则只会看到「命令没有效果」，排查成本极高
    console.log(`  [error] ${e && e.message ? e.message : e}`);
  });
  bot.on('kicked', (reason) => {
    connectError = new Error(`被踢出: ${JSON.stringify(reason)}`);
    console.log(`  [kicked] ${JSON.stringify(reason).slice(0, 120)}`);
  });
  bot.on('end', (reason) => console.log(`  [end] 连接断开（${reason || '未知'}）`));
  bot.on('message', (m) => {
    const text = plain(m.toString());
    if (!text.trim()) return;
    chats.push(text);
    for (const tag of ['TAG_PRESENT', 'TAG_GONE', 'TAG_NETHER', 'TAG_CLEAN']) {
      if (text.includes(tag)) markers.add(tag);
    }
    console.log(`  [msg] ${text}`);
  });

  step('连接并登录服务器');
  // 总超时时先收产物再退出（超时同样不能留村民/门框）
  cleanupHook.run = async () => {
    try {
      await cleanupProducts(false);
    } catch (e) {}
    try {
      bot.quit();
    } catch (e) {}
  };
  const spawned = await waitFor(`${USER} spawn`, () => bot.entity != null, SPAWN_TIMEOUT_MS);
  if (!spawned) {
    fail(
      `${USER} 加入服务器`,
      connectError ? connectError.message : `最近消息: ${chats.slice(-3).join(' | ') || '(无)'}`,
    );
    try {
      bot.quit();
    } catch (e) {}
    console.log(`失败 ${failed} 项`);
    process.exit(1);
  }
  pass(`${USER} 加入服务器`);

  try {
    // 等物理落地，再按「当前位置」推算门的坐标（不 tp 客户端，见文件头踩坑说明）
    await sleep(1500);
    const base = bot.entity.position;
    // 门比站位高 2 格、往 +X 偏 3 格：既不会把方块放进玩家身体里，也不会把玩家裹进门里
    const X = Math.round(base.x) + 3;
    const Y = Math.round(base.y) + 2;
    const Z = Math.round(base.z);
    // 门 + 前后挡墙 + 顶部标记方块所在区域（清理范围）
    const area = { minX: X - 1, minY: Y - 1, minZ: Z - 1, maxX: X + 4, maxY: Y + 6, maxZ: Z + 1 };
    anchor = { X, Y, Z, area };
    console.log(
      `站位 ${base.x.toFixed(1)},${base.y.toFixed(1)},${base.z.toFixed(1)} → 门坐标锚点 (${X},${Y},${Z})`,
    );

    // ---- 0. 清上一轮遗留（两个维度的村民 + 门区域） ----
    step('清理上一轮遗留');
    await cleanupProducts(false);
    const cleanStartOk = await waitFor('遗留方块清空', () => blockAt(X, Y, Z) === 'air', CLEAN_TIMEOUT_MS);
    if (!cleanStartOk) console.log('  (上一轮遗留未清干净，继续——门框整块会被覆盖)');

    // ---- 1. 建标准 4×5 门框（内 2×3，此时**不点亮**内层） ----
    // 内层故意留空：先把村民放进门槛并断言它在场，再点亮 → 消除「summon 失败也算传送成功」的假绿。
    step('建 4x5 门框（内 2x3，暂不点亮）');
    await cmd(`/fill ${X} ${Y} ${Z} ${X + 3} ${Y} ${Z} minecraft:obsidian`); // 底边
    await cmd(`/fill ${X} ${Y + 4} ${Z} ${X + 3} ${Y + 4} ${Z} minecraft:obsidian`); // 顶边
    await cmd(`/fill ${X} ${Y + 1} ${Z} ${X} ${Y + 3} ${Z} minecraft:obsidian`); // 左边
    await cmd(`/fill ${X + 3} ${Y + 1} ${Z} ${X + 3} ${Y + 3} ${Z} minecraft:obsidian`); // 右边
    // 前后挡墙：村民会乱走，走出门槛就重新计传送等待时间，测试会变成偶发失败
    await cmd(`/fill ${X + 1} ${Y + 1} ${Z - 1} ${X + 2} ${Y + 3} ${Z - 1} minecraft:stone`); // 后挡墙
    await cmd(`/fill ${X + 1} ${Y + 1} ${Z + 1} ${X + 2} ${Y + 3} ${Z + 1} minecraft:stone`); // 前挡墙
    await sleep(BUILD_SETTLE_MS);

    // ---- 2. 断言门框结构合法（4 角 + 4 条边中点 = 黑曜石） ----
    step('断言门框结构');
    const FRAME = [
      [X, Y, Z],
      [X + 3, Y, Z],
      [X, Y + 4, Z],
      [X + 3, Y + 4, Z],
      [X, Y + 2, Z],
      [X + 3, Y + 2, Z],
      [X + 1, Y + 4, Z],
      [X + 2, Y + 4, Z],
    ];
    const frameOk = await waitFor(
      '门框同步到客户端',
      () => FRAME.every(([x, y, z]) => blockAt(x, y, z) === 'obsidian'),
      FRAME_TIMEOUT_MS,
    );
    if (frameOk) {
      pass('门框结构合法（4×5 黑曜石门框：4 角 + 4 条边）');
    } else {
      fail(
        '门框结构合法（4×5 黑曜石门框：4 角 + 4 条边）',
        FRAME.map(([x, y, z]) => `[${x},${y},${z}]=${blockAt(x, y, z)}`).join(' '),
      );
    }

    // ---- 3. 召唤村民进门（门还没点亮 → 一定会留在原地），断言它在场（前置守卫） ----
    step('召唤村民并断言在场（门未点亮）');
    bot.chat(`/summon villager ${X + 1.5} ${Y + 1} ${Z + 0.5}`);
    await cmd(
      `/execute positioned ${X} ${Y} ${Z} if entity @e[type=villager,distance=..${SCAN_RADIUS}] run say TAG_PRESENT`,
      800,
    );
    const present = await waitFor('村民在场标记', () => markers.has('TAG_PRESENT'), PRESENT_TIMEOUT_MS);
    if (present) {
      pass('过门前：门槛内村民在场（前置守卫，排除 summon 失败导致的假绿）');
    } else {
      fail(
        '过门前：门槛内村民在场（前置守卫，排除 summon 失败导致的假绿）',
        `锚点 (${X},${Y},${Z}) 半径 ${SCAN_RADIUS} 内没有村民；没等到 TAG_PRESENT，` +
          `最近消息: ${chats.slice(-3).join(' | ') || '(无)'}`,
      );
    }

    // ---- 4. 点亮内层 2×3 → 门生效，村民开始计传送等待 ----
    step('点亮内层下界门');
    await cmd(`/fill ${X + 1} ${Y + 1} ${Z} ${X + 2} ${Y + 3} ${Z} minecraft:nether_portal`);
    const PORTAL_CELLS = [
      [X + 1, Y + 1, Z],
      [X + 2, Y + 1, Z],
      [X + 1, Y + 2, Z],
      [X + 2, Y + 2, Z],
      [X + 1, Y + 3, Z],
      [X + 2, Y + 3, Z],
    ];
    const portalOk = await waitFor(
      '内层下界门点亮',
      () => PORTAL_CELLS.every(([x, y, z]) => blockAt(x, y, z) === 'nether_portal'),
      LIGHT_TIMEOUT_MS,
    );
    if (portalOk) {
      pass('内层 2×3 全部为 nether_portal（门已点亮，不是无效门）');
    } else {
      fail(
        '内层 2×3 全部为 nether_portal（门已点亮，不是无效门）',
        PORTAL_CELLS.map(([x, y, z]) => `[${x},${y},${z}]=${blockAt(x, y, z)}`).join(' '),
      );
    }

    // ---- 5. 等村民离开原维度 ----
    step(`等待村民过门（最多 ${TRAVEL_TIMEOUT_MS / 1000}s）`);
    const gone = await waitFor(
      '村民离开原维度标记',
      async () => {
        // 轮询式条件命令：命中才会 `say TAG_GONE`（探针按聊天标记判定，不依赖客户端实体追踪）
        await cmd(
          `/execute positioned ${X} ${Y} ${Z} unless entity @e[type=villager,distance=..${SCAN_RADIUS}] run say TAG_GONE`,
          900,
        );
        return markers.has('TAG_GONE');
      },
      TRAVEL_TIMEOUT_MS,
    );
    if (gone) {
      pass('过门后：村民从原维度测试点消失（EntityPortalEvent 被插件放行）');
    } else {
      fail(
        '过门后：村民从原维度测试点消失（EntityPortalEvent 被插件放行）',
        `锚点 (${X},${Y},${Z}) 半径 ${SCAN_RADIUS} 内 ${TRAVEL_TIMEOUT_MS / 1000}s 后仍能命中 @e[type=villager]` +
          '（检查插件是否取消了实体传送门穿越 / 门是否有效）',
      );
    }

    // ---- 6. 更强的证据：村民确实到了地狱维度 ----
    step('断言村民在地狱维度');
    if (gone) {
      // 原版只缩放 X/Z，Y 原样带过去（见上方 cleanupProducts 里的同一句说明）
      const nx = Math.round(X / 8);
      const ny = Y;
      const nz = Math.round(Z / 8);
      await cmd(
        `/execute in minecraft:the_nether positioned ${nx} ${ny} ${nz} ` +
          `if entity @e[type=villager,distance=..${NETHER_SCAN_RADIUS}] run say TAG_NETHER`,
        900,
      );
      const inNether = await waitFor('村民在地狱维度标记', () => markers.has('TAG_NETHER'), NETHER_TIMEOUT_MS);
      if (inNether) {
        pass('村民确实在地狱维度（execute in the_nether + positioned 命中实体）');
      } else {
        fail(
          '村民确实在地狱维度（execute in the_nether + positioned 命中实体）',
          `地狱侧锚点 (${nx},${ny},${nz}) 半径 ${NETHER_SCAN_RADIUS} 内没有村民：` +
            '可能被直接销毁而不是穿越（检查实体传送策略/门坐标换算）',
        );
      }
    }
  } catch (e) {
    fail('探测异常', e && e.stack ? e.stack : String(e));
  } finally {
    // 清理必须放在 finally：断言失败/异常/超时都要把村民与门框收掉，否则下一次运行会脏
    step('清理本次产物（finally）');
    try {
      await cleanupProducts(true);
    } catch (e) {
      console.log(`  (清理异常: ${e && e.message ? e.message : e})`);
    }
    try {
      bot.quit();
    } catch (e) {}
  }

  console.log(`失败 ${failed} 项`);
  process.exit(failed === 0 ? 0 : 1);
})();

setTimeout(async () => {
  // 超时也必须能说清「卡在哪一步」，并尽量把产物收掉（否则留村民/门框污染下一次运行）
  console.log(`[FAIL] 总超时 ${TOTAL_TIMEOUT_MS}ms（卡在步骤：${currentStep}）`);
  try {
    if (cleanupHook.run) await cleanupHook.run();
  } catch (e) {}
  await sleep(500);
  process.exit(1);
}, TOTAL_TIMEOUT_MS);
