# OrzMC 安全能力对照与补强建议

> 定位：内部文档。对照通用 PaperMC 安全风险（通用知识见 [site 文章 25/26](../site/content/posts/2.server/25.papermc-dangerous-commands.md)），梳理 OrzMC 插件已有防护、识别缺口，并给出补强建议。
>
> 最后更新：2026-08-16
>
> **⚠️ 状态更新（2026-08-19）**：本文 §3 对照表与 §5 建议中的缺口（危险命令拦截、定时备份、聊天反垃圾、登录爆破、漏洞加固、IP 黑名单增强）**已由 [security-hardening-roadmap.md](./security-hardening-roadmap.md) 全部落地**（PR #179–#184）。本文保留为「加固前的现状快照」，最新能力清单见 `docs/features.md` 与 `docs/quality-testing-plan.md`。

---

## 1. 背景与目标

OrzMC 私服对外运营（离线模式 + 强制白名单 + 群机器人管理），安全风险集中在：指令滥用、登录爆破、调试入口泄露、备份/运维缺口。本文对照通用 PaperMC 危险指令与开服安全清单（见站点文章 25/26），逐项核对 OrzMC 插件覆盖情况，识别需优先修复的缺口。

**⚠️ 重要：`/orzdebug` 漏洞（P0）详见第 4 节，建议优先处理。**

---

## 2. 现有防护能力速览

| 能力 | 机制 | 配置入口 | 代码位置 |
|---|---|---|---|
| 强制白名单 | 启动时开启白名单 + enforce + 默认生存；未白名单玩家踢出；白名单被关闭时群告警 | `whitelist.forceWhitelist`、`whitelist.kick_message` | `features/whitelist/`、`FeatureModule.enableForceWhitelist()` |
| 白名单维护 | 群指令 `$a/$r/$w` 批量增删、踢出在线玩家、不活跃自动清理 | `whitelist.cleanupInactiveDays` | `features/whitelist/` |
| GeoIP 国家白名单 | 按国家码限制进服；私网直放行；12h 缓存；查询失败/超时/空国家码默认 fail-close 拒绝（可配 `geoip.fail_open: true` 放行），均私信告警管理员 | `geoip.allow_country_code`、`geoip.fail_open` | `features/security/GeoIpAccessService.java`、`features/player/PlayerEventService.java` |
| IP 黑名单 | 精确 IP / CIDR / 通配符三种规则，登录前拦截 | `ip_blacklist.yml` | `features/security/BlacklistService.java` |
| TNT 防护 | 点燃/放置/发射器/爆炸多拦截点；区域白名单；放置冷却；爆炸告警聚合 | `tnt.*` | `features/tnt/` |
| 权限链 Rank | LuckPerms 四级 track（default→member→builder→admin），自动晋升 + 申请审核；脏节点归一 | `permission.yml`、LP track | `features/rank/`、`features/review/` |
| 命令拦截器 | Brigadier 注册 + 责任链（PlayerOnly / AdminOnly / Cooldown），管理员命令对非管理员隐藏 | `command_policies` | `features/command/binding/` |
| 世界备份/优化 | `$b` 备份（save-off/on + ZIP）、`$o` 优化、互斥执行、旧备份轮转 | `maintenance.*` | `features/maintenance/WorldMaintenanceService.java` |
| LoginSecurity 二次认证 | 反射调用登录插件 API，未登录禁止踩传送门 | 依赖外部插件 | `features/security/PlayerAuthenticationService.java` |

---

## 3. 风险点对照表

| 危险场景 | OrzMC 覆盖情况 | 缺口 / 说明 |
|---|---|---|
| 原生/第三方指令滥用（`/op`、`/fill`、`/kill`…） | **不拦截** | 依赖 LuckPerms 节点 + `docs/permission-groups.md` 人工锁定高危节点（`*`、`luckperms.*`、`minecraft.command.op`、`bukkit.command.op`、`essentials.stop`、`essentials.reload`、`minecraft.command.summon` 不授任何组）。人工维护，组变更时易漂移，无自动校验 |
| `/orzdebug` 调试入口 | **已修复（2026-08-16）** | 见第 4 节：改为 AdminOnlyInterceptor，仅 OP / `orzmc.admin` / 控制台可用 |
| Bot `$e` 执行控制台命令 | **已正确门禁** | `BotCommandService` 通过 `guardAdminCommand` 校验 `isAdmin`；`$e` 属 `needAdminPermission=true`。群侧 `isAdmin` 由 `BotInboundDispatcher` 依据群成员身份传入 |
| 登录爆破 | **未覆盖** | LoginSecurity 自带基础防护，插件无额外限速/封禁；高频失败可打满 `ip_blacklist.yml` 前反复尝试 |
| GeoIP 查询失败 | **默认 fail-close，可配 fail-open** | 查询失败/超时/空国家码默认拒绝进入（3s 决策窗口，安全优先），`geoip.fail_open: true` 改为放行（可用性优先）；两种策略均私信告警管理员 |
| 定时备份 | **未覆盖** | 备份仅 `$b` 手动触发，无 cron/定时调度；依赖服主自觉 |
| 聊天反垃圾/反广告 | **未覆盖** | 不在本插件职责内，依赖外部插件 |
| 防 X-Ray | **未覆盖** | 依赖 Paper 内置 anti-xray 配置（站点文章已记录），非插件能力 |
| 白名单关闭告警 | **已覆盖** | `WhitelistToggleEvent` 推送群告警（需验证链路有效） |

---

## 4. P0 漏洞：`/orzdebug` 任意玩家执行控制台命令

> ✅ **已修复（2026-08-16）**：`/orzdebug` 已改为 AdminOnlyInterceptor 门禁，仅 OP / `orzmc.admin` / 控制台可用。以下为修复前的漏洞记录，保留备查。

### 漏洞链（修复前，代码已核实）

`/orzdebug` 在 `FeatureModule.setupCommandHandlers()` 中注册：

```java
literal("orzdebug")
        .requires(src -> true)                      // ← 对任何玩家开放，无权限要求
        .then(argument("cmd", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ...
                    inbound.handleMessage(cmd, true, "控制台", ...);  // ← 硬编码 isAdmin=true
                    ...
                }))
```

`BotInboundHandler` 将消息转给 `BotCommandService.parse(message, isAdmin, ...)`，其中 `$e`（执行控制台命令）的权限门禁：

```java
// BotCommandService.java
private void handleExecuteConsoleCommand(...) {
    if (!guardAdminCommand(cmd, isAdmin, callback)) return;  // 仅检查 isAdmin
    server.executeConsoleCommand(rawArgs);                    // 执行任意控制台命令
}
```

**结论**：任意非 OP 玩家执行 `/orzdebug e stop`（停服）、`/orzdebug e op <自己>`（自封 OP）、`/orzdebug e lp ...` 等，即可**完全接管服务器**。`$e` 本身门禁正确，但 `/orzdebug` 硬编码 `isAdmin=true` 绕过了它。

### 修复记录（2026-08-16 已实施）

修复方式（`FeatureModule.setupCommandHandlers()`）：

1. 将 `.requires(src -> true)` 改为 `.requires(requirement(adminInterceptors("orzdebug")))`——复用 `AdminOnlyInterceptor`，仅 OP / `orzmc.admin` 可用，非管理员在 Tab 补全中不可见、直接输入被 Brigadier 拒绝；控制台恒放行（`AdminOnlyInterceptor.canUse` 对非 Player 返回 `true`，保留调试通道）。
2. 执行体用 `guardedExec("orzdebug", debugInterceptors, ...)` 包裹，与其它命令拦截模式一致。
3. 修复后核对：`/orzdebug` 非管理员执行被拒，`$e` 群指令行为不受影响；`compileJava` / `spotlessCheck` / `test` 均通过。

---

## 5. 补强建议（分优先级）

### P0 — 上线前必须

- [x] **`/orzdebug` 权限收紧**：已改为 AdminOnlyInterceptor 门禁（见第 4 节）。
- [x] **核实 `$e` 群侧管理员判定**：已确认（2026-08-19）`OrzEasyBot` 的 `isAdmin` 判定 fail-closed——仅网关返回 `role=Owner/Admin` 视为管理员，role 缺失/未知一律按非管理员处理；网关 role 即权威，无需额外白名单兜底。
- [ ] **复核 op 与 `orzmc.admin` 边界**：`CommandPermissionService.requireAdmin()` 使用 `isOp() || hasPermission("orzmc.admin")`，确认线上无遗留 OP 账号。
- [ ] **验证白名单关闭告警链路**：实际关一次白名单确认群告警到达。

### P1 — 强烈建议

- [ ] **定时备份调度**：复用 `WorldMaintenanceService.runExclusive` 与备份逻辑，增加 cron/间隔配置（如每 6 小时），备份后沿用现有轮转（`backup_retention_count`）。
- [ ] **登录爆破限制**：对接 LoginSecurity 事件，连续 N 次失败即临时封禁该 IP，可选写入 `ip_blacklist.yml`。
- [ ] **命令审计/日志**：管理员命令（`/blacklist`、`/config`、`/review`、`$e`）输出到日志，便于事后排查。

### P2 — 可选

- [ ] 聊天反垃圾/反广告接入或外接插件选型。
- [ ] anti-xray / 防御类配置的集成说明（可引用站点文章 25/26）。
- [ ] 安全巡检报告：定期输出 OP 列表、黑名单、权限组 diff，防止权限漂移。

---

## 6. 与站点文档的关联

- 通用 PaperMC 危险指令与防护：`site/content/posts/2.server/25.papermc-dangerous-commands.md`
- 对外开服安全清单：`site/content/posts/2.server/26.papermc-open-server-checklist.md`
- 本文仅聚焦 OrzMC 插件自身能力与缺口，通用知识见上述两篇。
