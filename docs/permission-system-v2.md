# 权限系统二期 · 通用审核框架方案（v8 定稿）

> ⚠️ **设计稿（已交付 2026-08-07）**：现行权限配置事实以 **[permission-groups.md](permission-groups.md)** 为准（唯一权威）；本文件记录设计决策，不再随权限调整更新。

> 状态：✅ 已交付（2026-08-07）｜分支：`feat/rank-promotion`（PR #160）｜关联：[features.md](features.md)、[test-cases.md](test-cases.md)、[permission-system-v2-acceptance.md](permission-system-v2-acceptance.md)

## 一、背景与目标

**现状**：Rank 权限系统一期已闭环（时长读服务器原生 stats、default→member 自动晋升、member→builder 申请审核、LP 软依赖降级），但存在 3 个问题：

| # | 问题 | 现状 |
|:--|:--|:--|
| 1 | 审核流程**写死在 rank 模块** | `pending_application` 专用于 builder 晋升，无法复用 |
| 2 | 群内审核**依赖 `$e` 裸命令** | 无校验、无列表、易出错 |
| 3 | 晋升阈值**硬编码 10h** | 改阈值要改代码 |

**目标**：

1. **通用审核框架**——「申请→审核→处理→通知」全流程，本次落地晋升，未来扩展零框架改动
2. **2 条群指令 `$v`（审核）+ `$p`（升降级）** 承载权限管理，维护负担最低
3. 权限模块**单一独立配置文件**（`permission.yml`），阈值可调，不混 `config.yml`
4. 审核请求**携带结构化内容**（data），「审核什么」明确表达
5. **全链路通知**：提交/撤回/通过/拒绝 4 环节全部同步群，结果必达玩家
6. 玩家**自助查询**：当前权限组、申请状态、撤回申请
7. **代码边界按「未来独立沉淀为通用审核插件」设计**，可整体拆分

---

## 二、总体架构

```
┌─── 通用审核框架（可整体搬走，零宿主依赖）───┐
│ features/review/                           │
│  ReviewType  ReviewHandler  ReviewRequest  │
│  ReviewService  ReviewStore                │
└────────────────────────────────────────────┘
   ▲ 依赖注入（构造器）            ▲ 端口实现（留在宿主）
   │  ReviewStore(持久化)         │  PermissionStore（permission.yml）
   │  NotifierSink(通知)          │  现有 Notifier 适配
   │  PlayerLookup(玩家名↔UUID)    │  ServerAccess 适配
   │  Scheduler(异步)             │  SafeScheduler 适配
   └──────────────────────────────┴────────────────────
   features/rank/（消费者）
     ├ 注册 ReviewType 项（BUILDER_PROMOTION / ADMIN_PROMOTION 元数据）
     └ 注入 handler：LuckPermsPromoter（LP 授权）
```

**分层原则**：

- `review` 包内**只出现端口接口**（ReviewStore / NotifierSink / PlayerLookup / Scheduler），不 import `OrzServices`、`BotMessageService`、`ConfigService` 等宿主类
- 审核类型**元数据定义在框架侧，handler 由 rank 模块注入注册**——review 包零 LP 依赖
- 未来拆插件 = 搬 `features/review/` + `permission.yml` 的 reviews 节 → 新工程补 4 个端口适配器 → 注册自己的审核类型。**核心代码零改动**

---

## 三、详细设计

### 3.1 核心类（`features/review/`）

**ReviewRequest（值对象）**：

```java
public record ReviewRequest(
    String id,               // 唯一标识
    String typeId,           // 审核类型 id
    UUID applicantId,        // 申请人
    Map<String,String> data, // 请求内容（键值对）
    Status status,           // PENDING/APPROVED/REJECTED/CANCELLED
    long createdAt, long reviewedAt, String reviewerName) { ... }
```

**ReviewType（注册表）**——每项：id、展示名、命令键 + 参数解析 + 资格预检、列表摘要；handler 由 rank 模块注入：

```java
// 框架侧：定义元数据
BUILDER_PROMOTION(
    "builder-promotion", "晋升建造者",
    "builder",                              // /apply builder [理由]
    args -> Map.of("target-group", "builder",
                   "reason", args.getOrDefault("reason", "")),
    p -> RankService.currentGroup(p).equals("member"),   // 预检
    data -> "申请晋升builder" + ...)        // 列表摘要

// rank 模块侧：注入通过后处理
reviewRegistry.register(BUILDER_PROMOTION, id -> promoter.promoteToBuilder(id));
```

> 本次注册两种审核类型：`builder-promotion`（member→builder，`/apply builder`）与
> `admin-promotion`（builder→admin，`/apply admin`）——四级流转 default→member→builder→admin
> 的晋升通道全部闭环（default→member 为自动晋升，其余两级走申请审核）。

**ReviewService（核心）**——通知逻辑收在 service 层，任何入口触发都自动通知：

```java
String submit(ReviewType type, UUID applicantId, Map<String,String> data);
    // 资格预检（不满足直接拒）→ 防重复 → PENDING → 持久化
    // → 游戏内「已提交」 → 群 review_submitted
boolean cancel(String requestId, UUID applicantId);
    // 仅 PENDING 可撤回 → CANCELLED → 游戏内「已撤回」 → 群 review_cancelled
boolean review(String requestId, boolean approved, String reviewerName);
    // 先执行 handler（approved 时，LP 授权等副作用），成功后才落状态；
    // handler 返回 false 或抛异常 → 保持 PENDING + 提示（避免「已通过但未生效」）
    // → 落 APPROVED|REJECTED → 群 review_approved|rejected → 游戏内通知申请人（在线即发）
List<ReviewRequest> listPending();
boolean hasPending(ReviewType type, UUID applicantId);
Optional<ReviewRequest> pendingFor(ReviewType type, String playerName);
```

**ReviewStore**——持久化端口接口（实现 = PermissionStore 的 reviews 节）。
**ReviewHandler**——函数式接口 `boolean onApproved(UUID applicantId)`（true=授权成功；false=链顶/LP 异常，调用方保持 PENDING）。
**ReviewCommandService**——游戏内 `/review approve|reject <name>` 薄封装。

### 3.2 配置（单一独立文件，不混 config.yml）

```
permission.yml
├── config:      member-threshold-hours: 10      # 静态配置节
└── reviews:     requests.<id>: {type, applicant,# 审核记录节（运行时）
                 data, status, ...}
```

- 一个文件、一个统一 `PermissionStore` 类管理（load/save 整个文件），config 节静态，reviews 节 markAlwaysSave
- 命名 `permission.yml`（功能主体是权限，审核是其中流程）；将来拆审核插件时 reviews 节连同 review 框架整体切出
- **权限组状态不在本地存储**：LP track 为唯一事实源，升降级/当前组全走 LP API（一期 promoted/demoted 本地标记已删除）；一期 ranks.yml 晋升状态随 LP 接管失去意义，不做数据迁移（ranks.yml 资源已删除）

### 3.3 群指令（新增 `$v` 审核 + `$p` 升降级）

| 群指令 | 权限 | 功能 | 返回 |
|:--|:--|:--|:--|
| `$v l` | admin | 待审列表（多页 `$v l 2`，复用 Paginator） | `[晋升建造者] TestMember（当前组：member）：申请晋升builder（10分钟前）` |
| `$v y <玩家>` | admin | 通过 | `已通过 TestMember 的「晋升建造者」申请。` |
| `$v n <玩家>` | admin | 拒绝 | `已拒绝 TestMember 的申请。` |
| `$p u <玩家>` | admin | 权限升级（default→member→builder→admin，每次一级） | `已将 joker 升级为建造者。` |
| `$p d <玩家>` | admin | 权限降级（admin→builder→member→default，每次一级） | `已将 joker 降级为访客。` |

- 复用 `OrzUserCmd` 枚举 + `BotCommandService` handler + `needAdminPermission=true`
- 列表带申请人**当前组**；按玩家名定位唯一待审，`$v y <id>` 预留精确操作
- 审核人=消息发送者昵称透传（senderName 4 参），null 兜底「群管理员」
- **`$h` 帮助**：打印全部指令（管理员 + 通用，含 $v/$p）；**`$cmd ?`（如 `$v ?`/`$p ?`）**：打印该指令用法（未定义时降级为正常执行）——均由 BotCommandFeedbackService 提供

### 3.4 游戏内命令（注册表驱动）

| 命令 | 功能 |
|:--|:--|
| `/apply` | 列出可申请类型（自动生成帮助，**按当前玩家资格过滤**） |
| `/apply builder [理由]` | 提交晋升建造者申请（member 可申请） |
| `/apply admin [理由]` | 提交晋升管理员申请（builder 可申请） |
| `/apply whitelist [理由]` | 提交白名单申请（未来） |
| `/apply status` | 查看自己的申请及状态 |
| `/apply cancel <type>` | 撤回自己的待审申请 |
| `/review approve\|reject <name>` | （admin）替代 /rank approve/reject |
| `/rank` | 查自己：当前组 + 状态描述 + 下一步可申请项（**按当前组动态**） |
| `/rank <玩家>` | （admin）查指定玩家，审核前核对 |

`/rank` 返回**按当前权限组动态**（四级流转各组的展示）：

| 当前组 | /rank 内容 | /apply 可申请 |
|:--|:--|:--|
| default（访客） | 时长 + 晋升成员阈值进度（还需 X / ✅ 已达标）+ 「下一步：在线时长达标后自动晋升为成员」 | 无 |
| member（成员） | 时长 + 阈值（✅ 已达标）+ 「下一步可申请：晋升建造者（/apply builder）」 | 晋升建造者 |
| builder（建造者） | 时长（**不再展示已完成的 member 阈值**）+ 「下一步可申请：晋升管理员（/apply admin）」 | 晋升管理员 |
| admin（管理员） | 时长 + 「已达最高等级（管理员）」 | 无 |

- 「下一步可申请」由 ReviewType 注册表**反向生成**（资格预检通过的项）——与审核类型天然同步
- `/rank approve/reject` 与 `/rank demote` 已移除：审核迁移至 `/review`，升降级统一 `$p`（群侧）——`/rank` 纯查询
- 组名用 `RankService.groupDisplayName` 中文展示（admin=管理员/builder=建造者/member=成员/default=访客，全局唯一事实源）

### 3.5 通知矩阵（4 环节全覆盖）

| 环节 | 触发方 | 群通知（模板键） | 玩家侧 |
|:--|:--|:--|:--|
| 提交申请 | 玩家 `/apply` | 📋 `review_submitted` | 游戏内「已提交，等待审核」 |
| 撤回申请 | 玩家 `/apply cancel` | ↩️ `review_cancelled` | 游戏内「已撤回」 |
| 通过申请 | 管理员 `$v y` / `/review approve` | ✅ `review_approved` | 游戏内「已通过」+ 群兜底 |
| 拒绝申请 | 管理员 `$v n` / `/review reject` | ❌ `review_rejected` | 游戏内「被拒（原因）」+ 群兜底 |

| 模板键 | 内容示例 |
|:--|:--|
| `review_submitted` | `📋 [新申请] TestMember：申请晋升builder（$v l 查看）` |
| `review_cancelled` | `↩️ TestMember 撤回了申请：申请晋升builder` |
| `review_approved` | `✅ TestMember 的申请已通过（审核人：管理员）：申请晋升builder` |
| `review_rejected` | `❌ TestMember 的申请被拒（审核人：管理员）：申请晋升builder` |
| `rank_promoted` | `🎉 TestMember 权限已升级为「建造者」` |
| `rank_demoted` | `⬇️ TestMember 权限已被降级为「成员」` |
| `rank_status` | 保留键（`{message}` 透传；当前 `/rank` 文案由 RankCommandService 直生成，未走模板） |

- 机制复用现有 `TypedConfigProvider.renderEvent(key, vars)` + `Notifier.event(key, env)`（与 whitelist_block 同款）
- 模板键注册进 `TemplateKeys.ALL` + `templates.yml`（内容段 + format 段），文案可配不写死
- 群通知走 `ReviewNotifierAdapter.groupEvent`：按配置键 `renderTemplate` 直读 + fallback switch 双保险（存量部署无新键时也能出文案）
- **玩家结果三层兜底**：游戏内消息（在线即发）→ 群通知（离线可见）→ `/apply status`（随时自查）

### 3.6 数据迁移（一期 → 二期）

**定案：不做迁移。** 一期 ranks.yml 的 `pending_application`/`promoted` 标记在二期被取代：
- `pending_application=true` → 二期通用审核框架（reviews 节）——一期仅支持 builder 一种申请，二期上线时存量待审申请极少，直接重新提交即可
- `promoted=true` → 权限状态由 LP track 接管（唯一事实源），本地标记无意义
- ranks.yml 资源文件已删除，无迁移代码（早期实现过 migrateLegacyRanks，随配置最小化重构移除）

---

## 四、全流程时序

```
玩家 /apply builder [理由]
  → 预检 → permission.yml (PENDING)
  → 游戏内「已提交」 → 群 📋 review_submitted
        ↓
玩家 /apply cancel → CANCELLED → 游戏内「已撤回」 → 群 ↩️ review_cancelled
        ↓
管理员 $v l 查看（含当前组） / $v y|n 或 /review approve|reject
  → 状态变更 → handler 执行（LP 授权）
  → 群 ✅/❌ → 游戏内通知申请人（在线即发）
        ↓
玩家 /apply status 随时查结果 | /rank 查权限组/进度
```

**玩家侧三件套**：`/rank`（我是谁）· `/apply status`（我申请了什么）· `/apply cancel`（我能撤回）

---

## 五、未来扩展成本

| 新审核项 | 改动 | 涉及 |
|:--|:--|:--|
| 白名单申请 | 枚举加 1 项 + 注入 handler | 框架、`$v`、`/apply`、`/review`、通知**全部零改动** |
| 领地申请 | 枚举加 1 项（data 带坐标） | 同上 |
| 独立审核插件 | 搬 review 包 + reviews 节 + 补 4 适配器 | 核心零改动 |

---

## 六、范围清单

| 项 | 状态 |
|:--|:--|
| 通用审核框架（review 包，端口注入） | ✅ 本次 |
| 单一配置 permission.yml（两段式 + PermissionStore） | ✅ 本次 |
| 群指令 `$v`（l/y/n + Paginator）+ `$p`（u/d 升降级） | ✅ 本次 |
| 游戏内 `/apply` 通用化 + `/review` + `/rank` 增强 | ✅ 本次 |
| 4 环节群通知 + rank 双通道通知 + 玩家结果三层兜底 | ✅ 本次 |
| LP track 全链升降级（default→member→builder→admin，无反射软依赖） | ✅ 本次 |
| 在线列表格式化收敛（OnlineListFormatter 单一事实源） | ✅ 本次 |
| 数据迁移（遗留 pending → reviews 节） | ❌ 已取消（LP 接管权限状态，存量标记无意义，见 3.6） |
| builder→admin 申请 | ✅ 本次（ADMIN_PROMOTION：/apply admin） |
| 领地/白名单审核项 | ⏸ 暂缓（框架已预留） |

---

## 七、开发清单（执行跟踪）

| # | 任务 | 涉及 | 状态 |
|:--|:--|:--|:--|
| 1 | review 包：ReviewRequest / ReviewType / ReviewHandler / ReviewStore / ReviewService | features/review/ | ✅ |
| 2 | PermissionStore（permission.yml 两段：config/reviews，markAlwaysSave） | infra/config/ + 存储 | ✅ |
| 3 | rank 模块：阈值读取 + 完整视图查询（组+进度+可申请）+ handler 注入注册 | features/rank/ | ✅ |
| 4 | `/apply` 四子命令 + `/review` + `/rank` 增强（Brigadier 注册） | 命令注册 + ReviewCommandService | ✅ |
| 5 | `$v` 群指令（OrzUserCmd + handler + needAdminPermission） | features/botcommands/ | ✅ |
| 6 | 11 个模板键（review_* ×4 + rank_promoted/rank_demoted + rank_status + command_review_* ×4）+ templates.yml（内容段 + format 段） | TemplateKeys + 模板文件 | ✅ |
| 7 | 数据迁移（启动时，遗留 pending → reviews 节） | OrzServices 装配 | ❌ 已取消（见 3.6：LP 接管权限状态，不做迁移） |
| 8 | 单元测试 + MockBukkit 集成测试（含通知捕获 CapturingSink） | 各模块 test | ✅ |
| 9 | `./gradlew check` 全绿 + 本地服冒烟 | — | ✅ |

---

## 八、落地实现（代码映射）

### 8.1 文件清单

| 文件 | 角色 | 说明 |
|:--|:--|:--|
| `features/review/ReviewRequest.java` | 值对象 | id/typeId/applicantId/data/status/createdAt/reviewedAt/reviewerName |
| `features/review/ReviewType.java` | 注册表项 | id/命令键/参数解析/预检/摘要/handler（BUILDER_PROMOTION 等） |
| `features/review/ReviewHandler.java` | 端口 | 审核通过处理回调 `boolean onApproved(UUID)`（LP 授权等副作用；false=授权失败保持待审） |
| `features/review/ReviewStore.java` | 端口 | 持久化接口（save/find/listPending/pendingFor） |
| `features/review/ReviewNotifier.java` | 端口 | 4 环节通知接口 |
| `features/review/PlayerLookup.java` | 端口 | 玩家名↔UUID 解析 |
| `features/review/ReviewService.java` | 核心编排 | 提交/撤回/审核/查询，统一预检 + 防重复 + 通知 |
| `features/review/ReviewCommandService.java` | 游戏内命令 | `/review approve\|reject <玩家>` |
| `features/rank/PermissionStore.java` | 存储实现 | 同时实现 ReviewStore+RankStore（stats 时长），permission.yml 两段式（config/reviews），markAlwaysSave；权限状态由 LP track 持有 |
| `features/rank/RankStore.java` | 查询接口 | currentGroup/时长视图（移除 pending_application） |
| `features/rank/RankService.java` | 业务 | 自动晋升 + 手动升降级 + currentGroup（LP 唯一事实源，无 LP 回退 default）；阈值读 config 节 |
| `features/rank/RankCommandService.java` | 游戏内命令 | `/rank` 纯查询 + 注册表反向生成 |
| `features/rank/LuckPermsPromoter.java` | handler 实现 | LP 授权（主线程派发，见 8.3） |
| `features/rank/LuckPermsBootstrap.java` | 启动初始化 | 装即用：track「rank」/四级组缺失自动创建（幂等，已有不覆盖） |
| `features/botcommands/OrzUserCmd.java` | 群指令枚举 | 新增 `REVIEW("v", "查看/处理审核申请", true)` + `PERMISSION("p", "权限升降级", true)` |
| `features/botcommands/BotCommandService.java` | 群指令分发 | `$v l/y/n` + `$p u/d` handler（setReviewService setter 注入；$v 审核人=消息发送者昵称透传） |
| `infra/notify/ReviewNotifierAdapter.java` | 通知适配 | 按配置键 renderTemplate + fallback（见 8.4） |
| `infra/player/BukkitPlayerLookup.java` | 玩家解析适配 | OfflinePlayer 离线解析 |
| `infra/config/TemplateKeys.java` | 模板键 | review_* ×4 + rank_promoted/rank_demoted + rank_status + command_review_* ×4 |
| `assembly/FeatureModule.java` | 装配 | PermissionStore/ReviewService/handler 注册/命令注册/setter 注入 |
| `infra/config/ConfigService.java` | 配置注册 | `registerConfig("permission","permission.yml")` |
| `events/OrzDebugEvent.java` | 测试通道 | 仅监听 RemoteServerCommandEvent（RCON 触发；Brigadier 走 executes 直调） |
| `resources/permission.yml` | 默认资源 | 两段式模板（config + reviews） |
| `resources/templates.yml` | 模板 | 11 新键（内容段 + format 段：review_*/rank_* PLAIN、rank_status/command_review_list CODE_BLOCK） |

### 8.2 命令一览

| 命令 | 权限 | 说明 |
|:--|:--|:--|
| `/apply` | 玩家 | 列出可申请项（注册表驱动 + 按资格过滤） |
| `/apply builder [理由]` | 玩家 | 提交晋升建造者申请（预检：member 资格） |
| `/apply admin [理由]` | 玩家 | 提交晋升管理员申请（预检：builder 资格） |
| `/apply status` | 玩家 | 查询我的申请状态 |
| `/apply cancel <type>` | 玩家 | 撤回待审申请 |
| `/review approve\|reject <玩家>` | 管理员 | 游戏内审核 |
| `/rank` | 玩家 | 当前组 + 状态描述 + 下一步可申请（按组动态） |
| `/rank <玩家>` | 管理员 | 查询他人 |
| `$v l` | 群管理员 | 待审列表（分页，含当前组/摘要/时间） |
| `$v y\|n <玩家>` | 群管理员 | 通过/拒绝 |

### 8.3 关键实现决策（实战验证）

1. **LP 命令必须主线程派发**：Paper 异步线程 `dispatchCommand` 抛 `IllegalStateException: Asynchronous Command Dispatched Async`（群指令/orzdebug 走异步链路）。`LuckPermsPromoter` 注入 `ServerScheduler`，非主线程时 `runSync` 回主线程。
2. **通知渲染用 `renderTemplate` 而非 `renderEvent`**：实测 `renderEvent` 只认白名单事件键，未知键渲染为空 → 适配器按配置键直读 + fallback switch。
3. **`$v` 群指令 setter 注入**：`BotCommandService` 创建早于 `FeatureModule`，无法构造注入 → 仿 maintenance/blacklist 的 `setReviewService`，注入点在 `setupEventListeners`。
4. **资格预检统一在 `ReviewService.submit()`**：任何入口（游戏内/未来其他）都过同一校验。
5. **无数据迁移**：一期 ranks.yml 的 promoted/pending 标记随 LP track 接管失去意义（见 3.6），不做迁移；避免「迁移了已过时的状态」引入双写漂移。
6. **权限状态无本地推断**：`currentGroup` 无 LP 时一律回退 default（访客）——无 LP 时权限体系整体不可用，按审核记录推断组会造成虚假展示（一期 hasApprovedBuilder 已删）。
7. **叠加组/上下文脏节点是体系外数据**：LP API 无 TrackNode 概念，track 组即普通继承节点，代码**无法**区分「track 给的组」与「同名叠加组」；叠加组会干扰 `currentTrackGroup` 判定。运维规范：权限组只经 `$p u/d` 升降，禁止 `parent add` 叠加；存量脏数据用 `lp user <X> parent info` 检查后清理。
8. **LP 操作统一 global 上下文（根因修复）**：一期 `$p` 升降级用**玩家实时上下文**（在线时 world/gamemode/essentials 等）调 LP API，节点带完整上下文快照落库 → 与离线操作（global）的节点混存 → track 节点重叠、`/rank` 误判、promote/demote 报 `AMBIGUOUS_CALL`（joker/TestMember 实测复现）。修复：promote/demote/currentTrackGroup/isInGroup 全部固定 global 上下文；`$p u` 新玩家（不在 track）ADDED_TO_FIRST_GROUP 连续 promote 直达 member；AMBIGUOUS_CALL 输出 WARNING 日志 + 检查指引。
9. **装即用（LuckPermsBootstrap 自动初始化）**：部署前处理内置到插件——LP 可用时启动自动补齐 track「rank」+ 四级组骨架（缺失创建、已有跳过、**绝不覆盖线上定义**）；无 LP 时跳过（NoopRankPromoter 降级）。部署顺序不再敏感（无需先手动建 track/组）。仅剩人工项：核对**已有**组权限内容是否符合预期（插件不覆盖）。

### 8.4 测试通道修复（自动化测试前置）

- `OrzDebugEvent` 只监听 `RemoteServerCommandEvent`（RCON 专用通道，命令可能带前导斜杠 → 剥斜杠）。Paper 26 中 Brigadier 命令（游戏内/控制台）走 executes 直调，**不再监听 `ServerCommandEvent`**（前代实现双监听会构成双通道，潜在双重处理）。
- 测试脚本 `~/minecraft-bot/review-e2e.js`（主链路）+ `review-e2e-2.js`（补测）+ `review-real.js`（真实玩家场景），内嵌 node 原生 RCON 实现（length = id+type+payload+2null 总长，`$` 不经 shell 展开）。
