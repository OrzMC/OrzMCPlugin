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
./gradlew e2eLocalPaper      # Paper：完整 10 条控制台断言（PR 门禁 job，并行）
./gradlew e2eLocalFoliaLp    # Folia + 真实 LuckPerms：13 条断言（PR 门禁 job e2e-lp）
./gradlew e2eLocalFolia      # Folia：同上（本地/按需；CI 的 folia-smoke 已覆盖）
./gradlew e2eLocal -Pcore=paper   # 二选一入口（默认 folia）
./gradlew foliaSmoke         # 必须门禁：Folia 起服 + 10 条控制台断言 + D3 变体
./gradlew e2eLocalPaperLp    # Paper + LuckPerms（本地用；CI 跑 Folia 版）
```

断言内容（`build.gradle.kts` 的 `botConsoleChecks`，经 `/orzdebug` 模拟群用户命令）：

| 断言 | 覆盖的真实运行时问题 |
|:--|:--|
| `orzdebug $h` → 群指令帮助 | Brigadier 命令注册、Bot 命令解析与回执链路 |
| `orzdebug $l` → 当前在线 | 在线列表渲染 |
| `orzdebug $w` → 当前白名单玩家 | **Folia 分页调度**（BUG-E2E-001 回归护栏） |
| `orzdebug $d` → 访问规则/黑名单 | 安全模块查询链路 |
| `orzdebug $a E2EProbe` → 白名单添加 **且** 名字在列表 | 写路径：白名单落盘 + reload + 读回 |
| `orzdebug $r E2EProbe` → 白名单移除 **且** 名字消失 | 写路径还原（跑完不留脏数据） |
| `orzdebug $e say <唯一标记>` → 标记出现在日志 | 插件代执行控制台命令 |
| `config reload` → 已重新加载 | 配置子系统热加载 |
| `maintenance on` / `maintenance off` → 状态切换 | 维护模式状态机（进得去也退得出） |
| `$p` → `$p 权限管理` 用法 | **真实 LuckPerms 已就绪**（LP 缺失时这里会是「未检测到 LuckPerms」） |
| `$p u E2EProbe` → 找不到玩家 | LP 离线 UUID 解析的降级路径（不得抛异常/超时） |
| `/rank`（控制台）→ 需要玩家执行 | PlayerOnly 拦截器链 |

实现要点：每次命令前后取「日志稳定偏移」，**只匹配本次命令之后新增的日志**（避免命中历史日志造成假绿）；
日志/世界都在独立 run 目录（`run-e2e-<core>/`、`run-folia-smoke/`），互不污染。

夜间还会自动跑全量一遍（`.github/workflows/nightly.yml`：`check` + 双核心 E2E + builtin 变体），
失败自动开 issue、恢复自动关闭——作为回归发现入口。

与完整 L2 套件的分工：L1.5 覆盖「平台语义 + 命令链路」这类 MockBukkit 测不到的部分，每次 PR 跑；
真实 IM 平台（EasyBot/QQ 频控）、真实 LP 授权、多人交互仍由本目录的全量套件做迭代验收。

## 与单测/CI 的关系

| 层 | 工具 | 时机 | 门禁 |
|:--|:--|:--|:--|
| L0 单测 | JUnit5+Mockito（2011 用例，INSTRUCTION 81.9%） | 每次提交 | `./gradlew test` |
| L1 集成 | MockBukkit（16 类 / 104 用例） | 每次 PR | `./gradlew integrationTest` |
| L1.5 自举 | `e2eLocalPaper` / `foliaSmoke`（下载服务端 + stdin 断言） | 每次 PR | `./gradlew e2eLocalPaper` |
| L2 E2E | 本套件（bot+RCON） | 迭代验收 / 功能变更 | `bash e2e/run-all.sh` |

> ⚠️ 平台行为（EasyBot 网关、LP 授权、登录拦截、命令事件）MockBukkit 模拟不了，
> 必须本套件真实验收——**单测通过 ≠ 可用**（历史教训：orzdebug 前缀、$e 捕获、Folia 线程问题）。
