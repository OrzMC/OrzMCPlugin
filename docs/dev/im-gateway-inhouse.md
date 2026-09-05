# IM 网关内建方案（in-house IM gateway）与 EasyBot 双通道

> **状态：方案定稿**（待按 P1 起拆实施 PR）｜**最后更新**：2026-09-03
> **决策日期**：2026-09-03（owner 逐条拍板，见 §0）
> **前置调研**：EasyBot 源码（EasyIndie/EasyBot@main）协议取证 + 本插件 `infra/bot` 装配取证 + 四平台公共 API 可行性分析
> **配套**：[features.md §2.5 EasyBot 网关配置指南](../features.md)（现状文档，含失真项待修）、[EasyBot 接入文档 PR（EasyIndie/EasyBot#114）](https://github.com/EasyIndie/EasyBot/pull/114)

## 0. 决策记录（owner 拍板，勿回退）

| # | 项 | 决策 |
|:--|:--|:--|
| D1 | 切换粒度 | **全局** `backend: easybot \| builtin`（v1 不做平台级混合） |
| D2 | 配置归属 | **独立 `im.yml`**（backend + builtin 凭据/会话；easybot.yml 保留为 EasyBotDriver 连接配置，见 §4） |
| D3 | 失败回退 | builtin 通道启动失败 → **停群功能 + 日志/`/bot` 告警**，等管理员处理；**不做自动 fallback**（掩盖故障难排查） |
| D4 | 切换生效 | 首版仅**重启 或 `/orzmc config reload` 后生效**（复用现有回调链，不做运行时无感热切） |
| D5 | 平台落地顺序 | **QQ → 飞书 → Discord → Telegram**（每平台一个独立 PR，成熟一个挂一个） |
| D6 | 消息类型 | **仅支持文本消息，不支持媒体**（图片/文件/语音全部砍掉，实现量不值得） |
| D7 | 发送重试语义 | **尽力一次 + 日志/健康告警，不重试**（无持久化幂等，重试会造成重复通知；轻量定位放弃投递对账） |
| D8 | 能力边界 | 内建版**保留**：文本收发/一问一答、群主/管理判定、PUBLIC/PRIVATE 路由与降级、背压限频健康（插件已有等价复用）；**砍掉**：投递对账、会话/消息持久化与后台 UI、富文本 parse_mode、批量发送（简化为逐目标单发）、媒体 |
| D9 | 配置文件形态 | **两文件**：`im.yml`（schema：backend + 每平台凭据/开关，用户仅手配凭据）+ `im_bindings.yml`（运行时数据：会话绑定，命令可写，非手配主路径） |
| D10 | 绑定权限 | 首版**仅控制台 / 游戏内 op** 可执行绑定（防劫持；bootstrap 信任）；群内自动发现只提示 ID、不授予任何权限 |
| D11 | 自动发现提示去向 | 未绑定会话的提示**只进控制台日志 + status 候选列表**，不向陌生群回消息打扰 |
| D12 | 体验命令 | **并入现有命令体系**（建议 `/orzmc im <setup\|status\|bind\|test>`），不单立 `/im` 命令 |
| D13 | 服务器地域与代理 | 目标部署为**国内服务器**：直连 Telegram/Discord 域名不可达 → builtin 连接层必须支持**可选 HTTP 代理 / API 基址覆盖**（JDK HttpClient ProxySelector；TG 参考 EasyBot `base_url` 自定义）；无代理环境下 TG/DC 平台不可用并在文档明示 |
| D14 | QQ 双通道与频控红线 | QQ 区分**被动回复（带 `msg_id`，短窗口）与主动消息（配额/频控）**：builtin QQ 发送优先被动通道；主动广播沿用现有聚合/节流（红线：48 条打爆 40034100 事故）——被动窗口时长与主动配额在 P3a 前按官方文档核验 |

> D5 与「先 TG 试点」的早期草案不同——owner 以现网主平台 QQ 为优先。各平台仍建议首个落地平台同时铺「通用骨架 + 该平台 adapter」，后续平台只是往骨架里填 adapter。
> D6–D14 共同定义内建版 = **轻量直连、低上手成本**：仅保留「文本收发 + 群主/管理判定 + PUBLIC/PRIVATE 路由 + 健康告警」；凭据手配最少化，会话绑定命令化，连接/Token/断线全部由骨架层自动管理；国内服务器环境下 TG/DC 依赖代理（D13），QQ 主动消息严守频控红线（D14）。

## 1. 背景与目标

插件群消息/管理指令目前**依赖外部 EasyBot 网关进程**（OrzMC 只连它的 REST + WebSocket，见
`infra/bot/`）。外部服务单点与运维负担促使评估「把跨平台双向文本通信内建进插件」。调研结论：

- **四平台（QQ/飞书/Discord/Telegram）官方协议全部可手写实现（HTTP+WS+JSON），运行时依赖增量为 0**——现有
  JDK `HttpClient`（AsyncHttp）+ `Java-WebSocket`（已打进 jar 137KB）+ Gson 即覆盖全部需要；
- **官方 SDK 路线否决**：JDA/telegrambots/飞书 SDK 体积 MB 级，叠加会撞 Hangar 10MB 上限（当前 shadowJar 3.1MB）；
- **EasyBot 已稳定服役**，内建开发需要时间 → **双通道并存 + 可切换**是过渡与兜底策略（本方案）。

目标：
1. `backend=easybot`（默认）= 现状零风险，始终可回退；
2. `backend=builtin` 落地后，内建各平台 adapter 逐个替代；
3. 业务层（BotCommandService/Notifier/审核等）**零改动**——两通道对外语义一致。

非目标（v1）：平台级混合路由、运行时无感热切、媒体/富文本/交互消息（仅文本，D6）、QQ 个人号（无公共 API）、微信（不可行，已移出支持列表）。

### 1.1 能力边界对照表（D8 展开）

内建版相对 EasyBot 的能力取舍（「插件已有等价」指直接复用现有 `infra/` 代码，不重复实现）：

| EasyBot 能力 | 内建版处理 |
|:--|:--|
| 多平台协议归一 / 文本收发 + 一问一答 / PUBLIC-PRIVATE 路由与降级 | ✅ **保留（核心）** |
| `sender.role` 群主/管理员判定（平台官方数据） | ✅ **保留（核心）** |
| 出站背压 / 限频 / 健康聚合（`/bot`） | ✅ 插件已有等价（Semaphore / ThrottledLogger / HealthRegistry） |
| WS 断线重连 / 心跳 / 文本分段 | ✅ 插件已有等价（RobustWebSocketClient / MessageFormatter） |
| 投递对账（outbox / delivery ledger / 幂等持久化） | ❌ 砍（D7：尽力一次不重试） |
| 会话/消息持久化 + 后台 UI + API Key / Target Grant 管理 | ❌ 砍（配置即授权，无管理面） |
| 媒体（图片/文件/语音） | ❌ 砍（D6：仅文本） |
| 富文本 parse_mode / 批量发送 | ❌ 砍（纯文本；广播简化为逐目标单发） |
| 配额 / 计费 / Prometheus / 审计 / 插件 SDK | ❌ 砍（与本插件无关） |

## 2. 现状接缝（代码事实）

```
BotModule(组合根, assembly/BotModule.java)
  └─ BotMessageServiceProvider.create(...) ──► OrzEasyBot（BotMessageService 唯一实现）
        BotMessageService 接口：setup / send(MessageEnvelope) / tryReconnectIfDisconnected /
                                reloadConfig / tearDown
  运行时重载：/orzmc config reload [easybot] ─► easyBotConfigReload 回调
              ─► OrzEasyBot.reloadConfig() ─► WebSocketLifecycle.reconcile()（fingerprint 比对重建）
```

- `BotMessageService` 即现成 driver 抽象；`BotMessageServiceProvider` 即 driver 选择点——**架构无需改动**，
  双模式 = Provider 按 `im.yml` 的 backend 选实现 + 外层协调切换。
- 健康聚合：`HealthRegistry`（当前 key=`easybot`）→ `/bot` 命令展示；builtin 沿用同构（key 细化到平台，如 `builtin.qq`）。
- 业务侧依赖仅 orzmc-api 的 `MessageEnvelope{PUBLIC|PRIVATE, text, format}` 与 `BotInboundHandler`——不感知 backend。

## 3. 目标架构

```
              BotMessageService（接口）          ← 业务层无感知
                        ▲
        ┌───────────────┴────────────────┐
   ImGatewayService（Facade，新增）         ← 持当前 driver，转发；reload 时若 backend 变化
        │                                    tearDown 旧 driver → setup 新 driver（D4：仅 reload/重启生效）
        │
        ├─ EasyBotDriver（≈ 现 OrzEasyBot，默认兜底）
        │     ├─ WebSocketLifecycle / HttpSender / InboundEventParser（现状原样）
        │     └─ 会话路由（抽共享层，见下）
        └─ BuiltinDriver（内建；按 D5 顺序逐平台挂 adapter）
              ├─ QqAdapter → FeishuAdapter → DiscordAdapter → TelegramAdapter
              │    各含：InboundSource（WS 网关/长轮询） + Sender（REST 出站） + 角色判定
              └─ 会话路由（复用共享层）
```

**共享路由层**（从 OrzEasyBot 抽出的通用部分，双 driver 复用，行为不变）：
MessageEnvelope 目标解析（PUBLIC→player_group 降级 admin_group；PRIVATE→admin_dm）、
入站会话门槛（fail-closed）、限频、格式化分段。抽出后 OrzEasyBot 退化为「EasyBot transport」。
抽取本身应是**零行为变更的重构**（现测试全绿为验收门槛）。

## 4. 配置与首次接入体验（D2/D9–D12）

### 4.1 文件拆分（D9）

`schema 文件`（版本治理：只读 + 升级门控）与 `运行时数据文件`（插件原子写，`updateConfig` 基建已有）分开：

```yaml
# im.yml —— schema 文件，用户手配最小集（backend + 凭据/开关）
config-version: 1
backend: easybot            # easybot | builtin（D1）；builtin 不可用时按 D3 停群+告警
platforms:
  qq:      { enabled: false, app_id: '', client_secret: '' }
  feishu:  { enabled: false, app_id: '', app_secret: '' }
  discord: { enabled: false, token: '' }
  telegram:{ enabled: false, token: '' }
```

```yaml
# im_bindings.yml —— 运行时数据文件，绑定命令维护（/orzmc im bind，D10），一般不用手写
sessions:
  qq:
    admin_group:  'group:<GroupOpenID>'
    player_group: ''                    # 空则降级 admin_group
    admin_dm:     'user:<UserOpenID>'
  # 其他平台同理（DC channel id / TG chat_id / Feishu chat_id）
```

- `easybot.yml` **不改动、继续被 EasyBotDriver 使用**；其 13 个连接微调参数（超时/重试/心跳/日志节流）在 **builtin 模式不再暴露**——全部由骨架层代码内置默认（复用 RobustWebSocketClient 现有 5s 起 / 60s 上限 / ±jitter / 稳定 20s 重置）；
- `im.yml` / `im_bindings.yml` 都注册进 ConfigService，`/orzmc config reload im` 可重载；
- QQ 会话值 = 平台原生 OpenID（与 EasyBot target 语义一致）——**两模式共用同一会话值，切 backend 无需改绑定**。

### 4.2 连接生命周期与 Token 刷新（统一骨架层）

各平台 token 事实：

| 平台 | 令牌 | 有效期 | 失效处理 |
|:--|:--|:--|:--|
| QQ | access_token（app_id+client_secret 换） | 2h | 到期前 60s 预刷新；鉴权错误 → 即时重换 + 重试一次（同 EasyBot qq adapter 策略） |
| 飞书 | tenant_access_token | 2h | 同上（官方允许旧 token 宽限期换新；错误码如 99991663/4 触发刷新） |
| Discord | Bot token | 长期 | 无刷新；401 = 配置错误 → 告警停用；Gateway resume/identify 自愈 |
| Telegram | Bot token | 长期 | 无刷新；401 = 配置错误告警；长轮询无状态天然自愈 |

统一抽象（builtin 骨架，首个 adapter 落地时实现）：

```
AccessTokenProvider 接口
 ├─ RefreshableTokenProvider（QQ/飞书：缓存 + 到期前 60s 预刷新 + onAuthError 触发刷新）
 └─ StaticTokenProvider      （Discord/TG：直通；onAuthError=配置错误只告警）

统一连接生命周期（每平台一个 Adapter）：
  start → 取 token → 鉴权/建连 → 心跳 liveness
  → 网络断/超时 ──► 指数退避重连（复用 RobustWebSocketClient 参数）
  → 鉴权类错误 ──► TokenProvider.onAuthError()（刷新一次）→ 重连
                 ──► 仍失败：健康降级 builtin.<platform> + 告警 + 继续退避
```

健康按平台分 key（`builtin.qq` 等），含 token 到期时间 / 下次重试 / lastError，供 status 命令一览。

### 4.3 首次接入与绑定（D10/D11/D12）

- **凭据获取无法全自动**（平台侧注册不可避免：QQ 开放平台审核 / 飞书企业应用 / DC 开发者后台 / TG BotFather）——`/orzmc im setup` 逐平台 checklist：凭据未配 / 已配未验证 / 已验证(bot 名)，附官方链接；配完自动 getMe 类自检；
- **会话 ID 自动化**（当前最大手动成本，尤其 QQ openid 平台 UI 无处可查）：adapter 收到未绑定会话消息 → **仅控制台日志 + `/orzmc im status` 候选列表**提示（D11）；QQ 机器人进群后任意一条消息即暴露群 openid，自动捕获；
- **绑定命令**（D10：仅控制台 / 游戏内 op）：

```
/orzmc im bind <platform> <group|user> <chat_id> <admin_group|player_group|admin_dm>
/orzmc im test <platform> <chat_id>      # 发一条测试消息验证下行可达
/orzmc im status                          # 连接/Token/绑定/候选一览
/orzmc im setup                           # 首次接入引导 checklist
```

- 手工编辑 `im_bindings.yml` 仍可作为兜底；命令最终挂载与命名在实现时敲定（D12）。

### 4.4 builtin 实现约束清单（IM 专家评审补充，R3–R11）

下列为骨架层/各 adapter 实现时必须满足的约束（非决策级，随 P 阶段兑现）：

| # | 约束 | 归属 |
|:--|:--|:--|
| R3 | **凭据单实例消费**：一个 bot 凭据仅允许一个 builtin 实例消费（TG getUpdates 长轮询互斥、QQ/DC/飞书单 bot 事件单连接）；多服需多 bot，文档明示 | 骨架/文档 |
| R4 | **消息源过滤**：各 adapter 滤除自身 bot 与其它 bot 消息（TG `from.id==bot`、QQ `author.bot`、DC `author.id==bot`、飞书 sender_type=bot）再进命令层，防回声环 | 各 adapter |
| R5 | **凭据安全纪律**：日志/异常打码 token/secret；`/orzmc im status` 仅 op 可见；绑定文件不落 git | 骨架 |
| R6 | **入站限频保留**：builtin 各 adapter 入站同样套用现有限频（EasyBot 网关曾 100/s；防群内刷屏触发批量命令） | 骨架 |
| R7 | **单条文本上限常量表**：各平台单条上限（QQ 群/私聊待查证、DC 2000 普通/4000 boost、TG 4096 官方最新、飞书按文档）对接现有 formatter 分段 | 各 adapter |
| R8 | **TG offset 语义**：长轮询严格推进 offset；**内存 offset 只防进程内重连重复**（EasyBot 同款）；进程重启后 24h 未确认积压会重拉 → 启动首拉后丢弃积压（轻量，不做持久化） | TG adapter |
| R9 | **QQ 沙箱测试环境**：QQ 官方沙箱（可加测试成员）作为 P3a 冒烟与回归环境 | 测试 |
| R10 | **PUBLIC 广播逐平台隔离**：单平台发送失败不阻塞其他平台（try/catch + 局部日志），承接 D7 | 共享路由层 |
| R11 | **出站白名单域名清单**：运维文档列出需放行域名（QQ `bots.qq.com`/`api.bot.qq.com` + wss 网关由 API 下发、飞书 `open.feishu.cn`、TG `api.telegram.org`、DC `discord.com`）；有防火墙的服务器需放行 | 文档 |
| R12 | **线程模型红线**：网络/WS/轮询线程回调**不得直接触碰 Bukkit API**——入站事件必须经 `ServerFacade.runSync` / SafeScheduler 调度到服务器线程后再进命令层（对齐 folia-luckperms-gotchas 红线） | 骨架 |
| R13 | **生命周期清理**：每平台 WS/轮询线程与 ScheduledExecutor 在 tearDown 必须 shutdown 并等待终止（防 reload 泄漏）；线程命名带平台前缀便于诊断 | 骨架 |

## 5. 两模式语义一致性（双通道可行前提）

| 语义 | EasyBotDriver（网关归一） | BuiltinDriver（平台 API 直判） |
|:--|:--|:--|
| `sender.role` 取值 | `Owner/Admin/Member/Bot/Anonymous` | 各 adapter 归一为同枚举 |
| QQ 群主/管理 | 事件自带 `author.member_role`（owner/admin） | 同左（事件自带，零 API） |
| Telegram | `getChatAdministrators` creator/administrator | 同左（per-chat 缓存 + chat_member 事件失效） |
| Discord | 群主 `GET /guilds/{id}`；管理 `member.permissions` ADMINISTRATOR 位 | 同左 |
| 飞书 | `GET /im/v1/chats/{id}` owner_id + user_manager_id_list | 同左（role cache TTL + 事件失效） |
| 私聊 | 无角色 → 管理指令仅群内 | 同左（admin_dm 仅下行告警 + 入站门槛） |

> 判定来源在两通道都是**平台官方数据，不配置 ID 白名单**（owner 决策）。业务层
> `guardAdminCommand(isAdmin)` 无需区分 backend——这是双实现能并存的最重要前提。

## 6. 平台实现要点（builtin 落地依据，均已对照 EasyBot 源码/官方协议取证）

| | QQ（官方开放平台） | 飞书 | Discord | Telegram |
|:--|:--|:--|:--|:--|
| 鉴权 | `app_id+client_secret`→`bots.qq.com` 换 access_token（2h 缓存刷新） | `app_id+app_secret`→`/open-apis/auth/v3/tenant_access_token/internal` | `Authorization: Bot <token>` | token 在 URL 路径 |
| 上行 | 出站 WS Gateway（identify/heartbeat/resume/session，同 Discord 构型） | 事件订阅**长连接 WS**（端点 `{domain}/callback/ws/endpoint`，集群单活） | Gateway WS `gateway.discord.gg`（opcode/intents/resume） | `getUpdates` 长轮询（免公网） |
| 下行 | `POST /v2/groups/{openid}/messages`（`msg_type: 0` 文本；带 `msg_id` = 被动回复通道）；C2C `/v2/users/{openid}/messages` | `POST /im/v1/messages`（chat_id, msg_type text） | `POST /channels/{id}/messages`（2000 上限，boost 服可 4000） | `sendMessage`（4096 上限，官方最新仍 4096） |
| 身份 | openid（非 QQ 号） | openid + chat_id | snowflake + roles | 数字 id |
| 入站限 | 事件 `author.member_role` 自带角色 | 事件无角色→ chats API 查询（缓存） | 事件 `member.permissions` 自带 | 无角色→ admins API（缓存） |
| 政策/门槛 | 开放平台注册+审核+进群方式（**需 owner 后台核验**） | 需企业组织；事件端点细节核验 | 开放；>100 guilds 需申请 intent | 完全开放（BotFather 即建即用） |
| 预估 adapter 量 | ~600 行 | ~500 行 | ~600 行 | ~300 行 |

> **协议复核（2026-09-03，对照 EasyBot main 源码 + 官方文档）**：QQ 群文本 `msg_type: 0`（早期流传 1 系误传）；`msg_id` 仅回复时携带 = 被动回复通道（D14）；事件双轨 `GROUP_AT_MESSAGE_CREATE`（@机器人）与 `GROUP_MESSAGE_CREATE`（全量群消息）**均带 `author.member_role`**；intents：群+C2C 单 intent `GROUP_AND_C2C_EVENT = 1<<25`，频道私域 `GUILD_MESSAGES = 1<<9`（官方：[event-emit](https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/interface-framework/event-emit.html)）；飞书长连接 WS 端点为 `{domain}/callback/ws/endpoint`；TG 上限 4096 经官方最新文档核实；DC 2000（boost 4000）。

体积：四平台 adapter 全落地预计 shadowJar 增加 <100KB（仅业务代码，无新依赖）——10MB 上限余量充足。

## 7. 实施路线

| 步 | 内容 | 验收（对应测试） |
|:--|:--|:--|
| **P1** | `im.yml` + `im_bindings.yml` schema/文件注册（D9）+ Provider 按 backend 选 driver（builtin 未实现时 backend=builtin 报「不可用」并停群功能+告警，D3）+ Facade 骨架 | 新单测：backend 解析/选择/不可用路径；现有 EasyBot 测试全绿；`backend=easybot` 行为与现状一致 |
| **P2** | 共享路由层抽取（OrzEasyBot 拆 transport/路由，零行为变更） | 现有 26 个 EasyBot 相关测试 + e2e（Paper/Folia）全绿，diff 仅移动 |
| **P3a** | **QQ adapter**（D5 首个）：WS Gateway + member_role 角色 + `/v2/...` 下行 + openid 会话；同时铺骨架：AccessTokenProvider + 统一连接生命周期（§4.2）+ 绑定/测试/status/setup 命令（§4.3，并入 /orzmc）；D13 代理落地 = 扩展 AsyncHttp 支持 per-proxy HttpClient（现状按 connectTimeout 缓存 client，proxy 入 key） | QQ adapter 单测（MockWebServer + mock gateway）；绑定命令权限测试（D10）；真实平台冒烟脚本 |
| **P3b-e** | 飞书 → Discord → Telegram adapter 逐个挂载 | 同 P3a 模式 |
| **P4** | backend=builtin 端到端（`/bot` 健康/投递扩展为**多平台 key 聚合展示**、群指令一问一答）+ 文档（features.md §2.5 重写为双通道、R11 域名清单） | e2e 双核心全绿 |

每步独立 PR（AGENTS.md：单 PR <500 行），逐步合并、逐步可回退（backend 一行切回 easybot）。

## 8. 测试策略

- **模式选择/切换**：`im.yml` backend 解析、Provider 选择、builtin 不可用路径 → JUnit（MockBukkit 不需要，纯装配级）；
- **builtin 各 adapter**：协议层单测用 OkHttp MockWebServer（已有 testImplementation）模拟平台 REST；QQ/Discord/飞书
  WS 网关用本地 WebSocket mock（复用测试基建思路），角色判定矩阵化断言；
- **回归门槛**：P2 重构必须现测试全绿（EasyBot 相关 26 文件 + 全量 1600+）再合并；
- **真实平台冒烟**：QQ 优先（D5），按 `docs/dev/folia-luckperms-gotchas.md` §6 测试服方法论，事件/通知/审核改动真机验证后再合。

## 9. 风险与待核验清单

1. **QQ 政策门槛**（最高风险，先于 P3a 排期核验）：开放平台注册（个人/企业）、机器人进群方式、沙箱→正式流程——owner 后台确认；
2. **飞书企业组织**：无企业资源则该平台实际不可用（需从 builtin 列表剔除或仅文档支持）；
3. **字段演进**：EasyBot 0.0.33 事件 `sender.nickname/user_id` 与 main `sender.name/id` 不一致——若升级网关需适配（已在 EasyBot#114 文档落账）；
4. **会话值兼容**：QQ 填 openid 而非群号（现有生产 easybot.yml 若填错则两模式都发不出，需核对现网值）；
5. **国内服务器 × TG/DC 可达性**（D13 落地形态待验）：代理/API 基址覆盖方案需在首个非国内平台 adapter（Discord/TG 顺序靠后）前定案；QQ/飞书不受影响；
6. **QQ 被动窗口与主动配额数字**（D14）：被动回复窗口时长、主动消息频次配额、文本长度上限需按官方文档/沙箱实测核验后固化进常量表（R7）——直接决定一问一答超时处理与广播节流参数。
