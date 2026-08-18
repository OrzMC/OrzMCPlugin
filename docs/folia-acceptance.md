# OrzMC Folia 适配验收清单

> 本文档收录 OrzMC 插件在 **Folia 运行时**下需要完成的功能验收项。
> 目标：确认插件在 Folia（regionized 多线程）上功能与 Paper 等价、无 region 线程错误。
> 与 [test-cases.md](test-cases.md)（Paper E2E）互补——Folia 的调度/区域亲和差异只能靠真实 Folia 服务器验证。
>
> - 适配与测试策略详见 [folia-migration.md](folia-migration.md)（D3 区域亲和 / D6 测试策略 / D7 并发）
> - 结论标记：✅ 已验收（附证据）/ ⬜ 待真实环境 / ⚠️ 工具限制（非插件缺陷）/ ❌ 失败（含修复记录）
> - **失败判据**：日志出现 `IllegalThreadStateException`、`not the correct region`、死锁（卡 tick）任一即失败

---

## 一、已完成验收（无头/自动化）

以下项不需要真实玩家/Bot，已在开发机与 CI 上验证通过。

| # | 验收项 | 结果 | 证据 |
|:--|:--|:--|:--|
| FA-01 | Folia 服务端启动加载 OrzMC，无插件崩溃 | ✅ | CI `folia-smoke` job（PR #191 run 32052367629、合并后 push run 32052509225 均 SUCCESS）；PR-5 本地 `foliaSmoke` 通过（Folia 26.2 约 7s 启动） |
| FA-02 | 插件在 Folia 上干净退出（`stop` → exit 0） | ✅ | 同上；`foliaSmoke` 断言 90s 内 exit 0 |
| FA-03 | `./gradlew runFolia` 本地调试服务器可用 | ✅ | PR-5 验证：11s 启动、OrzMC 加载、`stop` 正常卸载插件并保存世界 |
| FA-04 | 调度器门面（global region / async）在 Folia 启动路径无异常 | ✅ | FA-01/02 覆盖；单测 `ServerFacadeTest`（PR-1 重写） |
| FA-05 | 区域亲和投递正确性（单元级） | ✅ | PR-2/3 新增：`RegionSchedulerProvider.run(world,cx,cz,task)` 坐标断言、`EntityScheduler` kick 投递、`teleportAsync` 验证 |
| FA-06 | 并发安全（单元级） | ✅ | PR-4：64 线程爆炸聚合 ×64 精确、50 线程入队单批精确、tick×回调并发无 `ConcurrentModificationException` |
| FA-07 | Paper 侧无回归 | ✅ | 主 `check` 流水线（spotless + test + integrationTest + jacoco + shadowJar）全绿（PR #186-#191） |

## 二、待真实环境验收（⬜）

以下项需要真实 Folia 服务器 + EasyBot 网关 + 玩家（推荐复用 test-cases.md 的 mineflayer 机器人 + 真实玩家方案，在 Folia 测试服执行）。每项执行时开启调试日志并在结束后检索服务器日志，确认无失败判据命中。

### TC-F1 白名单管理（`$w` / `$a` / `$r`）

| 项 | 内容 |
|:--|:--|
| 前置条件 | Folia 测试服 + EasyBot 接入 + 管理员群 |
| 步骤 | ① 群内 `$w` 查白名单；② `$a <玩家>` 添加；③ 非白名单玩家进服触发踢出；④ `$r <玩家>` 移除 |
| 预期 | 查询/添加正常回传；非白名单玩家被踢出并收到提示（kick 走 EntityScheduler region 线程，无错） |
| 实际 | ✅ **2026-08-18 真实环境通过**：① `$w` 返回白名单列表（abinkabi/pa_pa_yuan/yuhaomax 等）；② `$a HermesTest01` → `✔︎ HermesTest01` + whitelist.json 121 条落盘；③ mineflayer `NotWhitelisted01` 进服 → 在 `Folia Region Scheduler Thread #0` 被踢出（`Disconnecting ... 不在服务器白名单中` + 中文提示含 QQ 群），**无 region 线程错误**；④ `$r HermesTest01` → whitelist.json 移除（grep 0）。命令审计 audit/command_audit.log 全部记录 |
| 方式 | orzdebug（控制台 stdin）+ mineflayer |

### TC-F2 备份/优化（`$b` / `$o`）

| 项 | 内容 |
|:--|:--|
| 前置条件 | 同上 |
| 步骤 | ① 群内 `$b` 触发一键备份；② `$o` 触发地图优化；③ 观察进度消息到完成 |
| 预期 | 备份/优化完整执行，进度实时回传，完成无异常（调度走 async + global region） |
| 实际 | 🟡 **$o 部分通过 / $b 环境限制（2026-08-18 真实环境）**：`$o` 触发链路完整验证——`/config set maintenance.optimize_enabled true`（注册路径 ✓）→ orzdebug `$o` → `正在优化地图，请稍等......` + save-off/save-all/save-on 切换（runExclusive 流程）→ MCA 优化器真实写回 region 文件（117+ 个，含 8.7MB 大文件）→ **0 region 线程异常**；优化期间真实玩家（joker）在线正常游戏。⚠️ 17G 世界（21689 region）完整跑完需数小时，验收中途因部署调试版重启中断 → **转入 TC-F6 长稳窗口重新触发直至完成**。**$b 备份：环境限制无法安全执行**——备份 zip 整个 worldContainer（17G symlink 世界）需 >17G 磁盘空间，本机仅 7.9G 可用，强行执行会写满磁盘导致数据损坏风险（非插件缺陷） |
| 方式 | orzdebug + RCON |

### TC-F3 TNT 保护与爆炸通知

| 项 | 内容 |
|:--|:--|
| 前置条件 | TNT 保护启用 + 玩家在线 |
| 步骤 | ① 白名单区域内放 TNT 并引爆；② 白名单区域外放 TNT；③ 发射器连环爆炸 |
| 预期 | 区域外被拦截；爆炸通知聚合为 ×N 单条告警（`TntEventService.pendingAlerts` 并发安全），无重复调度 |
| 实际 | ✅ **2026-08-18 真实环境通过**：⚠️ 配置语义实测：`tnt.enable: false` 才是**严格防护模式**（区域外 TNTPrime 点燃/放置/发射取消 + 告警），`true` 为宽容模式（仅通知不拦截）。区域外红石激活 TNT → `[TNT警报] TNT被点燃（已禁止）`（TNTPrime 拦截 ✅）；3s 窗口内 6 次触发聚合为 **`×6` 单条告警**（聚合 ✅）；白名单区域（临时加 1000-1010,60-70,995-1005）→ `[TNT警报] TNT被点燃`（放行）+ `[爆炸警报] TNT爆炸`（EntityExplode 通知 ✅）；发射器拦截（PreDispense）因 RCON 下 `item replace/insert` 命令语法注入受限未实弹——源码 `onBlockPreDispense` 逻辑与 TNTPrime 一致（L123-124），工具限制非缺陷。测试后配置已恢复原状 |
| 方式 | RCON setblock + 红石激活（mineflayer placeBlock 水下失败为工具限制） |

### TC-F4 传送弓（`/tpbow`）

| 项 | 内容 |
|:--|:--|
| 前置条件 | 玩家在线 + 权限 `orzmc.tpbow.use` |
| 步骤 | ① 执行 `/tpbow` 射箭；② 远距离射击（触发 force-load 区块）；③ 落点非安全位置时自动就近找安全点 |
| 预期 | 传送至落点；`ForceLoadedChunkLease` 经 region scheduler 获取/释放，无「not the correct region」；实体策略按配置 |
| 实际 | ✅→❌→✅ **2026-08-18 真实环境发现并修复 bug**：mineflayer 登录（AuthMe）→ `/tpbow` 获得传送弓 → 射箭 → `[传送弓] 传送完成!` 位置变化（1000.5→999.5,1003.5）✅。但日志命中 `IllegalStateException: Cannot read force-loaded chunk off global region`（连续 3 区块 62,62/63/64）——**`ForceLoadedChunkLease.acquire` 经 region scheduler 投递 chunk region 线程后调 `chunk.isForceLoaded()`，而 Folia 中 force-load 状态由 GlobalRegion 持有（反编译 `folia-26.2.jar` CraftWorld：`ensureGlobalTickThread`）→ 线程越权**。**修复**（分支 `fix/folia-force-load-global-region` e5301dd）：新增 `GlobalSchedulerProvider` 端口，计数 + `isChunkForceLoaded`/`setChunkForceLoaded` 读写全走 global region 线程（天然串行），仅 `unloadChunk` 投递所属 region。单测 12 用例全绿；**修复版部署后同场景复测 0 异常** ✅。⚠️ 远距离（未加载区块）射击因 mineflayer 射箭弹道不可控（箭飞 ≤13 格）未触发「未加载区块 force-load」分支——已加载区块 acquire 路径（修复前必现异常的场景）已闭环验证 |
| 方式 | mineflayer（AuthMe 注册/登录）+ RCON |

### TC-F5 跨服传送门（`/portal`）

| 项 | 内容 |
|:--|:--|
| 前置条件 | 双服 Folia（或 Folia + Paper）配置传送门 |
| 步骤 | ① 管理员创建传送门；② 玩家踩踏传送门触发跨服 transfer；③ 删除传送门 |
| 预期 | 创建/删除经 region scheduler 在 anchor chunk 投递方块操作，无跨界异常；玩家 transfer 正常；`portal.yml` 运行时读写正确 |
| 实际 | 🟢 **创建/删除 ✅ / transfer ✅（2026-08-18 真实环境，补偿方案本 PR）**：① bot（临时 OP）`/portal 127.0.0.1 25566` → 传送门真实生成（4x5 框架 + NETHER_PORTAL 方块）→ portals.yml 落盘 ✓ 0 region 异常；③ `/portal remove` → portals.yml 清空 ✓ 0 region 异常。② 初测发现 **Folia 26.2-4 核心限制**：PlayerPortalEvent 不触发（[PortalDebug] 日志实证 0 输出 + 反编译 folia-26.2.jar：`callPlayerPortalEvent` 无任何调用者——下界传送门走 `portalAsync` 新路径绕过 Bukkit 事件），transfer 完全失效。**补偿方案**：`PortalEventService.handleMove` 监听 PlayerMoveEvent——玩家方块坐标变化（真正走进传送门）时查 interiorTargets 命中 → 认证通过 → transfer 命令；仅 Folia 运行时生效（RegionizedServer 检测），Paper 保持 PlayerPortalEvent 原路径；5s 冷却防双路径。**验证：bot 走进下界传送门 → 服务器日志 `Transferring OrzTestBot01 to 127.0.0.1:25566`**（此前该路径从未触发）。局限：mineflayer 不支持 transfer 协议重连，Paper 侧连接需真实客户端（服务器端命令执行已为决定性证据）。**已知行为差异（评审 G1，接受）**：PlayerMoveEvent 无法取消原版下界传送——transfer 成功时抢先带走玩家；若目标不可达/transfer 失败，玩家会被原版传送到下界（Paper 路径为取消传送，语义不同）。触发时机为玩家**脚底或躯干格（脚+1）水平精确命中传送门内部格**（无水平邻域容差，评审 G-A/G-1 收敛：贴着门水平 1 格路过不误触发；垂直按身体两格匹配，地面传送门内部格从脚底+1 起），早于 Paper 的传送门激活 ~4s，快速穿过/被推动经过也会触发。**Paper 侧变化（评审 G-B，接受）**：冷却（5s）内再次激活传送门不再被本插件取消/接管（放行原版传送）——冷却前置保证「取消 ⇔ transfer」自洽，避免「取消但未传送」的卡门状态；transfer 命令执行失败会输出 WARNING 日志（此前静默） |
| 方式 | 管理员 + 真实玩家 |

### TC-F6 长稳运行（8h+ 无死锁）

| 项 | 内容 |
|:--|:--|
| 前置条件 | Folia 测试服持续运行 |
| 步骤 | 持续运行 ≥8 小时，期间混合执行 TC-F1~F5 各 ≥2 次 |
| 预期 | 无死锁（tick 持续推进）、无 region 线程异常、内存无异常增长 |
| 实际 | ⬜ |
| 方式 | 自动脚本 + 日志检索 |

## 三、验收结论

- 全部 ⬜ 项完成且无失败判据命中 → 视为 Folia 适配验收通过，可发正式版（Folia loader）。
- 任一 ❌ → 记录日志尾部、修复后走 PR 重新验证。

> 备注：Folia 下 `runFolia` 的 `run-folia/` 与 `run-folia-smoke/` 均为隔离运行目录，不影响真实配置（[`.gitignore`](../../.gitignore) 已忽略）。
