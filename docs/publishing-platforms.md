# 发布平台运维手册

> OrzMC 插件发布平台统一信息源与运维指南
> 最后更新：2026-07-03

---

## 目录

1. [平台项目信息](#1-平台项目信息)
2. [统一项目描述](#2-统一项目描述)
3. [许可证](#3-许可证)
4. [页面内容同步策略](#4-页面内容同步策略)
5. [Hangar 自动发布](#5-hangar-自动发布)
6. [Modrinth 自动发布](#6-modrinth-自动发布)
7. [Token 管理](#7-token-管理)
8. [发布检查清单](#8-发布检查清单)

---

## 1. 平台项目信息

以下元数据在 Hangar 和 Modrinth 上需保持一致：

| 字段 | 统一值 | Hangar | Modrinth |
|------|--------|--------|----------|
| **项目名称** | `OrzMC` | `OrzMC` | `OrzMC` |
| **项目 ID/Slug** | — | `wangzhizhou666/OrzMC` | 待创建后填入 |
| **简短描述** | OrzMC — 多平台机器人集成的 Paper 服务器管理插件 | 同左 | 同左 |
| **完整介绍** | 见[第 2 节](#2-统一项目描述) | 同左 | 同左 |
| **分类** | 服务端管理 / 工具 / 社交 | `admin_tools`, `dev_tools`, `chat` | 待创建时确认 |
| **许可证** | GPL-3.0 | GPL-3.0 | GPL-3.0 |
| **图标/Logo** | `assets/avatar.png` | 上传此文件 | 上传此文件 |
| **网站** | `https://orzmc.jokerhub.cn` | 同左 | 同左 |
| **支持平台/加载器** | Paper | `PAPER` | `paper` |
| **Minecraft 版本** | `plugin_support_paper_versions`（`gradle.properties`） | 同左 | 同左 |
| **源码仓库** | `https://github.com/OrzMC/OrzMCPlugin` | 同左 | 同左 |
| **Issues** | `https://github.com/OrzMC/OrzMCPlugin/issues` | 同左 | 同左 |
| **讨论区** | QQ频道 `https://pd.qq.com/s/9zuis6m4v` | 填入 Discord/社区链接 | 填入讨论链接 |

> **注意**：Hangar 和 Modrinth 的完整项目介绍页面（长描述、功能列表、截图等）统一从 `README.md` 同步，详见[第 4 节](#4-页面内容同步策略)。

---

## 2. 统一项目描述

### 2.1 简短描述（一行）

> OrzMC — 多平台机器人集成的 Paper 服务器管理插件

### 2.2 完整描述（~200 字）

> OrzMC 是一款面向 PaperMC 服务器的综合管理插件，深度集成 QQ、Discord、飞书三大社交平台，让管理员在群聊中即可完成白名单管理、世界备份与优化、IP 黑名单、跨服传送门等运维操作。支持 TNT 爆炸防护与 GeoIP 区域访问控制，保障服务器安全。插件采用六边形架构设计，模块化分离核心逻辑与平台适配层，配置驱动、零数据库依赖，适合个人服主与小型社区快速部署。无论你运营的是私服还是公开服务器，OrzMC 都能让你的日常管理更加高效便捷。

### 2.3 功能列表

| 功能模块 | 说明 |
|----------|------|
| 🤖 **多平台机器人** | 支持 QQ（NapCatQQ/OneBot）、Discord（JDA）、飞书 Webhook |
| 📋 **白名单管理** | 强制白名单，通过 Bot 命令 `$a`/`$r` 远程管理 |
| 💾 **世界备份与优化** | `$b` 备份、`$o` 优化、定时维护编排 |
| 🚪 **跨服传送门** | 可配置的传送门系统，支持跨服务器传送 |
| 💥 **TNT 防护** | 爆炸保护 + 区域白名单 + 爆炸告警 |
| 🌍 **GeoIP 访问控制** | 按国家/地区限制登录，IP 黑名单管理 |
| 🎨 **自定义样式** | 可定制的消息模板与通知样式 |
| 🔒 **维护模式** | 自定义 MOTD，维护期间拒绝普通玩家登录 |

---

## 3. 许可证

- **许可证**：GNU General Public License v3.0（GPL-3.0）
- **LICENSE 文件**：项目根目录 `LICENSE`
- **平台选择**：Hangar 和 Modrinth 创建/编辑项目时均选择 GPL-3.0
- **版本约束参考**：https://www.gnu.org/licenses/gpl-3.0.html

---

## 4. 页面内容同步策略

两个平台都支持从 `README.md` 自动同步项目主页内容。

### 4.1 Hangar

Hangar Publish Gradle 插件支持 `pages.resourcePage()` 同步：

```kotlin
hangarPublish {
    publications.register("plugin") {
        // ...
        pages.resourcePage(project.file("README.md").readText())
    }
}
```

同步命令：`./gradlew syncPluginPublicationMainResourcePagePageToHangar`

> **当前状态**：未启用。后续运行此 task 即可首次同步，之后可加入 CI 自动同步。

### 4.2 Modrinth

Minotaur Gradle 插件提供 `modrinthSyncBody` task：

```kotlin
modrinth {
    syncBodyFrom.set(project.file("README.md").readText())
}
```

同步命令：`./gradlew modrinthSyncBody`

### 4.3 README.md 内容约定

- `README.md` 为唯一内容源
- 平台专属内容用 HTML 注释标记排除：
  ```markdown
  <!-- modrinth_exclude.start -->
  这部分内容不会同步到 Modrinth
  <!-- modrinth_exclude.end -->
  ```

---

## 5. Hangar 自动发布

### 5.1 当前状态

| 项目 | 详情 |
|------|------|
| **Hangar 项目** | `wangzhizhou666/OrzMC` |
| **Gradle 插件** | `io.papermc.hangar-publish-plugin:0.1.4` |
| **触发条件** | Push `main` → Snapshot / Push tag `x.y.z` → Release |
| **CI 文件** | `.github/workflows/publish.yml` |
| **Token Secret** | `HANGAR_API_TOKEN`（`create_version` 权限） |
| **重试策略** | 3 次，指数退避（20s/40s/60s），检测"版本已存在"幂等 |

### 5.2 配置要点

```kotlin
// build.gradle.kts
hangarPublish {
    publications.register("plugin") {
        version = shadowJarVersion          // 自动判断 snapshot/release/pr/dev 后缀
        channel = if (isRelease) "Release" else "Snapshot"
        changelog = changelogContent        // 最新 commit message
        id = pluginYaml["name"] as String   // "OrzMC"
        apiKey = System.getenv("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = plugin_support_paper_versions  // 来自 gradle.properties
            }
        }
    }
}
```

### 5.3 版本号规则

| 触发场景 | 版本号格式 | Channel |
|----------|-----------|---------|
| PR 构建 | `{version}-pr-#{PR}-{run}` | 不发布 |
| Push main | `{version}-snapshot-{run}` | Snapshot |
| Push tag `x.y.z` | `{version}` | Release |
| 本地构建 | `{version}-dev` | 不发布 |

---

## 6. Modrinth 自动发布

### 6.1 实施计划

#### 前置步骤（手动）

1. 在 [Modrinth](https://modrinth.com/new) 创建新项目
   - 名称：`OrzMC`
   - 简短描述：见[第 2 节](#21-简短描述)
   - 完整描述：见[第 2 节](#22-完整描述约-200-字)
   - 图标：上传 `assets/avatar.png`
   - 许可证：GPL-3.0
   - 分类：服务端管理 / 工具 / 社交（创建时确认可用分类）
   - 加载器：`paper`
   - 版本：先不手动上传（后续由 CI 自动发布）
2. 记录创建后获得的 `project_id`
3. 在 Modrinth Settings → Personal Access Tokens 生成 PAT
   - 权限勾选：`VERSION_CREATE` + `PROJECT_WRITE`
4. 在 GitHub 仓库 Settings 中添加：
   - **Secret**：`MODRINTH_TOKEN`（PAT 值）
   - **Variable**：`MODRINTH_PROJECT_ID`（项目 ID）

#### 代码改动

**`build.gradle.kts`** — 新增 Minotaur 插件和配置：

```kotlin
// plugins {} 中新增
id("com.modrinth.minotaur") version "2.+"

// 与 hangarPublish 并列，复用已有变量
modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
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
    // 可选：同步 README.md 到项目主页
    // syncBodyFrom.set(project.file("README.md").readText())
}
```

**`.github/workflows/publish.yml`** — 新增 Modrinth 发布 step：

```yaml
- name: publish to Modrinth
  env:
    MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
  run: |
    max_attempts=3
    for attempt in $(seq 1 $max_attempts); do
      echo "::group::Modrinth publish attempt ${attempt}/${max_attempts}"
      output=$(./gradlew modrinth --stacktrace 2>&1) && {
        echo "$output"
        echo "::endgroup::"
        echo "✅ Published to Modrinth on attempt ${attempt}"
        exit 0
      }
      echo "$output"
      echo "::endgroup::"
      if echo "$output" | grep -qi "already exists\|already.*version\|duplicate"; then
        echo "⚠️  Version appears to already exist on Modrinth — treating as success"
        exit 0
      fi
      if [ "$attempt" -lt "$max_attempts" ]; then
        delay=$((attempt * 20))
        echo "⏳ Attempt ${attempt} failed, retrying in ${delay}s..."
        sleep "$delay"
      fi
    done
    echo "❌ All ${max_attempts} Modrinth publish attempts failed"
    exit 1
```

### 6.2 目标状态

| 事件 | Hangar | Modrinth | GitHub Release |
|------|--------|----------|----------------|
| PR opened | ❌ | ❌ | ❌ |
| Push `main` | Snapshot | beta | ❌ |
| Push tag `x.y.z` | Release | release | ✅ |

---

## 7. Token 管理

| Token | 位置 | 权限 | 说明 |
|-------|------|------|------|
| `HANGAR_API_TOKEN` | GitHub Secret | `create_version` | Hangar 自动发布 |
| `MODRINTH_TOKEN` | GitHub Secret | `VERSION_CREATE` + `PROJECT_WRITE` | Modrinth 自动发布 |
| `MODRINTH_PROJECT_ID` | GitHub Variable | — | Modrinth 项目标识（非敏感） |

### Token 轮换

- Hangar API Key 在 [Hangar → Settings → Api keys](https://hangar.papermc.io/) 管理
- Modrinth PAT 在 [Modrinth → Settings → Personal Access Tokens](https://modrinth.com/settings/pat) 管理
- 轮换后需同步更新 GitHub Secrets

---

## 8. 发布检查清单

每次版本发布后检查以下项目：

- [ ] Hangar Snapshot/Release 版本已出现且文件可下载
- [ ] Modrinth beta/release 版本已出现且文件可下载
- [ ] GitHub Release 已创建（仅 tag 触发）
- [ ] 版本号 bump PR 已自动创建并启用 auto-merge（仅 tag 触发）
- [ ] 各平台页面显示信息一致（描述、版本号、Minecraft 版本）
- [ ] 如有 README.md 变更，确认页面同步已完成

### 故障排查

| 症状 | 可能原因 | 解决 |
|------|---------|------|
| Hangar 发布失败 | Token 过期或权限不足 | 重新生成 API Key，更新 GitHub Secret |
| Modrinth 发布失败 | PAT 权限不完整 | 确认勾选 `VERSION_CREATE` 和 `PROJECT_WRITE` |
| 版本号冲突 | 同一版本号重复发布 | CI 已处理幂等，检查日志中的 "already exists" 消息 |
| 平台版本不匹配 | `plugin_support_paper_versions` 未更新 | 更新 `gradle.properties` |
