# Changelog

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
