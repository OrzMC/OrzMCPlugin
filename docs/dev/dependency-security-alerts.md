# 依赖安全告警处置记录（Dependabot / 依赖图）
> **状态：现行**（告警处置结论与复核条件；新增告警先读本文件再动手） ｜ **最后更新**：2026-10-22

本仓库的 Dependabot **security update** 作业在 2026-09-14 起每天红一片（`Dependabot Updates`
工作流，maven ×5 + npm ×1）。根因不是依赖真的没修，而是**依赖图里残留了一条已删除的 manifest**。
处置结论与复核条件记在这里，避免下次又去猜「为什么没开 PR」。

## 1. 现状（2026-10-22 处置后）

`gh api "repos/OrzMC/OrzMCPlugin/dependabot/alerts?state=open"` → **0 条 open**。

| 告警 | 生态 / 依赖 | 结论 | 原因 |
|:--|:--|:--|:--|
| #2–#7 | maven：`snakeyaml` `guava`×2 `commons-lang3` `plexus-utils` `log4j-api` | `not_used`（dismissed） | manifest 是 `pom.xml`，而它在 **2026-01-09 的 `6a246ce`「remove maven build method」** 里已删除；此后唯一构建方式是 Gradle。Dependabot 每次照旧去 checkout 里找 `/pom.xml`，报 `Error during file fetching; aborting: …/pom.xml not found` → 作业必红且**无法用 PR 修复**（没有可改的文件）。这些版本号都是 pom 时代的（guava 31.1、snakeyaml 1.33、plexus-utils 3.3.0、log4j 2.17.1、commons-lang3 3.8.1）。 |
| #1 | npm：`uuid`（`e2e/package-lock.json`） | `tolerable_risk`（dismissed） | 传递依赖路径 `mineflayer → prismarine-auth → @azure/msal-node@2.16.3`（要求 `uuid ^8.3.0`）+ `yggdrasil@1.8.0`（`^10.0.0`）。Dependabot 自报 `fix_available=false`：唯一修复路径会把 `mineflayer` 从 4.37.1 **降级到 1.4.0**。该公告（GHSA-w5hq-g745-h8pq）只在调用方自带 buffer 的 `v3()/v5()/v6()` 触发，msal-node 只用 `v4()`；且 `e2e/` 是测试套件，node 依赖不随插件 jar 分发。 |

**为什么不用 `dependabot.yml` 的 `ignore` 掩盖**：`ignore` 只是不再开 PR，告警仍在、update 作业
照样每天红；maven 那 6 条更是连"改哪里"都不存在。真正的动作是不让残留 manifest 继续产生作业。

## 2. 复核条件（什么时候该回来看）

- **maven 侧**：只要仓库里没有 `pom.xml`（`git log --diff-filter=D -- pom.xml` 有墓碑即可），就永远是残留。
  若哪天真的重新引入 Maven 构建（不建议），先补 `pom.xml` 再让 Dependabot 重开告警。
- **uuid 侧**：`mineflayer` 上游把 `prismarine-auth`/`@azure/msal-node` 的 uuid 抬到 ≥ 11.1.1
  （或插件把 `mineflayer` 升到相应版本）后，可在 Security 页 **Reopen** 该告警或直接升级 lockfile；
  升级后跑一次 e2e（`e2e/run-all.sh`）确认认证链路没坏。
- **其它新告警**：先看 `dependency.manifest_path` 是否仍存在于默认分支。不存在的，按「残留 manifest」处理
  （dismiss + 记录在本文件）；存在的，正常开 PR 修（`approval-gate` 让 dependabot PR CI 绿即可自动合并）。

## 3. 核对命令

```bash
# 打开中的告警（含 manifest 路径，判断是否为残留）
gh api "repos/OrzMC/OrzMCPlugin/dependabot/alerts?state=open" \
  -q '.[] | "\(.number) \(.dependency.package.ecosystem) \(.dependency.package.name) \(.dependency.manifest_path)"'

# 已处置的告警与理由
gh api "repos/OrzMC/OrzMCPlugin/dependabot/alerts?per_page=100" \
  -q '.[] | "\(.number) \(.state) \(.dismissed_reason) \(.dependency.package.name)"'

# 最近一次 Dependabot Updates 作业是否还在红
gh run list -R OrzMC/OrzMCPlugin --workflow "Dependabot Updates" --limit 3
```

> 关掉告警后，`Dependabot Updates` 不会再为它们排作业（没有 open 告警就没有 security update）。
> 版本类（weekly `gradle` / `github-actions`）更新由 `.github/dependabot.yml` 的分组 PR 负责，与本文件无关。
