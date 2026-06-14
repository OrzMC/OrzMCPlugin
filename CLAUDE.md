# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供此仓库的指引信息。

## 构建与测试命令

```bash
./gradlew spotlessApply            # 自动格式化 Java 代码（Palantir 风格）
./gradlew spotlessCheck            # 格式检查（CI 门禁）
./gradlew test                     # 运行单元测试（JUnit 5 + Mockito）
./gradlew integrationTest          # 运行集成测试（MockBukkit，需要 Java 21）
./gradlew check                    # 完整 CI 门禁：spotless + test + integrationTest + shadowJar
./gradlew clean build              # 全量构建 + shadowJar
./gradlew runServer                # 启动本地 Paper 调试服务器（Java 21）
./gradlew :orzmc-api:build         # 仅构建 orzmc-api 子模块（纯 Java，无 Bukkit 依赖）
./gradlew :orzmc-api:publishToMavenLocal  # 本地发布 orzmc-api SDK
```

注意：CI 强制使用 Java 21。如果默认 JDK 版本较高，请通过 `JAVA_HOME=/path/to/jdk21 ./gradlew ...` 指定。

## 架构概览

**PaperMC 服务端插件** — 集成 QQ/Discord/Lark 机器人，具备白名单管理、跨服传送门、TNT 防护、GeoIP 区域限制等功能。

### 模块结构（Gradle 多模块）

```
OrzMC/
├── orzmc-api/              ← 纯 Java，零 Bukkit 依赖（7 个端口 + 消息模型）
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
│   │   ├── BotModule.java          QQ/Discord/Lark 机器人、通知派发
│   │   ├── PortalModule.java       跨服传送门
│   │   ├── MaintenanceModule.java  世界备份与地图优化
│   │   └── FeatureModule.java      所有 Feature 服务 + 命令/事件注册
│   ├── core/ports/            含 Bukkit 依赖的端口（PortalPort, ServerAccess 等）
│   ├── features/              业务逻辑层（36 个文件）
│   │   ├── botcommands/       Bot 命令解析（$a, $r, $b, $o 等）
│   │   ├── maintenance/       世界备份/优化编排
│   │   ├── whitelist/         服务器白名单管理
│   │   ├── tnt/               TNT 保护 + 区域白名单
│   │   ├── portal/            传送门业务逻辑
│   │   ├── security/          GeoIP 访问控制、命令权限
│   │   ├── server/            服务端生命周期事件
│   │   └── ...                guide, menu, teleport, player
│   ├── infra/                 基础设施实现
│   │   ├── config/            ConfigService, TypedConfigs, ConfigHealthCheck
│   │   ├── bot/               OrzBotManager, OrzQQBot, OrzDiscordBot, OrzLarkBot
│   │   ├── notify/            Notifier + ThrottledNotifier
│   │   ├── ws/                RobustWebSocketClient（自动重连 + 心跳检测）
│   │   ├── net/               AsyncHttp（指数退避重试）
│   │   ├── scheduler/         SafeScheduler（异步异常日志包装器）
│   │   └── ...                templates, paging, styles, health, binding
│   ├── commands/              Bukkit CommandExecutor 适配器（/bot, /portal, /tpbow 等）
│   └── events/                Bukkit EventListener 适配器（玩家加入、TNT、传送门等）
```

### 核心架构模式

- **六边形架构（Ports & Adapters）**：`core/ports/` 定义接口，`infra/` 实现，`features/` 编排业务
- **手工依赖注入**：`OrzServices.assemble()` 是显式组合根，不使用 DI 框架
- **模块生命周期**：每个领域模块实现 `ServiceModule { setup(); tearDown(); }` 接口
- **循环依赖处理**：`BotModule` 实现 `Initializable.afterPropertiesSet()` 处理跨模块回引用
- **命令拦截器**：责任链模式（`InterceptorExecutor` + `CommandInterceptor`）
- **通知策略**：策略模式（`NotifierSink` 接口，测试中可用 `CapturingSink`）

### 版本号与发布规则

| 事件 | 版本号格式 | 目标 |
|------|-----------|------|
| PR → main | `{version}-dev-{timestamp}` | PR 构建产物 |
| Push → main | `{version}-snapshot-{GITHUB_RUN_NUMBER}` | Hangar Snapshot |
| Push tag `1.0.0` | `{version}`（纯 SemVer） | Hangar Release + GitHub Release |

Tag 使用严格 SemVer，**不加 `v` 前缀**。

### 关键设计决策

- **无数据库**：所有状态存储在 YAML 配置文件中（portals.yml 在运行时修改）
- **无 DI 框架**：通过显式组合根进行构造器注入
- **类型化配置**：所有 YAML 访问通过 `TypedConfigs` 记录类型，附带健康检查
- **异步安全**：`SafeScheduler` 包装 Bukkit 调度器，统一异常日志
- **健康注册表**：`HealthStatus` 接口在 orzmc-api 中，`HealthAccessor` 适配器桥接静态 `HealthRegistry`
