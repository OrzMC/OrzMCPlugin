# OrzMC 安全加固路线图

> 定位：执行路线图。对照站点文章《PaperMC 危险指令与防护》(25) 与《对外开服安全清单》(26)，
> 把 OrzMC 插件的安全补强工作拆成**可独立推进、按优先级落地**的子任务清单。
> 现状对照与背景分析见 [security-gap-analysis.md](./security-gap-analysis.md)（本文不重复展开）。
>
> 最后更新：2026-08-17

---

## 1. 目标与拆分原则

**目标**：通过 OrzMC 插件自身能力保障服务器安全健康运行 —— 把文章 25/26 里"依赖人工自律"的防护原则，
变成插件**自动拦截 + 自动审计 + 自动告警**的硬约束。

**拆分原则**（适配 Flash 级推理强度）：每个子任务必须满足——

1. **单文件交付**：一个配置记录 / 一个纯逻辑服务 / 一个监听器 / 一个测试类，一次只做一个；
2. **无跨模块重构**：只新增，不侵入现有模块（组合根 `FeatureModule` 的接线除外，一步到位）；
3. **验收可测**：每个子任务自带 JUnit 单测，`spotlessApply + test` 通过才算完成；
4. **可独立合入**：每个子任务可单独开 PR，被依赖任务完成即可开工，互不阻塞。

**执行约定**：所有代码/测试改动走 PR（分支 → CI 绿 → merge），不直推 main；每个任务完成标准 = `spotlessCheck` + `test` 全绿 + PR 描述说明改动点。

---

## 2. 路线图总览

| 优先级 | 子任务 | 状态 | 依赖 |
|---|---|---|---|
| **P0** | 危险命令拦截模块（文章 25 核心） | | |
| | P0-1 `SecurityGuardConfig` 配置记录 | ☑ | — |
| | P0-2 `CommandGuardService` 判定核心 | ☑ | P0-1 |
| | P0-3 `CommandGuardListener` 事件接入 | ☑ | P0-2 |
| | P0-4 `CommandAuditService` 命令审计 | ☑ | P0-2 |
| | P0-5 `$e`/`/orzdebug`/RCON 执行路径接入 guard | ☑ | P0-2 |
| **P1** | 运维自动化（文章 26 §5 + 上线验收） | | |
| | P1-1 定时自动备份 | ☑ | — |
| | P1-2 启动安全自检报告 | ☑ | — |
| **P2** | 游戏内加固（文章 26 §4） | | |
| | P2-1 聊天反垃圾/反广告 | ☑ | #181 |
| | P2-2 进服限流/反 bot | ☑ | #182 |
| | P2-3 已知漏洞加固（书页/32k/实体上限） | ☑ | #183 |
| | P2-4 IP 黑名单增强（IPv6 + 封禁告警） | ☑ | #184 |

---

## 3. P0 — 危险命令拦截模块（文章 25 核心）

> 现状缺口：文章 25 的整张危险指令清单与防护原则②（高危节点默认拒绝）、④（命令审计）、⑦（执行前过一遍目标）在插件内均为空白。
> 现有地基：`features/command/binding/` 拦截器链、`Notifier` 的 PRIVATE 私信路由、`CommandFeedbackService` 中文化反馈、`FeatureModule` 组合根。

### P0-1 `SecurityGuardConfig` 配置记录

- **目标**：新增 `infra/config/configs/SecurityGuardConfig.java`（`record` + `from(ConfigurationSection)`）。
- **字段**：
  - `enabled`（boolean，默认 `true`）—— 总开关；
  - `blockedCommands`（`List<String>`，默认 `op, deop, publish, seed, reload, plugman, stop`）—— 危险命令 deny-list（支持子命令项如 `plugman reload`）；
  - `notifyAdmins`（boolean，默认 `true`）—— 拦截时是否私信管理员；
  - `auditEnabled`（boolean，默认 `true`）—— 是否记录命令审计。
- **落点**：`config.yml` 新增 `guard:` 配置段；`TypedConfigProvider` 增加 `securityGuard()`；`DefaultTypedConfigProvider` 实现（`sectionOrLegacy("config", "guard", ...)`）；`ConfigHealthCheck` 增加 `validateGuardSection`。
- **验收**：`SecurityGuardConfigTest` 覆盖 null/空/完整配置段三态 + 非法值回退；`spotlessCheck + test` 绿。

### P0-2 `CommandGuardService` 判定核心

- **目标**：新增 `features/security/CommandGuardService.java`，纯逻辑、无 Bukkit 依赖，可独立单测。
- **职责**：
  1. 命令归一化：去 `/`、转小写、剥 `minecraft:` 前缀，分离命令名与参数；
  2. deny-list 匹配：命令名精确匹配，或子命令前缀匹配（`plugman reload` 命中 `plugman` 的 deny 项 `plugman reload`）；
  3. 目标选择器守护：对 `kill` / `clear` / `give` / `execute` / `effect` 中出现**未限定** `type=..` 或 `distance=..` 的裸 `@e` / `@a` 标记为 WARN（文章 25 目标选择器警示）；
  4. 返回 `GuardDecision`（`ALLOW` / `BLOCK` + 原因 / `WARN` + 原因）。
- **验收**：`CommandGuardServiceTest` 覆盖：普通命令放行、deny-list 命中、子命令命中、`minecraft:` 前缀归一、裸 `@e`/`@a` 命中 WARN、已限定 `@e[type=zombie,distance=..32]` 放行。

### P0-3 `CommandGuardListener` 事件接入

- **目标**：新增监听器，接两个同步事件：
  - `ServerCommandEvent`（控制台 / RCON / 命令方块）；
  - `PlayerCommandPreprocessEvent`（玩家在聊天框执行 `/...`）。
- **行为**：`BLOCK` → `event.setCancelled(true)` + 发送者中文反馈 + （`notifyAdmins`）`Notifier` PRIVATE 私信管理员「高危命令被拦截：<命令> by <发送者>」；`WARN` → 记日志并放行；`ALLOW` → 放行。
- **落点**：`events/OrzCommandGuardEvent.java` + 在 `FeatureModule.setupEventListeners` 注册。
- **验收**：`CommandGuardListenerTest`（MockBukkit 或事件 mock）验证取消、反馈、私信调用、WARN 放行四分支。

### P0-4 `CommandAuditService` 命令审计

- **目标**：新增 `features/security/CommandAuditService.java`。
- **职责**：把（放行的）命令执行与（拦截的）危险命令追加写入 `plugins/OrzMC/audit/command_audit.log`，一行一条：`ISO 时间 | 来源(game/console/RCON/bot) | 发送者 | 命令原文 | 结果(executed/blocked)`；文件超过上限（默认 5MB）轮转；`auditEnabled=false` 时跳过。
- **验收**：`CommandAuditServiceTest` 覆盖追加格式、轮转、禁用跳过。

### P0-5 `$e`/`/orzdebug`/RCON 执行路径接入 guard

- **目标**：堵住"群内任意控制台执行"这扇最危险的门。
- **行为**：`BotCommandService.handleExecuteConsoleCommand` 在 `server.dispatchCommand` **之前**先过 `CommandGuardService`：`BLOCK` → 返回「命令已被安全拦截」反馈 + 记审计，不执行；`WARN` → 仍执行但记审计。`/orzdebug` 与 RCON 入口复用同一处理路径，自动覆盖。
- **验收**：扩展 `BotCommandServiceTest`，覆盖 `$e stop`（deny-list）被拦、普通命令放行。

---

## 4. P1 — 运维自动化（文章 26 §5 + 上线验收）

### P1-1 定时自动备份

- **目标**：文章 26 §5 明确"备份不要依赖手动"。插件已有完整备份机械（`$b`、踢人→save-off→压缩→save-on、保留轮转、维护 MOTD/锁登录），只缺调度。
- **行为**：`MaintenanceConfig` 增加 `backupIntervalHours`（默认 `0` = 关闭）；新增 `features/maintenance/ScheduledBackupService.java`，用 `SafeScheduler.runTaskTimer` 按小时触发 `WorldMaintenanceService.backup()`；`runExclusive` 天然互斥，运行中跳过；错误沿用现有 PRIVATE 私信。**热重载**：常驻轻量检查器（每分钟）惰性读取配置，间隔/开关经 `/config reload` 修改后下一检查点即生效，无需重启。
- **验收**：`ScheduledBackupServiceTest`（0 不调度、正数调度且重复 tick 不叠加）+ `MaintenanceConfigTest` 扩展。

### P1-2 启动安全自检报告

- **目标**：把文章 26 的上线 check-list 自动化，启动即体检。
- **行为**：`ServerLoadEvent` 时采集：`online-mode`、`enable-command-block`、RCON 端口、`whitelist`/enforce 状态、OP 列表、关键插件是否安装（LuckPerms / LoginSecurity / Grim / Vulcan）；渲染新增 `security_audit` 模板（`templates.yml` + `TemplateKeys` 注册）走 PRIVATE 私信管理员。
- **验收**：`StartupSecurityAuditServiceTest` + 模板渲染测试。

---

## 5. P2 — 游戏内加固（文章 26 §4）

| 子任务 | 说明 | 验收 |
|---|---|---|
| P2-1 聊天反垃圾/反广告 | `AsyncChatEvent` 按玩家限流 + 链接/重复文本检测，命中静默丢弃或提示 | `ChatSpamFilterServiceTest` |
| P2-2 进服限流/反 bot | `AsyncPlayerPreLoginEvent` 按 IP 限并发/频率，超限 disallow + 可选告警 | `LoginRateLimitServiceTest` |
| P2-3 已知漏洞加固 | `PlayerEditBookEvent` 书页上限；物品 NBT 上限清理（防 32k）；单区域实体数量上限 | `ExploitHardeningServiceTest` |
| P2-4 IP 黑名单增强 | `BlacklistService` 支持 IPv6 CIDR；封禁命中 PRIVATE 告警 + 日志 | `BlacklistServiceTest` 扩展 |

---

## 6. 执行状态追踪

> 当前进度以任务清单（Task List）为准，每完成一个子任务在此打勾并附 PR 链接。

### P0
- [x] P0-1 `SecurityGuardConfig` — 分支 feat/security-hardening（`a67674c`）
- [x] P0-2 `CommandGuardService` — 分支 feat/security-hardening（`a67674c`）
- [x] P0-3 `CommandGuardListener` — 分支 feat/security-hardening（`7fa4326`）
- [x] P0-4 `CommandAuditService` — 分支 feat/security-hardening（`e03349d`）
- [x] P0-5 `$e` 路径接入 guard — 分支 feat/security-hardening（`506fab6`）

### P1
- [x] P1-1 定时自动备份 — PR [#179](https://github.com/OrzMC/OrzMCPlugin/pull/179)（含 P0 全模块）
- [x] P1-2 启动安全自检报告 — PR [#180](https://github.com/OrzMC/OrzMCPlugin/pull/180)

### P2
- [x] P2-1 聊天反垃圾/反广告 — PR #181（`8b16ce9`）
- [x] P2-2 进服限流/反 bot — PR [#182](https://github.com/OrzMC/OrzMCPlugin/pull/182)（`4ba5774`）
- [x] P2-3 已知漏洞加固 — PR [#183](https://github.com/OrzMC/OrzMCPlugin/pull/183)（`782d6ed`）
- [x] P2-4 IP 黑名单增强 — PR [#184](https://github.com/OrzMC/OrzMCPlugin/pull/184)（`616fb2d`）

---

## 7. 相关文档

- 站点文章 25：`../site/content/posts/2.server/25.papermc-dangerous-commands.md`
- 站点文章 26：`../site/content/posts/2.server/26.papermc-open-server-checklist.md`
- 现状对照：`./security-gap-analysis.md`
- 插件功能清单：`./features.md`
