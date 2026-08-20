# OrzMC 插件 E2E 测试套件

真实服务器端到端测试（L2 层），配合单测（L0）+ MockBukkit（L1）构成三层测试体系。
详细策略与指标见 [docs/quality-testing-plan.md](../docs/quality-testing-plan.md)。

## 环境要求

| 项 | 要求 |
|:--|:--|
| 测试服在线 | `~/papermc-test`（Paper）或 `~/folia-test`（Folia），端口统一 25565，RCON 25575/orztest2026 |
| 核心自动检测 | run-all.sh 进程检测（`folia-test/folia.*jar` / `papermc-test/paper.*jar`），`ORZMC_CORE=folia|paper` 可显式覆盖（端口统一后无法靠端口区分核心） |
| Node | 单 v24（`~/.n/bin/node`） |
| mineflayer | `~/minecraft-bot/node_modules`（run-all.sh 自动设置 NODE_PATH） |
| 测试服账号 | 无需预置——用例自动注册专用账号（SimpleLogin/LoginSecurity 自适应）并清理 |

## 快速开始

```bash
# 全量跑（01-06 用例，约 15-25 分钟）
bash e2e/run-all.sh

# 只跑指定用例
bash e2e/run-all.sh -c 01 -c 03

# 生成 Markdown 报告
bash e2e/run-all.sh -r        # → reports/e2e-report-YYYYMMDD-HHMMSS.md

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

## 与单测/CI 的关系

| 层 | 工具 | 时机 | 门禁 |
|:--|:--|:--|:--|
| L0 单测 | JUnit5+Mockito（1283 用例，覆盖率 82.2%） | 每次提交 | `./gradlew test` |
| L1 集成 | MockBukkit（14 类） | 每次 PR | `./gradlew integrationTest` |
| L2 E2E | 本套件（bot+RCON） | 迭代验收 / 功能变更 | `bash e2e/run-all.sh` |

> ⚠️ 平台行为（EasyBot 网关、LP 授权、登录拦截、命令事件）MockBukkit 模拟不了，
> 必须本套件真实验收——**单测通过 ≠ 可用**（历史教训：orzdebug 前缀、$e 捕获、Folia 线程问题）。
