# Changelog

## [Unreleased]

---

## [1.0.12] - 2026-07-09

### 🐛 修复
- **管理命令控制台执行支持** — 移除 `blacklist` / `config` 命令的 `PlayerOnlyInterceptor`，允许控制台直接执行黑名单管理和配置热重载命令。

### ⚙️ CI/CD
- **BOT_PAT 直接推送** — CI 创建的个人访问令牌（PAT）直接推送到 `main` 分支，绕过仓库规则集限制，无需通过 PR 提交 bump commit。
- **Modrinth 重复版本检测** — 分页查询参数 `?limit=10000` 确保检查所有历史版本，避免因默认 10 条限制导致漏检重复版本号。
- **Pull Request 权限补全** — bump 版本工作流添加 `pull-requests: write` 权限，支持通过 `gh` CLI 创建和处理 PR。
- **移除 force-push** — bump 版本步骤先清理已存在的远程分支再正常推送，避免 `--force` 的安全风险。

---

## [1.0.11] - 2026-07-06

### 🚀 新功能
- **EasyBot IM 网关适配器** — 新增 `OrzEasyBot` 适配器，支持通过 EasyBot IM Gateway WebSocket 协议接入 IM 平台（QQ / Discord / Lark 等），配置于 `easybot.yml`。
- **`/bot` 命令 EasyBot 重连支持** — `/bot` 命令触发重连时，除 QQ Bot 外同时检查并重建 EasyBot 适配器的 WebSocket 连接。

### 🐛 修复
- **BotReconnectionManager 异常处理** — `tryReconnectIfDisconnected` 中 `onReconnect.run()` 抛出的异常不再阻断后续重连逻辑，正确记录重连状态。
- **BotReconnectionManager 测试修复** — `tryReconnect_qqDisabled_doesNothing` 测试修正为验证 `enable_qq_bot=false` 时方法提前返回行为。

### 📝 文档
- 新增 飞书 WebSocket 多实例限制说明及 EasyBot 网关平台信息

### ⬆️ 依赖升级
- `net.dv8tion:JDA: 6.4.2` → `6.5.0`
- `org.mockbukkit.mockbukkit:mockbukkit-v26.1.2` → `4.114.0`
- `com.diffplug.spotless: 8.7.0` → `8.8.0`
- `codecov/codecov-action: 5` → `7`

---

## [1.0.10] - 2026-07-05

### 🐛 修复
- **bump 版本回退 PR 方式** — 由于 main 分支有 branch protection 规则，bump 版本改为通过 PR 方式提交，直接推送到 main 会因保护规则被拒绝。

### ⚙️ CI/CD
- 版本号递增至 1.0.10

---

## [1.0.9] - 2026-07-05

### 🐛 修复
- **Modrinth 版本号唯一性检查** — 修复 Modrinth 发布因版本号重复失败的问题，bump 版本改为直接推送到 main（绕过 PR 限制）。

### ⚙️ CI/CD
- 版本号递增至 1.0.9

---

## [1.0.8] - 2026-07-05

### 🚀 新功能
- **Modrinth 自动发布** — 集成 Minotaur Gradle 插件，CI 支持自动发布到 Modrinth 平台，与 Hangar 对称的重试/幂等策略。
- **项目图标** — 添加 `assets/avatar.png` 项目图标，并嵌入 README 标题。
- **orzmc-api 模块独立测试** — 为 orzmc-api 纯 Java 模块补充独立测试套件。
- **README 自动同步** — 发布时自动将 README.md 同步至 Modrinth 和 Hangar 项目页面。

### 🔧 重构
- **统一 SemVer 版本号** — Hangar 与 Modrinth 统一使用标准 SemVer 版本号格式，消除两套不兼容的版本字符串。
- **代码质量提升** — JaCoCo 覆盖率阈值提升，新增 Codecov 集成，修复 6 项代码质量问题。

### 🐛 修复
- **server_maintenance_hint 模板顺序** — 交换 MOTD 与提示信息顺序，MOTD 显示在上方。
- **bump-version 失败处理** — 当 PR 创建失败时正确退出而非静默继续。
- **CI 触发修复** — push 事件跳过 PR comment 步骤，避免空操作报错。

### 📝 文档
- 新增 `docs/features.md`（完整的插件功能清单文档，14 个模块详细说明）
- 新增 `docs/publishing-platforms.md`（发布平台运维手册，含 Hangar / Modrinth 配置、Token 管理、发布检查清单）
- 合并 CONTRIBUTING.md、docs/development.md、docs/governance.md 为统一贡献指南
- 清理 images/ 中 4 个废弃文件（architecture.png、architecture.mmd、gradle_build_guide.png、puppeteer.json）
- 更新 publishing-platforms.md（完整重写，对齐当前发布配置）
- README.md 持续更新（功能表格、项目图标、贡献链接）

### 📄 许可
- 添加 GPL-3.0 开源许可证

### ⚙️ CI/CD
- bump-version 步骤在 PR 创建失败时正确退出而非继续执行
- 为 main 分支添加 push 触发构建

### ⬆️ 依赖升级
- `backup-core: 0.1.5` → `0.1.6`

---

## [1.0.7] - 2026-07-03

### ⚡ 性能优化
- **CI 工作流优化** — 去重测试执行、减少 `clean` 的过度调用、添加 Gradle 缓存、合并 release 流程到 publish 工作流。

### 🐛 修复
- **Hangar 发布重试** — 添加指数退避重试逻辑（3 次，20s / 40s / 60s），处理 504 Gateway Timeout。
- **bump 版本 PR 权限** — 为 bump 步骤添加 pull-requests 写入权限。
- **bump 分支处理** — force-push bump 分支，支持已有 PR 时自动跳过。
- **CI 门禁修复** — publish.yml 补全 write 权限声明。

### ⚙️ CI/CD
- 合并 release.yml 到 publish.yml，统一发布流程
- Add Gradle caching to speed up CI builds

---

## [1.0.6] - 2026-07-03

### ⚙️ CI/CD
- 修复 publish workflow 版本号和环境变量问题
- 调整 CI 触发条件

---

## [1.0.5] - 2026-07-03

### ⚙️ CI/CD
- 版本号递增（无代码逻辑变更）

---

## [1.0.4] - 2026-07-03

### ⚙️ CI/CD
- **移除阿里云 Maven 镜像** — 解决国内 CI 因阿里云镜像 502 导致的构建阻断，恢复从 Maven Central 直接拉取依赖。

---

## [1.0.3] - 2026-07-03

### 🐛 修复
- **deprecation / removal 警告** — 解决 Paper API 废弃方法和已移除方法的编译警告。
- **orzmc-api Javadoc** — 补充公开 API 面缺失的 Javadoc 注释，修复 Javadoc 构建警告。
- **CI bump 分支** — publish workflow bump 分支改为从 `origin/main` 创建，避免本地分支状态滞后导致 PR 冲突。

---

## [1.0.2] - 2026-07-03

### 🐛 修复
- **IP 黑名单持久化** — 修复 IP 黑名单在服务器重启后为空的问题（BlacklistService 加载逻辑修正）。
- **CI bump 版本 PR** — 发布后 bump 版本因分支保护规则导致 CI 失败，改为通过 PR 方式提交 bump commit。

### ⚙️ CI/CD
- publish.yml 增加 permissions 显式声明

---

## [1.0.1] - 2026-07-02

### 🚀 新功能
- **IP 黑名单机制** — 新增 `/blacklist` 命令（别名 `/bl`）和 `$d` 机器人命令，支持添加/移除/查看 IP 地址黑名单，匹配的玩家将被禁止加入服务器。黑名单存储于 `config.yml` → `ip_blacklist` 段。
- **Bot 命令统一分派** — 重构 `BotCommandService`，消除三条代码路径分叉，所有 `$cmd` 指令（`$a`, `$r`, `$b`, `$o`, `$e`, `$l`, `$w`, `$h`, `$d`）统一经 `parse()` 方法分派。
- **`$cmd ?` 查询指令用法** — 支持在 Bot 命令后加 `?`（或 `？`）查询该命令的具体用法说明（如 `$a ?`）。
- **指令帮助信息（游戏内）** — 使用 Brigadier 直接注册命令后，游戏内命令帮助（`/help`）正确显示参数结构，不再显示多余的 `[args]` 标记。
- **世界目录结构文档** — 新增 `docs/world-directory-structure-comparison.md`，详细对比旧版 Paper、新版 Paper（26.1+）和 Vanilla 的世界目录结构差异。

### 🔧 重构
- **命令注册迁移** — 从旧的 `CommandMap API` + `CommandBinder` 迁移到 Paper 26.1 官方 `LifecycleEvents.COMMANDS` + Brigadier `LiteralCommandNode` 注册。Tab 补全支持 subcommand 名称自然提示。
- **死代码清理** — 删除未使用的命令类（`OrzBotStatus`, `OrzGuideBook`, `OrzMenuCommand`, `OrzPortalCommand`, `OrzTPBow`）、命令绑定类（`CommandBinder`, `BasicCommandAdapter`, `TabCompleterDelegate`）和拦截器执行器（`InterceptorExecutor`），共删除 444 行死代码。
- **配置重命名** — `whitelist.kick_message.player_group_id` → `whitelist.kick_message.qq_group_id`（兼容旧 key，自动读取）。

### 🐛 修复
- **`WorldMaintenanceService.backup` dryRun 参数修复** — 备份功能 dry-run 模式因参数传递错误失效的问题。
- **Brigadier 命令帮助信息显示** — 修复 `[args]` 在帮助中错误显示的问题，改为干净的无参数 literal。
- **本地开发版本号修复** — 改为固定 `{version}-dev`，移除时间戳后缀，避免 CI 产物冲突。

### ⬆️ 依赖升级
- `backup-core: 0.1.4` → `0.1.5`

### 📝 文档
- 新增 `docs/world-directory-structure-comparison.md`
- README.md、架构文档持续更新

### ⚙️ CI/CD
- Release 成功后自动递增 patch 版本号并提交到 main
- PR 产物调整为 `-pr-#{PR_NUMBER}-{run_number}` 格式

---

## [1.0.0] - 2025-07-26

### 🚀 初始发布
OrzMC 插件首次正式发布，支持 PaperMC 服务器。

### 核心功能
- **多平台 Bot 系统** — 集成 QQ（WebSocket/NapCatQQ）、Discord（JDA）、飞书（Webhook）三端
- **白名单管理** — 强制白名单、Bot 命令远程增删（`$a` / `$r` / `$w`）、不活跃玩家自动清理
- **跨服传送门** — `/portal` 命令创建/移除 4×5 黑曜石传送门，跨服 transfer 跳转
- **TNT 保护** — 爆炸防护 + 区域白名单 + 放置冷却 + 爆炸群聊通知
- **安全控制** — GeoIP 国家限制访问、IP 黑名单（精确/CIDR/通配符）
- **传送弓** — `/tpbow` 获得传送弓，射箭传送 + 安全落点检测
- **世界维护** — `$b` 一键备份、`$o` 地图优化、维护模式 MOTD
- **玩家通知** — 加入/退出/踢出推送（含坐标、世界、角色信息）
- **新手指南书** — 首次进服自动发放，YAML 配置内容
- **运行时配置** — `/config` 命令热重载 24 项配置，无需重启
- **Bot 命令** — `$l` 在线查询、`$h` 帮助、`$e` 执行控制台、`$d` 黑名单管理

### 🔧 架构
- 六边形架构（Ports & Adapters），手工 DI
- 多模块拆分（orzmc-api + platform）
- 命令拦截器链（PlayerOnly / AdminOnly / Cooldown）
- 8 个通知模板 + 变量替换系统

### 📝 文档
- 完整 README、架构文档、配置说明
- 目录结构对比文档
- CLAUDE.md 项目指引

### ⚙️ CI/CD
- GitHub Actions 构建/测试/发布流水线
- Hangar 平台自动发布（Snapshot + Release）
- Tag 驱动版本发布（严格 SemVer）
- Dependabot 自动依赖升级
