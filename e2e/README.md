# OrzMC 插件 E2E 测试套件

真实服务器端到端测试（L2 层），配合单测（L0）+ MockBukkit（L1）构成三层测试体系。
详细策略与指标见 [docs/quality-testing-plan.md](../docs/quality-testing-plan.md)。

## 环境要求

| 项 | 要求 |
|:--|:--|
| Folia 测试服 | `~/folia-test/` 在线（端口 25565，RCON 25575/orztest2026） |
| Node | 单 v24（`~/.n/bin/node`） |
| mineflayer | `~/minecraft-bot/node_modules`（run-all.sh 自动设置 NODE_PATH） |
| 测试服账号 | 无需预置——用例自动注册专用账号（SimpleLogin）并清理 |

## 快速开始

```bash
# 全量跑（01-04 用例，约 2-3 分钟）
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
| `04-maintenance.js` | $b 备份三阶段 + 完成耗时 + 文件落盘 + 服务恢复 | ✅ 5 项 |
| `05-portal.js`（规划） | /portal 创建/移除（bot 部分链路） | ⬜ |
| `06-tpbow.js`（规划） | /tpbow 获取 + 权限边界 | ⬜ |
| `07-review.js`（规划） | /apply→$v 审核→LP 授权闭环 | ⬜ |

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
