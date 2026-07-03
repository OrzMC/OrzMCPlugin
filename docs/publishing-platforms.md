# 发布平台运维手册

> OrzMC 插件发布平台统一信息源与运维指南
> 最后更新：2026-07-03

---

## 目录

1. [平台项目信息](#1-平台项目信息)
2. [统一项目描述](#2-统一项目描述)
3. [许可证](#3-许可证)
4. [README 页面同步](#4-readme-页面同步)
5. [Hangar 自动发布](#5-hangar-自动发布)
6. [Modrinth 自动发布](#6-modrinth-自动发布)
7. [Token 与配置管理](#7-token-与配置管理)
8. [发布检查清单](#8-发布检查清单)

---

## 1. 平台项目信息

| 字段 | 统一值 | Hangar | Modrinth |
|------|--------|--------|----------|
| **项目名称** | `OrzMC` | `OrzMC` | `OrzMC` |
| **项目页面** | — | [hangar.papermc.io/wangzhizhou666/OrzMC](https://hangar.papermc.io/wangzhizhou666/OrzMC) | [modrinth.com/plugin/r8ZufLjY](https://modrinth.com/plugin/r8ZufLjY) |
| **项目 ID** | — | slug: `wangzhizhou666/OrzMC`（插件内部 id 取自 `paper-plugin.yml: name`） | `r8ZufLjY`（定义在 `gradle.properties: modrinth_project_id`） |
| **简短描述** | OrzMC — 多平台机器人集成的 Paper 服务器管理插件 | 创建项目时填入 | 创建项目时填入 |
| **完整介绍** | 见[第 2 节](#2-统一项目描述) | `README.md` 同步（见[第 4 节](#4-readme-页面同步)） | `README.md` 同步 |
| **分类** | 服务端管理 / 工具 / 社交 | `admin_tools`, `dev_tools`, `chat` | `management`, `utility`, `social`（以平台实际可选项为准） |
| **许可证** | GPL-3.0 | GPL-3.0 | GPL-3.0 |
| **图标** | `assets/avatar.png`（89 KB） | 上传此文件 | 上传此文件 |
| **网站** | `https://orzmc.jokerhub.cn` | 填入项目设置 | 填入项目设置 |
| **支持平台** | Paper | `PAPER` | loader: `paper` |
| **Minecraft 版本** | `26.1.x` | `plugin_support_paper_versions`（`gradle.properties`） | 同左 |
| **JDK 版本** | 25 | — | — |
| **源码仓库** | `https://github.com/OrzMC/OrzMCPlugin` | 填入项目设置 | 填入项目设置 |
| **Issues** | `https://github.com/OrzMC/OrzMCPlugin/issues` | 填入项目设置 | 填入项目设置 |
| **讨论区** | QQ频道 `https://pd.qq.com/s/9zuis6m4v` | 填入项目设置 | 填入项目设置 |

> **注意**：平台特有的字段（如图标、分类、外链等）需在项目首次创建时手动填入。后续版本发布由 CI 全自动完成，无需人工干预。

---

## 2. 统一项目描述

### 2.1 简短描述（一行）

> OrzMC — 多平台机器人集成的 Paper 服务器管理插件

### 2.2 完整描述（~200 字）

> OrzMC 是一款面向 PaperMC 服务器的综合管理插件，深度集成 QQ、Discord、飞书三大社交平台，让管理员在群聊中即可完成白名单管理、世界备份与优化、IP 黑名单、跨服传送门等运维操作。支持 TNT 爆炸防护与 GeoIP 区域访问控制，保障服务器安全。插件采用六边形架构设计，模块化分离核心逻辑与平台适配层，配置驱动、零数据库依赖，适合个人服主与小型社区快速部署。无论你运营的是私服还是公开服务器，OrzMC 都能让你的日常管理更加高效便捷。

### 2.3 功能列表

| 功能模块 | 说明 |
|----------|------|
| 🤖 **多平台机器人** | QQ（NapCatQQ/OneBot）、Discord（JDA）、飞书（Webhook） |
| 📋 **白名单管理** | 强制白名单，Bot 命令 `$a` / `$r` 远程增删 |
| 💾 **世界备份与优化** | `$b` 备份、`$o` 优化、定时维护编排 |
| 🚪 **跨服传送门** | 可配置传送门，`/portal` 命令创建/移除 |
| 💥 **TNT 防护** | 爆炸保护 + 区域白名单 + 爆炸告警 |
| 🌍 **GeoIP 访问控制** | 按国家/地区限制登录 + IP 黑名单 |
| 🎨 **自定义样式** | 可定制的消息模板与通知样式 |
| 🔒 **维护模式** | 自定义 MOTD，维护期间拒绝普通玩家登录 |

---

## 3. 许可证

- **许可证**：GNU General Public License v3.0（GPL-3.0）
- **文件**：项目根目录 `LICENSE`
- **平台设置**：Hangar 和 Modrinth 项目设置中均选择 GPL-3.0
- **参考**：https://www.gnu.org/licenses/gpl-3.0.html

---

## 4. README 页面同步

两个平台均支持将 `README.md` 同步为项目主页内容。`README.md` 是唯一内容源，修改后通过 Gradle task 推送到各平台。

### 4.1 Hangar

**启用方式**：在 `build.gradle.kts` 的 `hangarPublish` 块中添加：

```kotlin
hangarPublish {
    publications.register("plugin") {
        // ... 现有配置 ...
        pages.resourcePage(project.file("README.md").readText())
    }
}
```

**同步命令**：

| Task | 说明 |
|------|------|
| `syncAllPluginPublicationPagesToHangar` | 同步 "plugin" publication 的所有注册页面 |
| `syncAllPagesToHangar` | 同步所有 publication 的所有注册页面 |

> **当前状态**：未启用。启用后如需 CI 自动同步，可在 `publish.yml` 中追加 `./gradlew syncAllPluginPublicationPagesToHangar`。

### 4.2 Modrinth

**启用方式**：在 `build.gradle.kts` 的 `modrinth` 块中添加：

```kotlin
modrinth {
    // ... 现有配置 ...
    syncBodyFrom.set(project.file("README.md").readText())
}
```

**同步命令**：`./gradlew modrinthSyncBody`

> **当前状态**：未启用。

### 4.3 内容约定

- `README.md` 为唯一内容源，修改后手动或 CI 触发同步
- 平台专属内容用 HTML 注释标记排除：
  ```markdown
  <!-- modrinth_exclude.start -->
  这部分不会同步到 Modrinth
  <!-- modrinth_exclude.end -->
  ```

---

## 5. Hangar 自动发布

### 5.1 当前状态

| 项目 | 详情 |
|------|------|
| **项目页面** | [hangar.papermc.io/wangzhizhou666/OrzMC](https://hangar.papermc.io/wangzhizhou666/OrzMC) |
| **Gradle 插件** | `io.papermc.hangar-publish-plugin:0.1.4` |
| **发布 task** | `publishPluginPublicationToHangar` |
| **触发条件** | Push `main` → Snapshot channel / Push tag `x.y.z` → Release channel |
| **Token Secret** | `HANGAR_API_TOKEN`（权限：`create_version`） |
| **重试策略** | 3 次，指数退避（20s / 40s / 60s），检测"版本已存在"幂等退出 |

### 5.2 Gradle 配置

```kotlin
// build.gradle.kts (lines 151-165)
hangarPublish {
    publications.register("plugin") {
        version = shadowJarVersion          // 自动拼接 snapshot/release/pr/dev 后缀
        channel = if (isRelease) "Release" else "Snapshot"
        changelog = changelogContent        // 最新 commit message
        id = pluginYaml["name"] as String   // "OrzMC"
        apiKey = System.getenv("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = (property("plugin_support_paper_versions") as String)
                    .split(",").map { it.trim() }
            }
        }
    }
}
```

### 5.3 CI 发布流程

`publish.yml` 中 Hangar 发布 step 的执行顺序：

1. `./gradlew shadowJar --stacktrace` — 构建产物（快速失败，不消耗 API 调用）
2. `./gradlew publishPluginPublicationToHangar --stacktrace` — 上传到 Hangar
3. 若失败 → 指数退避重试（最多 3 次）
4. 若返回 "already exists" → 幂等退出（上一次重试实际已成功）

### 5.4 版本号规则

| 触发场景 | `shadowJarVersion` | Hangar Channel | GitHub Release |
|----------|--------------------|----------------|----------------|
| PR 打开 | `{version}-pr-#{PR}-{run}` | 不发布 | 不发布 |
| Push `main` | `{version}-snapshot-{run}` | Snapshot | 不发布 |
| Push tag `x.y.z` | `{version}`（纯 SemVer） | Release | ✅ 创建 |
| 本地构建 | `{version}-dev` | 不发布 | 不发布 |

版本号源：`paper-plugin.yml` → `version` 字段（当前 `1.0.7`）。

---

## 6. Modrinth 自动发布

### 6.1 当前状态

| 项目 | 详情 |
|------|------|
| **项目页面** | [modrinth.com/plugin/r8ZufLjY](https://modrinth.com/plugin/r8ZufLjY) |
| **项目 ID** | `r8ZufLjY`（`gradle.properties: modrinth_project_id`） |
| **Gradle 插件** | `com.modrinth.minotaur:2.+` |
| **发布 task** | `modrinth` |
| **触发条件** | Push `main` → `beta` / Push tag `x.y.z` → `release` |
| **Token Secret** | `MODRINTH_TOKEN`（权限：`VERSION_CREATE` + `PROJECT_WRITE`） |
| **重试策略** | 3 次，指数退避（20s / 40s / 60s），检测"版本已存在"幂等退出 |
| **审核状态** | 审核中（审核通过前 API 可能无法创建版本） |

### 6.2 Gradle 配置

```kotlin
// build.gradle.kts (lines 167-180)
modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(
        System.getenv("MODRINTH_PROJECT_ID")
            ?: (property("modrinth_project_id") as String)
    )
    versionNumber.set(shadowJarVersion)
    versionName.set(shadowJarVersion)
    versionType.set(if (isRelease) "release" else "beta")
    changelog.set(changelogContent)
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(
        (property("plugin_support_paper_versions") as String)
            .split(",").map { it.trim() }
    )
    loaders.add("paper")
}
```

`projectId` 优先级：环境变量 `MODRINTH_PROJECT_ID` > `gradle.properties: modrinth_project_id`。本地构建使用 `gradle.properties` 中的值，CI 可通过 GitHub Variable 覆盖。

### 6.3 CI 发布流程

与 Hangar 完全对称的设计：

1. 复用 `shadowJar build` 产物（与 Hangar 共享同一次构建）
2. `./gradlew modrinth --stacktrace` — 上传到 Modrinth
3. 若失败 → 指数退避重试（最多 3 次）
4. 若返回 "already exists" → 幂等退出

### 6.4 待办

- [ ] 生成 Modrinth PAT，添加到 GitHub Secrets → `MODRINTH_TOKEN`
- [ ] 等待 Modrinth 项目审核通过

---

## 7. Token 与配置管理

### 7.1 凭证清单

| 凭证 | 存储位置 | 权限 | 用途 |
|------|---------|------|------|
| `HANGAR_API_TOKEN` | GitHub Secret | `create_version` | Hangar 版本发布 |
| `MODRINTH_TOKEN` | GitHub Secret | `VERSION_CREATE` + `PROJECT_WRITE` | Modrinth 版本发布 + 页面同步 |
| `MODRINTH_PROJECT_ID` | GitHub Variable（可选） | — | 覆盖 `gradle.properties` 中的项目 ID |

### 7.2 配置清单

| 配置项 | 文件 | 当前值 | 说明 |
|--------|------|--------|------|
| `plugin_support_paper_versions` | `gradle.properties` | `26.1` | 两个平台共用，逗号分隔 |
| `modrinth_project_id` | `gradle.properties` | `r8ZufLjY` | 本地默认值，CI 可通过变量覆盖 |
| `plugin_debug_server_version` | `gradle.properties` | `26.1.2` | 仅本地调试用 |
| `version` | `paper-plugin.yml` | `1.0.7` | 版本号源，tag 发布后自动递增 |

### 7.3 Token 轮换

| 平台 | 管理入口 | 操作 |
|------|---------|------|
| Hangar | [Hangar → Settings → Api keys](https://hangar.papermc.io/) | 删除旧 key → 创建新 key（勾选 `create_version`） → 更新 GitHub Secret |
| Modrinth | [Modrinth → Settings → PAT](https://modrinth.com/settings/pat) | 删除旧 token → 创建新 token（勾选 `VERSION_CREATE` + `PROJECT_WRITE`） → 更新 GitHub Secret |

---

## 8. 发布检查清单

### 每次版本发布后

- [ ] **Hangar**：对应 channel 出现新版本，JAR 可下载
- [ ] **Modrinth**：对应 version_type 出现新版本，JAR 可下载
- [ ] **GitHub Release**：tag 推送后自动创建（仅 `x.y.z` tag）
- [ ] **版本号 Bump PR**：自动创建并启用 auto-merge（仅 `x.y.z` tag）
- [ ] **平台页面一致**：版本号、Minecraft 兼容版本、描述信息三个平台一致

### 故障排查

| 症状 | 可能原因 | 排查步骤 |
|------|---------|---------|
| Hangar 发布 401/403 | Token 过期或权限不足 | 重新生成 API Key，更新 `HANGAR_API_TOKEN` |
| Hangar 发布 504 | Release 通道处理慢 | CI 已处理重试 + 幂等，观察后续 attempt |
| Modrinth 发布 401 | PAT 未生成或权限不完整 | 确认 PAT 勾选了 `VERSION_CREATE` + `PROJECT_WRITE` |
| Modrinth 发布 404 | 项目未审核通过 | 等待 Modrinth 审核，或检查 `project_id` 是否正确 |
| 版本号冲突 | 同一版本号重复发布 | CI 检测 "already exists" 并幂等退出，属正常行为 |
| 平台版本不匹配 | `plugin_support_paper_versions` 过期 | 更新 `gradle.properties`，提交 PR |
| 本地 `modrinth` task 报错 | 缺少 token | 本地不发布，仅在 CI 中运行 |
