# OrzMC 插件测试计划与质量体系

> **版本**：v1.0（2026-08-19）
> **适用范围**：OrzMCPlugin 全部功能（Paper 26.x + Folia 双运行时）
> **基线**：main@f79e165（#197 群消息样式统一），OrzMC 1.0.18-dev.302
> **配套**：[功能测试用例（E2E）](test-cases.md) · [Folia 适配验收清单](folia-acceptance.md)

---

## 一、功能点全图（代码核对版）

> 与 `features.md` 的差异：本节为 2026-08-19 从源码 `features/` 目录逐模块核对的最新清单，
> 补齐了 features.md 未收录的**安全加固四大模块**（guard/chat/login_rate_limit/exploit_hardening）、
> 命令审计、定时备份、上下线通知聚合等新功能。

### 1.1 领域模块总览（16 个）

| 领域 | 核心服务 | 功能点 | 测试层 |
|:--|:--|:--|:--|
| **whitelist** | WhitelistService / WhitelistEventService | 强制白名单（force_whitelist）；踢出提示（QQ 群号/多平台 UP 主联系方式）；`$a/$r/$w` Bot 管理（批量、分页、在线状态）；不活跃 90 天清理（$w 触发） | 单测+集成+E2E |
| **botcommands** | BotCommandService / BotCommandFeedbackService | 11 个 Bot 命令（$l/$w/$h/$a/$r/$b/$o/$e/$d/$v/$p）；`$cmd ?` 三段式帮助；前缀匹配防误触；U+3000 全角空格归一化；fallback 收敛；$e 输出捕获（Log4J Appender + 水位窗口 + 30 行截断） | 单测+E2E |
| **bot** | BotStatusService / OrzEasyBot | EasyBot 网关多平台接入（QQ/飞书/Telegram/Discord/微信）；PUBLIC/PRIVATE 路由；`/bot` 健康状态；WS 自动重连；命令前缀可配置 | 单测+E2E |
| **portal** | PortalCommandService / PortalEventService | `/portal <host> [port]` 建门（4×5 黑曜石框架+文字标签）；`/portal remove`；跨服 transfer；未登录禁传送（LoginSecurity 集成）；portals.yml 持久化；Folia PlayerMoveEvent 补偿路径（#195） | 单测+集成+E2E(部分) |
| **tnt** | TntEventService / TntPolicy | TNT 放置拦截（4 拦截点）；区域白名单；5s 放置冷却；重生锚控制；爆炸通知聚合（128×128×64 + 3s 窗口 + ×N）；exempt_entities 豁免 | 单测+集成 |
| **security** | 11 个服务（见 1.2） | GeoIP 国家限制；IP 黑名单（精确/CIDR/通配符）；危险命令拦截；命令审计；聊天反垃圾；进服限流；漏洞加固；登录验证 | 单测+集成+E2E |
| **teleport** | TeleportBowService / TeleportBowEventService / EntityTeleportPolicyService | `/tpbow` 传送弓（无限附魔）；飞行路径 force-load（提前 24 格）；落点安全检查+最近安全点搜索；猫咕噜声；实体传送策略（白名单 TAMEABLE/ENDERMAN/ARMOR_STAND/SHULKER） | 单测+集成+E2E |
| **maintenance** | WorldMaintenanceService / ScheduledBackupService | `$b` 备份（踢人→save-off→ZIP→save-on→保留 N 份）；`$o` 优化（tick 阈值过滤）；三阶段进度报告；维护 MOTD；定时自动备份（backup_interval_hours）；完成耗时中文可读化（duration_human） | 单测+E2E |
| **player** | PlayerEventService / PlayerEventAggregator | 上下线/踢出通知（世界别名/坐标/权限组/在线列表）；3s 聚合窗口+摘要；max_list_items 截断；限流 | 单测+集成+E2E |
| **guide** | GuideService | `/guide` 新手书；首次进入自动发放；YAML 配置（链接/悬停/样式/分页） | 单测+E2E |
| **menu** | MenuService / MenuCommandService / MenuEventService | `/menu` 箱子 GUI；占位「功能开发中」 | 单测+E2E |
| **rank** | RankService / LuckPermsPromoter / PermissionStore | 4 级权限链（default→member→builder→admin）；LP track 自动初始化/校正；自动晋升（member-threshold-hours）；`$p u/d` 手动升降级；`/rank` 进度展示；升级通知 | 单测+集成 |
| **review** | ReviewService / ReviewCommandService / ReviewHandler | `/apply` 申请（资格预检）；`/review approve/reject`；`$v l/y/n` 群审核；LP 异步授权+状态一致性；申请历史 10 条裁剪；通知双方 | 单测+集成+E2E |
| **server** | ServerLifecycleService / ExceptionAlertService / StartupSecurityAuditService | 启动/停止通知；异常告警（PRIVATE）；启动安全审计 | 单测 |
| **chat** | ChatSpamFilterService | 聊天限流（6 条/分钟）；链接检测；重复消息检测 | 单测+集成 |
| **command** | CommandFeedbackService + 拦截器链 | PlayerOnly/AdminOnly/Cooldown 三拦截器；命令反馈；/config 子命令 | 单测 |

### 1.2 安全加固四大模块（features.md 未收录，2026-08 新增）

| 模块 | 配置节 | 拦截点 | 行为 | 测试状态 |
|:--|:--|:--|:--|:--|
| 危险命令拦截 | `guard` | ServerCommandEvent | deny-list（op/deop/publish/seed/reload/plugman/stop）命中拦截+私信管理员+审计日志 | ✅ 单测覆盖（CommandGuardServiceTest 等 4 类） |
| 聊天反垃圾 | `chat` | AsyncChatEvent | 限流 6 条/分、链接检测、重复检测，命中取消消息+提示 | ✅ 单测覆盖（ChatSpamFilter 2 类） |
| 进服限流 | `login_rate_limit` | AsyncPlayerPreLoginEvent | 每 IP 5 次/分登录尝试；同 IP 并发上限 3；私信管理员 | ✅ 单测覆盖（LoginRateLimit 2 类） |
| 漏洞加固 | `exploit_hardening` | 书页/物品属性/实体生成 | 书 100 页上限；物品 6 属性修饰符上限；单区块 128 实体上限 | ✅ 单测覆盖（ExploitHardening 2 类） |

### 1.3 命令清单（11 Bot 命令 + 9 游戏命令 + /config）

Bot 命令：`$l` 在线 / `$w` 白名单 / `$h` 帮助 / `$a` 加白 / `$r` 移白 / `$b` 备份 / `$o` 优化 / `$e` 控制台 / `$d` 黑名单 / `$v` 审核 / `$p` 升降级

游戏命令：`/tpbow` `/guide` `/menu` `/bot` `/portal` `/blacklist` `/config` `/rank` `/apply` `/review` `/orzdebug`

### 1.4 配置面（6 配置文件 + 25+ 运行时配置项）

config.yml（白名单/维护/TNT/GeoIP/实体传送/命令策略/安全加固四模块）· easybot.yml · templates.yml（50+ 模板）· permission.yml · portals.yml · ip_blacklist.yml · guide_book.yml

---

## 二、测试分层策略（金字塔）

> 用户规范：「哪些逻辑可以用单元测试和集成测试保证的，优先使用单元测试和集成测试；
> 其他情况必须要真实验收的，再使用机器人做端到端真实测试。」

```
        ┌─────────┐
        │ L3 跨服 │  双服 transfer / 压测（真实玩家+bot 部分链路）
        │ E2E    │
        ├─────────┤
        │ L2 真实 │  测试服 bot + RCON + orzdebug 全链路验收
        │ E2E    │  （平台行为：EasyBot 网关、LP 授权、命令事件）
        ├─────────┤
        │ L1 Mock │  MockBukkit 集成测试（事件注册、命令分发、模块装配）
        │ Bukkit  │
        ├─────────┤
        │ L0 单测 │  JUnit5+Mockito（业务状态机、解析、持久化）
        └─────────┘
```

| 层 | 工具 | 覆盖内容 | 执行时机 | 门禁 |
|:--|:--|:--|:--|:--|
| L0 | JUnit 5 + Mockito | 业务逻辑、命令解析、配置解析、模板渲染、限流算法 | 每次提交/PR | `./gradlew test` 全绿 |
| L1 | MockBukkit | 事件监听绑定、Brigadier 命令注册、模块装配 | 每次 PR | `./gradlew integrationTest` 全绿 |
| L2 | mineflayer bot + RCON + screen | EasyBot 链路、LP 真实授权、通知送达、登录拦截、GUI 交互 | 迭代验收（每次功能变更） | `e2e/run-all.sh` 全绿 |
| L3 | 真实玩家 + 双服 + stress 脚本 | transfer 闭环、性能压测、基岩连通 | 大版本/跨服变更 | 专项验收报告 |

---

## 三、测试用例矩阵（功能点 × 测试）

> 状态图例：✅ 已有 · 🟡 部分（工具限制）· ⬜ 缺失待补

### 3.1 L0 单测覆盖矩阵（按领域）

| 领域 | 测试类数 | 覆盖率 | 状态 | 补测重点 |
|:--|:--|:--|:--|:--|
| maintenance | 3 类 | **34.2%** | ⬜ | WorldMaintenanceService 备份编排分支、ScheduledBackupService 定时触发、失败路径 |
| paging | 1 类 | **47.0%** | ⬜ | Paginator 边界（首页/末页/超页/空列表） |
| ws | 2 类 | **47.4%** | ⬜ | RobustWebSocketClient 重连退避、心跳超时、消息乱序 |
| review | 3 类 | **65.9%** | 🟡 | ReviewHandler 异步授权分支、资格预检全类型、撤回/拒绝路径 |
| teleport | 6 类 | **73.6%** | 🟡 | ForceLoadedChunkLease 租约边界、安全落点搜索算法 |
| botcommands | 6 类 | **76.0%** | 🟡 | $e 输出捕获边界、$d 通配符解析 |
| assembly | 5 类 | **63.3%** | 🟡 | OrzServices.assemble 装配失败路径 |
| guidebook | 2 类 | **60.8%** | 🟡 | GuideBookConfigParser 复杂格式解析 |
| infra/bot | 3 类 | **68.0%** | 🟡 | OrzEasyBot 路由降级（player_group 为空） |
| 其余 7 领域 | — | 80-96% | ✅ | 维持 |

### 3.2 L1 MockBukkit 集成测试（13 类）

| 测试类 | 覆盖 |
|:--|:--|
| AssemblyIntegrationTest | 全模块装配+生命周期 |
| CommandAndEventIntegrationTest | 命令+事件联动 |
| CommandIntegrationTest / EventIntegrationTest | 注册链路 |
| ConfigIntegrationTest | 配置装载 |
| Guide/Menu/PlayerNotify/Portal/Security/TeleportBow/Tnt/Whitelist IntegrationTest | 各领域事件绑定 |

### 3.3 L2 真实服 E2E 用例（28 项，见 test-cases.md）

| 分类 | 用例 | 状态 |
|:--|:--|:--|
| 玩家命令 | /guide /menu /tpbow /bot /config /blacklist /portal /orzdebug | ✅ 8 项 |
| Bot 命令 | $h $l $w $a $r $d $b $e $o | ✅ 9 项 |
| 事件拦截 | 上下线通知 / KICK / 维护踢出 / 维护拒登 / 黑名单拦截 / GeoIP / 启动通知 / transfer / TNT / 冷却 | ✅ 10 项 |
| 权限链路 | /apply→$v 审核→LP 授权闭环（review-e2e） | ✅ 1 项（脚本化） |

---

## 四、质量指标体系（可持续跟踪）

### 4.1 核心指标与基线（2026-08-19 实测，补测后更新）

| 指标 | 基线 | 目标 | 采集方式 | 频率 |
|:--|:--|:--|:--|:--|
| 单测用例数 | **1296**（+13） | 单调递增 | `build/test-results/*.xml` 解析 | 每次构建 |
| 代码覆盖率 INSTRUCTION | **83.6%**（+1.4%） | ≥80% 维持 | JaCoCo XML | 每次构建 |
| 分支覆盖率 BRANCH | **72.7%**（+1.0%） | ≥75% | JaCoCo XML | 每次构建 |
| 方法覆盖率 METHOD | 85.0%+ | ≥85% | JaCoCo XML | 每次构建 |
| 类覆盖率 CLASS | 96.6%+ | ≥95% | JaCoCo XML | 每次构建 |
| CI 通过率 | — | 100% | GitHub Actions API | 每周 |
| E2E 用例通过率（Folia） | **32/32**（2026-08-19） | 100% | e2e/run-all.sh 报告 | 迭代时 |
| E2E 用例通过率（Paper） | 01 用例 8/8（2026-08-19） | 100% | 同上（ORZMC_* 环境变量） | 迭代时 |
| 测试执行时长 | ~2min（完整） | 回归 <3min | gradle 计时 | 每周 |
| Bug 逃逸率 | 见 4.3 | 趋零 | 线上问题登记 | 每月 |

**薄弱模块补测进展（2026-08-19）**：maintenance 34.2%→**66.9%**、paging 47.0%→**80.8%**、ws 47.4%→**59.0%**；剩余 review 65.9%、teleport 73.6% 列入下一轮。

### 4.2 门禁建议（CI 强化方向）

| 门禁 | 现状 | 建议 |
|:--|:--|:--|
| JaCoCo 覆盖率门禁 | ≥60% | **提升至 ≥75%**（当前 82.2%，留余量；patch 覆盖 ≥70% 已配） |
| BRANCH 门禁 | 未配置 | 新增 violationRules：BRANCH ≥70% |
| folia-smoke | continue-on-error | **稳定后转必须**（当前 #197 已 3 连绿） |
| E2E | 无 CI 集成 | 本地 run-all.sh 强制；CI 可加 nightly job（需常驻测试服，暂缓） |

### 4.3 缺陷逃逸统计（历史）

| 版本 | 逃逸 Bug | 根因类别 | 防线缺口 |
|:--|:--|:--|:--|
| 1.0.14-dev | orzdebug 前缀被原版 /debug 抢占 | 平台命令冲突 | 单测 mock 绕过真实解析（已在 orzmc-bot-command-testing.md 记录教训） |
| 1.0.15 | GeoIP 内网误拦截 | 私有段未短路 | 测试环境未覆盖内网 IP |
| 1.0.16 | $e 输出捕获不全 | 异步输出丢失 | Log4J Appender 兜底（已修） |
| 1.0.17 | $b ? 误触备份 | 全角空格绕过 | Claude Code 审查发现（M1-M4） |
| 1.0.18-dev | Folia 线程越权（tpbow force-load）| 平台线程模型 | foliaSmoke 上线兜底 |

**教训沉淀**：单测通过 ≠ 可用（必须真实环境验证）；配置语义反转（entity_teleport_enabled）需要 E2E 行为断言。

---

## 五、可复用测试体系（E2E 套件架构）

### 5.1 目录结构（e2e/ 随插件仓库版本化）

```
plugin/e2e/
├── README.md            # 使用说明（触发条件/环境/执行/报告）
├── run-all.sh           # 一键全量入口（-h 帮助 / -c 指定用例 / -r 生成报告）
├── lib/
│   ├── rcon.js          # 原生 RCON 客户端（$ 安全）
│   ├── bot.js           # mineflayer bot 工厂（粒子 patch/登录/等待）
│   └── report.sh        # 报告聚合（PASS/FAIL 汇总）
└── cases/
    ├── 01-bot-cmds.js       # $h/$l/$w/$a/$r/$d/$b/$o/$e/$v/$p
    ├── 02-player-cmds.js    # /guide /menu /bot /config /rank /apply /review
    ├── 03-security.js       # 黑名单拦截 / 限流 / 聊天过滤 / 命令守卫
    ├── 04-maintenance.js    # $b 备份三阶段 / 维护踢出 / 维护拒登
    ├── 05-notify.js         # 上下线通知 / KICK / 聚合摘要
    ├── 06-portal.js         # /portal 创建/移除（bot 部分链路）
    └── 07-tpbow.js          # 传送弓获取/权限边界
```

### 5.2 用例规范（每个用例脚本约定）

1. **自包含**：前置条件自检（服务器在线/账号白名单/配置状态）
2. **测前记录原值 → 测 → 立即还原**（写操作类用例）
3. **退出码约定**：0=全部通过，1=有失败（run-all.sh 聚合）
4. **输出约定**：`[PASS]/[FAIL] 用例名 — 断言内容`，失败附实际输出
5. **测试账号**：TestNewbie/TestMember/TestAdmin（密码见 orzmc-e2e-robot-testing.md）

### 5.3 执行方式

```bash
bash e2e/run-all.sh                 # 全量（约 3-5 分钟）
bash e2e/run-all.sh -c 01 -c 03     # 只跑 Bot 命令+安全类
bash e2e/run-all.sh -h              # 帮助
```

### 5.4 质量指标自动化（cron）

| 任务 | 计划 | 内容 |
|:--|:--|:--|
| **插件质量周报** | 每周一 9:30 | `gradlew test integrationTest jacocoTestReport` → 解析用例数/覆盖率 → GitHub API 拉最近 CI 通过率 → 飞书表格报告（对比上周基线） |
| **E2E 回归** | 迭代合并后手动/PR 触发 | run-all.sh 全量 → 报告落盘 docs/e2e-reports/ |

---

## 六、路线图

| 阶段 | 内容 | 状态 |
|:--|:--|:--|
| P0 基线建立 | 功能全图 + 覆盖率基线 + 薄弱模块定位 | ✅ 2026-08-19 |
| P1 单测补强 | maintenance/paging/ws/review/teleport 补测至 ≥75% | ⬜ 待排期 |
| P2 E2E 套件 | e2e/ 框架 + 7 类用例脚本化 + run-all.sh | 🔄 进行中 |
| P3 指标自动化 | 质量周报 cron + CI 门禁强化（覆盖率 75% + BRANCH 70%） | ⬜ |
| P4 CI 深度集成 | folia-smoke 转必须；nightly E2E job（需测试服常驻方案） | ⬜ 远期 |

---

*维护：功能变更/新模块须同步本节（功能全图 + 用例矩阵 + 指标基线）。*
