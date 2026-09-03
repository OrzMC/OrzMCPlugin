# OrzMC 代码质量与架构改进路线图

> 定位：执行路线图。六维审查（架构 / 质量保障 / 文档 / 性能 / PaperMC·Folia / 安全）的**完整问题清单 + 可独立推进的任务拆分**。
> 与既有 [security-hardening-roadmap.md](./security-hardening-roadmap.md)（安全功能已全部落地）互补——本文聚焦**已上线功能的缺陷修复、泄漏治理、测试补齐与文档校正**。
>
> 最后更新：2026-08-19（已对 HEAD `1248213` = 1.0.19 重新评估，#198–#203 的变更已核对：P0 命令绕过仍开放，新增 N1/N2 两项）
>
> 2026-09-03：全量复核（对照 HEAD `f7d09ea`）——A1/A2/A3/A4/A6/A8/A9/A10/A11/A12/A13/A14/A15/A16/N1/Q3/Q4/Q5 已随历次重构闭环（见 §2.3 表下注）；**A5**（架构决策待定）与 **A7**（产品行为待确认）暂不执行；A17/Q1/Q2/N2 保持开放。

---

## 0. 执行模型说明（重要，先读）

本路线图按**任务可被哪种模型独立执行**分为两类：

- **Flash 级任务**（§3）：单文件改动 / 明确修复点 / 自带验收标准 / 无跨模块重构。每个任务给出了 `问题 → 位置 → 修复方案 → 验收`，DeepSeekV4Flash 可**无需理解全局架构**直接照做并独立开 PR。
- **Claude 级任务**（§4）：God class 拆分、六边形边界修复、跨模块生命周期重构。这些需要理解全局依赖、判断副作用，**不适合 Flash 一次做完**，必须分阶段小步推进或由强推理模型执行。

**通用执行约定**（所有任务遵守，沿用 [AGENTS.md](../AGENTS.md)）：

1. 代码/测试改动一律走 `fix/<主题>` 分支 → PR → CI 绿（`./gradlew check`）→ squash merge，**禁止直推 main**。
2. 本地提交前 `./gradlew spotlessApply && ./gradlew test` 全绿。
3. 每个任务完成标准 = `spotlessCheck + test` 全绿 + PR 描述说明改动点与验收结果。
4. 涉及行为/配置变更需同步文档（README / docs/），与代码同 PR。

> 代码路径基址：`src/main/java/com/jokerhub/paper/plugin/orzmc/`（下文均用相对该基址的短路径）。

---

## 1. 审查结论摘要

六维并行审查（5 个独立审查 agent + 人工核对），未发现**当前可触发的 P0 致命漏洞**（`/orzdebug` 提权已修复、LP 死锁红线主路径已落实、运行时密钥文件已 gitignore）。但存在** 2 个命令防线绕过缺陷**、**4 个 tick 线程阻塞点**、**4 处无界内存泄漏**、**1 个跨线程并发竞态**，以及大量文档过时与测试盲区。

| 维度 | 结论 | P1+ 发现数 | 亮点 |
|---|---|---|---|
| **安全** | deny-list 可被 `/ op` 与 `bukkit:` 命名空间绕过；`$e` 群侧 isAdmin 信任网关字段 | 2 | `/orzdebug` 修复正确、`/config` 密钥不可达、无路径遍历/命令注入 |
| **线程/性能** | 命令审计/审核落盘/GeoIP 登录在 tick 线程同步 I/O；4 处 Map 无界增长 | 4 | LP 异步化、节流聚合、`Semaphore(32)` 背压到位 |
| **架构/质量** | 3 个 God class；`core/ports` 泄漏 Bukkit 类型；二阶段注入半初始化窗口 | 8 | `orzmc-api` 子模块纯 Java 边界干净、`shutdownAll` 顺序正确 |
| **测试** | `FeatureModule`（993 行）零测试；review/rank 集成链路缺失；2 处真实 sleep 慢测试 | 3 | 外部依赖隔离（LP/WS/GeoIP mock）质量高 |
| **文档** | `security-gap-analysis.md` 与已落地代码严重脱节；3 处引用不存在文件/命令 | 3 | rank track 层级、命令清单与代码一致 |
| **CI/构建** | codecov 集成覆盖率从未上报；build.yml 对 main push 的 artifact 名取空 sha | 2 | folia-smoke、jacoco 门禁、dependabot 齐全 |

完整问题清单见 §2；Flash 可执行任务见 §3；架构演进见 §4。

---

## 2. 问题总览清单（去重后）

> 严重级别：**P0**=死锁/崩溃/可远程接管/数据泄露；**P1**=明显缺陷/泄漏/可维护性；**P2**=改进/小瑕疵。`[F]`=Flash 可执行，`[C]`=需 Claude 级模型。

### 2.1 安全

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| S1 | P1 | deny-list 绕过：`/ op`（斜杠后空白）→ `primary=""` 永不命中 | `features/security/CommandGuardService.java:136-146` | F |
| S2 | P1 | deny-list 绕过：`bukkit:stop`/`bukkit:reload` 等命名空间前缀不归一 | `CommandGuardService.java:141-144` | F |
| S3 | P2 | `$p` 残留调试日志 + 用户可控 `rawArgs` 直拼日志（注入） | `features/botcommands/BotCommandService.java:393-395` | F |
| S4 | P2 | `connectionFingerprint()` 把明文 apiKey 拼入指纹串 | `infra/config/configs/EasyBotConfig.java:46-62` | F |
| S5 | P2 | EasyBot `api_server`/`ws_server` 无 scheme 校验，apiKey 可明文传输 | `EasyBotConfig.java:118-119`、`infra/bot/OrzEasyBot.java:255/317/555` | F |
| S6 | P2 | GeoIP 私网判定漏 IPv4-mapped IPv6（`::ffff:x.x.x.x`） | `features/security/GeoIpAccessService.java:130-176` | F |
| S7 | P2 | 32k 属性扫描仅覆盖进服主背包，不覆盖后续获得/末影箱/光标 | `features/security/ExploitHardeningEventService.java:82-108` | C |
| S8 | P0(待办) | `$e` 群侧 isAdmin 完全信任网关 `sender.role`，无白名单兜底 | `infra/bot/OrzEasyBot.java:757-769`（见 security-gap-analysis.md §5） | C |

### 2.2 线程 / 性能 / 泄漏

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| T1 | P1 | 每条命令在主/region 线程同步写审计文件（含 5MB `Files.size`） | `features/security/CommandAuditService.java:64-91` | F |
| T2 | P1 | GeoIP 登录 `.get(3000ms)` 同步阻塞 netty 线程（登录 DoS 放大） | `features/player/PlayerEventService.java:100-121` | C |
| T3 | P1 | 审核 reject 路径急切求值，tick 线程全量写 permission.yml | `features/review/ReviewService.java:253-254`、`features/rank/PermissionStore.java:90-98` | F |
| T4 | P1 | 公开 API `promote()/demote()` 无守卫 `.join()`，调度线程误用即自锁 | `features/rank/RankService.java:117-119,155-157` | F |
| L1 | P1 | `LoginRateLimitService.attemptTimes` 空 deque 键永不删除（无界） | `features/security/LoginRateLimitService.java:36,58-68` | F |
| L2 | P1 | `TntEventService.playerCooldowns` 只 put 不清理（无界） | `features/tnt/TntEventService.java:58,168-170` | F |
| L3 | P1 | `PortalEventService.lastTransfer` 冷却 Map 只 put 不清理（无界） | `features/portal/PortalEventService.java:38,139` | F |
| L4 | P1 | `CooldownRegistry` 静态 Map 命中后永不 remove（无界） | `features/command/binding/CooldownRegistry.java:6` | F |
| C1 | P1 | `PermissionStore` 读路径不加锁，与写路径并发触发 CME | `features/rank/PermissionStore.java:56-63 vs 101-167` | F |
| C2 | P2 | `RobustWebSocketClient.retryCount` 非 volatile 跨线程竞态 | `infra/ws/RobustWebSocketClient.java:31,81,157,183` | F |
| C3 | P2 | `AsyncHttp` 静态 `HttpClient` 缓存永不 close（线程池泄漏） | `infra/net/AsyncHttp.java:19` | F |
| C4 | P2 | `ServerFacade.executeConsoleCommand` 单数版不包 runSync，与复数版不一致 | `infra/server/ServerFacade.java:76-107` | F |
| C5 | P2 | `BukkitPlayerLookup.getOfflinePlayer` 在 tick 线程磁盘读 | `infra/player/BukkitPlayerLookup.java:18,24` | F |

### 2.3 架构 / 代码质量

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| A1 | P1 | `FeatureModule` God class（993 行，20+ 服务 + 14 监听 + 11 命令全内联） | `assembly/FeatureModule.java` | C |
| A2 | P1 | `OrzEasyBot` God class（834 行，HTTP/WS/解析/限流/鉴权混杂） | `infra/bot/OrzEasyBot.java` | C |
| A3 | P1 | `BotCommandService` God class（735 行，11 命令 + 分页 + 守卫） | `features/botcommands/BotCommandService.java` | C |
| A4 | P1 | `core/ports` 泄漏 Bukkit 类型（Location/Player/World/Server） | `core/ports/portal/*`、`core/ports/server/ServerAccess.java` | C |
| A5 | P1 | `TypedConfigProvider`（core）反向 import `infra.config.configs.*` | `core/ports/config/TypedConfigProvider.java:4-13` | C |
| A6 | P1 | 二阶段注入半初始化窗口（bot 先连上，review/rank 后注入） | `OrzServices.java:70-81` | F |
| A7 | P1 | `enableForceWhitelist` 无条件覆盖服务器 gamemode/white-list | `assembly/FeatureModule.java:402-416` | F |
| A8 | P2 | `OrzMC.onDisable` 未判空，启动失败时二次 NPE | `OrzMC.java:18` | F |
| A9 | P2 | `(Player) sender` 强转依赖拦截器顺序（潜在 CCE） | `assembly/FeatureCommandRegistrar.java`（registerSimple 已改 instanceof） | F |
| A10 | P2 | 死字段 `rankEventService` + 空 `setup()` override | `assembly/FeatureModule.java` | F |
| A11 | P2 | 空 catch 吞异常（`PlayerAuthenticationService:56`、`TntEventService:317`） | 两处 | F |
| A12 | P2 | `OrzEasyBot` 错误路径用 `info` 级别记录 | `infra/bot/OrzEasyBot.java` | F |
| A13 | P2 | `WhitelistService` 每次调用 `defaultImpl` 新建实例 | `BotCommandService.java:223,258,269` | F |
| A14 | P2 | `OnlineListFormatter` 实例化 3 次 + 注释与实现不符 | 现状仅 1 处 `new`（BotCommandListFeedbackService:17） | F |
| A15 | P2 | `ReviewType` 两段注册 lambda 逐字重复 | `assembly/FeatureModule.java:261-262,328`（promotionType 模板） | F |
| A16 | P2 | 三个渲染方法（review×2 + rank）样板重复 | `assembly/ReviewCommandRegistrar.java`、`RankCommandRegistrar.java` | F |
| A17 | P2 | `FeatureModule` 无 tearDown（flight tracker 等任务依赖 Bukkit 自动回收） | `assembly/FeatureModule.java` | F |
> **2026-09-03 复核**（HEAD `f7d09ea` = #243 合入后）：逐行对照当前代码核实，以下行**已在前序重构中闭环**，无需再执行：
> - **A1 已治理**：命令注册全部分出为 `assembly/` 逐特性注册器（#243 `f7d09ea`，FeatureCommandRegistrar 970→298）；FeatureModule 现存 506 行，经评估为**合法组合根**（跨特性服务 DAG 装配 + 事件委托），不做机械拆分（详见 #243 PR 讨论）。
> - **A2 已完成**：E2（OrzEasyBot 834→229 行编排）。
> - **A3 已完成**：E3（BotCommandService 735→192 行分派）。
> - **A4 已闭环**：orzmc-api `core/ports` 无 org.bukkit import（2026-09-03 grep 核验）。
> - **A6 已闭环**：依赖注入在 `botModule.setup()`（连接 WS）**之前**完成（`OrzServices.assemble` L70–81，注释明示「避免半初始化窗口」）。
> - **A8 已闭环**：`OrzMC.onDisable` 判空 + 注释即原问题描述（`OrzMC.java:18-19`）。
> - **A9 已闭环**：`registerSimple` 改 `instanceof Player`，assembly/commands 无 `(Player) sender` 强转残留。
> - **A10 已闭环**：`rankEventService` 死字段与空 `setup()` 已删除。
> - **A11 已闭环**：两处空 catch 均已补日志（grep 核验无残留）。
> - **A12 已闭环**：E2 拆分后错误日志随 WebSocketLifecycle/HttpSender 下沉，OrzEasyBot 仅编排。
> - **A13 已闭环**：`WhitelistCommandHandler:25-26` 单例注入（注释即原问题描述）。
> - **A14 已闭环**：全仓仅 `BotCommandListFeedbackService:17` 一处 `new OnlineListFormatter`（共享单实例注入）。
> - **A15 已闭环**：`promotionType` 模板消除两段 ReviewType 注册 lambda 重复。
> - **A16 已闭环**：命令拆分后渲染方法已变薄（纯 instanceof→send），无样板重复。
> - **N1 已闭环**：`runExclusive` 已复位 `chunkErrorCount`/`fatalErrorReported`（`WorldMaintenanceService:265-268`，注释即 N1 原文描述）。
> - **Q3 已闭环**：`PortalsWriterTest` 已存在。
> - **Q4 已闭环**：`PortalEventServiceTest` 已无 `Thread.sleep`。
> - **Q5 已闭环**：`CooldownRegistryTest` 已无真实时间/静态状态依赖（grep 核验）。
>
> 仍开放：
> - **A5**（core→infra 反向 import）：**架构决策待定**——config 记录依赖 Bukkit `ConfigurationSection` 解析无法入纯 api；机械搬移 30+ 调用点纯 churn，需先定 `core/ports/config` 定位（2026-09-03 判断暂不执行）。
> - **A7**（enableForceWhitelist 无条件覆盖）：**产品行为待确认**——false 分支应「不触碰运维手动配置」还是「显式关闭」？默认 `force_whitelist=true` 主路径不受影响；唯一可疑点 `setDefaultGameMode(SURVIVAL)` 在 false 时也执行。建议等到有实际事故证据再改（2026-09-03 判断暂不执行）。
> - **A17**（FeatureModule 无 tearDown）：未核实，保持开放。
> - **N2**（文档碎片化）：本文档即一例；#244 为跟进动作。
> - **Q1/Q2**：FeatureModule 零测试（组合根定位下价值低）；review/rank 链路已有单测 + E2E 部分缓解。

| N1 | P2 | ~~备份/优化错误计数器 `chunkErrorCount`/`fatalErrorReported` **跨 run 不复位**~~ → ✅ 已闭环（`runExclusive` L265-268 复位，见 §2.3 下注） | `features/maintenance/WorldMaintenanceService.java:265-268` | F |
| N2 | P2 | 文档碎片化加剧：`quality-testing-plan.md` 成为第 4 份功能清单（与 features.md / architecture.md / AGENTS.md 并行），根因是「新增文档而非更新旧文档」 | `docs/quality-testing-plan.md` | F |

### 2.4 测试质量

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| Q1 | P1 | `FeatureModule` 零测试（最复杂装配 + 审核渲染链路） | 无 `FeatureModuleTest` | C |
| Q2 | P1 | review/rank 两条命令链路无 MockBukkit 集成测试（**2026-08-19 已部分缓解**：新增 `ReviewCommandServiceTest` 单测 + E2E `06-permission-msg.js` 覆盖权限/审核群消息） | `src/integrationTest/.../integration/` | C |
| Q3 | P2 | `PortalsWriter`（32 行纯逻辑）无测试 | `infra/config/PortalsWriter.java` | F |
| Q4 | P2 | `Thread.sleep(5100)` 真实 5s 慢测试 | `src/test/.../portal/PortalEventServiceTest.java:245` | F |
| Q5 | P2 | `CooldownRegistryTest` 依赖真实时间 + 静态状态污染 | `.../binding/CooldownRegistryTest.java:14` | F |
| Q6 | P2 | `OrzServicesTest` 全类 `@Disabled` + `assertTrue(true)` 占位 | `src/test/.../OrzServicesTest.java:16,21` | F |
| Q7 | P2 | `ArmorStandCleanup` / `BukkitPlayerLookup` 无测试 | 两文件 | F |
| Q8 | P2 | `AsyncHttpTest` 重试用例竞态 + 弱断言 | `.../net/AsyncHttpTest.java:49-65` | F |

### 2.5 文档

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| D1 | P1 | 引用不存在的 `docs/player-guide-newbie.md` | `AGENTS.md:149` | F |
| D2 | P1 | `security-gap-analysis.md` 与已落地代码严重脱节（称备份/聊天/登录爆破"未覆盖"） | `docs/security-gap-analysis.md:37-107` | F |
| D3 | P1 | config.yml 配置段清单过时（漏 player_notify/guard/chat/login_rate_limit/exploit_hardening） | `docs/architecture.md:232`、`docs/features.md:419` | F |
| D4 | P2 | "/config 管理 25 项"过时（实际 29 项） | `README.md:38`、`README.zh-CN.md:35`、`features.md:319` | F |
| D5 | P2 | `features.md` 仍列已移除的 `tnt.notify_throttle_ms` | `features.md:343,196` | F |
| D6 | P2 | 英文 README 权限链首组写 "guest"（实际 "default"） | `README.md:28` | F |
| D7 | P2 | "15 个配置记录类" 实际 20 个，清单互相矛盾 | `AGENTS.md:63,95`、`architecture.md:58,369` | F |
| D8 | P2 | `architecture.md` Bot 命令表漏 `$v`/`$p`（自矛盾） | `architecture.md:328-339` | F |
| D9 | P2 | CHANGELOG [Unreleased] 两个"新功能"标题 + Folia 归属错位 | `CHANGELOG.md:5,16,17` | F |
| D10 | P2 | AGENTS.md 构建命令缺 Folia 任务（runFolia/foliaSmoke） | `AGENTS.md:13-23` | F |
| D11 | P2 | AGENTS.md "7 个端口"实际 3 个、"（QQ群/飞书）"实际 5 平台 | `AGENTS.md:34,9` | F |
| D12 | P2 | `folia-migration.md` 现状段过时 + 引用不存在 `/orzbackup` | `folia-migration.md:12-14,187` | F |
| D13 | P2 | `test-cases.md` 引用错误文件名 + `/portal create` 语法自矛盾 | `test-cases.md:323,308` | F |
| D14 | P2 | `publishing-platforms.md` 版本号 1.0.16/17 + 漏 Folia loader | `publishing-platforms.md:183,295,34,221` | F |
| D15 | P2 | `permission-system-v2-acceptance.md` 章节编号重复 | 该文件:97,112,127,153 | F |
| D16 | P2 | `paper-plugin.yml` 命令注册方式注释过时 | `src/main/resources/paper-plugin.yml:21` | F |
| D17 | P2 | `features.md` 漏 `guide_book.yml` + `tnt.enable` 语义不准确 + 踢出消息字段不符 | `features.md:419,183,26` | F |

### 2.6 CI / 构建

| # | 级别 | 问题 | 位置 | 执行 |
|---|---|---|---|---|
| I1 | P2 | codecov 声明 `integrationtests` flag 但 build.yml 从未上传集成覆盖率 | `codecov.yml` vs `.github/workflows/build.yml` | F |
| I2 | P2 | build.yml 对 main push 的 artifact 名取 `pull_request.head.sha` 为空 | `.github/workflows/build.yml`（upload jar artifact 步） | F |
| I3 | P2 | `orzmc-api` pom url 指向 `OrzGeeker/OrzMC`（应为 `OrzMC/OrzMCPlugin`） | `orzmc-api/build.gradle.kts:39` | F |
| I4 | P2 | folia-smoke 仍 `continue-on-error: true`（待连续 10 次绿转必须） | `.github/workflows/build.yml`（folia-smoke job） | F |

---

## 3. Flash 级任务清单（按优先级）

> 每个任务 = 一个独立 PR。依赖为空即可并行开工。完成标准统一为 §0 约定。

> ⚠️ **进度核对（2026-08-20 全量）**：逐项核对后，§3 绝大多数任务已落地，剩余仅少量低价值项。
>
> **已完成（无需再做）**：P0-01/02；P1-01~08、P1-11~13；P2-01~07、P2-11~13、P2-15/16、P2-18~20、P2-30、P2-32；
> 文档 D1~D17 全部校正；CI I1~I4 全部落地（codecov 合并覆盖率、artifact 命名 guard、folia-smoke 已转必须门禁）。
>
> **剩余（低价值，可延后）**：P1-09（ServerFacade 单数版 runSync 一致性，cosmetic，调用方已各自包 runSync）。
>
> **需测试服（另起一轮统一处理）**：P1-10（reject 异步化）、E7（32k 扫描扩展）、E8（GeoIP 登录异步）。

### P0 — 安全防线（立即，最高优先级）

#### P0-01 修复 `/ op`（斜杠后空白）绕过 deny-list
- **问题**：`normalize()` 去 `/` 后未再 trim；`parse()` 对 `" op".split("\\s+")` 取 `tokenArray[0]` 得到空串，deny-list 永不命中。玩家 `/ op`、`/  stop`、`/   reload` 均绕过。
- **位置**：`features/security/CommandGuardService.java:125-146`
- **修复**：`normalize()` 在 `substring(1)` 与剥 `minecraft:`/`bukkit:` 前缀后各补一次 `trim()`；`parse()` 对 `tokenArray` 跳过空 token 取第一个非空为主命令（或先对 `normalized` 整体 `trim()` 后再 `split`）。
- **验收**：`CommandGuardServiceTest` 新增用例：`guard("/ op")`、`guard("/  stop")`、`guard("/   reload")` 均返回 `BLOCK`；`guard("/op")` 仍 BLOCK；`guard("/help")` 仍 ALLOW。`spotlessApply + test` 绿。

#### P0-02 归一化 `bukkit:`/任意命名空间前缀
- **问题**：`normalize()` 只剥 `minecraft:`，`/bukkit:stop`、`/bukkit:reload`（Bukkit 自注册的 namespaced 命令）以 `primary="bukkit:stop"` 绕过 deny-list 的 `stop`/`reload` 规则。
- **位置**：`features/security/CommandGuardService.java:141-144`
- **修复**：归一化时剥除 `minecraft:` 与 `bukkit:` 两个前缀（如遇任意 `ns:` 形式，剥最后一个 `:` 前的命名空间部分，仅保留命令名）。注意保留命令名内的其它冒号语义不变（本场景命令名不含冒号）。
- **验收**：`CommandGuardServiceTest` 新增：`guard("/bukkit:stop")`、`guard("/bukkit:reload")`、`guard("/bukkit:op")` 均 BLOCK；`guard("/minecraft:op")` 仍 BLOCK。绿。

> ⚠️ P0-01/P0-02 可合并为一个 PR（同一文件、同一条测试链），但各自提交信息独立，便于回溯。

### P1 — 泄漏 / 并发 / 阻塞（短期）

#### P1-01 清理 `LoginRateLimitService.attemptTimes` 空键
- **位置**：`features/security/LoginRateLimitService.java:36,58-68`
- **修复**：`isRateLimited` 惰性清理时，若 deque 已空则 `attemptTimes.remove(ip)`；或在 `clear(ip)` 之外增加一个每次调用时移除空 deque 的收尾。二选一即可，保持线程安全（ConcurrentHashMap）。
- **验收**：`LoginRateLimitServiceTest` 新增：单次尝试过期后键被移除（`attemptTimes` 不增长）；连续多 IP 各尝试一次后 Map 大小为 0。绿。

#### P1-02 清理 `TntEventService.playerCooldowns` 过期项
- **位置**：`features/tnt/TntEventService.java:58,168-170`
- **修复**：在 `isOnCooldown`/放置检查处，若 `System.currentTimeMillis() - lastPlace > cooldownMs` 则 `remove(playerId)` 而非仅返回 false。
- **验收**：`TntEventServiceTest` 新增：冷却过期后再放置，Map 不残留该 UUID。绿。

#### P1-03 清理 `PortalEventService.lastTransfer` 过期项
- **位置**：`features/portal/PortalEventService.java:38,139`
- **修复**：传送冷却判定处对已过期项 `remove(playerId)`；并（可选）在 `PlayerQuitEvent` 时移除。
- **验收**：`PortalEventServiceTest` 新增：冷却过期后 Map 不残留。绿。

#### P1-04 清理 `CooldownRegistry` 静态 Map
- **位置**：`features/command/binding/CooldownRegistry.java:6`
- **修复**：`isCoolingDown` 判定时若已过期则 `remove(key)`；并加 `static void reset()` 供测试用（顺带解决测试静态污染，配合 Q5）。
- **验收**：`CooldownRegistryTest` 新增：过期后 key 被移除。绿。

#### P1-05 `PermissionStore` 读路径加锁防 CME
- **位置**：`features/rank/PermissionStore.java`（读方法 `listPending/findById/pendingFor/hasPending/listByApplicant` 约 101-167 行）
- **修复**：读方法同样 `synchronized (saveLock)` 包裹（与写路径同一把锁），避免 region 线程写、WS/主线程读并发触发 `ConcurrentModificationException`。改动仅加锁，不改逻辑。
- **验收**：`PermissionStoreTest` 新增并发读写冒烟用例（多线程同时 list + save 不抛 CME）。绿。

#### P1-06 给 `RankService.promote()/demote()` 加线程守卫
- **位置**：`features/rank/RankService.java:117-119,155-157`
- **修复**：在同步便捷方法体首行加 `if (serverFacade().isServerTickThread()) throw new IllegalStateException("禁止在服务器调度线程调用同步 promote/demote，请用 promoteAsync")`（或降级为调用 async 版并返回其 future）。`isServerTickThread` 若未暴露则用 `Bukkit.isGlobalTickThread()` 判定。
- **验收**：`RankServiceTest` 新增：模拟 tick 线程调用抛异常/降级；非 tick 线程调用仍走 async。绿。

#### P1-07 `RobustWebSocketClient.retryCount` 改 `AtomicInteger`
- **位置**：`infra/ws/RobustWebSocketClient.java:31,81,157,183`
- **修复**：字段类型改 `AtomicInteger`，`retryCount++` 改 `incrementAndGet()`、置 0 改 `set(0)`、读取改 `get()`。
- **验收**：现有 `RobustWebSocketClientLifecycleTest`/`HeartbeatTest` 全绿（行为不变）。绿。

#### P1-08 `AsyncHttp` 静态 HttpClient 提供关闭
- **位置**：`infra/net/AsyncHttp.java:19`
- **修复**：新增 `static void shutdown()`，遍历 `CLIENTS` 调 `close()` 并清空；在 `PlatformModule.tearDown()` 或 `OrzServices.shutdownAll()` 末尾调用。改 CLIENTS 为 `ConcurrentHashMap` 已可迭代。
- **验收**：`AsyncHttpTest` 新增 shutdown 后 Map 为空用例；`OrzServicesTest`（若已改真实）或 shutdownAll 相关测试绿。

#### P1-09 `ServerFacade.executeConsoleCommand` 单数版包 runSync
- **位置**：`infra/server/ServerFacade.java:76-107`
- **修复**：单数版内部与复数版一致地 `runSync` 包裹 `dispatchCommand`；同步语义下返回执行结果。同步两处现有调用方（`BotCommandService:325`、`PortalEventService:146`）删掉手动 runSync 包裹避免双重调度（或保留，因嵌套 runSync 已内联，二选一）。
- **验收**：`ServerFacadeTest` 新增单数版在异步线程调用时仍在同步线程 dispatch。绿。

#### P1-10 审核落盘异步化（reject 急切求值 + 全量写盘）
- **位置**：`features/review/ReviewService.java:253-254`、`features/rank/PermissionStore.java:90-98`
- **修复**：拒绝分支改为 `finalizeApproved` 相同的 `syncExecutor` 分派（返回 `CompletableFuture` 而非 `completedFuture(急切求值)`），让 `PermissionStore.save` 的 YAML 全量写盘脱离 tick 线程。（若只想做最小改动：仅把 reject 的 `completedFuture(finalizeStatus(...))` 改成 `CompletableFuture.runAsync(...)` + 回同步线程发消息，参考 approve 路径。）
- **验收**：`ReviewServiceTest` 新增：reject 路径返回 future 且 `store.save` 在非 tick 线程执行（可用 mock 断言）。绿。**注意**：涉及 LP/线程，动手前先读 `docs/dev/folia-luckperms-gotchas.md`。

#### P1-11 `CommandAuditService` 写盘异步化
- **位置**：`features/security/CommandAuditService.java:64-91`
- **修复**：`record()` 改为只入内存队列，由单个守护写线程（或 `ServerScheduler.runAsync`）批量落盘；`rotateIfNeeded` 的 `Files.size` 检查移到写线程。
- **验收**：`CommandAuditServiceTest` 扩展：`record()` 快速返回（不阻塞调用线程）、队列最终落盘、轮转仍正确。绿。

#### P1-12 修复 `$p` 日志注入 + 删除残留调试日志
- **位置**：`features/botcommands/BotCommandService.java:393-395`
- **修复**：删除该 `.info` 调试日志，或改为复用 `CommandAuditService` 的 sanitize（过滤 `\r\n`）后按 debug 级输出。
- **验收**：`BotCommandServiceTest` 无影响（若原有用例依赖该日志需同步）；`spotlessApply + test` 绿。

#### P1-13 二阶段注入上移（消除半初始化窗口）
- **位置**：`OrzServices.java:78,84`、`FeatureModule.java:276-277`
- **修复**：把 `setReviewService`/`setRankService` 从 `setupEventListeners` 上移到 `assemble()` 阶段，与 `setBlacklistService`（`OrzServices.java:71`）并列，确保 `botModule.setup()`（连接 WebSocket）之前所有跨模块注入已完成。
- **验收**：`OrzServicesTest`（或现有装配测试）验证注入顺序：`setup()` 前 review/rank/blacklist 均非 null。绿。**注意**：改装配顺序需确认无其他模块在 setupEventListeners 时依赖"尚未注入"的假设。

### P2 — 代码质量 / 安全加固（中期）

#### P2-01 `OrzMC.onDisable` 判空
- **位置**：`OrzMC.java:18`
- **修复**：`if (services != null) services.shutdownAll();`
- **验收**：编译 + 现有测试绿；可选补 `OrzServicesTest` 验证 assemble 失败时 onDisable 不 NPE。

#### P2-02 `(Player) sender` 改 `instanceof`
- **位置**：`FeatureModule.java:342,350,358`
- **修复**：`registerSimple` 的 action 内用 `if (sender instanceof Player player)`，否则给控制台友好反馈返回，不依赖拦截器顺序兜底。
- **验收**：现有 `CommandIntegrationTest` 绿。

#### P2-03 删除死字段/死方法
- **位置**：`FeatureModule.java:126`（`rankEventService`）、`267-270`（空 `setup()`）
- **修复**：删除未读取字段与冗余 override。
- **验收**：编译通过，无引用报错。

#### P2-04 空 catch 加降级日志
- **位置**：`features/security/PlayerAuthenticationService.java:56-58`、`features/tnt/TntEventService.java:317`
- **修复**：两个空/注释-only catch 加 `logger.warning/debug` 记录探测失败原因 / 非法配置项，不吞异常。
- **验收**：编译 + 现有测试绿。

#### P2-05 `OrzEasyBot` 错误路径日志级别统一
- **位置**：`infra/bot/OrzEasyBot.java:286,348,417,603,785`
- **修复**：HTTP/WS/解析异常从 `info` 改 `warning`（或 `error`）。
- **验收**：`OrzEasyBotTest` 绿（若断言了级别需同步）。

#### P2-06 `WhitelistService` 改为注入单例
- **位置**：`features/botcommands/BotCommandService.java:223,258,269`
- **修复**：构造注入 `WhitelistService`（`BotModule`/`FeatureModule` 装配处提供单例），替换三处 `defaultImpl(server.plugin())` 新建。
- **验收**：`BotCommandServiceTest` 绿。

#### P2-07 `OnlineListFormatter` 单实例收敛
- **位置**：`FeatureModule.java:97-98,216`、`BotCommandService.java:100-103`
- **修复**：只保留一个 formatter，通过构造/单一注入点共享给 listFeedback 与上下线广播；修正误导注释。rankService 只注入一次。
- **验收**：`BotCommandListFeedbackServiceTest`、`OrzPlayerEventTest` 绿。

#### P2-08 `ReviewType` 注册抽工厂方法
- **位置**：`FeatureModule.java:220-254`
- **修复**：抽 `promotionType(id, name, targetGroup, fromGroup)` 工厂，消除两段逐字重复。
- **验收**：`ReviewServiceTest` 绿。

#### P2-09 三个渲染方法收敛
- **位置**：`FeatureModule.java:745-779`
- **修复**：抽泛型 `renderResult(sender, result)` 处理 `Failure/Success` 样板；`renderReviewResultAsync` 显式 `.thenAcceptAsync(..., syncExecutor)` 回主线程发消息。
- **验收**：相关测试绿。

#### P2-10 `FeatureModule` 补 tearDown
- **位置**：`FeatureModule.java`
- **修复**：override `tearDown()`，显式停掉 flight tracker 等定时任务（`TeleportBowFlightTracker`），与其它模块对称。
- **验收**：编译 + 现有测试绿。

#### P2-11 `connectionFingerprint` 用 apiKey 哈希
- **位置**：`infra/config/configs/EasyBotConfig.java:46-62`
- **修复**：指纹串中 apiKey 换成 SHA-256 前缀（8-16 字符），不存明文。
- **验收**：`ConfigBackwardCompatTest` / `EasyBotConfig` 相关测试绿（指纹相等性判定不变）。

#### P2-12 EasyBot scheme 校验
- **位置**：`infra/config/configs/EasyBotConfig.java` + `ConfigHealthCheck`
- **修复**：加载时校验 `api_server` 非 `https://`（或显式 loopback 白名单）、`ws_server` 非 `wss://` 时输出 warning（不硬阻断，因内网部署常见）。至少告警。
- **验收**：`ConfigHealthCheckTest` 新增 scheme 校验用例。

#### P2-13 GeoIP 私网判定补 IPv4-mapped IPv6
- **位置**：`features/security/GeoIpAccessService.java:130-176`
- **修复**：IPv6 分支先识别 `::ffff:` 前缀，剥离后复用 IPv4 私网判断。
- **验收**：`GeoIpAccessServiceTest` 新增 `::ffff:127.0.0.1`、`::ffff:192.168.1.1` 直放行（不触发查询）。

#### P2-14 `RobustWebSocketClient` 命名/杂项（与 P1-07 合并时一并处理）

> 注：此条已并入 P1-07，不单列。

### P2 — 测试补齐

#### P2-15 删除 `OrzServicesTest` 占位或改真实装配测试
- **位置**：`src/test/java/.../OrzServicesTest.java:16,21`
- **修复**：删除 `@Disabled` 全类 + `assertTrue(true)` 占位；若装配逻辑已被 `CommandAndEventIntegrationTest` 覆盖则直接删文件，否则改为真实 MockBukkit 装配冒烟。
- **验收**：`test` 绿，无 `@Disabled` 残留。

#### P2-16 补 `PortalsWriter` 单测
- **位置**：`src/test/.../infra/config/PortalsWriterTest.java`（新建）
- **修复**：覆盖「清旧 key + 分组写入 + null 入参短路」三条路径（`PortalsWriter.write` 静态方法，纯逻辑可独立测）。
- **验收**：`test` 绿。

#### P2-17 补 `ArmorStandCleanup` / `BukkitPlayerLookup` 单测
- **位置**：新建两个测试类
- **修复**：前者 mock 世界/实体验证清理逻辑；后者 mock static `Bukkit.getOfflinePlayer` 验证 `hasPlayedBefore` 分支。
- **验收**：`test` 绿。

#### P2-18 消除 `Thread.sleep(5100)` 慢测试
- **位置**：`src/test/.../portal/PortalEventServiceTest.java:245`
- **修复**：给 `PortalEventService` 注入可替换时钟（`Supplier<Long>` 或 `Clock`），测试用假时钟推进，生产用 `System.currentTimeMillis`。
- **验收**：测试不再有 5s 睡眠，用例仍绿。

#### P2-19 `CooldownRegistryTest` 假时钟 + reset
- **位置**：`.../binding/CooldownRegistryTest.java:14`
- **修复**：结合 P1-04 的 `reset()`，改用假时钟替代 `Thread.sleep(1000)`；每个用例前 `reset()`。
- **验收**：无 sleep，测试稳定绿。

#### P2-20 修复 `AsyncHttpTest` 竞态与弱断言
- **位置**：`src/test/.../net/AsyncHttpTest.java:49-65`
- **修复**：改为先不启动 mock server 使首次请求失败，收到重试后再启动；断言 `requestCount >= 2` 且最终 200。
- **验收**：测试稳定绿，不再依赖 200ms 竞态窗口。

### P2 — 文档校正（适合 Flash，可打包小批量）

> 下列纯文档改动可单 PR 或按文件分组批量 PR。每项均需"改文档 → 与代码核对 → 提交"。

#### P2-21 修正 `AGENTS.md` 四处过时
- **位置**：`AGENTS.md:9,13-23,34,63,95,149`
- **修复**：平台描述改 5 平台；构建命令补 `runFolia`/`downloadFoliaJar`/`foliaSmoke`；"7 端口"改"3 端口"；"15 配置记录"改"20"；删除/修正 `docs/player-guide-newbie.md` 引用。

#### P2-22 `security-gap-analysis.md` 标注已落地
- **位置**：`docs/security-gap-analysis.md:37-107`
- **修复**：§3 对照表与 §5 建议项标注"已由 security-hardening-roadmap 落地（PR #179-#184）"，与路线图不再矛盾。

#### P2-23 `architecture.md` + `features.md` 配置清单补全
- **位置**：`docs/architecture.md:232,328-339`、`docs/features.md:419`
- **修复**：config.yml 段清单补 `player_notify`/`entity_teleport_*`/`guard`/`chat`/`login_rate_limit`/`exploit_hardening`；Bot 命令表补 `$v`/`$p`；配置文件清单补 `guide_book.yml`。

#### P2-24 数字类校正（29 项 / 移除 notify_throttle_ms / tnt.enable 语义）
- **位置**：`README.md:38`、`README.zh-CN.md:35`、`features.md:319,343,196,183,26`
- **修复**："/config 25 项"→"29 项"；删除 `tnt.notify_throttle_ms` 行；补 `tnt.enable` 两态语义（false 拦截 / true 仅通知）；删踢出消息"Discord 链接"表述。

#### P2-25 英文 README 权限链首组名
- **位置**：`README.md:28`
- **修复**："guest" → "default (guest)"。

#### P2-26 CHANGELOG 结构 + Folia 归属
- **位置**：`CHANGELOG.md:5,16,17`
- **修复**：合并两个"✨ 新功能"标题；Folia 适配归入 1.0.18 并补近期 Folia 修复 #193-#196。

#### P2-27 `folia-migration.md` / `test-cases.md` / `publishing-platforms.md` / `acceptance` / `paper-plugin.yml` 小修
- **修复**：folia-migration 现状段标为迁移前快照 + `/orzbackup`→`$b`；test-cases 文件名 `e2e-test-report.md`→`e2e-test-report-20260806.md` + `/portal create` 语法矛盾；publishing-platforms 版本 1.0.18 + Folia loader；acceptance 章节编号连续；paper-plugin.yml 命令注册注释改 Brigadier。

### P2 — CI / 构建

#### P2-28 上传集成测试覆盖率到 codecov
- **位置**：`.github/workflows/build.yml`、`codecov.yml`
- **修复**：`jacocoTestReport` 合并 test + integrationTest 的 exec 后上传，或新增一个 codecov 上传步携带 `flags: integrationtests`（指向 integrationTest 的 jacoco 报告）。确保与 `codecov.yml` 的 `integrationtests` flag 对齐。
- **验收**：codecov 面板出现 `integrationtests` 覆盖数据。

#### P2-29 修复 build.yml 对 main push 的 artifact 命名
- **位置**：`.github/workflows/build.yml`（upload jar artifact / generate build summary 步）
- **修复**：给 `upload jar artifact`、`generate build summary` 步加 `if: github.event_name == 'pull_request'`，或对 push 分支用 `github.sha` 兜底，避免 `pull_request.head.sha` 为空。
- **验收**：push main 构建不再产生空名 artifact / 摘要空值。

#### P2-30 修正 orzmc-api pom url
- **位置**：`orzmc-api/build.gradle.kts:39`
- **修复**：`url` 改 `https://github.com/OrzMC/OrzMCPlugin`。

#### P2-31 folia-smoke 转必须门禁（待条件满足）
- **位置**：`.github/workflows/build.yml`（folia-smoke job）
- **修复**：连续 10 次绿后移除 `continue-on-error: true`（当前 2/10，跨会话计数见 memory）。届时同步更新 AGENTS.md 的 CI 门禁描述。

#### P2-32 修复备份/优化错误计数器跨 run 不复位（N1）
- **问题**：`chunkErrorCount`/`fatalErrorReported` 是 `private final` 字段且从不复位。首次 run 致命错误后 `fatalErrorReported` 恒为 true，后续 run 的致命错误**不再发群通知**（仅剩服务器日志）；`chunkErrorCount` 跨 run 累积，干净 run 也误报「含 N 个损坏区块」。
- **位置**：`features/maintenance/WorldMaintenanceService.java:163-176,194-201`
- **修复**：在 `backup(...)`（行 267）与 `optimize(...)`（行 289）进入 `runExclusive` 前（或 `runExclusive` 的 `asyncWork` 开始处）复位 `chunkErrorCount.set(0)` + `fatalErrorReported.set(false)`。
- **验收**：`WorldMaintenanceServiceTest` 新增：第一次 run 触发致命错误后，第二次 run 的致命错误仍触发 `notifier.event`（可用 mock 断言）；含损坏区块的 run 后紧跟干净 run，Done 消息不含「损坏区块」汇总。绿。

---

## 4. Claude 级架构演进项（非 Flash，分阶段小步）

> 这些需要理解全局依赖与副作用，**禁止 Flash 一次做完**。建议由强推理模型主导，每步一个可独立验证的小 PR，逐步推进；统一用「提取 → 保留原行为 → 全测试绿 → 下一轮再删旧路径」的安全重构节奏。

### 4.1 顺序与文件冲突约束（核实代码后新增，执行前必读）

拆分落地时，**同一文件的并发改动会产生 merge 冲突**，需串行或分轮：

- **E1 ↔ §3 的 6 个 Flash 任务**（P2-02 instanceof、P2-03 死字段、P2-07 OnlineListFormatter、P2-08 ReviewType 工厂、P2-09 渲染收敛、P2-10 tearDown）**同改 `FeatureModule.java`** → 二选一顺序：建议**先做 Flash 小改**（低风险、改动小），再做 E1 大重构；或反过来，但**不可并行**。
- **E2 ↔ E6（均已闭环 ✅ 2026-08-20）**：E2 已把 `OrzEasyBot` 拆为编排 + `InboundEventParser`/`WebSocketLifecycle`/`HttpSender` 协作类（834→229 行）；E6/S8 已由 2026-08-19 决策关闭（网关 `sender.role` 即权威，fail-closed 降级，无需白名单兜底）。
- **E3 先于 E1（已完成 ✅ 2026-08-20）**：E3 已将 `BotCommandService` 拆为纯分派 + 独立 `$cmd` 处理器，并把 6 个 setter 收敛为 `injectDependencies(BotCommandDependencies)`（组合根在连接 WebSocket 前一次性注入）。E1 现已解耦：接线点从 6 个 setter 变为 1 个 `injectDependencies`，可直接推进。
- **E4（orzmc-api SDK 化）已立项**：目标与顺序见 §4.3。注意 SDK 化会动主模块 `core/ports`（被 portal/whitelist 等多个 feature 引用），建议在 E1-E3 稳定后启动，且按 §4.3 分阶段、每阶段全测试绿。
- **E7/E8 是功能改动非重构**：各自独立，但 E8 涉及 netty 线程模型，需测试服真机验证（见 `folia-luckperms-gotchas.md` §6）。

### 4.2 拆分项

| # | 主题 | 范围 | 建议拆法（已按实际代码核实） |
|---|---|---|---|
| E1 | `FeatureModule` 拆分（993→服务装配 + 命令注册分离） | `assembly/FeatureModule.java` | ✅ **已完成**（`FeatureModule` 993→354 行：命令注册抽到 `FeatureCommandRegistrar`，拦截器/渲染助手抽到 `BrigadierSupport`，`setupCommandHandlers` 只剩一行委托） |
| E2 | `OrzEasyBot` 拆分（834→编排 + 协作类） | `infra/bot/OrzEasyBot.java` | ✅ **已完成**（`OrzEasyBot` 834→229 行编排：抽 `WebSocketLifecycle`（连接/对账/监听器）+ `InboundEventParser`（入站解析/校验/分派）；`HttpSender` 承担出站 HTTP + 批量结果解析，`BotInboundDispatcher` 承担业务分派） |
| E3 | `BotCommandService` 拆分（735→分派 + 策略） | `features/botcommands/` | ✅ **已完成**（2026-08-20，分支 `refactor/botcommand-service` 5 个 commit：Step 0 `BotCommandContext` → Step 1a/1b/c Review/Permission/Console → Step 2 Whitelist/PlayerList/Maintenance/Blacklist → Step 3 `injectDependencies(BotCommandDependencies)`）。`BotCommandService` 735→192 行纯分派，11 个 `$cmd` 全部独立处理器 |
| E4 | `orzmc-api` SDK 化（原 E4 去 Bukkit 化 + E5 依赖倒置合并） | `orzmc-api/` + 主模块 `core/ports/*` | **已立项**。分 5 阶段推进，详见 §4.3 |
| E6 | `$e` 群侧 isAdmin 白名单兜底（S8） | `infra/bot/InboundEventParser.java` 入站鉴权 | ✅ **已决策关闭（2026-08-19）**：网关 `sender.role` 即权威，fail-closed 降级为非管理员，无需额外白名单兜底（代码注释已记录） |
| E7 | 32k 属性扫描扩展（S7） | `ExploitHardeningEventService.java` | 对 `InventoryClickEvent`/`Pickup`/末影箱等进物品路径做属性上限清理；需评估性能与误伤 |
| E8 | GeoIP 登录异步续接（T2） | `PlayerEventService.java:100-121` | 用 `AsyncPlayerPreLoginEvent` 异步续接或先 allow 再异步 disallow；需实测 netty 线程模型 |

---

### 4.3 orzmc-api SDK 化路线（E4，5 阶段）

> 目标（已确认立项）：把 `orzmc-api` 建成**纯 Java、零 Bukkit、可独立发布**的领域契约层，供插件主模块与未来生态工具复用；同时消除「orzmc-api 与主模块各有一份 `com.jokerhub.paper.plugin.orzmc.core.ports`」的双份包分裂。
>
> **核实后的关键修正**：并非所有主模块端口都该「去 Bukkit」——`ServerAccess`（返回裸 `Server`）与 `WorldProvider`（返回裸 `World`）本质是** Bukkit 逃生舱 + 测试缝**，它们的 Bukkit 类型是「有意保留」而非「泄漏」，不应 SDK 化。真正值得纯化的是领域契约（Portal）与配置值对象。

| 阶段 | 内容 | 产出 | 风险/说明 |
|---|---|---|---|
| S1 | **统一 core.ports 结构**：明确「orzmc-api = 纯契约层（health/server/bot/assembly + 新增 domain 值对象）；主模块 = Bukkit 侧 seam + 适配实现」。主模块 `core.ports` 下 5 个文件中，`PortalPort`/`PortalInfo`/`TypedConfigProvider` 迁向 orzmc-api，`ServerAccess`/`WorldProvider` 保留主模块并加「有意 Bukkit seam」注释 | 消除双份包，端口归属清晰 | 纯梳理 + 命名 + 文档，最低风险，可先行 |
| S2 | **定义纯值对象**：`Location`（worldName + x/y/z + yaw/pitch，record）、`Axis`（X/Z 枚举）、`WorldRef`（worldName）、`PortalInfo`（纯 record）→ 全部入 orzmc-api `core/domain` | SDK 的领域模型 | 纯新增，零行为变化 |
| S3 | **`PortalPort` 去 Bukkit 化 → 迁入 orzmc-api**：`createPortal(Location origin, Axis axis, String host, int port)`（feature 层从 Bukkit Player 提取 position/facing 后传纯值）；`findTarget`/`findTargetExact` 用纯 `Location`。infra `PortalService` 内部做「纯值 → Bukkit Location」转换；更新 `PortalCommandService`/`PortalEventService`/`PortalLabelRenderer` 调用点 | 领域契约纯化，portal 逻辑可脱离 Bukkit 单测 | 中等风险，每步全测试绿 |
| S4 | **`TypedConfigProvider` 依赖倒置**：20 个 config record 的「纯字段 + accessor」下沉为 orzmc-api 值对象；`from(ConfigurationSection)` 解析留在 infra（YAML→record）。`TypedConfigProvider` 接口迁 orzmc-api | 配置 schema 成为可复用值对象 | **价值存疑、churn 最大**：需有明确外部消费方（如配置 dashboard / 编辑器）才值得，否则**暂缓** |
| S5 | **发布与消费验证**：orzmc-api 已在 Maven 发布（`orzmc-api:publishToMavenLocal`），修正 pom url（见 P2-30）；引入一个真实消费方（如独立工具/测试项目）验证 SDK 可用性 | SDK 闭环 | 依赖 S1-S4 |

**执行约定**：S1→S2→S3 为 SDK 化主线（价值最高、可逐步交付）；S4 待有明确消费方再启动；每阶段一个 PR、`spotlessCheck + test` 全绿、orzmc-api 维持 100% 覆盖率门禁。

---

## 5. 执行顺序建议

1. **第一批（安全 + 泄漏，1-2 个冲刺）**：P0-01、P0-02、P1-01～P1-05、P1-12、P2-28～P2-31——这些是真实缺陷/泄漏，改动小、验收明确，最适合 Flash 并行。
2. **第二批（并发/阻塞）**：P1-06～P1-11、P1-13——涉及线程与 LP，逐项先读 `docs/dev/folia-luckperms-gotchas.md` 再动。
3. **第三批（测试补齐 + 文档校正）**：P2-15～P2-27——可批量并行，纯增量/纯文本，零行为风险。
4. **架构演进（E1-E8）**：由强推理模型分阶段推进，每步一个绿 PR，不追求一步到位。

---

## 6. 相关文档

- [security-hardening-roadmap.md](./security-hardening-roadmap.md) — 安全功能落地路线图（P0-P2 已完成）
- [security-gap-analysis.md](./security-gap-analysis.md) — 安全现状对照
- [architecture.md](./architecture.md) — 架构设计
- [folia-migration.md](./folia-migration.md)、[dev/folia-luckperms-gotchas.md](./dev/folia-luckperms-gotchas.md) — Folia 线程红线
- [AGENTS.md](../AGENTS.md) — 仓库协作约定（单一事实源）
