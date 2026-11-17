# OrzMC 插件 E2E 测试套件

真实服务器端到端测试（L2 层），配合单测（L0）+ MockBukkit（L1）构成三层测试体系。
详细策略与指标见 [docs/quality-testing-plan.md](../docs/quality-testing-plan.md)。

## 环境要求

| 项 | 要求 |
|:--|:--|
| 测试服在线 | MCSM 本机栈实例（`papermc-test`/`folia-test`，端口统一 25565）——**2026-09-03 起测试服由 MCSM 面板管理**，实例 java 在容器内宿主 ps 不可见 → 核心/目录全部环境变量显式注入（见下），仓库零本机路径假设 |
| 运行入口 | 技能侧 wrapper 注入环境（`orzmc` 技能 `scripts/e2e-mcsm-wrapper.sh [paper\|folia] [参数]`：查实例状态 + 注入 ORZMC_CORE/TEST_DIR/CONSOLE_URL/API_KEY + 调 run-all.sh）——**套件本身保持独立，不感知 MCSM 布局** |
| Node | v22+（开发机单 v24） |
| mineflayer | `cd e2e && npm install`（**仓库内** `e2e/node_modules`，固定 `mineflayer@4.37.1` + `minecraft-data@3.113.2`，版本锁 package-lock.json；不再引用仓库外目录） |
| 控制台命令 | 默认 **http 模式**（`ORZMC_RCON_MODE=http`）= MCSM 面板 console API（`ORZMC_CONSOLE_URL`/`ORZMC_API_KEY` 注入）；`=rcon` 原生协议模式保留（需 `ORZMC_RCON_PASS`，直连场景） |
| 测试服账号 | 无需预置——用例自动注册专用账号（SimpleLogin/LoginSecurity 自适应）并清理 |

## 快速开始

```bash
# 通过技能 wrapper（推荐——自动查实例状态 + 注入环境）:
#   wrapper 在 orzmc 技能 scripts/ 下；用法示例:
bash ~/.hermes/skills/gaming/orzmc/scripts/e2e-mcsm-wrapper.sh paper -c 01
bash ~/.hermes/skills/gaming/orzmc/scripts/e2e-mcsm-wrapper.sh folia -r     # Folia 全量 + 报告

# 或手动注入环境直接跑（测试服目录 = MCSM 实例 InstanceData/<uuid>）:
export ORZMC_CORE=paper
export ORZMC_TEST_DIR=/Users/Shared/orzmc/mcsmanager/daemon/data/InstanceData/<uuid>
export ORZMC_CONSOLE_URL=https://mcs.jokerhub.cn/api/protected_instance/command
export ORZMC_API_KEY=<MCSM_LOCAL_API_KEY>
bash e2e/run-all.sh -c 03        # 只跑指定用例
bash e2e/run-all.sh -r           # 全量 + Markdown 报告（e2e/reports/，不入库）

# 帮助
bash e2e/run-all.sh -h
```

## 用例清单

| 用例 | 覆盖功能 | 状态 |
|:--|:--|:--|
| `01-bot-cmds.js` | $h/$l/$w/$a/$r/$d/$e（Bot 命令全链路，写操作带还原） | ✅ 8 项 |
| `02-player-cmds.js` | /guide /menu /bot /rank /apply /config 权限隔离、新手书自动发放 | ✅ 10 项 |
| `03-security.js` | IP 黑名单登录拦截 / 聊天过滤（重复+链接）/ 命令守卫 | ✅ 10 项 |
| `04-maintenance.js` | $b 备份三阶段 + 完成耗时 + 文件落盘 + 服务恢复 | ✅ 4 项 |
| `05-groupmsg.js` | 群消息发送（白名单拦截/上下线/聚合/IP 黑名单拦截，日志断言） | ✅ 11 项（2026-08-19 加入，PR #201） |
| `06-permission-msg.js` | 权限/审核消息（申请发起/通过/晋升/拒绝/撤回，LP+op 自建） | ✅ 19 项（2026-08-19 加入，PR #202） |

> 2026-08-20 双核心验收（OrzMC 1.0.19-dev）：**Paper 62/62 + Folia 62/62 全绿**。
> ⚠️ **备份时序**：04 触发 $b 后服务器进入维护模式（踢人+拒登），05/06 必须在备份完成后运行，
> 否则 bot 登录被拒 → 用例零输出 exit 0（汇总假绿，勿误判）。全套跑完若 05/06 无输出，
> 等备份完成（日志「地图备份 完成」+ zip 落盘）后补跑 `-c 05 -c 06`。

## 已知 Bug 检测

套件首个版本即检测到 **BUG-E2E-001**：`$w` 白名单分页在 Folia 上抛
`Delay ticks may not be <= 0`（Paginator i=0 delay=0，Folia runDelayed 要求 ≥1）。
用例 `01-bot-cmds.js` 中 `$w` 标记为 `FAIL-KNOWN`——**修复前套件持续红**，修复后自动转绿，
形成回归护栏。详见 [buglog.md](buglog.md)。

## 新增用例规范

1. 文件放 `cases/`，命名 `NN-<主题>.js`（NN 为两位序号，顺序执行）
2. 自包含：前置条件自检 + 专用账号自动注册 + 写操作「测前记录原值→测→立即还原」
3. 输出 `[PASS]/[FAIL] 用例名 — 断言内容`，汇总行 `通过 X/Y`
4. 退出码：0=全过，1=有失败（run-all.sh 聚合判定）
5. 复用 `lib/rcon.js`（Promise RCON + waitLog）与 `lib/bot.js`（spawnBot 自适应登录 + waitMessage）

## CI 自举层（L1.5，无需常驻测试服）

真实服务端本身也能在仓库内自举——不依赖 MCSM 面板/console API，`gradle` 一条命令完成
「下载服务端 → 隔离 run 目录 → 放入插件 → 起服 → 控制台断言 → stop → 断言 exit 0」：

```bash
./gradlew e2eLocalPaper      # Paper：完整 13 条控制台断言（PR 门禁 job，并行）
./gradlew e2eLocalFoliaLp    # Folia + 真实 LuckPerms：16 条断言（PR 门禁 job e2e-lp）
./gradlew e2eLocalIm         # builtin QQ + 仓库内假平台：入站群消息 → 命令 → 出站回复（PR job e2e-im）
./gradlew e2eLocalPlayer     # 玩家侧：mineflayer 客户端加入 → /menu GUI → /guide 开书 → 非管理员 /config 被拒
./gradlew e2eLocalPerm       # 权限三档矩阵：default/member/builder + op 对照 → 逐档断言放行/拒绝（PR job e2e-perm）
./gradlew e2eLocalPortal     # 村民穿越下界传送门：建门 → 召唤 → 断言过门到地狱维度（PR job e2e-portal）
./gradlew e2eLocalFolia      # Folia：同上（本地/按需；CI 的 folia-smoke 已覆盖）
./gradlew e2eLocal -Pcore=paper   # 二选一入口（默认 folia）
./gradlew foliaSmoke         # 必须门禁：Folia 起服 + 13 条控制台断言 + D3 变体
./gradlew e2eLocalPaperLp    # Paper + LuckPerms（本地用；CI 跑 Folia 版）
```

**服务端版本策略（2026-11 决策）**：Paper 与 Folia **永远跟随同一个 `plugin_debug_server_version`**，
不拆独立版本属性。上游节奏不同时（如 26.3：Paper 为 STABLE、Folia 仅 ALPHA），Folia 冒烟就跟着跑 alpha——
这是有意的：alpha 阶段跑冒烟才能在插件兼容问题上**最早**发现，而钉旧版只会把问题推迟到用户升级时。
若某天 alpha 挂了且上游迟迟不发 stable，临时手段是把 `plugin_debug_server_version` 整体回退一格
（Paper 也一起退，保持两平台一致），而不是引入第二个版本变量。

断言内容（`build.gradle.kts` 的 `botConsoleChecks`，经 `/orzdebug` 模拟群用户命令）：

| 断言 | 覆盖的真实运行时问题 |
|:--|:--|
| `orzdebug $h` → 群指令帮助 | Brigadier 命令注册、Bot 命令解析与回执链路 |
| `orzdebug $l` → 当前在线 | 在线列表渲染 |
| `orzdebug $w` → 当前白名单玩家 | **Folia 分页调度**（BUG-E2E-001 回归护栏） |
| `orzdebug $d` → 访问规则/黑名单 | 安全模块查询链路 |
| `orzdebug $v l` → 待审核申请 | 审核模块查询链路 |
| `orzdebug $a E2EProbe` → 白名单添加 **且** 名字在列表 | 写路径：白名单落盘 + reload + 读回 |
| `orzdebug $r E2EProbe` → 白名单移除 **且** 名字消失 | 写路径还原（跑完不留脏数据） |
| `orzdebug $e say <唯一标记>` → 标记出现在日志 | 插件代执行控制台命令 |
| `orzdebug $b` → 地图备份 完成 **且** 备份 zip 落盘（>10KB） | 真实备份链路（备份库打包 + 维护模式进出 + 保留策略） |
| `orzdebug $o` → 地图优化 完成 | 真实优化链路（另有落盘断言，防"日志说完成但没文件"） |
| `config reload` → 已重新加载 | 配置子系统热加载 |
| `maintenance on` / `maintenance off` → 状态切换 | 维护模式状态机（进得去也退得出） |
| `$p` → `$p 权限管理` 用法 | **真实 LuckPerms 已就绪**（LP 缺失时这里会是「未检测到 LuckPerms」） |
| `$p u E2EProbe` → 找不到玩家 | LP 离线 UUID 解析的降级路径（不得抛异常/超时） |
| `/rank`（控制台）→ 需要玩家执行 | PlayerOnly 拦截器链 |

**IM 全链路（`e2eLocalIm`）**：仓库内 `scripts/e2e/fake_qq_platform.py` 起一个假 QQ 开放平台
（纯标准库：HTTP `/app/getAppAccessToken`、`/gateway/bot`、`/v2/groups/{id}/messages` + 同端口 WS
hello→identify→READY→心跳，以及控制面 `/control/message` 注入入站事件）。断言链：
`im.yml` backend=builtin + `api_base` 指向假平台 → 等「网关 READY」→ 注入 `$h` → 假平台 `/control/sent`
必须出现 `/v2/groups/G1/messages` 且内容含「群指令帮助」。**这是此前只能靠真实 QQ 群人工验收的那一层。**
限制：真实平台的频控/被动回复窗口时长不可仿造，仍属人工验收范围。

**权限三档矩阵（`e2eLocalPerm`）**：真实 Paper + 真实 LuckPerms。`e2e/ci/perm-tiers-probe.js` 分两个阶段跑：
① `MC_PERM_PHASE=register` 让四个账号各登录一次（LuckPerms 只能按名字解析**登录过**的玩家，
对陌生名字执行 `lp user X parent set ...` 只会回 `[LP] A user for X could not be found.`）；
② Gradle 下发 `lp creategroup / parent set / permission set`（等 N 条 `[LP]` **结果**落地——排队提示行
「目前有另一個指令正在執行」不算；且行数到齐后还要断言「没有报错行」，只豁免 `already exists` 幂等提示）；
③ `MC_PERM_PHASE=assert` 逐档断言：

| 档位 | 断言 |
|:--|:--|
| default | `/rank` 显示 `default` 组；`/apply` 无可申请项；`/portal`、`/orzdebug` 不可见（原版「Unknown or incomplete command」） |
| member | `/rank` 显示 `member`；`/apply` 列出 `/apply builder`；管理命令仍不可见（LP 继承不越权） |
| builder | `/rank` 显示 `builder`；`/apply` 列出 `/apply admin`；挂 `orzmc.admin` 后 `/portal` 放行 |
| op（对照） | `/orzdebug` 受理、`/portal` 放行（证明拒绝来自权限判定，而不是命令本身坏了） |

> 拒绝文案是**实测抓的**：OrzMC 的 admin-only 命令在 Brigadier `requires()` 阶段就被过滤，
> 非管理员看到的只能是原版「Unknown or incomplete command」；自定义「需要管理员权限」只在
> 「命令可见但执行时被拦」时出现，目前没有玩家可达的那种命令。
> `/bot http` 四档只做「**未被拒绝**」断言——错误回显同样含 `HTTP`，不当作放行证据
> （放行证据是 `/rank` 组名、`/apply` 列表、`/portal` 用法回显、`/orzdebug` 受理）。
> 客户端版本固定 **1.20.4**：1.21.x 客户端经 ViaBackwards 连 26.3 会被判
> `Invalid move player packet received` 踢人（详见探针文件头）。
> 超时：探针按「各步上限之和 × 1.5 + 30s」自算（register ≈225s、assert ≈390s），Gradle 侧再宽一截（260s / 460s），
> 慢 runner 上先由探针自己超时并报出「卡在哪一步」；全部清理放在 `finally`。

**村民过传送门（`e2eLocalPortal`）**：`e2e/ci/entity-portal-probe.js` 以 op 客户端用**原生命令**（不靠 `/orzdebug $e`）建
标准 4×5 下界门（内 2×3）→ **先召唤村民、后点亮内层**（门没亮时村民不会走，让「summon 成功」可被确定断言）
→ 等村民离开原维度 → 再断言它确实出现在地狱维度。断言项：
门框结构合法（4 角 + 4 条边黑曜石）/ 内层 6 格 `nether_portal`（门真的亮了）/ 过门前村民在场 /
过门后原维度消失 / 村民在地狱维度 / 测试产物已清理。
实体判定一律**锚定到门坐标**（`execute positioned <X> <Y> <Z> ...`），不依赖世界出生点；
超时预算同上方公式（≈264s，Gradle 侧 300s），清理走 `finally`（断言失败/超时也执行）。

> 这里**没有**「日志里不得出现『实体传送被禁用』」的文本守卫：实体名来自 `getName()`/`getType().name()`，
> 裸串/收窄串都可能恒绿（`Villager`/`VILLAGER`/`minecraft:villager`），扫 offset 0 又会被上一轮日志污染
> （2026-11-16 CI 曾因同句里无关实体 `Crimson Roots` 误红）。「插件没拦」由**行为断言**覆盖：
> 若被 `EntityPortalEvent`/`OrzTPEvent` 拦下，村民根本过不去，探针会直接失败。

**玩家侧（`e2eLocalPlayer`）**：`e2e/ci/player-probe.js` 用真实 mineflayer 客户端连本机 Paper，双玩家断言
（管理员：加入 / `/menu` 箱子 GUI / `/guide` 打开新手书；非管理员：`/config` 被拒 = 权限隔离）。
协议兼容（2026-11 实测）：mineflayer 最高协议 1.21.11 ↔ 服务端 26.3，**只装 ViaVersion 或只用协议补丁都会被拒
「Outdated client」；ViaVersion + ViaBackwards 组合可正常登录**，两个插件由 gradle 任务自动下载。

每次自举 E2E 还会落一份性能快照（`build/quality/perf-<label>.json`：启服耗时 + `$b` 备份耗时），
由夜间趋势看板汇总成可 diff 的两列（启动/备份），劣化超阈值标 ⚠️。

实现要点：每次命令前后取「日志稳定偏移」，**只匹配本次命令之后新增的日志**（避免命中历史日志造成假绿）；
日志/世界都在独立 run 目录（`run-e2e-<core>/`、`run-folia-smoke/`），互不污染。

夜间还会自动跑全量一遍（`.github/workflows/nightly.yml`：`check` + 双核心 E2E + builtin 变体），
失败自动开 issue、恢复自动关闭——作为回归发现入口。

**命令面完整性由单测强制**：`E2eCommandCoverageTest` 解析 `build.gradle.kts` 的断言表并与
`OrzUserCmd` 枚举比对——新增 Bot 命令若没补断言（也没在 `NOT_COVERED` 登记原因）会让 `./gradlew test` 直接红。
**当前 11/11 全覆盖**（`NOT_COVERED` 为空）：`$b`/`$o` 已在隔离 run 目录的小世界上跑，并额外断言 zip 落盘。

与完整 L2 套件的分工：L1.5 覆盖「平台语义 + 命令链路」这类 MockBukkit 测不到的部分，每次 PR 跑；
真实 IM 平台（EasyBot/QQ 频控）、真实 LP 授权、多人交互仍由本目录的全量套件做迭代验收。

## 与单测/CI 的关系

| 层 | 工具 | 时机 | 门禁 |
|:--|:--|:--|:--|
| L0 单测 | JUnit5+Mockito（2011 用例，INSTRUCTION 81.9%） | 每次提交 | `./gradlew test` |
| L1 集成 | MockBukkit（16 类 / 104 用例） | 每次 PR | `./gradlew integrationTest` |
| L1.5 自举 | `e2eLocalPaper` / `e2eLocalFoliaLp` / `e2eLocalIm` / `e2eLocalPlayer` / `e2eLocalPerm` / `e2eLocalPortal`（服务端 + stdin/假平台/客户端断言） | 每次 PR | `./gradlew e2eLocalPaper` |
| L2 E2E | 本套件（bot+RCON） | 迭代验收 / 功能变更 | `bash e2e/run-all.sh` |

> ⚠️ 平台行为（EasyBot 网关、LP 授权、登录拦截、命令事件）MockBukkit 模拟不了，
> 必须本套件真实验收——**单测通过 ≠ 可用**（历史教训：orzdebug 前缀、$e 捕获、Folia 线程问题）。
