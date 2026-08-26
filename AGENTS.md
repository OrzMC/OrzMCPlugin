# AGENTS.md — OrzMC 插件仓库统一 AI 协作指引

> **本文件是仓库 AI 指引的单一事实源**（AGENTS.md 开放标准，Claude Code / OpenAI Codex / Gemini CLI / Cursor / Cline 等主流工具均自动读取）。
> `CLAUDE.md`、`GEMINI.md`、`.cursor/rules/` 仅为各工具入口桥接，**只引用本文件，不重复内容**——新增/修改指引只改这里。
> 详细实战案例（Folia/LuckPerms 红线背景、修复时间线、测试方法论）见 `docs/dev/folia-luckperms-gotchas.md`。

## 项目是什么

**PaperMC 服务端插件**（支持 Paper + Folia）——通过 EasyBot 网关统一接入多平台机器人（QQ / Telegram / Discord / 飞书 / 微信），具备白名单管理、跨服传送门、TNT 防护、GeoIP 区域限制、权限链（LuckPerms track）与玩家审核晋升等功能。

## 构建与测试命令

```bash
./gradlew spotlessApply            # 自动格式化 Java 代码（Palantir 风格）
./gradlew spotlessCheck            # 格式检查（CI 门禁）
./gradlew test                     # 运行单元测试（JUnit 5 + Mockito）
./gradlew integrationTest          # 运行集成测试（MockBukkit，需要 Java 25）
./gradlew check                    # 完整 CI 门禁：spotless + test + integrationTest + shadowJar
./gradlew clean build              # 全量构建 + shadowJar
./gradlew runServer                # 启动本地 Paper 调试服务器（Java 25）
./gradlew runFolia                 # 启动本地 Folia 调试服务器
./gradlew foliaSmoke               # 真实 Folia 无头冒烟：启动 → 校验插件加载 → 干净退出
./gradlew :orzmc-api:build         # 仅构建 orzmc-api 子模块（纯 Java，无 Bukkit 依赖）
./gradlew :orzmc-api:publishToMavenLocal  # 本地发布 orzmc-api SDK
```

注意：CI 强制使用 Java 25。如果默认 JDK 版本较高，请通过 `JAVA_HOME=/path/to/jdk25 ./gradlew ...` 指定。
**提交前必须 `./gradlew spotlessApply && ./gradlew test` 全绿。**

## 架构概览

### 模块结构（Gradle 多模块）

```
OrzMC/
├── orzmc-api/              ← 纯 Java，零 Bukkit 依赖（3 个端口 + 消息模型）
│   └── src/main/java/.../orzmc/
│       ├── core/bot/           BotInboundHandler, MessageEnvelope
│       ├── core/ports/health/  HealthStatus（只读健康查询接口）
│       ├── core/ports/server/  ServerLogger, ServerScheduler（调度抽象）
│       └── assembly/           ServiceModule, Initializable（生命周期契约）
│
├── src/main/java/.../orzmc/   ← 主模块（platform，包含全部业务逻辑）
│   ├── OrzMC.java              插件入口（继承 JavaPlugin）
│   ├── OrzServices.java        组合根（装配 5 个领域模块）
│   ├── assembly/               领域模块：
│   │   ├── PlatformModule.java     配置、服务端门面、样式、限流
│   │   ├── BotModule.java          EasyBot 机器人、通知派发、消息路由
│   │   ├── PortalModule.java       跨服传送门
│   │   ├── MaintenanceModule.java  世界备份与地图优化
│   │   └── FeatureModule.java      所有 Feature 服务 + 命令/事件注册（Brigadier 命令注册 + 拦截器链）
│   ├── core/ports/            含 Bukkit 依赖的端口（PortalPort, ServerAccess, TypedConfigProvider 等）
│   ├── features/              业务逻辑层
│   │   ├── botcommands/       Bot 命令解析（$l/$w/$h/$a/$r/$b/$o/$e/$d/$v/$p 共 11 个，统一分派 + $cmd ? 查询）
│   │   ├── maintenance/       世界备份/优化编排
│   │   ├── whitelist/         服务器白名单管理
│   │   ├── tnt/               TNT 保护 + 区域白名单
│   │   ├── portal/            传送门业务逻辑
│   │   ├── security/          GeoIP 访问控制 + IP 黑名单管理
│   │   ├── rank/              权限链（default→member→builder→admin）：自动晋升 + 手动升降级（LP track）
│   │   ├── review/            通用审核框架（/apply 申请 / /review 审核 / $v 群指令）
│   │   ├── server/            服务端生命周期事件
│   │   └── ...                guide, menu, teleport, player, bot
│   ├── infra/                 基础设施实现
│   │   ├── config/            ConfigService + 类型化配置记录类（20个，含 EasyBotConfig）、ConfigHealthCheck
│   │   ├── bot/               BotMessageService, BotMessageServiceProvider, OrzEasyBot
│   │   ├── ws/                RobustWebSocketClient（自动重连 + 心跳检测）
│   │   ├── net/               AsyncHttp（指数退避重试）
│   │   ├── scheduler/         SafeScheduler（异步异常日志包装器）
│   │   └── ...                templates, paging, styles, health, binding
│   ├── commands/              Bukkit CommandExecutor 适配器（仅 OrzConfigCommand，其余已内联至 Brigadier 注册）
│   └── events/                Bukkit EventListener 适配器（玩家加入、TNT、传送门等）
```

### 核心架构模式

- **六边形架构（Ports & Adapters）**：`core/ports/` 定义接口，`infra/` 实现，`features/` 编排业务
- **手工依赖注入**：`OrzServices.assemble()` 是显式组合根，不使用 DI 框架
- **模块生命周期**：每个领域模块实现 `ServiceModule { setup(); tearDown(); }` 接口
- **循环依赖处理**：`BotModule` 实现 `Initializable.afterPropertiesSet()` 处理跨模块回引用
- **命令拦截器**：责任链模式（`guardedExec()` 包装 Brigadier `Command` 执行体，运行时按序检查 `CommandInterceptor` 链）
- **通知策略**：策略模式（`NotifierSink` 接口，测试中可用 `CapturingSink`）

### 版本号与发布规则

| 事件 | 版本号格式 | Hangar Channel | Modrinth Type | 目标 |
|------|-----------|---------------|---------------|------|
| Push → main | `{version}-dev.{GITHUB_RUN_NUMBER}` | beta | beta | Dev 快照 |
| Push tag `1.0.0` | `{version}`（纯 SemVer） | release | release | 正式发布 + GitHub Release |

Tag 使用严格 SemVer，**不加 `v` 前缀**。本地构建产物为 `{version}-dev`，PR 构建产物为 `{version}-pr.{PR}.{RUN}`。

### 关键设计决策

- **无数据库**：所有状态存储在 YAML 配置文件中（portals.yml 在运行时修改）
- **无 DI 框架**：通过显式组合根进行构造器注入
- **类型化配置**：所有 YAML 访问通过 `configs/` 子包中的记录类（20 个，含 `WhitelistConfig`, `TntConfig`），附带健康检查
- **异步安全**：`SafeScheduler` 包装 Bukkit 调度器，统一异常日志
- **健康注册表**：`HealthStatus` 接口在 orzmc-api 中，`HealthAccessor` 适配器桥接实例化的 `HealthRegistry`

## 开发红线（2026-08-19 实战教训，AI 迭代必读）

### Folia 线程模型（最高优先级）

- **服务器调度线程（global/region）绝不能同步等待 LuckPerms 的异步 future**（`loadUser`/`saveUser`/`track.promote` 的 `.get()/.join()`）：LP 的 future 完成回调调度回服务器同步线程执行——在 global/region 线程上同步等待等于回调排在自己后面，必自锁（实测：修复前 132s 死锁卡服；转 global 线程后仍 3s 超时）。
- **授权处理（promote/demote）必须异步化**：`ReviewHandler` 返回 `CompletableFuture<Boolean>`，LP 操作在**自己管理的异步线程**执行，审核框架异步等待结果后再落状态。服务器线程「能不能不等」是修线程问题第一问。
- **状态一致性**：授权结果与业务状态必须原子一致（LP 已晋升 + 申请仍 PENDING = 漂移，重复 approve 会越级晋升）。
- 用 `ServerFacade.runSync`（Folia GlobalRegionScheduler / Paper 主线程），勿用 removed 的 BukkitScheduler；嵌套 runSync 在已处同步线程时直接内联（`Bukkit.isGlobalTickThread()`）。
- 读路径（查当前组等）优先 `um.getUser(uuid)` 在线缓存（不阻塞、不调度）；仅离线加载才转异步。
- `done.join()` 必须带超时（`done.get(3s)`），否则调度器停摆时调用线程永久挂起。

### LuckPerms 集成

- track 节点必须创建/查询在 **global 上下文**（`ImmutableContextSet.empty()`），在线/离线上下文混存 → `AMBIGUOUS_CALL`（promote/demote 报歧义）。
- `promote/demote` 只改内存态，必须显式 `saveUser` 落库（失败视为操作失败）；操作前 `normalizeSingleGroup` 归一组。
- LP 命令经 RCON **无回显**，验证走日志或实际行为。
- 新增 LP 逻辑先看 `LuckPermsPromoterTest` 的 mock 范式。

### 群消息通知（防刷屏）

- **高频事件必须节流/聚合**：whitelist_block 曾 48 次拦截 → 48 条消息 → QQ 主动消息频控（40034100）被打爆。上下线走 3s 聚合（player_notify.window_ms），TNT 走 notify_aggregate_ms，其余用 `ThrottledNotifier`。
- `ThrottledNotifier.shouldRun` 判定+更新必须原子（`ConcurrentHashMap.compute`），多 region 线程并发时 check-then-act 有竞态。
- 限频 key 防「换马甲刷」用**全局 key**（per-player key 会放行每马甲一条）；玩家侧提示不受节流影响。
- 新增通知类型先评估触发频率：单事件直发 → 需限频/聚合。

### 审核/命令/测试

- `/apply` 资格预检（isEligible）：builder 申请要求当前组 member，default 组返回「当前没有可申请的审核类型」。
- AuthMe 登录完成前玩家命令被**静默拦截**（bot 自动化测试须等 spawn + /login 完成）。
- orzdebug 控制台命令**只回显日志、不发群**，无法模拟群用户命令。
- 测试服验证方法论（bot 脚本、RCON、投递查询、重启流程）见 `docs/dev/folia-luckperms-gotchas.md` §6。

## 多 AI Agent 协作约定（不同厂商工具交叉使用）

本仓库支持 Claude Code / Codex / Gemini CLI / Cursor / Cline 等**任意厂商 AI agent 工具**协作迭代。共同遵守：

1. **单一事实源**：所有仓库指引只维护本文件（AGENTS.md）。`CLAUDE.md` / `GEMINI.md` / `.cursor/rules/` 是桥接入口，内容一律引用本文件。修改指引只改 AGENTS.md；详细案例写 `docs/dev/` 并被本文件引用。
2. **动手前必读**：任何 agent 在改代码前，先读 AGENTS.md（自动加载）+ 涉及模块的 `docs/` 文档；不熟悉 Folia 线程/LP 集成必须先读 `docs/dev/folia-luckperms-gotchas.md`。
3. **开发流程**（仓库级规范，所有 agent 遵守）：
   - main 分支冻结：开发一律在 `feature/<主题>` 或 `fix/<主题>` 分支，经 PR 合并，**禁止直接 push main**。
   - CI 门禁：PR 必须 CI 绿（`./gradlew check`：spotless + test + integrationTest + shadowJar）。
   - 本地提交前：`./gradlew spotlessApply && ./gradlew test` 全绿。
   - 提交信息遵循仓库现有风格（conventional commits，如 `fix(folia): ...` / `feat(portal): ...` / `docs: ...`），中文描述。
   - 合并规范：squash merge；PR 落后 main 先 rebase + force push。
4. **多工具分工建议**（可按需组合，无强制）：
   - 实现/重构：任一工具均可（Claude Code / Codex / Gemini CLI）
   - 代码审查：建议换一个工具做（不同厂商模型视角互补），审查输出记录到 PR 评论区
   - 测试补齐：可并行给另一个 agent（如 Cursor 补测试用例）
   - 接力原则：上一棒把上下文写进 PR 描述/提交信息/任务文件（如 `/tmp/task.md`），下一棒以「验证 + 完成剩余」为主，**不要重写已确认的代码**
5. **文档纪律**：
   - 行为/配置/权限变更必须同步文档（README、docs/、`docs/features.md` 玩家指南等），与代码同 PR。
   - 新增实战教训（踩坑）沉淀到 `docs/dev/` 并同步本文件红线；本文件只放精炼红线，细节进 docs。
   - CHANGELOG.md 按仓库惯例更新。
6. **测试服验证**：涉及事件/通知/审核流程的改动，按 `docs/dev/folia-luckperms-gotchas.md` §6 在 Folia 测试服真机验证后再合并。

## AI 迭代成本控制（2026-08-26 实战教训，必读）

大 PR / 多轮评审 / 长会话的 token 消耗会指数增长：整文件读进上下文后**后续每个回合都重复计费**；一轮全量 code review、一次全量 1371 用例测试都是几十万 token。任何 agent 在长迭代中都遵守以下红线，避免项目维护成本爆炸：

1. **评审分级，默认少评**：改动 < 100 行用 `git diff` 本地自查即可；大改动先明确「审查范围 + 维度」（如只审安全/并发）再派评审；全量 code review 默认 ≤ 2 轮，每轮开始前先问需求方「是否继续」。换厂商 agent 交叉评审只用于安全/线程/资金相关高风险改动。
2. **需求一次给齐**：偏好/默认值/边界（如「默认 fail-close、告警不刷屏」）必须在动手前确认。「先做后改」= 已做工作推倒重验，成本翻倍——默认不做，除非需求方明确要求探索式开发。
3. **测试分层**：中途只跑受影响测试类（`./gradlew test --tests <类名>`）；`spotlessApply && test` 全量只在提交前跑一次。每轮全量跑一遍与改动无关的用例是纯浪费。
4. **上下文瘦身**：不整文件读进主对话——`grep` 定位 + 片段读取；大范围扫描派 Explore/子代理只回结论，不把文件灌进主上下文。读进上下文的内容在后续每个回合重复计费，是最隐蔽的成本来源。
5. **大 PR 拆小**：单 PR 增删目标 < ~500 行；同一主题拆多个顺序小 PR，评审面小成本小，也便于合并与回滚。
6. **合并授权前置**：需求方在开始时说清「修完即合 / 每轮确认 / 等 CI」，agent 不再每步往返确认。
7. **成本透明 + 可喊停**：长会话/多轮评审中 agent 每轮主动报累计规模；需求方随时可喊「直接合」，agent 应立即停止迭代只做收尾（合并 PR #226 时用户中途喊停即正确干预）。

## 完整案例与修复时间线

详见 `docs/dev/folia-luckperms-gotchas.md`（Folia/LP 红线根因分析、正确异步模式、f0fbe1b/8000f2f/bf2f588 三阶段修复教训、测试服验证方法论）。
