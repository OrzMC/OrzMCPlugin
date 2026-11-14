# 仓库 PR 审批自动化（Approval Gate）

> 状态：现行 ｜ 最后更新：2026-11-09
> 适用：`OrzMC/OrzMCPlugin`（public，org 仓库，默认分支 = `develop`）

## 1. 要解决的问题

- 仓库规则：**外部 PR 必须经至少 1 位协作者评审**。
- 现实：仓库/组织**只有 1 个成员**（owner），且 GitHub **禁止批准自己创建的 PR**。
- 后果：owner 自己开的 PR（含 dependabot 之外的日常开发 PR）**永远拿不到评审 → 无法合并**（死锁）。

## 2. 为什么不能用 ruleset bypass

ruleset 的 `bypass_actors` 按**执行操作的人**生效，不区分 PR 作者：
把 owner 加入 bypass，会让 owner **连外部 PR 也一起免审**，无法实现“只豁免自建 PR”。
因此“外部 PR 需评审”必须由**第二个审批者**或**自定义状态检查**来保证。本仓库采用后者（零新增凭据）。

## 3. 方案：`approval-gate` 必需状态检查

`.github/workflows/approval-gate.yml`：

| PR 作者 | 检查结果 |
|---|---|
| `wangzhizhou`（owner）/ `dependabot[bot]` / `github-actions[bot]` | ✅ 通过（可信，不阻塞自动合并） |
| 其他（外部真人） | 仅当 `reviewDecision == APPROVED` 才通过，否则 ❌（合并不了） |

触发：`pull_request`（opened/reopened/synchronize/ready_for_review）+ `pull_request_review`（submitted/dismissed）
—— 评审被 dismiss 时自动重跑并回到失败态。

## 4. 需要的仓库设置（一次性）

以下设置**不在仓库文件里**（GitHub 侧），迁移/重建仓库时需重做：

### 4.1 develop

1. **ruleset `protection-develop`**：`required_status_checks` 增加 context `approval-gate`；
   `require_extra_approval_for_unattributed_changes=false`。
2. **ruleset `Default`**（作用于默认分支）：同上，并置 `strict_required_status_checks_policy=false`
   （否则每次 base 前进都要 rebase，自动合并会停摆）。
3. **经典分支保护 `develop`**：删除 `required_pull_request_reviews`（原生 1 审批要求），
   并把 `enforce_admins=false`（保留 owner `--admin` 紧急逃生口）。

### 4.2 main

`main` **没有经典分支保护**，只由 ruleset `protection-main` 承载。与 develop 对齐后的设置为：
`required_approving_review_count=0`、无 bypass、必需检查 `build` + `folia-smoke` + `approval-gate`、
`strict_required_status_checks_policy=false`、`require_extra_approval_for_unattributed_changes=false`。

即 main 与 develop 一致：**外部 PR 需 APPROVED，可信作者（owner / dependabot）免审**，
owner 用 `gh pr merge --squash --auto --delete-branch` 即可（紧急时 `--admin`）。

> 时机注记：`approval-gate` 已随 #512 里程碑进入 `main`，故 `protection-main` 已登记该检查；
> 从 main 拉出的热修复分支继承 main 的 workflow 文件，检查可正常上报。
> ⚠️ 若将来重建仓库 / 回滚到不含该 workflow 的 main，**不要**提前登记 `approval-gate`：
> `pull_request` 使用 **head 分支**的 workflow 文件，head 不含该 workflow 时必需检查永为 pending、PR 被挂起。

## 5. 日常用法

```bash
# 自建 / dependabot PR：CI 绿后自动合并（也可手动）
gh pr merge <n> --squash --auto --delete-branch

# 外部真人 PR：先评审通过（gate 转绿），再合并；或直接开 auto-merge 等它自动合
gh pr review <n> --approve && gh pr merge <n> --squash --auto --delete-branch

# 紧急逃生（越过所有门禁，仅 owner）
gh pr merge <n> --squash --admin
```

## 5.1 依赖 PR 的自动合并（`dependabot-merge.yml`）

依赖 PR 从「每周 N 个、每个都要人点」收敛成「**每周 1 个 minor/patch 分组 PR，CI 绿即自动合**」，
分两层：

| 层 | 位置 | 作用 |
|---|---|---|
| 分组 | `.github/dependabot.yml` | 同生态 minor/patch → 一个分组 PR（`gradle-minor-patch` / `actions-minor-patch`）；major 逐个单列；security 单独分组（`applies-to: security-updates`）；`cooldown` 7 天（Gradle 额外 major 14 天）避开上游刚发布就回滚的窗口 |
| 自动合并 | `.github/workflows/dependabot-merge.yml` | `workflow_run`（`Pull Request Build Check` 整跑成功）后，对**标题含 `minor-patch`** 的 dependabot PR 显式 `gh pr merge --squash` |

**为什么不用 `gh pr merge --auto`**：Actions 内的 `GITHUB_TOKEN` 无法 enable auto-merge（实测），
所以改成「CI 整跑成功 → 显式合并」，用 `workflow_run` 而非 `pull_request` 是因为需要
`build` + `folia-smoke` 两个 job 都跑完才有结论。

**不会自动合并的**（静默跳过，不留评论）：

- 非 dependabot 作者（所有人的日常 PR —— 不会收到机器人评论）、目标分支非 `develop`、草稿；
- **major 单列 PR**、**security 分组 PR**：需要人看 release notes / 破坏性变更；
- head 已不是本次 CI 的提交（等待期间又推了新提交，等下一轮 CI）。

**会留言说明的**：分组 PR 但 `mergeStateStatus` 等待超时（约 10 分钟）或轮询期间 head 变化 ——
避免 PR 无声挂在那里。

**门禁依赖**：自动合并用的是仓库侧 ruleset（`build` + `folia-smoke` + `approval-gate`），
本 workflow 只加「低风险」这一层筛选，不绕过任何必需检查。

**关闭方式**：删除 `.github/workflows/dependabot-merge.yml` 即恢复全人工；
或把 `.github/dependabot.yml` 的分组名从 `*-minor-patch` 改掉（标题匹配不到就不会自动合并）。

## 6. 回滚

```bash
# 关闭检查门禁，回到原生 1 审批（owner 用 --admin 合并）
gh api -X PUT repos/OrzMC/OrzMCPlugin/branches/develop/protection/required_pull_request_reviews \
  -f required_approving_review_count=1
# 并从两个 develop ruleset 的 required_status_checks 中移除 approval-gate（网页 Settings → Rules 操作最稳）
```

## 7. 已知失败模式

- **workflow 文件只在 head 分支生效**（实测）：`pull_request` 用 **head 分支**的 workflow 文件。
  所以新增 `approval-gate.yml` 后，**已存在的 PR 不会自动获得该检查**（其 head 里没有这个文件）；
  需 rebase / 推一次提交让 head 包含它——仅 close→reopen 不够（head 不含该文件时依然不跑，实测）。
- **`approval-gate` 是必需检查**：若 workflow 被禁用/删除/语法错误，所有 PR 到 develop 都会被挂起 →
  优先改用 `--admin` 或按 §6 回滚。
- 可信作者白名单是**硬编码在 workflow 里**的；新增可信机器人需同步修改本文件与 `approval-gate.yml`。
- 该检查在 fork PR 上使用只读 `GITHUB_TOKEN`，仅调用 `GET /pulls/{n}`，无需写权限。
- **`workflow_run` 只在「默认分支上的 workflow 文件」触发**：`dependabot-merge.yml` 合并进 `develop`
  （默认分支）后才会生效；合并进前的 push 不会触发它。改文件名/改名 workflow 会一并失效。
- **`dependabot-merge.yml` 依赖 PR 标题里的分组名**（`minor-patch`）：改 `.github/dependabot.yml`
  的分组命名时，必须同步改 workflow 的匹配条件，否则自动合并会静默停止（不报错）。
- **合并前 head 核验**：`workflow_run.head_sha` 与 `gh pr view --json headRefOid` 不一致时跳过，
  避免「旧 run 绿了 → 合并了更新的提交」。
- **`dependabot.yml` 写错会静默停更**（2026-11-11 实测）：非法字段不会报在 Actions 里，而是把默认分支
  上一个叫 `.github/dependabot.yml` 的 check run 变红，Dependabot **不再开任何版本更新 PR**。
  排查命令：`gh api repos/<owner>/<repo>/commits/<sha>/check-runs --jq '.check_runs[]|select(.name==".github/dependabot.yml")|.output.summary'`。
  已知限制：`cooldown.semver-*-days` 仅部分生态支持（`gradle` ✅、`github-actions` ❌ 只认 `default-days`）。

## 8. 必选检查清单与维护（2026-11 更新）

main/develop 两个 ruleset 的必选检查：

| 检查 | 来源 | 覆盖 | 单次耗时 |
|:--|:--|:--|:--|
| `build` | `build.yml` / job `build` | spotless + 密钥扫描 + 单测 + 集成 + 覆盖率门禁 + shadowJar | ~3 min |
| `folia-smoke` | `build.yml` / job `folia-smoke` | 真 Folia 起服 ×2 变体，各 10 条控制台断言 | ~2.5 min |
| `e2e-paper` | `build.yml` / job `e2e-paper` | 真 Paper 起服，10 条控制台断言（白名单读写/热加载/维护模式） | ~2 min |
| `e2e-lp` | `build.yml` / job `e2e-lp` | 真 Folia + 真 LuckPerms，13 条断言（`$p` 权限链 / 离线解析降级 / 拦截器链） | ~2 min |
| `e2e-im` | `build.yml` / job `e2e-im` | builtin QQ + 仓库内假平台：入站群消息 → 命令层 → 出站回复 | ~2 min |
| `codecov/patch` | Codecov App（OIDC 上传，`build` job 内） | 新增/改动行覆盖率 ≥70%（JaCoCo 上报，Codecov 口径）；`if_not_found: success` 保证纯文档 PR 不阻塞。**上报有 1–3 分钟延迟**（上传后异步处理），PR 会短暂 waiting | 秒级（含等待） |
| `e2e-player` | `build.yml` / job `e2e-player` | 真实 mineflayer 客户端（ViaVersion+ViaBackwards）：加入 / `/menu` GUI / `/guide` 开书 / 非管理员 `/config` 被拒 | ~2 min |
| `approval-gate` | `approval-gate.yml` | 外部作者必须有 APPROVED 评审 | 秒级 |

- 四个 job 并行，PR 墙钟约 3 分钟；`e2e-paper` 与 `folia-smoke` 都是**自举**（下载服务端 + 隔离 run 目录），
  不依赖常驻测试服/面板 API。
- **稳定性记录（2026-11-14）**：`e2e-paper` 8/8、`e2e-lp` 3/3、`e2e-im` 2/2、`e2e-player` 2/2 全绿，均未加 job 级重试；
  若出现红，先看是断言失败（真回归）还是启动/网络（环境），再决定是否加重试。
- 七个 job 全并行，PR 墙钟仍约 3 分钟（最慢的 `build` 主导）；`codecov/patch` 由 App 在上传后 1–3 分钟内补报。
- **交付时序**：若 PR 只改文档/CI（无可覆盖行），Codecov 仍会给出 `pass`（`if_not_found: success`），不会卡合并；
  实测 2026-11-14（#584 纯文档 PR，check 在 build 完成后约 1 分钟到达）。

**临时摘掉某个必选检查**（例：上游 Fill API 长时间不可用导致 e2e-paper 无法下载服务端）：

```bash
# 查看当前必选检查
for id in $(gh api repos/OrzMC/OrzMCPlugin/rulesets --jq '.[] | select(.target=="branch") | .id'); do
  gh api repos/OrzMC/OrzMCPlugin/rulesets/$id --jq '"\(.name): " + ([.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context] | join(","))'
done

# 摘掉（把 e2e-paper 从上下文列表里去掉；PUT 整个 ruleset）
gh api repos/OrzMC/OrzMCPlugin/rulesets/<id> > /tmp/rs.json
jq '{name, target, enforcement, conditions, bypass_actors,
     rules: [.rules[] | if .type=="required_status_checks"
             then (.parameters.required_status_checks |= map(select(.context!="e2e-paper")))
             else . end]}' /tmp/rs.json > /tmp/rs-put.json
gh api -X PUT repos/OrzMC/OrzMCPlugin/rulesets/<id> --input /tmp/rs-put.json
```

> 摘掉是应急手段，必须在对应 issue/PR 里写明原因与恢复时间；恢复就是把 `e2e-paper` 加回上下文列表。

## 9. 夜间失败的处理闭环（自动发现 → 分诊 → 指派）

```
nightly 失败
  ├─ 收集分诊（scripts/quality/triage.py，if: always()）
  │    失败用例 + 三个 run 目录的 smoke.log 尾部 + 最近 8 个提交 + 工作区改动
  ├─ 报告 job：下载产物 → 开/更新 issue（nightly-failure 标签，REST 去重）
  │    · 正文含分诊 + 失败 job 列表 + run 链接
  │    · 指派 admin 协作者（组织 owner 不能指派，故取 collaborator）
  ├─ 可选推送：仓库 secret `NOTIFY_WEBHOOK_URL`（不配则只靠 GitHub 指派通知）
  └─ 下次夜间通过 → 自动关闭该 issue
```

排查顺序（照这个走，不要先改断言）：

1. 看 issue 里的「失败用例」：单测/集成红了 → 真回归，直接定位。
2. 用例没红但 job 红 → 看「服务端日志尾部」：起服失败（下载/端口/Java）还是控制台断言失败。
3. 只在夜间红而 PR 绿 → 优先怀疑环境/上游（Fill API 版本变化、runner 负载），再怀疑 flaky。

> 已知限制：分诊只覆盖「测试结果 + 起服日志」。`e2eLocal*` 的断言失败信息在 Gradle 输出里，
> 若需要更细的断言上下文，后续可把 `build/reports/tests` 一并纳入分诊（目前作 artifact 保留）。

## 10. 夜间自动维护的两类 issue

| 标签 | 触发 | 行为 |
|:--|:--|:--|
| `nightly-failure` | nightly 任一 step 失败 | 开/更新 issue（附分诊：失败用例 + 5 个 run 的 smoke.log 尾部 + 最近提交 + 工作区），指派 admin；恢复后自动关闭 |
| `upstream-drift` | Paper/Folia 出现更新稳定版，或 LuckPerms 有新 release | 开/更新 issue（附对比表与建议动作）；无漂移时夜间自动关闭。**只提示，不自动升级**（兼容性/发布窗口要人判断） |

两个 issue 都用 REST 列表 + 重试做去重（`gh issue list` 走搜索索引，新建 issue 1–3 秒内查不到，
曾导致每夜新开一条）。手工改这两类 issue 的正文没有意义——夜间会按模板重写。
