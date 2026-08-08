# 权限系统二期 · 验收报告（2026-08-07）

> 关联：[permission-system-v2.md](permission-system-v2.md)（方案 v8 定稿）｜分支：`feat/rank-promotion`（PR #160）｜构建：`OrzMC-1.0.16-dev.jar`

## 一、验收范围

| 验收层 | 内容 | 结论 |
|:--|:--|:--|
| 静态门禁 | `./gradlew clean check`（spotless + 单测 + MockBukkit 集成 + JaCoCo 覆盖率） | ✅ PASS |
| 自动化单元测试 | ReviewServiceTest / PermissionStoreTest / RankServiceTest / ConfigHealthCheckTest 等 | ✅ PASS |
| 本地服启动冒烟 | Paper 26.2 真实启动、模板键完整、无缺失告警 | ✅ PASS |
| 配置结构 | permission.yml 两段式（config/reviews），权限状态由 LP track 持有（无本地迁移） | ✅ PASS |
| E2E 自动化（bot 玩家） | 提交→预检→列表→通过→拒绝→撤回→查询 全链路 | ✅ PASS |
| **真实玩家场景** | 申请→下线→离线审核→重新上线→LP 权限实测 | ✅ PASS |

## 二、自动化测试（单元 + 集成）

### 2.1 新增/更新测试

| 测试 | 覆盖点 |
|:--|:--|
| `ReviewServiceTest`（新增 234 行） | submit 预检+防重复+通知、cancelForApplicant、review approve/reject（跑 handler+notifier）、reviewByApplicantName 单/多 pending、hasPending/pendingFor |
| `PermissionStoreTest`（新增 183 行） | 两段式存取（config/reviews）、时长读取、坏数据容错 |
| `RankServiceTest`（更新） | 阈值读 config 节、完整视图 |
| `ConfigHealthCheckTest`（更新） | requiredCmds 补 11 新模板键（review_* ×4 + rank_promoted/rank_demoted + rank_status + command_review_* ×4） |

### 2.2 门禁结果

```
./gradlew clean check --no-build-cache
> Task :orzmc-api:jacocoTestCoverageVerification   PASS
> Task :jacocoTestCoverageVerification             PASS
BUILD SUCCESSFUL in 1m 4s
```

## 三、E2E 自动化测试（bot 玩家，本地 Paper 26.2）

脚本：`~/minecraft-bot/review-e2e.js`（主链路）+ `review-e2e-2.js`（补测）
通道：Mineflayer bot 玩家（TestNewbie member / TestMember default）+ RCON `orzdebug $v`（模拟群管理员，isAdmin=true）

### 3.1 主链路结果

| # | 环节 | 结果 | 证据 |
|:--|:--|:--|:--|
| 1 | `/apply builder 理由` 提交 | ✅ | 「申请已提交，等待管理员审核。」 |
| 2 | 重复提交拦截 | ✅ | 「你已提交过「晋升建造者」申请，请等待管理员审核。」 |
| 3 | `$v l` 待审列表 | ✅ | `[晋升建造者] TestNewbie（当前组：member）：申请晋升 builder：…（刚刚 提交）` |
| 4 | `$v y TestNewbie` 通过 | ✅ | 玩家游戏内「你的「晋升建造者」申请已通过！」 |
| 5 | LP 授权 | ✅ | 日志 `[LP] testnewbie 現在從環境 global 中繼承 builder 的權限` |
| 6 | `/apply status` | ✅ | `✅ 已通过（群管理员）` |
| 7 | `/rank` 组联动 | ✅ | `当前权限组：建造者（builder）` |

### 3.2 补测结果（拒绝/撤回/预检路径）

| # | 场景 | 结果 | 证据 |
|:--|:--|:--|:--|
| ① | 资格预检拒绝（TestMember 本地 default） | ✅ | 「你不满足「晋升建造者」的申请条件。」 |
| ② | `$v n` 拒绝 | ✅ | 玩家「申请被拒绝。」+ status `❌ 已拒绝（群管理员）` |
| ③ | 拒绝后可重新申请 | ✅ | 「申请已提交…」+ `$v l` 显示新申请 |
| ④ | `/apply cancel builder` 撤回 | ✅ | 「已撤回「晋升建造者」申请。」+ `$v l` → 「当前没有待审核的申请。」 |
| ④b | 历史记录完整 | ✅ | status 同时显示 `❌ 已拒绝` 与 `↩️ 已撤回` 两条 |

## 四、真实玩家场景测试（关键验收）

**场景设计**：真实玩家行为链 —— 进服确认起点 → 提交申请 → **下线**（不在线等审核）→ 管理员离线审核 → **重新上线**验证结果与权限。

| 阶段 | 动作 | 结果 | 证据 |
|:--|:--|:--|:--|
| 1-1 | 进服 `/rank` 确认起点 | ✅ | `当前权限组：成员（member）` + 时长 604/600 ✅达标 |
| 1-2 | `/apply builder 想用WorldEdit建造一个村庄` | ✅ | 「申请已提交，等待管理员审核。」 |
| 1-3 | 玩家下线 | ✅ | 真实场景：不等待审核 |
| 2-1 | 管理员 `$v l` | ✅ | `[晋升建造者] TestNewbie（当前组：member）：…想用WorldEdit建造一个村庄（刚刚 提交）` |
| 2-2 | `$v y TestNewbie` **离线通过** | ✅ | 「已通过 TestNewbie 的「晋升建造者」申请。」 |
| 2-3 | **LP 离线授权** | ✅ | `[LP] testnewbie 已經從環境 global 中繼承了 builder.`（OfflinePlayer 解析生效） |
| 3-1 | 重新上线 `/apply status` | ✅ | `✅ 已通过（群管理员）` |
| 3-2 | `/rank` | ✅ | `当前权限组：建造者（builder）` |
| 3-3 | `//wand` **LP 权限实测** | ✅ | WorldEdit 木斧激活提示（builder 组专属权限，真实生效） |

**本场景独有验证点**：
1. **离线审核**：申请者不在线时审核，`OfflinePlayer` 名字解析 + LP 命令执行正常
2. **LP 授权端到端**：不止日志/状态推断，用 WE 命令实测权限生效
3. **状态持久化**：跨多次服务器重启数据正确

## 五、测试过程中发现并修复的问题

| # | 问题 | 根因 | 修复 | 验证 |
|:--|:--|:--|:--|:--|
| 1 | RCON `orzdebug` 不触发 Bot 模拟 | `OrzDebugEvent` 只监听 `ServerCommandEvent`（stdin），Paper 26 RCON 走 `RemoteServerCommandEvent` | 改监听 `RemoteServerCommandEvent`（RCON 专用通道）+ 兼容前导斜杠；Brigadier 命令走 executes 直调，不双监听 | RCON 驱动 `$v l` 成功 |
| 2 | `$v y` 抛 `IllegalStateException: Asynchronous Command Dispatched Async` | 群指令/orzdebug 异步线程 dispatch LP 命令，Paper 要求主线程 | `LuckPermsPromoter` 注入 `ServerScheduler`，非主线程 `runSync` 回主线程 | 离线审核 LP 授权成功 |
| 3 | 自动化脚本 RCON 连不上 | ① shell 展开 `$v`；② node RCON length 字段少算 8 字节头部 | 原生 net 实现 + 正确 length 语义（id+type+payload+2null 总长） | 脚本稳定运行 |
| 4 | `$h` 帮助缺 `$v`/`$p`；`$v ?`/`$p ?` 无用法 | `BotCommandFeedbackService.helpInfo` 硬编码拼接遗漏新指令；`usageTip` 的 REVIEW/PERMISSION 走 default 返回空（`$cmd ?` 降级为直接执行） | helpInfo 补 $v/$p；usageTip 补 REVIEW（l/y/n）与 PERMISSION（u/d + 权限链） | 实测 `$h` 输出含两指令；`$v ?`/`$p ?` 输出完整用法 |
| 5 | `$p d joker` 降级后 `/rank` 仍显示「建造者」 | joker 存在**体系外叠加组/上下文脏节点**（`lp user joker parent info` 实锤：无上下文 `builder` + world/gamemode 上下文 `builder`/`member`）；`currentTrackGroup` 按「继承组 ∩ track 组」取最高位，脏节点干扰判定；LP API 无 TrackNode，代码无法区分来源 | ① 数据清理：移除 joker 无上下文叠加组；② **根因修复（63dc5ef）**：`$p` 升降级/组查询统一 global 上下文——一期用玩家实时上下文操作导致节点带上下文快照落库（见方案 8.3 决策 8） | 清理后 + 新代码下 `$p d joker` 恢复 REMOVED_FROM_FIRST_GROUP（不再 AMBIGUOUS_CALL），`/rank` 与 track 同步 |
| 6 | `$p d joker` 提示「无法再降级」但实际是数据歧义 | track 节点上下文混存（global + world 上下文并存）→ LP demote 报 `AMBIGUOUS_CALL`，旧代码一律按「已在最低等级」提示，误导 | ① 统一 global 上下文（同上）；② `AMBIGUOUS_CALL` 输出 WARNING 日志 + 检查指引；③ `$p` 失败提示合并「已达边界或权限数据异常（详见服务器日志）」；④ `$p u` 新玩家（不在 track）连续 promote 直达 member，不再出现「升级为访客」 | 实测 `$p u joker`：ADDED_TO_FIRST_GROUP → 连续 promote SUCCESS → 「已将 joker 升级为成员。」 |

## 六、验收结论

**✅ 验收通过**。12 个自动化场景 + 10 步真实玩家场景全部通过；`./gradlew clean check` 全绿；LP 授权端到端实测生效；配置持久化验证通过（权限状态以 LP 真实组为准，本地无状态快照）。

**遗留说明**：
- `$v n` 拒绝、`/apply cancel` 撤回已测（补测 ②④）；`$v y` 通过已测（主链路 + 真实场景）
- 群通知真实投递（EasyBot 网关 → 飞书测试群）链路已通（模板键 + 适配器冒烟），未做真实群消息端到端（避免打扰生产群；测试群无管理员身份的机器人会话）
- 代码已在 `feat/rank-promotion` 分支（commit `63dc5ef`，含 review 修复 + Alerts 清理 + 一期残留清理 + 状态动态化 + admin 申请通道 + global 上下文修复），PR #160 OPEN 待合并
- 审核人记录：验收时 RCON/orzdebug 通道显示「群管理员」；后续 S2 修复后审核人=消息发送者昵称透传（BotInboundHandler 4 参），null 兜底「群管理员」——`permission.yml` 落库为真实昵称/ID（如「控制台」「RCON」）
- **一期残留清理（34a198c）**：/rank demote 移除（升降级统一 `$p`）、无 LP 本地推断删除（hasApprovedBuilder）、模板死占位符 role_alias 清理——权限状态全面收敛为 LP 单一事实源，无行为变化（LP 在线路径不受影响）
- **状态展示动态化（63ee984）**：/rank 与 /apply 按当前权限组展示（实测四级流转：default→成员→建造者→管理员 各分支文案正确）；新增 ADMIN_PROMOTION（/apply admin）打通 builder→admin；TestMember 实测全链路：/apply admin → $v l 列表（当前组：builder）→ $v y → LP promote SUCCESS → /rank 管理员（已达最高等级）
- **global 上下文修复（63dc5ef）**：$p 升降级/组查询统一 global 上下文——根治 track 节点上下文混存（joker world 上下文脏节点不再影响判定；AMBIGUOUS_CALL 消失）；$p u 新玩家连续 promote 直达 member；失败提示合并边界/数据异常（详见五、问题 5/6）
- **结案历史裁剪（d145578）**：permission.yml 每玩家保留最近 10 条结案记录（PENDING 永不删，防重复提交保证其有界）——文件大小有上限，全量写盘/扫描成本恒定
- **装即用（d303b02）**：LuckPermsBootstrap 启动自动初始化——track「rank」/四级组缺失自动创建（幂等不覆盖）；实测：track 改名模拟缺失 → 重启自动重建（链序 default→member→builder→admin 正确）+ $p 功能正常；线上部署仅剩人工项=核对已有组权限内容

## 七、测试脚本沉淀

| 脚本 | 用途 |
|:--|:--|
| `~/minecraft-bot/review-e2e.js` | 主链路：提交→列表→通过→status→rank |
| `~/minecraft-bot/review-e2e-2.js` | 补测：预检拒绝 / `$v n` 拒绝 / 重申请 / 撤回 |
| `~/minecraft-bot/review-real.js` | 真实玩家场景：提交→下线→离线审核→上线验证→WE 权限实测 |
| `~/minecraft-bot/p-test.js` | RCON 驱动 `orzdebug` 模拟群消息（`$p`/`$v`/`$h` 等） |
| `~/minecraft-bot/rank-single.js` / `cmd-one.js` | bot 玩家登录执行游戏内命令（`/rank`/`/apply`，自动 /login） |
| `~/minecraft-bot/lp-joker-check.js` / `rcon-*.js` | LP 数据查询/RCON 调试辅助 |

脚本内嵌 **node 原生 RCON 实现**（不经 shell，`$` 安全），可复用于后续所有 Bot 命令自动化测试。

---

## 六、Exaroton 线上服验收（2026-08-08）

**验收目标**：权限系统在 Exaroton 云端测试服（与线上 MCSM 同插件基线）正常工作。

### 部署与环境
- 部署 OrzMC **1.0.16-dev**（本地编译产物）→ 启动自动初始化 track「rank」（default→member→builder→admin）+ member 组（`lp track rank info` / `lp group member info` 日志实证）
- 同步权限组配置表（85 条 LP 命令，`perm_commands.txt` 蓝本 → Exaroton API command 批量执行，全部成功）
- TestMember 配 admin 组（控制台 LP 命令），作为验收管理员

### 验收用例（全链路实测）
| 步骤 | 操作 | 结果 |
|:--|:--|:--|
| 1 | TestNewbie `/rank` | 访客（default）✅ |
| 2 | TestMember `/orzdebug $p u TestNewbie` | ✅ TestNewbie → 成员（member） |
| 3 | TestNewbie `/apply builder 验收测试` | ✅ 申请已提交，等待管理员审核（访客申请被拒「不满足条件」= 条件校验正常） |
| 4 | TestMember `/orzdebug $v y TestNewbie` | ✅ 审核通过，自动生效 |
| 5 | TestNewbie `/rank` | ✅ **建造者（builder）** |
| 6 | TestMember `lp user TestMember permission check minecraft.command.op` | ✅ **false**（admin 不可自封 op） |
| 7 | TestMember `lp user TestMember permission check luckperms.user` | ✅ **undefined**（admin 不可改 LP） |
| 8 | `$p u HermesBot`（未注册玩家） | 无 LP 用户记录——$p 对新玩家正常建组路径（未影响） |

### 结论
权限系统在 Exaroton 线上服**全部功能正常**：四级链显示、手动升降级（$p）、申请条件校验（/apply）、审核晋升（$v）、高危隔离（无 op / 无 luckperms）。**部署流程**：新 jar 上传 → 重启 → 同步权限组配置表 → 可用。权限组配置表（docs/permission-groups.md）为唯一权威源，三端（本地/Exaroton/MCSM）保持一致。

---

## 七、MCSM 主服务器同步（2026-08-08）

**目标**：生产服对齐权限组配置表 + 清理高危权限 + 部署 OrzMC 1.0.16-dev。

### 部署
- OrzMC 1.0.16-dev 上传 `plugins/update/` → 玩家同意重启 → 重启应用（日志 `Enabling OrzMC v1.0.16`）
- **装即用生效**：自动创建 track「rank」（default→member→builder→admin）+ member/admin 组（线上原本只有 builder/default）

### 对齐前现状（重启前 LP export 实证）
- 组：builder（`worldedit.*`/`worldguard.*` 全量通配 + gamemode）、default（9 项 balance/tpa）
- 无 member/admin 组、无 track
- **高危**：momo 用户级 `minecraft.command.op` + `deop`（可自封 op）
- 用户归属：8 名 builder（joker/ultrablind/fancy/nnnnn/sasha_uzbek/mark/ripprenameagin/kingsang）+ 8 名 default

### 对齐执行（用户确认「完全对齐」）
1. `lp export pre-align` 备份
2. 配置表 85 条 set（perm_commands.txt 蓝本）
3. 清理旧节点 10 条：builder 通配 ×3（worldedit.\*/worldguard.\*/gamemode.\*）+ default 旧项 ×7（balance/tpa 系列）
4. momo 高危清理 ×2（op/deop）
5. `lp export post-align` 验证

### 对齐后验证（post-align export 实证）
| 组 | 结构 | 权限节点数 |
|:--|:--|:--|
| default | 无继承 | 15（配置表 14 项） |
| member | 继承 default | 16 |
| builder | 继承 default | **20（WE 裁剪子集 9 项，无通配）** |
| admin | 继承 builder | 33（无 \* / luckperms.\* / op） |

- ✅ momo op/deop 已清除
- ✅ builder 从 `worldedit.*` 通配 → 9 项子集（brush/clipboard/history/region/selection/tool/utility/help/wand）
- ✅ 备份链：pre-sync（重启前）→ pre-align（对齐前）→ post-align（对齐后）

### 用户级杂权限清理（追加）
- **第一批**（eric/momo 12 项）：TP/TPA/essentials.tp/bukkit.command.tps/minecraft.command.TP/minecraft.command.gamemode/spark.tps/luckperms.*.meta.setprefix（momo op/deop 已在前章清理）
- **第二批**（11 项，post-clean export 暴露）：vodka002/wodandlike/__sa_ka_na__ 的 `minecraft.command.gamemode`、wodandlike 的 `minecraft.command.give`（可刷物品⚠️）、mei 的 essentials.gamemode.* 全量 6 项、kingsang 的 essentials.tp
- **验证**（post-clean2 export）：全部用户仅剩继承组（meta.lp-editor-key 为 LP 编辑器会话 key 无害，保留）
- 备份链追加：post-clean → post-clean2
### 遗留说明
- ~~用户级杂权限~~ ✅ 已全部清理（两批共 23 项，post-clean2 实证）
- 玩家组归属未动（builder 玩家继续 builder）——四级流转（$p/$v）待玩家自然晋升/管理员操作生效
