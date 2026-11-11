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
| 分组 | `.github/dependabot.yml` | 同生态 minor/patch → 一个分组 PR（`gradle-minor-patch` / `actions-minor-patch`）；major 逐个单列；security 单独分组（`applies-to: security-updates`）；`cooldown` 7 天（major 14 天）避开上游刚发布就回滚的窗口 |
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
