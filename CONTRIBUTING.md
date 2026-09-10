# 贡献指南

欢迎参与 OrzMC 插件的开发。请花几分钟阅读以下规范。

## 环境要求

- **Java 25**（CI 与 integrationTest 固定使用 Java 25；如果默认 JDK 版本较高，通过 `JAVA_HOME=/path/to/jdk25 ./gradlew ...` 指定）
- 推荐 IDE：**IntelliJ IDEA** + [Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)
- 构建工具：Gradle（使用项目自带的 Wrapper）

## 分支策略

采用双轨三支模型（**完整规范见 [AGENTS.md](AGENTS.md) 的「开发工作流与发布」**）：

```text
main（冻结）── 仅 owner 验收的里程碑（develop→main）与批准的热修复
develop（默认分支）── 日常开发集散地；PR 全走这里；永不直接发布
feature|fix|hotfix/<主题> ── 临时分支，PR 合并后删除
```

| 场景 | 拉分支基线 | PR base | 合并方式 | 合并后发布 |
|------|-----------|---------|---------|-----------|
| 新功能 / 缺陷修复 | `origin/develop` | `develop` | squash | 不发布 |
| 热修复（需 owner 批准） | `origin/main` | `main` | squash（owner 手动） | 1 个 beta |
| 里程碑发布 | —（PR head = develop） | `main` | squash（CI 绿自动） | 含代码改动 → 1 个 beta |

- **默认分支是 `develop`**：新建 PR 请确认 base = `develop`；`main` 只承载「已验收、准备发布」的内容
- **禁止直接 push `main` / `develop`**：两个分支都有分支保护（PR + 必选检查 + 禁强推），一切改动经 PR
- **纯文档 / CI 改动默认也走 `develop`**：合入 `main` 时会被 `publish.yml` 的 paths-ignore 跳过，不产生 beta

## 分支命名

| 前缀 | 用途 |
|------|------|
| `feature/<主题>` | 新功能 |
| `fix/<主题>` | 缺陷修复 |
| `hotfix/<主题>` | 紧急修复（从 `origin/main` 拉，需 owner 批准） |
| `refactor/<主题>` | 代码重构 |
| `docs/<主题>` | 文档 |
| `chore/<主题>` / `ci/<主题>` | 构建 / 配置 / 依赖 / CI |

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

分支模型决定版本产物（完整流程见 [AGENTS.md](AGENTS.md)，平台侧操作与故障排查见 [发布平台运维手册](docs/publishing-platforms.md)）：

| 事件 | 版本号格式 | Hangar Channel | Modrinth Type | 目标 |
|------|-----------|---------------|---------------|------|
| Push `develop` | — | 不发布 | 不发布 | — |
| Push `main`（含代码改动） | `{version}-dev.{GITHUB_RUN_NUMBER}` | beta | beta | Dev 快照 |
| Push tag `1.0.0` | `{version}`（纯 SemVer，**无 `v` 前缀**） | release | release | 平台 Release + GitHub Release |

- `main` 只在里程碑（develop→main）或批准的热修复时前进，一次里程碑 ≈ 1 个 beta；beta 应对应「验收过的功能集」
- 纯文档 / CI / 流程改动（`docs/**`、`*.md`、`.github/**` 等）合入 `main` **不发版**
- tag 发布前会跑完整 `./gradlew check`；发布后 CI 自动 bump `paper-plugin.yml`（以 PR 合回 main，见发布手册 §5.5）

## PR 流程

1. 从 `origin/develop` 拉临时分支（一步完成，不碰工作区）：
   ```bash
   git fetch origin --prune && git checkout -b feature/<主题> origin/develop
   ```
2. 在本地完成开发和测试：`./gradlew spotlessApply && ./gradlew test` 全绿
3. 提交 PR，**base 选 `develop`**（热修复才选 `main`，且需 owner 批准）
4. CI 自动运行：`spotlessCheck` → `test` → `integrationTest` → `shadowJar`，以及真实 Folia 启动的 `folia-smoke`
5. 等待 review / CI 绿（`main`、`develop` 均要求 build + folia-smoke 必选检查）
6. **Squash merge 到 `develop`**，合并后删除临时分支

> 需跨分支操作时先提交或 stash——仓库有过「改完未提交就切换分支导致改动被 reset 吞掉」的教训（见 AGENTS.md 铁律）。

## 问题反馈

- Bug 报告和建议请通过 [GitHub Issues](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose) 提交
- 其他问题可通过项目主页的 QQ 频道联系

## 相关链接

- [PaperAPI 文档](https://papermc.io/javadocs)
- [SpigotAPI 文档](https://hub.spigotmc.org/javadocs/spigot/)
- [Bukkit Wiki](https://bukkit.fandom.com/wiki/Main_Page)
- [Adventure TextComponent](https://docs.adventure.kyori.net/text.html#creating-components)
