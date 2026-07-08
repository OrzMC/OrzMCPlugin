# 贡献指南

欢迎参与 OrzMC 插件的开发。请花几分钟阅读以下规范。

## 环境要求

- **Java 25**（CI 与 integrationTest 固定使用 Java 25；如果默认 JDK 版本较高，通过 `JAVA_HOME=/path/to/jdk25 ./gradlew ...` 指定）
- 推荐 IDE：**IntelliJ IDEA** + [Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)
- 构建工具：Gradle（使用项目自带的 Wrapper）

## 分支策略

- **`main`** 是唯一的永久分支，同时也是发版分支
- 所有开发分支从 `main` 创建，PR 合入 `main`
- PR 目标分支仅限 `main`

## 分支命名

| 前缀 | 用途 |
|------|------|
| `feat/*` | 新功能 |
| `fix/*` | Bug 修复 |
| `refactor/*` | 代码重构 |
| `chore/*` | 构建/配置/依赖变更 |
| `docs/*` | 文档 |

## 提交规范

- 一类改动一个提交，避免跨模块混杂
- 提交信息简明扼要，说明改动内容和动机

## 常用命令

```bash
./gradlew spotlessApply            # 自动格式化代码（Palantir 风格）
./gradlew spotlessCheck            # 代码格式检查
./gradlew test                     # 运行单元测试（JUnit 5 + Mockito）
./gradlew integrationTest          # 运行集成测试（MockBukkit，需要 Java 25）
./gradlew check                    # 完整 CI 门禁：spotless + test + integrationTest + shadowJar
./gradlew clean build              # 全量构建 + shadowJar
./gradlew runServer                # 启动本地 Paper 调试服务器
```

配置与模板的冒烟测试会直接读取 resources 下的默认配置，确保类型化映射与模板解析可用。
集成测试会在 MockBukkit 环境中执行命令与事件链路，同时对默认配置进行健康检查与模板变量校验。

## 设计与实现原则

- **新能力优先落在服务层**；入口层（Events/Commands）仅做参数转发
- **新依赖通过构造注入**，由 `OrzServices` 统一装配，不新增静态全局依赖，避免隐式状态
- **关键流程需补齐日志与通知事件**（如维护、玩家上下线）

## 配置兼容

- 新配置先写入 `resources/` 默认配置文件，并在 `DefaultTypedConfigProvider` 中建立类型映射，透出到 `TypedConfigProvider`
- 旧配置废弃需给出迁移说明与默认兼容策略
- 变更需同步更新 README 文档中的功能与配置说明

## 版本发布

- 推送 Strict SemVer 标签（如 `1.0.0`，**无 `v` 前缀**）到 GitHub 自动触发 CI 构建并创建 GitHub Release
- 版本命名规则见下表：

| 事件 | 版本号格式 | Hangar Channel | Modrinth Type | 目标 |
|------|-----------|---------------|---------------|------|
| Push → main | `{version}-dev.{GITHUB_RUN_NUMBER}` | beta | beta | Dev 快照 |
| Push tag `1.0.0` | `{version}`（纯 SemVer） | release | release | 平台 Release + GitHub Release |

## PR 流程

1. 从 `main` 创建你的特性/修复分支
2. 在本地完成开发和测试（`./gradlew spotlessApply && ./gradlew build`）
3. 提交 PR 到 `main`
4. CI 自动运行：`spotlessCheck` → `test` → `integrationTest` → `shadowJar`
5. Maintainer Review
6. Squash merge 到 `main`

## 问题反馈

- Bug 报告和建议请通过 [GitHub Issues](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose) 提交
- 其他问题可通过项目主页的 QQ 频道联系

## 相关链接

- [PaperAPI 文档](https://papermc.io/javadocs)
- [SpigotAPI 文档](https://hub.spigotmc.org/javadocs/spigot/)
- [Bukkit Wiki](https://bukkit.fandom.com/wiki/Main_Page)
- [Adventure TextComponent](https://docs.adventure.kyori.net/text.html#creating-components)
