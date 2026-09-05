# OrzMC 插件功能清单

> 多平台机器人集成的 Paper / Folia 服务器管理插件
>
> 本文档系统梳理插件的所有功能模块，方便用户快速了解插件能力。
>
> **运行环境**：Paper 26.x 或 Folia（`folia-supported: true`，同一 JAR 双运行时兼容）。适配细节与测试策略见 [Folia 迁移评估文档](folia-migration.md)。

> **测试与质量**：
> - 测试策略 / 质量体系：[quality-testing-plan.md](quality-testing-plan.md)
> - 自动化 E2E 套件：`e2e/`（`run-all.sh`，01-06 用例：Bot 命令/玩家命令/安全拦截/备份维护/群消息/权限审核）——详见 [e2e/README.md](../e2e/README.md)
> - 历史手工用例与验收快照（已归档）：见 [docs/README.md](README.md)「历史快照」节

---



## 一、白名单管理

### 1.1 强制白名单
- 启动时自动开启服务器白名单（`whitelist.force_whitelist`，默认 `true`）
- 未添加到白名单的玩家无法进入服务器
- 检测到白名单被意外关闭时，通过 Bot 发送告警通知

### 1.2 踢出提示消息
- 非白名单玩家被踢出时显示自定义消息
- 支持配置 QQ 群号、Discord 邀请链接（可点击）、最多 5 个联系方式（名称 + 平台）

### 1.3 Bot 命令管理
- `$a <玩家>` — 添加玩家到白名单（支持批量：空格或逗号分隔）
- `$r <玩家>` — 从白名单移除玩家（支持批量）
- `$w` — 查看白名单列表（分页显示，带在线/离线状态）

### 1.4 不活跃玩家清理
- `$w` 命令触发清理超过 N 天（默认 90）未上线的白名单玩家
- 自动踢出当前在线的被移除玩家

---

## 二、多平台 Bot 系统

### 2.1 支持的平台

插件提供**双通道**接入多平台 IM（方案：[docs/dev/im-gateway-inhouse.md](dev/im-gateway-inhouse.md)）：

- **EasyBot 网关（默认，`backend: easybot`）**：外部网关服务统一接入 QQ、Telegram、Discord、飞书和微信；
  EasyBot 使用 WebSocket 向插件推送入站消息，插件通过 HTTP API 发送回复和服务器通知；
- **内置直连（`backend: builtin`，QQ / 飞书 / Telegram / Discord 已落地）**：插件直连各平台官方 API（QQ/Discord WS 网关 + REST，飞书长连接 WS，Telegram 长轮询），不再依赖外部网关进程；
  业务层命令/通知语义与 EasyBot 通道完全一致。⚠️ 会话值体系不同：EasyBot 通道用后台分配的「会话 key」（如 `qq:conv_xxx`，见 §2.5），builtin 通道用**平台原生会话标识**（QQ 为 `group:<OpenID>` / `user:<OpenID>`，飞书为 `group:<chat_id>` / `user:<chat_id>`，Telegram 为 `group:<chat_id>` / `user:<chat_id>`，Discord 为 `group:<channel_id>` / `user:<user_id>`；接入见 §2.6 QQ、§2.7 飞书、§2.8 Telegram、§2.9 Discord）——切 backend 后需按新通道重新绑定会话。

### 2.2 Bot 命令一览

所有命令使用可配置前缀（`easybot.cmd_prompt_char`，默认 `$`），在命令后加 `?` 可查看详细用法。

| 命令 | 功能 | 权限 |
|------|------|------|
| `$l` | 查看在线玩家 | 通用 |
| `$w` | 查看/清理白名单 | 通用 / 管理员 |
| `$h` | 查看帮助信息 | 通用 |
| `$a` | 添加白名单 | 管理员 |
| `$r` | 移除白名单 | 管理员 |
| `$b` | 触发世界备份 | 管理员 |
| `$o` | 世界优化 | 管理员 |
| `$e` | 执行控制台命令，输出完整回传群聊（含异步输出，日志窗口捕获 + 噪音过滤 + 30 行截断） | 管理员 |
| `$v` | 查看/处理审核申请（`$v l` 列表 / `$v y`/`yes` 通过 / `$v n`/`no` 拒绝；同名多类型申请用 `$v y <typeId> <玩家>`） | 管理员 |
| `$p` | 权限升降级（`$p u`/`up` 升级 / `$p d`/`down` 降级，default→member→builder→admin） | 管理员 |
| `$d` | 访问规则管理（`$d <IP>` / `$d -<IP>` 黑名单；`$d player <type> <value>` 加 / `$d -player <type> <value>` 删玩家名规则；type: `exact`/`prefix`/`suffix`/`contains`/`glob`/`regex`） | 管理员 |

### 2.3 通知系统

插件将服务器事件实时推送到 Bot 群/频道：

- **玩家事件**：加入、退出、踢出（含坐标、世界、权限组、在线人数）
- **安全事件**：TNT 爆炸告警（突发聚合）、GeoIP 拦截告警、GeoIP 上游异常告警（私信管理员）
- **维护事件**：备份/优化进度（阶段、百分比、速率、ETA）
- **系统事件**：服务器启动/停止、异常告警、白名单开关告警
- **权限事件**：晋升申请提交/撤回/通过/拒绝、权限升降级通知

所有通知消息通过 **可配置模板** 渲染（50 余个消息模板，涵盖事件通知、Bot 命令反馈、审核/晋升与维护进度），支持变量替换。

### 2.4 Bot 健康状态

- 游戏内 `/bot` 命令查看 **EasyBot 通道**（backend=easybot）连接状态：`enabled`、`http`（Ok / Unknown / NotOk）、`ws`（Ok / NotOk）三个彩色状态词，http 与 ws 异常时可点击跳转 `/bot http`、`/bot ws` 查看详情
- backend=builtin 时改用 `/config im status` 查看通道健康（含平台连接/绑定/未绑定候选）
- 执行命令时自动尝试重连 WebSocket

### 2.5 EasyBot 网关配置指南（backend=easybot 默认通道）

> 本节适用于默认的 **EasyBot 网关通道**（`im.yml` 中 `backend: easybot`）；使用插件内置直连请见下文 **§2.6 builtin 内置直连配置指南**。
>
> EasyBot 是一个统一的 IM 网关服务，对外暴露一套 REST API + WebSocket 事件推送接口，
> 屏蔽了各 IM 平台（QQ / Telegram / Discord / 飞书 / 微信）的协议差异。
> 项目地址：[https://github.com/easyIndie/EasyBot](https://github.com/easyIndie/EasyBot)

#### 安装 EasyBot

1. 参考 [EasyBot 官方文档](https://github.com/easyIndie/EasyBot) 完成网关服务部署
2. 启动后通过浏览器访问 EasyBot 管理后台（默认 `http://<部署地址>`）
3. 在管理后台创建 **客服类 API Key**，用于插件与 EasyBot 之间的接口鉴权
4. 在管理后台为各平台添加会话并获取对应的 **会话 key**，填入插件配置

#### 获取配置值

EasyBot 的配置值并非平台原生 ID，均需从 EasyBot 管理后台获取：

| 配置项 | 获取方式 | 示例值 |
|--------|---------|--------|
| `api_key` | EasyBot 后台 → API 密钥 → 创建「客服类」密钥 | `sk-xxxxxxxxxxxx` |
| `admin_group` | EasyBot 后台 → 会话管理 → 创建/查看会话 → 复制**会话 key** | `qq:conv_xxxxxxxx` |
| `player_group` | 同上 | `qq:conv_yyyyyyyy` |
| `admin_dm` | 同上（管理员私聊会话） | `qq:conv_zzzzzzzz` |

> ⚠️ **注意：** `admin_group` / `player_group` / `admin_dm` 的值不是 QQ 群号、Discord 频道 ID 等平台原生标识，而是 EasyBot 管理后台为每个会话分配的 **会话 key**。易混淆时请以 EasyBot 后台显示的值为准。

#### 配置 OrzMC 对接 EasyBot

修改 `easybot.yml`：

```yaml
# EasyBot 连接地址（替换为你的部署地址）
api_server: 'http://127.0.0.1:8080'
ws_server: 'ws://127.0.0.1:8080'
# 客服类 API Key（从 EasyBot 管理后台获取）
api_key: 'sk-your-customer-service-api-key'
# 文本解析模式：markdown / html / none
parse_mode: 'markdown'
```

启用需要接入的平台（例如同时启用 QQ 和 Telegram）：

```yaml
platforms:
  qq:
    enabled: true
    admin_group: 'qq:conv_xxxxxxxx'    # 管理群会话 key（EasyBot 后台获取）
    player_group: ''                   # 玩家群（留空降级 admin_group）
    admin_dm: 'qq:conv_yyyyyyyy'       # 管理员私聊会话 key
  telegram:
    enabled: true
    admin_group: 'telegram:conv_zzzzzzzz'
    player_group: ''
    admin_dm: 'telegram:conv_wwwwwwww'
```

#### 消息路由规则

EasyBot 适配器只保留公开与管理员私聊两类路由：

```
消息发送请求（MessageEnvelope）
    │
    ├─ PUBLIC 类型 → 遍历所有已启用平台的 player_group
    │                  ↓ 为空则降级为 admin_group
    │
    └─ PRIVATE 类型 → 遍历所有已启用平台的 admin_dm
```

事件投递目标由代码固定：玩家状态、服务器状态、TNT、GeoIP 和白名单事件走
PUBLIC；异常告警（含 GeoIP 上游异常私信）与维护失败事件走 PRIVATE。

#### 飞书多实例注意事项

> **⚠️ 飞书 WebSocket 多实例限制：** 飞书开放平台 WebSocket 事件订阅使用**集群模式**——同一飞书应用**只随机推送到一个 WebSocket 客户端**。部署多个 EasyBot 实例时，需确保：
> - **方案一：单实例独占**——只启动一个 EasyBot 实例接收飞书事件，其他实例通过配置 `enabled: false` 停用飞书平台；
> - **方案二：多应用隔离**——每个 EasyBot 实例注册不同的飞书应用（不同的 `app_id` / `app_secret`），各自独立接收事件。

---

### 2.6 builtin 内置直连（backend=builtin）· QQ 接入操作手册

> 本节为 **QQ** 平台接入手册；飞书平台接入见 **§2.7**、Telegram 见 **§2.8**、Discord 见 **§2.9**（平台侧准备不同，插件侧配置/会话绑定/验收流程同构）。

> **适用范围**：服务器管理员。跟随本手册从零把 QQ 机器人接入插件内置直连通道（不依赖 EasyBot 网关）。
> **验证状态**：QQ 平台全流程已真机验收通过（2026-09-04，本地 Paper 26.2；Paper/Folia 同一 JAR）。
> 三类会话均已实测闭环：管理群（上行问答+管理指令 fail-closed）、玩家群（PUBLIC 广播接收）、管理员私聊（下行 + 绑定后私聊问答）；绑定跨重启持久化验证通过。
> **预计耗时**：约 30 分钟（不含 QQ 平台注册/审核等待）。文末附「验收清单」与「常见问题」。
> QQ 平台相关界面名称以 [QQ 开放平台](https://q.qq.com/) 当前版本为准。

---

#### 0. 概念速览（先读，避免踩坑）

插件提供两条消息通道，由 `im.yml` 的 `backend` 选择：

| 通道 | backend | 会话值 | 备注 |
|------|---------|--------|------|
| EasyBot 网关（默认） | `easybot` | EasyBot 后台「会话 key」（如 `qq:conv_xxx`） | 需部署 EasyBot 进程；用法见 §2.5 |
| **内置直连** | `builtin` | QQ **平台原生 OpenID**（`group:<群OpenID>` / `user:<用户OpenID>`） | 本手册；与 EasyBot 不共用一套值 |

涉及文件：

- `plugins/OrzMC/im.yml` —— 通道与平台凭据（`backend` / `platforms.qq`），**改后重启生效**；
- `plugins/OrzMC/im_bindings.yml` —— 会话绑定（`sessions.qq.*`），`/config im bind` 写入后**即时生效**（一般不用手改）。

权限红线：**bind/status/test 仅控制台或游戏内 op 可用**（D10）；一个 QQ 机器人凭据只允许一个实例消费事件（R3），若你同时在跑 EasyBot 连同一机器人，请先停用其一再切 builtin。

#### 阶段 A · QQ 平台侧准备（一次性，约 15 分钟）

**A1 注册并创建机器人**
1. 打开 [QQ 开放平台](https://q.qq.com/) → **立即注册**（个人/企业实名均可）并登录；
2. **创建机器人**，填名称/简介/头像；测试阶段可选**私域机器人**（无需正式审核，配合沙箱即可完整验证）。

**A2 获取凭据**
1. 进入机器人管理 → 左侧 **开发设置**；
2. 复制 **BotAppID**（如 `123456789`）→ 对应插件 `app_id`；
3. **AppSecret** 点**查看**后复制（**仅首次查看时可复制，离开页面即不可见**）→ 对应插件 `client_secret`；⚠️ 妥善保管，勿提交到版本库或分享给他人（R5）。

**A3 配置 IP 白名单（必须）**
- 开发设置 → **IP 白名单**，加入运行服务器的**公网出口 IP**（查看：`curl -s https://api.ipify.org`）；
- 本机/服务器走代理工具（Surge/Clash 等）时，让 `api.bot.qq.com` 与 `bots.qq.com` **走直连（DIRECT）**，或把代理出口 IP 加进白名单；
- 漏配会报 `接口访问源IP不在白名单`。

**A4 准备沙箱测试群（推荐测试用）**
1. 建一个 QQ 群，**群名须含“测试”二字**，且你是群主；
2. 开发设置 → **沙箱配置** → 下拉选择该测试群并添加；
3. （可选）沙箱私聊白名单里添加允许与机器人私聊的 QQ 号。

**A5 开通两项消息权限（必须，真机验证）**
1. **机器人可获取的群聊消息范围 → 获取群内全部消息**：不开则插件只收到 @ 机器人的事件，收不到普通群消息；
2. **机器人主动在群聊内发言 → 开启**：不开则主动下行/广播会被 QQ 拒绝（一问一答的被动回复不受影响）。

**A6 把机器人拉进测试群**
- 手机 QQ → 进测试群 → 群设置 → **群机器人** → 找到你的机器人 → 添加。

> **阶段 A 完成标志**：能在群里 @机器人 得到 QQ 自带的“机器人已接入”类反馈即可。

#### 阶段 B · 插件侧配置（一次重启）

**B1 定位配置文件**：服务器 `plugins/OrzMC/im.yml`（首次运行由插件生成）。

**B2 填写配置**：

```yaml
backend: builtin
platforms:
  qq:
    enabled: true
    app_id: '你的BotAppID'
    client_secret: '你的AppSecret'
```

**B3 重启服务器**（backend 与凭据在启动时装配；`/config reload im` 只重载文件不重建通道，因此**切 backend / 改凭据必须重启**）。

**B4 确认通道启用**：重启后看控制台应出现：

```
[OrzMC] IM backend=builtin：启用内置直连（可用平台：qq）。
```

随后 QQ 自动连接出站网关（换发 2h access_token、到期前自动预刷新），几十秒内出现：

```
[OrzMC] [qq] 网关连接已建立
[OrzMC] [qq] 发送 identify（intents=33554432）
[OrzMC] [qq] 网关 READY（会话已建立）
```

> **阶段 B 完成标志**：控制台出现 `[qq] 网关 READY`。也可用 `/config im status` 复核：应显示 `backend: builtin`、`QQ 平台: 启用`、`connection: 已连接`。
>
> 若 B 阶段未出现 READY：见文末「常见问题」——最常见是 A3 IP 白名单、A5 权限未开或凭据抄错。

#### 阶段 C · 会话发现与绑定（10 分钟）

QQ 群/私聊的 OpenID **在平台界面查不到**，由插件自动发现（D11）：

**C1 触发发现**：在测试群发任意一条消息（如 `hi`）。控制台应出现：

```
[OrzMC] [qq] 忽略未绑定会话消息 target=qq:group:F73A3B0AE04A8E82B75039A1519AE8EB（绑定见 /config im bind，候选入 status）
```

末尾 `F73A...` 就是该群的 **GroupOpenID**（也会出现在 `/config im status` 的候选列表）。

**C2 绑定会话**（控制台或游戏内 op 执行）：

```
/config im bind qq group <群OpenID> admin_group
/config im bind qq group <群OpenID> player_group   # 玩家群（可略；留空则公开通知降级发管理群）
/config im bind qq user <用户OpenID> admin_dm      # 管理员私聊（可选）
```

成功提示：`qq 会话绑定已写入并持久化：admin_group = group:<群OpenID>（im_bindings.yml；入站/广播即时生效）`。绑定即时生效，**无需重启**；绑定后该会话自动从候选清除。

> 三类会话含义：`admin_group` 管理群（群主/管理员可发管理指令）；`player_group` 玩家群（公开通知）；`admin_dm` 管理员私聊（仅下行通知）。

#### 阶段 D · 端到端验证（验收清单）

按顺序执行并在“期望结果”处打勾；全部通过即接入完成：

| # | 验证项 | 操作 | 期望结果 |
|---|--------|------|----------|
| 1 | 通道健康 | `/config im status` | `QQ 平台: 启用` + `connection: 已连接` |
| 2 | 上行一问一答（被动回复） | 群内 @机器人 发 `$h` | 机器人回复命令帮助 |
| 3 | 上行通用命令 | 群内 @机器人 发 `$l` | 返回在线玩家列表 |
| 4 | 管理指令权限 | 群主/管理员发 `$a <玩家名>`（示例） | 非 owner/admin 角色的成员被拒（fail-closed）；owner/admin 正常执行 |
| 5 | 下行主动发言 | `/config im test qq group <群OpenID> 你好` | 群里收到“你好” |
| 6 | 绑定持久化 | 重启服务器后 `/config im status` | 绑定仍在（sessions.qq.admin_group 已落盘） |
| 7 | 通知推送 | 触发一条服务器通知（如玩家上下线） | player_group/admin_dm 收到对应通知（按你的绑定与事件类型） |

> **完成标志**：1–6 全过即完成 QQ 平台接入；第 7 项用于核验广播/通知路径（依赖 A5 的“主动发言”权限已开）。

#### 管理命令速查

| 子命令 | 功能 |
|--------|------|
| `setup` | 首次接入 checklist（backend/凭据/绑定引导） |
| `status` | 通道健康 + 会话绑定 + 未绑定候选一览 |
| `bind <平台> <group\|user> <会话id> <admin_group\|player_group\|admin_dm>` | 绑定会话并持久化（仅控制台/游戏内 op） |
| `test <平台> <group\|user> <会话id> <文本>` | 向指定会话发一条测试文本验证下行 |

#### 出站域名放行清单（R11，有防火墙/白名单的服务器需放行）

| 用途 | 域名 | 方向 |
|------|------|------|
| QQ 鉴权（换 access_token） | `bots.qq.com` | HTTPS 出站 |
| QQ 开放 API（网关地址 / 消息发送） | `api.bot.qq.com` | HTTPS 出站 |
| QQ 出站网关 WS | 由 `/gateway/bot` 接口下发（`wss://…`） | WSS 出站 |

#### 常见问题（真机踩坑实录）

| 现象 | 原因 / 处理 |
|------|------------|
| 重启后日志 `IM backend=builtin 已选择，但无任何可用平台…已停用群功能` | im.yml 平台未启用或凭据缺失/抄错（D3：停群告警不自动回退）。修好后**重启** |
| 启动后一直不见 `[qq] READY`，或报 `接口访问源IP不在白名单` | A3 IP 白名单漏配 / 代理未放行 `api.bot.qq.com`、`bots.qq.com` |
| 群里发普通消息插件完全没反应（只收到 @ 事件或收不到） | A5 第 1 项权限「群内全部消息」未开——真机验证：未开前非 @ 消息不推送，开启后立即收到 |
| 群内能一问一答（被动回复 OK），但 `/config im test` 等主动下行无动静 | A5 第 2 项「主动发言」开关未开；被动回复不受影响 |
| 健康 `builtin.qq` 未连接 + `token not exist or expire`（11244） | access_token 被提前失效：插件自动强换并重试一次；持续出现则核对 app_id/client_secret |
| 连接后反复 `收到 op9（无效会话）` | identify token 缺 `QQBot ` 前缀（插件已内置正确格式）；持续出现多为凭据/token 问题（同上） |
| 重连被限频 `HTTP 400 code 100017` | `/gateway/bot` 有频率限制；插件已内置 60s URL 缓存与退避，避免手工高频重启触发 |
| 广播/通知类主动消息失败 | QQ 对主动消息有权限与配额约束（D14）：被动回复（一问一答）默认可用；主动推送请确认 A5 权限已开，并控制发送频率 |
| 无法识别网关帧 / 心跳异常 | 网络抖动会自动重连；持续异常检查代理/DNS（解析到 `198.18.x` 多为代理拦截特征） |
| EasyBot 与 builtin 同时连同一机器人 | 一个凭据只允许一个实例消费事件（R3）：切通道前先停用其一 |

#### 能力边界

- **仅文本**（D6）：图片/文件/语音不支持；
- **发送尽力一次不重试**（D7）：失败经健康告警，无投递对账；
- 被动回复窗口、主动消息配额、单条文本上限按 QQ 官方当前规则（被动回复窗口实测参考 ~分钟级，R7 常量待沙箱实测固化）；
- Discord/Telegram 平台按同一骨架后续接入，届时本文档相应平台小节同步更新。Telegram 接入手册见 **§2.8**（长轮询免公网入站 + 代理出墙，与 QQ/飞书同构）。

---

### 2.7 builtin 内置直连（backend=builtin）· 飞书接入操作手册

> **适用范围**：服务器管理员。跟随本手册从零把飞书机器人接入插件内置直连通道（不依赖 EasyBot 网关）。
> **验证状态**：飞书平台全流程已真机验收通过（2026-09-05，本地 Paper 26.2）。三类会话均已实测闭环：
> 管理群（上行问答 + 管理指令权限 fail-closed）、玩家群（PUBLIC 广播接收）、管理员私聊（PRIVATE 下行 + 绑定后私聊问答）。
> **预计耗时**：约 30 分钟（不含飞书企业自建应用创建/权限审核等待）。文末附「验收清单」与「常见问题」。
> 飞书平台界面名称以[飞书开放平台](https://open.feishu.cn/)当前版本为准。

---

#### 0. 与 QQ 接入的差异速览

飞书接入流程与 §2.6 QQ 手册同构（插件侧 `im.yml` 配置、`/config im bind` 会话绑定、`/config im status` 健康查看均一致），
差异在**平台侧准备**与**会话值**：

| 维度 | QQ（§2.6） | 飞书（本节） |
|------|-----------|-------------|
| 应用形态 | 开放平台机器人 | **企业自建应用**（需有飞书企业/团队租户） |
| 凭据 | BotAppID + AppSecret | App ID（`cli_` 前缀）+ App Secret |
| 鉴权 | access_token（2h 预刷新） | tenant_access_token（2h 预刷新，同一机制） |
| 入站通道 | 出站 WS 网关 | 事件订阅**长连接 WS**（二进制帧，无需公网） |
| 会话值 | `group:<GroupOpenID>` / `user:<UserOpenID>` | `group:<chat_id>` / `user:<chat_id>`（chat_id 形如 `oc_...`，群/单聊均为 chat_id；需从 D11 候选或平台侧获取） |
| 角色判定 | 事件自带 `member_role` | 事件无角色，插件查群信息 API（owner_id + 管理员列表，带缓存） |
| 单聊 | C2C user_openid | p2p 会话 chat_id（同为 `oc_` 前缀） |

涉及文件（与 QQ 相同）：

- `plugins/OrzMC/im.yml` —— 通道与平台凭据（`backend` / `platforms.feishu`），**改后重启生效**；
- `plugins/OrzMC/im_bindings.yml` —— 会话绑定（`sessions.feishu.*`），`/config im bind` 写入后**即时生效**。

权限红线：**bind/status/test 仅控制台或游戏内 op 可用**（D10）；一个飞书应用凭据只允许一个实例消费事件（R3），
且飞书长连接为**集群单活**（同一应用事件只推送到一个连接）——若你同时在跑 EasyBot 连同一飞书应用，请先停用其一再切 builtin。

#### 阶段 A · 飞书平台侧准备（一次性，约 15 分钟）

**A1 创建企业自建应用**
1. 打开[飞书开放平台](https://open.feishu.cn/) → **创建企业自建应用**（需有飞书企业/团队租户的管理员权限）；
2. 填写应用名称/描述/图标。

**A2 获取凭据**
1. 左侧菜单 **凭证与基础信息** → 复制 **App ID**（`cli_...`，对应插件 `app_id`）；
2. **App Secret** 点查看复制（对应插件 `app_secret`）；⚠️ 妥善保管，勿提交版本库/分享（R5）。

**A3 配置权限（必须）**
1. 左侧 **权限管理** → 开启：
   - `im:message` —— 发送和接收消息；
   - `im:message.group_msg` —— 【敏感权限，需管理员审核】获取群内所有消息（不 @ 也收群消息）；
   - `im:message.group_at_msg:readonly` —— 读取群聊 @ 机器人消息（默认）；
   - `im:message.p2p_msg:readonly` —— 读取发给机器人的单聊消息；
   - `im:chat` —— 获取群信息（**角色判定必需**：判群主/管理员）；
   - `contact:user.base` ——（可选）获取用户信息。
2. ⚠️ 敏感权限（`im:message.group_msg` 等）需管理员审核；**发布新版本并审核通过**后生效。

**A4 订阅事件（长连接）**
1. 左侧 **事件与回调** → 添加事件 `im.message.receive_v1`（接收消息）；
2. **接收方式必须选「使用长连接」**（插件连飞书长连接 WS 端点接收事件；不要选 Webhook，除非你有公网 URL）；
3. 发布新版本并审核通过。

**A5 把应用拉进测试群**
- 飞书 → 目标群 → 群设置 → **群机器人/应用** → 添加该应用（需要群内成员可添加应用）。

> **阶段 A 完成标志**：应用在测试群内；能 @ 应用 得到飞书的应用消息反馈。

#### 阶段 B · 插件侧配置（一次重启）

**B1 定位配置文件**：服务器 `plugins/OrzMC/im.yml`。

**B2 填写配置**：

```yaml
backend: builtin
platforms:
  feishu:
    enabled: true
    app_id: 'cli_xxxxxxxxxxxxxxxx'
    app_secret: '你的AppSecret'
```

**B3 重启服务器**（backend 与凭据在启动时装配；改凭据必须重启）。

**B4 确认通道启用**：重启后看控制台出现：

```
[OrzMC] [feishu] 网关连接已建立
```

> **阶段 B 完成标志**：控制台出现 `[feishu] 网关连接已建立`。也可 `/config im status` 复核：
> `feishu 平台: 启用` + `connection: 已连接`。若未出现，最常见是 A3 权限/A4 事件订阅未审核生效或凭据抄错。

#### 阶段 C · 会话发现与绑定（10 分钟）

飞书群/单聊的 chat_id 同样由插件自动发现（D11）：

**C1 触发发现**：在测试群发任意一条消息。控制台出现：

```
[OrzMC] [feishu] 忽略未绑定会话消息 target=feishu:group:oc_xxxxxxxx...（绑定见 /config im bind，候选入 status）
```

末尾 `oc_...` 就是该群 chat_id（也会出现在 `/config im status` 候选列表）。

**C2 绑定会话**（控制台或游戏内 op 执行；群/单聊均为 `chat_id`）：

```
/config im bind feishu group <chat_id> admin_group
/config im bind feishu group <chat_id> player_group   # 玩家群（可略；留空则公开通知降级发管理群）
/config im bind feishu user <chat_id> admin_dm       # 管理员私聊：先在飞书单聊机器人发一条消息，取候选中的单聊 chat_id
```

> 单聊 chat_id 与群 chat_id 同为 `oc_` 前缀，二者不同；单聊需先在**机器人私聊窗口**发一条消息触发发现。绑定即时生效，**无需重启**。

#### 阶段 D · 端到端验证（验收清单）

| # | 验证项 | 操作 | 期望结果 |
|---|--------|------|----------|
| 1 | 通道健康 | `/config im status` | `feishu 平台: 启用` + `connection: 已连接` |
| 2 | 上行一问一答 | 群内发 `$h` | 机器人回复命令帮助 |
| 3 | 上行 @机器人 | 群内 @机器人 发 `$l` | 返回在线玩家列表（插件自动剥离 @ 占位符） |
| 4 | 管理指令权限 | 群主/管理员发 `$e help` | 返回 Bukkit 命令帮助（非 owner/admin 成员被拒，fail-closed） |
| 5 | 下行主动发言 | `/config im test feishu group <chat_id> 你好` | 群里收到“你好” |
| 6 | 通知推送 | 触发一条服务器通知 | player_group/admin_dm 收到对应通知 |
| 7 | 绑定持久化 | 重启后 `/config im status` | 绑定仍在（sessions.feishu.* 已落盘） |

> **完成标志**：1–5 全过即完成飞书平台接入；6/7 用于核验广播/通知与持久化。

#### 常见问题（真机踩坑实录）

| 现象 | 原因 / 处理 |
|------|------------|
| 重启后日志 `无任何可用平台…已停用群功能` | im.yml 平台未启用或凭据缺失/抄错（D3）。修好后**重启** |
| 一直不见 `[feishu] 网关连接已建立` | A3 权限 / A4 事件订阅未审核生效；凭据抄错；应用未发布新版本 |
| 能发消息但**收不到群事件**（群内发消息无反应） | A4 事件订阅接收方式不是「长连接」（选了 Webhook 且无公网）；或 `im.message.receive_v1` 未添加 / 新版本未审核发布 |
| 只收到 @ 消息、普通群消息收不到 | A3 `im:message.group_msg`（群内全部消息）敏感权限未开或未审核 |
| @机器人 命令不回复，普通 `$h` 正常 | 飞书把 @ 转成 `@_user_N` 占位符前缀；插件已自动剥离（2026-09-05 真机修复），请升级到含该修复的版本 |
| 管理指令被拒 / 群主也被当非管理 | A3 `im:chat` 权限未开（角色判定查询失败按非管理 fail-closed）；或发送者不是群主/管理员 |
| 应用收不到单聊消息 | A3 `im:message.p2p_msg:readonly` 未开；先在机器人私聊窗口发一条消息触发发现 |

#### 出站域名放行清单（R11，有防火墙/白名单的服务器需放行）

| 用途 | 域名 | 方向 |
|------|------|------|
| 飞书开放 API（鉴权 / 消息发送 / 群信息 / 长连接端点引导） | `open.feishu.cn` | HTTPS 出站 |
| 飞书长连接 WS | 由长连接端点引导下发（`wss://…`） | WSS 出站 |

#### 能力边界

- **仅文本**（D6）：图片/文件/语音/富文本不支持（事件中非 text 消息丢弃）；
- **发送尽力一次不重试**（D7）：失败经健康告警，无投递对账；
- 飞书无 QQ 式被动回复窗口/msg_id 语义：回复均以 chat_id 直发，受应用消息权限约束；
- 群成员角色查询带 30s 缓存；应用需在群内才能收发该群消息。

---

### 2.8 builtin 内置直连（backend=builtin）· Telegram 接入操作手册

> **适用范围**：服务器管理员。跟随本手册从零把 Telegram 机器人接入插件内置直连通道（不依赖 EasyBot 网关）。
> **验证状态**：Telegram 平台全流程已真机验收通过（2026-09-05，本地 Paper 26.2）。三类会话均已实测闭环：
> 管理群（上行问答 + @提及剥离 + 管理指令权限）、玩家群（PUBLIC 广播接收）、管理员私聊（PRIVATE 下行 + 绑定后私聊问答）。
> **预计耗时**：约 15 分钟（Telegram 机器人即时开通，无平台审核等待）。文末附「验收清单」与「常见问题」。
> Telegram 界面名称以当前版本为准；平台操作通过 [@BotFather](https://t.me/BotFather) 对话完成。

---

#### 0. 与 QQ / 飞书接入的差异速览

Telegram 接入流程与 §2.6 QQ / §2.7 飞书手册同构（插件侧 `im.yml` 配置、`/config im bind` 会话绑定、
`/config im status` 健康查看均一致），差异在**平台侧准备**、**入站通道**与**会话值**：

| 维度 | QQ（§2.6） | 飞书（§2.7） | Telegram（本节） |
|------|-----------|-------------|-------------------|
| 应用形态 | 开放平台机器人（实名/审核） | 企业自建应用（需租户+审核） | **BotFather 创建 bot**（即时开通，免审核实名） |
| 凭据 | BotAppID + AppSecret | App ID + App Secret | **bot token**（`<bot_id>:<auth>`，如 `123456789:AAF...`） |
| 入站通道 | 出站 WS 网关 | 事件长连接 WS | **长轮询 getUpdates**（免公网入站，无 WS/Webhook） |
| 会话值 | `group:<GroupOpenID>` / `user:<UserOpenID>` | `group:<chat_id>` / `user:<chat_id>`（`oc_...`） | `group:<chat_id>` / `user:<chat_id>`——群 chat_id 为**负整数**（超级群 `-100...`），私聊 chat_id = 用户 id（正整数） |
| @提及 | 独立 AT 事件（无占位符） | 文本含 `@_user_N` 占位符 | 文本为纯 `@bot $cmd` 前缀（插件剥离开头 @token，见常见问题） |
| 角色判定 | 事件自带 `member_role` | 查群信息 API（owner_id+管理员列表） | **getChatAdministrators**（creator/administrator，60s 缓存+单飞） |
| 群普通消息 | 需「群内全部消息」权限 | 需 `im:message.group_msg` 敏感权限 | 需 bot **关闭 Privacy mode** 或设为群管理员（见 A3） |
| 主动私聊 | 受平台限制 | 受平台限制 | **bot 不能主动私聊从未联系它的用户**（TG 平台限制，见 D5/FAQ） |
| 网络可达 | 国内可达 | 国内可达 | `api.telegram.org` 国内**不可达**，需配代理（D13，见 A5/B2） |

涉及文件（与 QQ/飞书相同）：

- `plugins/OrzMC/im.yml` —— 通道与平台凭据（`backend` / `platforms.telegram` / 可选顶层 `proxy`），**改后重启生效**；
- `plugins/OrzMC/im_bindings.yml` —— 会话绑定（`sessions.telegram.*`），`/config im bind` 写入后**即时生效**。

权限红线：**bind/status/test 仅控制台或游戏内 op 可用**（D10）；一个 bot token 只允许一个实例消费事件（R3），
且长轮询为**抢占式拉取**——若你同时在跑 EasyBot/其他脚本拉同一 bot 的 getUpdates，事件会被抢走，请先停用其一再切 builtin。

#### 阶段 A · Telegram 平台侧准备（一次性，约 5 分钟）

**A1 用 BotFather 创建机器人**
1. Telegram 内打开 [@BotFather](https://t.me/BotFather) → 发送 `/newbot`；
2. 按提示填 bot 显示名称 → 填**用户名**（须以 `bot` 结尾，如 `MyServerBot`）；
3. 创建成功会立即返回 **bot token**（见 A2）——即时可用，无需审核。

**A2 获取凭据（bot token）**
- BotFather 成功消息里 `Use this token to access the HTTP API:` 一行即为 token（`<bot_id>:<auth>` 格式）；
  对应插件 `platforms.telegram.token`。⚠️ 妥善保管，勿提交到版本库/分享（R5）。

**A3 关闭 Privacy mode（必须，收群内普通消息）**
TG bot 默认开启 Privacy mode——只收 **@提及**和**斜杠命令**消息，收不到群里普通 `$h` 文本。二选一：
1. **关闭 Privacy**：@BotFather → `/mybots` → 选你的 bot → `Bot Settings` → `Group Privacy` → `Turn off`；或
2. **把 bot 设为测试群管理员**（群设置 → 管理员 → 添加 bot）——管理员天然可见全部消息，且能查到成员列表。

> ⚠️ 只开 A3 之一即可。真机测试 bot 用方法 1（Privacy off）验证通过：群内直接发 `$l` 有回复。

**A4 把 bot 拉进测试群**
- Telegram 目标群 → 群信息 → 添加成员 → 搜索 bot 用户名（`@...`）→ 添加。

**A5 准备代理（国内服务器必需，出墙）**
- TG Bot API 域名 `api.telegram.org` 国内不可达。服务器能直连可跳过；否则准备一个 **HTTP 代理**（Surge/Clash 等
  本地代理软件或服务器出口代理），代理地址填入 B2 的 `proxy` 段。**判断是否需要**：B4 启动若报 getMe 自检失败
  /网络不可达 → 需代理。

> **阶段 A 完成标志**：能私聊 bot 得到 TG 自带“开始”类回复；bot 在测试群内。

#### 阶段 B · 插件侧配置（一次重启）

**B1 定位配置文件**：服务器 `plugins/OrzMC/im.yml`。

**B2 填写配置**（国内服务器示例，含全局代理兜底 + 平台凭据）：

```yaml
backend: builtin

# 全局代理（出墙兜底，D13）：不配或 enabled: false = 直连；平台级 platforms.telegram.proxy 可覆盖本段
proxy:
  enabled: true
  type: http        # 默认 http（socks 预留未落地）
  host: '127.0.0.1' # 你的代理主机
  port: 7890        # 你的代理端口

platforms:
  telegram:
    enabled: true
    token: '123456789:AAFxxxx你的botToken'
    # 也可只在此平台级覆盖代理（省略 = 用全局 proxy 段）：
    # proxy:
    #   enabled: true
    #   host: '...'
    #   port: 7890
```

> 能直连 `api.telegram.org` 的海外服务器可省略整个 `proxy` 段（默认直连）。

**B3 重启服务器**（backend/凭据/代理在启动时装配；改这些**必须重启**）。

**B4 确认通道启用**：重启后看控制台出现：

```
[OrzMC] [telegram] 启动成功（bot @MyServerBot），开始长轮询
```

> **阶段 B 完成标志**：控制台出现上述“启动成功”行。也可 `/config im status` 复核：`telegram 平台: 启用`。
> 若报 `getMe 自检失败（token 无效或网络不可达）`：token 抄错，或需 A5 代理（见常见问题）。

#### 阶段 C · 会话发现与绑定（10 分钟）

TG 的 chat_id（群为负整数、私聊为正整数用户 id）同样由插件自动发现（D11），无需在平台侧查：

**C1 触发发现**：在**群**和 **bot 私聊**里各发一条任意消息（如 `hi`）。控制台出现：

```
[OrzMC] [telegram] 未绑定会话消息 telegram:group:-1001234567890，绑定命令（复制执行任一条即完成，即时生效；admin_group=管理群 / player_group=玩家群 / admin_dm=管理员私聊）:
  /config im bind telegram group -1001234567890 admin_group
  /config im bind telegram group -1001234567890 player_group
绑定后本会话自动从 status 候选清除
```

末尾 `-100...`（群）/ `5668266914` 形（私聊）就是该会话 chat_id（也会出现在 `/config im status` 候选列表）。

**C2 绑定会话**（控制台或游戏内 op 执行）：

```
/config im bind telegram group -1001234567890 admin_group
/config im bind telegram group -1001234567890 player_group   # 玩家群（可略；留空则公开通知降级发管理群）
/config im bind telegram user 5668266914 admin_dm            # 管理员私聊：先在 TG 单聊 bot 发一条消息，取候选中的私聊 chat_id
```

成功提示：`telegram 会话绑定已写入并持久化：admin_dm = user:5668266914（im_bindings.yml；入站/广播即时生效）`。
绑定即时生效，**无需重启**；绑定后该会话自动从候选清除。

#### 阶段 D · 端到端验证（验收清单）

按顺序执行并在“期望结果”处打勾；全部通过即接入完成：

| # | 验证项 | 操作 | 期望结果 |
|---|--------|------|----------|
| 1 | 通道健康 | `/config im status` | `telegram 平台: 启用` + 无 lastError |
| 2 | 上行一问一答（私聊） | bot 私聊发 `$h` | bot 回复命令帮助 |
| 3 | 上行群消息（普通文本） | 群内直接发 `$l`（不 @） | 返回在线玩家列表（依赖 A3 Privacy off/管理员） |
| 4 | 上行 @提及 | 群内 @bot 发 `$l` | 返回玩家列表（插件自动剥离 @token 前缀） |
| 5 | 管理指令权限 | 群主/管理员发 `$e help`（示例） | 非群主/管理员成员被拒（fail-closed）；群主/管理员正常执行 |
| 6 | 下行主动发言 | `/config im test telegram user <私聊id> 你好` | 私聊收到“你好” |
| 7 | 绑定持久化 | 重启服务器后 `/config im status` | 绑定仍在（sessions.telegram.* 已落盘） |
| 8 | 通知推送 | 触发一条服务器通知（如玩家上下线） | player_group/admin_dm 收到对应通知（按你的绑定与事件类型） |

> **完成标志**：1–7 全过即完成 Telegram 平台接入；第 8 项用于核验广播/通知路径。
> 第 6 项下行到私聊：TG 限制 bot 只能给**先私聊过它的用户**发消息——若失败请先在 bot 私聊发一条再试。

#### 常见问题（真机踩坑实录）

| 现象 | 原因 / 处理 |
|------|------------|
| 启动报 `getMe 自检失败…已停用轮询` | token 抄错（A2），或 `api.telegram.org` 不可达需配代理（A5/B2）。修好后**重启** |
| 私聊 bot 无“开始”类回复 / 发消息 bot 没反应 | token 无效（BotFather 重新生成）；或 401 后已停用轮询（见上） |
| 群里直接发 `$h` 没回复，但 @bot 有回复 | 这是**反了**的典型：bot Privacy mode 开启只能收 @/命令——按 A3 关 Privacy 或设管理员，普通群消息才可达 |
| @bot 发 `$l` 无回复，普通 `$l` 正常 | 老版本缺 @token 剥离（TG @提及是纯文本 `@bot $l` 前缀）——2026-09-05 真机发现并修复，请升级到含该修复的版本 |
| 管理指令被拒 / 群主也被当非管理 | A3 方法 2 未把 bot 设为管理员时 getChatAdministrators 拿不到完整列表会 fail-closed；或发送者确实非群主/管理员 |
| `/config im test` 下行私聊失败 | TG 限制 bot 不能主动私聊从未联系它的用户：先在 bot 私聊窗口发一条消息建立会话再测 |
| 重启后日志提示无平台可用 / 未启用 telegram | `platforms.telegram.enabled` 非 true 或 token 为空（D3 停群告警不自动回退）；修好**重启** |
| 轮询断断续续 / 频繁退避 | 网络抖动或代理不稳（getUpdates 失败 5s 退避重试）；持续则换更稳代理或检查出口 |
| EasyBot 与 builtin 同时拉同一 bot | 长轮询抢占式：事件会被先拉的客户端抢走（R3）——切通道前先停用其一 |

#### 出站域名放行清单（R11，有防火墙/白名单的服务器需放行）

| 用途 | 域名 | 方向 |
|------|------|------|
| Telegram Bot API（getMe / getUpdates 长轮询 / sendMessage / getChatAdministrators） | `api.telegram.org` | HTTPS 出站（走 A5 代理时仅需代理可达） |

> Telegram 无需 WS/公网入站——长轮询全部走 `api.telegram.org` 一个 HTTPS 域名（D13 代理透传）。

#### 能力边界

- **仅文本**（D6）：图片/文件/语音等无 `text` 字段的媒体消息丢弃；channel（频道）消息不入会话；
- **发送尽力一次不重试**（D7）：失败经健康告警，无投递对账；
- **长轮询免公网入站**（R8）：每轮 getUpdates 挂起 30s，超时无事件立即续轮（无需 WS/Webhook/公网 URL），
  适合无公网入站的服务器；401（token 无效）→ 健康降级停用轮询；
- **主动私聊限制**：TG bot 不能主动私聊从未联系它的用户（D5 下行到陌生 user 会失败）；群消息读取依赖
  Privacy off 或 bot 为群管理员（A3）；
- 群角色判定基于 getChatAdministrators（creator/administrator），带 60s 缓存+并发单飞，查不到按非管理 fail-closed；
- 群 chat_id 为负整数（超级群 `-100` 开头）、私聊 chat_id = 用户 id 正整数；两者都需先发消息触发 D11 发现。

---

### 2.9 builtin 内置直连（backend=builtin）· Discord 接入操作手册

> **适用范围**：服务器管理员。跟随本手册从零把 Discord 机器人接入插件内置直连通道（不依赖 EasyBot 网关）。
> **验证状态**：Discord 平台全流程已真机验收通过（2026-09-06，本地 Paper 26.2）。三类会话均已实测闭环：
> 频道（上行问答 + @提及剥离 + 管理指令权限）、玩家广播（通知推送）、bot 私聊 DM（下行 + 上行问答）。
> **预计耗时**：约 15 分钟（Discord 应用即时创建，无审核等待）。文末附「验收清单」与「常见问题」。
> Discord 界面名称以[开发者门户](https://discord.com/developers/applications)当前版本为准。

---

#### 0. 与 QQ / 飞书 / Telegram 接入的差异速览

Discord 接入流程与 §2.6–§2.8 手册同构（插件侧 `im.yml` 配置、`/config im bind` 会话绑定、
`/config im status` 健康查看均一致），差异在**平台侧准备**、**入站通道**、**会话粒度**与**@提及形态**：

| 维度 | QQ / 飞书 / Telegram | Discord（本节） |
|------|---------------------|-----------------|
| 应用形态 | QQ 开放平台 / 飞书自建 / BotFather | **开发者门户 Application + Bot**（即时创建，免审核实名） |
| 凭据 | AppID+Secret / AppID+Secret / bot token | **Bot Token**（开发者门户 → Bot → Reset Token） |
| 入站通道 | WS 网关 / 长连接 WS / 长轮询 | **Gateway WS v10**（identify/resume + 心跳，免公网入站） |
| 会话粒度 | 群/私聊单层 | 服务器内**每文本频道一个 `group` 会话**（一服务器多频道各自绑定） |
| 会话值 | `group:<chat_id>` / `user:<user_id>` | `group:<channel_id>`（频道，snowflake）/ `user:<user_id>`（DM 用户） |
| @提及 | TG 纯文本 `@bot` 前缀、飞书占位符 | **snowflake 标记** `<@bot_id>` 内嵌 content（2026-09-06 修复：剥离开头连续提及） |
| 角色判定 | 群主/管理员名单 | **guild owner 或成员角色含 ADMINISTRATOR/MANAGE_GUILD 权限位**（REST 查询+缓存） |
| 群普通消息 | 需平台权限/隐私设置 | 需开发者门户开启 **MESSAGE CONTENT INTENT**（特权） |
| 网络可达 | TG 不可达需代理 | `discord.com` 国内不可达需代理（D13，A5/B2） |

涉及文件（与其它平台相同）：

- `plugins/OrzMC/im.yml` —— 通道与平台凭据（`backend` / `platforms.discord` / 可选顶层 `proxy`），**改后重启生效**；
- `plugins/OrzMC/im_bindings.yml` —— 会话绑定（`sessions.discord.*`），`/config im bind` 写入后**即时生效**。

权限红线：**bind/status/test 仅控制台或游戏内 op 可用**（D10）；一个 bot token 只允许一个实例消费事件（R3），
Gateway 会话为抢占式——若你同时在跑 EasyBot/其他程序连同一 bot，请先停用其一再切 builtin。

#### 阶段 A · Discord 平台侧准备（一次性，约 10 分钟）

**A1 创建 Application 与 Bot**
1. 打开 [Discord 开发者门户](https://discord.com/developers/applications) → **New Application**（填名字）；
2. 左侧 **Bot** → **Add Bot** → 建 bot（免审核）。

**A2 获取凭据（Bot Token）**
- Bot 页 → **Reset Token** → 复制 token（对应插件 `platforms.discord.token`）。⚠️ 妥善保管，
  只显示一次；泄露后立即 Reset（勿提交版本库/分享，R5）。

**A3 开启 MESSAGE CONTENT INTENT（必须，收群普通消息文本）**
- Bot 页 → **Privileged Gateway Intents** → 打开 **MESSAGE CONTENT INTENT**。
  ⚠️ 不开则 gateway 收得到事件但 `content` 为空——`$l`/`$h` 等文本命令全部静默无响应
  （插件的 intents 已含 MESSAGE_CONTENT=1<<15，特权开启后才能拿到文本）。

**A4 把 bot 拉进测试服务器**
1. 目标服务器 → 服务器设置 → **成员** → 邀请 bot（或经 OAuth2 URL：`https://discord.com/api/oauth2/authorize?client_id=<应用ID>&permissions=0&scope=bot`）；
2. ⚠️ **建议授予 bot 一个含 Administrator 的角色**（或至少让它能查成员/角色）：
   - 群主判定（guild owner）不依赖权限；
   - 但<b>成员角色权限位判定</b>需 bot 能读服务器角色（GET /guilds/{id}/roles）——普通 bot 无权限时
     该查询 403，按 fail-closed 处理（仅群主可发管理指令）；
3. 建一个文本频道（如「开发测试」）作为测试会话。

> **阶段 A 完成标志**：bot 在测试服务器内、能读频道消息（频道里能看到 bot 上线状态）。

#### 阶段 B · 插件侧配置（一次重启）

**B1 定位配置文件**：服务器 `plugins/OrzMC/im.yml`。

**B2 填写配置**（国内服务器示例，含全局代理兜底 + 平台凭据）：

```yaml
backend: builtin

# 全局代理（出墙兜底，D13）：不配或 enabled: false = 直连；平台级 platforms.discord.proxy 可覆盖本段
proxy:
  enabled: true
  type: http        # 默认 http（socks 预留未落地）
  host: '127.0.0.1' # 你的代理主机
  port: 7890        # 你的代理端口

platforms:
  discord:
    enabled: true
    token: '你的BotToken'
    # 也可只在此平台级覆盖代理（省略 = 用全局 proxy 段）：
    # proxy:
    #   enabled: true
    #   host: '...'
    #   port: 7890
```

> 能直连 `discord.com` / `gateway.discord.gg` 的海外服务器可省略整个 `proxy` 段（默认直连）。

**B3 重启服务器**（backend/凭据/代理在启动时装配；改这些**必须重启**）。

**B4 确认通道启用**：重启后看控制台出现：

```
[OrzMC] IM backend=builtin：启用内置直连（可用平台：discord）。
[OrzMC] [discord] 网关连接已建立
[OrzMC] [discord] 发送 identify（intents=37376）
[OrzMC] [discord] 网关 READY（会话已建立，bot @MyBot）
```

> **阶段 B 完成标志**：控制台出现 `[discord] 网关 READY`。也可 `/config im status` 复核：`discord 平台: 启用`。
> 若一直见不到 READY / 反复退避：token 无效（4004 后自动停用，见常见问题），或网络/代理不可达。

#### 阶段 C · 会话发现与绑定（10 分钟）

Discord 的 channel_id / user_id 同样由插件自动发现（D11），无需在平台侧查：

**C1 触发发现**：在目标**频道**和 bot **私聊**里各发一条任意消息（如 `hi`）。控制台出现：

```
[OrzMC] [discord] 未绑定会话消息 discord:group:1101910610033250468，绑定命令（复制执行任一条即完成，即时生效；admin_group=管理群 / player_group=玩家群 / admin_dm=管理员私聊）:
  /config im bind discord group 1101910610033250468 admin_group
  /config im bind discord group 1101910610033250468 player_group
绑定后本会话自动从 status 候选清除
```

末尾那串 snowflake（频道/用户 id）就是该会话 id（也会出现在 `/config im status` 候选列表）。

**C2 绑定会话**（控制台或游戏内 op 执行）：

```
/config im bind discord group <频道id> admin_group
/config im bind discord group <频道id> player_group   # 玩家频道（可略；留空则公开通知降级发管理频道）
/config im bind discord user <用户id> admin_dm         # 管理员私聊：先在 bot 私聊发一条消息，取候选中的用户 id
```

> Discord 一服务器多频道：每个频道独立会话（绑定哪个频道，该频道内命令才响应；其它频道发消息仅 D11 提示）。
> 绑定即时生效，**无需重启**；绑定后该会话自动从候选清除。

#### 阶段 D · 端到端验证（验收清单）

按顺序执行并在“期望结果”处打勾；全部通过即接入完成：

| # | 验证项 | 操作 | 期望结果 |
|---|--------|------|----------|
| 1 | 通道健康 | `/config im status` | `discord 平台: 启用` + 无 lastError |
| 2 | 上行一问一答（频道） | 频道发 `$h` | bot 回复命令帮助 |
| 3 | 上行 @提及 | 频道 @bot 发 `$l` | 返回玩家列表（插件自动剥离 `<@bot_id>` snowflake 标记） |
| 4 | 管理指令权限 | 群主/管理员频道发 `$e help`（示例） | 非群主/管理角色被拒（fail-closed）；群主/管理员正常执行 |
| 5 | DM 上行问答 | bot 私聊发 `$h` | bot 回复（admin_dm 会话） |
| 6 | 下行主动发言 | `/config im test discord user <用户id> 你好` | bot 私聊发给你“你好” |
| 7 | 绑定持久化 | 重启服务器后 `/config im status` | 绑定仍在（sessions.discord.* 已落盘） |
| 8 | 通知推送 | 触发一条服务器通知（如启动/停止/玩家上下线） | player_group/admin_dm 收到对应通知 |

> **完成标志**：1–7 全过即完成 Discord 平台接入；第 8 项用于核验广播/通知路径。
> 第 6 项下行 DM：bot 只能私聊与它<b>共享服务器</b>的用户（Discord 平台限制；真机 owner 验证通过）。

#### 常见问题（真机踩坑实录）

| 现象 | 原因 / 处理 |
|------|------------|
| 启动后一直见不到 `[discord] READY`，日志反复 `网关地址不可用` | token 无效（403/4004 停用）或网络/代理不可达 `discord.com` |
| 网关 4004 关闭后不再重连 | token 无效（Discord 4004 Authentication failed）——静态凭据无刷新语义：控制台重启前先检查 token（Bot → Reset Token 重新生成） |
| 频道发 `$l` 有回复，但 @bot 发 `$l` 无反应 | Discord @提及是 snowflake 标记 `<@bot_id>`（非纯文本 @bot）——2026-09-06 真机发现并修复（插件自动剥离开头连续提及），请升级到含该修复的版本 |
| 频道发 `$h`/`$l` 全部静默无响应（连不 @ 的也没反应） | A3 MESSAGE CONTENT INTENT 未开（content 为空）；开发者门户 → Bot → Privileged Gateway Intents 开启后重启 |
| 管理指令被拒 / 群主也被当非管理 | A4 建议给 bot Administrator 角色——非群主成员的「角色权限位」判定需 bot 能读服务器角色，读不到按非管理 fail-closed；群主（guild owner）判定不依赖该权限 |
| `/config im test` 下行 DM 失败 | Discord bot 只能私聊与它共享服务器的用户；且该用户需未屏蔽私信 |
| 重启后日志提示无平台可用 / 未启用 discord | `platforms.discord.enabled` 非 true 或 token 为空（D3 停群告警不自动回退）；修好**重启** |
| gateway 反复断连/心跳超时 | 网络/代理不稳；Discord 会自动 resume（op7/op9 决策内置）；持续异常检查代理与出口 |
| EasyBot 与 builtin 同时连同一 bot | Gateway 会话抢占式：同一 token 两处 identify 会互踢（R3）——切通道前先停用其一 |

#### 出站域名放行清单（R11，有防火墙/白名单的服务器需放行）

| 用途 | 域名 | 方向 |
|------|------|------|
| Discord REST API（/gateway/bot 引导 / 发消息 / DM / 角色查询） | `discord.com` | HTTPS 出站（走 A5 代理时仅需代理可达） |
| Discord Gateway WS（事件长连接） | `gateway.discord.gg` | WSS 出站（同代理） |

> Discord 无公网入站需求——gateway 为出站 WS 长连接（D13 代理透传，REST 与 WS 同一代理）。

#### 能力边界

- **仅文本**（D6）：图片/文件等无 `content` 文本的媒体消息丢弃；
- **发送尽力一次不重试**（D7）：失败经健康告警，无投递对账；
- **会话粒度 = 频道**：Discord 服务器内每文本频道独立 `group` 会话（子频道/帖子同构，channel_id 全局唯一）；
  未绑定频道消息仅 D11 提示不回复；
- **主动私聊限制**：bot 只能私聊与它共享服务器的用户（D5）；DM 会话以用户 id 绑定，出站经
  `/users/@me/channels` 建/取 DM 通道（每用户缓存）；
- **角色判定**：群主（guild owner）或成员角色含 ADMINISTRATOR/MANAGE_GUILD 权限位（REST+60s 缓存+单飞）；
  查不到/无权限按非管理 fail-closed；DM 恒非管理；
- **@提及为 snowflake 标记**：剥离开头连续 `<@id>`/`<@!id>`/`<@&id>`（中间提及保留），纯提及无正文丢弃；
- 消息内容读取依赖 A3 MESSAGE CONTENT INTENT（特权）；intents=37376（GUILD_MESSAGES+DIRECT_MESSAGES+MESSAGE_CONTENT）。

---

## 三、跨服传送门

### 3.1 创建传送门
- 命令：`/portal <host> [port]`
- 在玩家当前位置生成下界合金风格传送门（4×5 黑曜石框架）
- 传送门上方生成文字标签显示目标服务器地址

### 3.2 删除传送门
- 命令：`/portal remove <host> [port]`
- 清除对应传送门方块及文字标签
- 需要 OP 或 `orzmc.admin` 权限

### 3.3 跨服传送
- 玩家走进传送门触发 Paper `transfer` 指令，跨服传送
- 集成 LoginSecurity 插件，**未登录玩家禁止使用传送门**
- 传送门数据持久化到 `portals.yml`

---

## 四、TNT 保护系统

### 4.1 放置控制
- 全局开关：`tnt.enable` 控制是否允许放置/激活 TNT（默认关闭）
- 区域白名单：可在指定世界 + 坐标范围内允许 TNT
- 放置冷却：每玩家默认 5 秒冷却（`tnt.place_cooldown`）

### 4.2 重生锚控制
- 独立开关 `tnt.enable_respawn_anchor` 控制是否允许放置重生锚

### 4.3 爆炸通知
- 所有方块/实体爆炸事件自动通知到 Bot，通知附带爆炸坐标
- 可配置豁免实体（默认：苦力怕、火球、风弹、末影龙、末地水晶、凋灵、凋灵骷髅、史莱姆、流浪者等）
- **突发聚合防刷屏**：同一区域（128×128×64 方块）+ 同类型事件在聚合窗口内合并，窗口尾部只发一条告警（带 `×N` 次数与首个事件坐标）；批次内事件不立即发送，避免「立即发送 + 尾部汇总」双条刷屏
- 聚合窗口由 `tnt.notify_aggregate_ms` 控制（默认 3000ms），既是突发合并也限制持续刷屏频率
- 方块爆炸统一归并为「方块爆炸」标签，不再按方块材质拆分
- 上下线消息限流由 `player_notify.window_ms`（1s 聚合窗口）承担（`tnt.notify_throttle_ms` 已废弃移除）

---

## 五、安全与访问控制

### 5.1 GeoIP 国家限制
- 玩家登录前异步查询 IP 地理位置
- 仅允许配置的国家代码（`allow_country_code`）通过；未配置时放行所有 IP
- 内网/私有地址（RFC1918、环回、CGNAT、链路本地及 IPv6 内网段）直接放行，不触发 GeoIP 查询
- 上游查询失败/超时/返回空国家码时默认 **fail-close 拒绝进入**（安全优先；需放行时手动设 `geoip.fail_open: true`），拦截与放行均私信告警管理员（1 分钟限频，日志始终保留完整现场），告警不入玩家群
- 被拒玩家踢出消息中显示其所在国家及允许的国家列表；拦截时 Bot 推送通知

### 5.2 访问规则
- 运行时规则持久化于 `access_rules.yml`（取代旧 `ip_blacklist.yml`，存量数据不自动导入）
- IP 黑名单支持多种匹配模式：
  - 精确 IP：`192.168.1.1`
  - CIDR：`192.168.1.0/24`
  - 通配符：`10.*`、`192.168.*`
- 玩家名规则支持：
  - 精确匹配：`exact Steve`
  - 前缀：`prefix bot_`
  - 后缀：`suffix _alt`
  - 关键词：`contains admin`
  - glob：`glob Steve*`
  - 正则：`regex ^bot\d+$`
- 玩家名匹配默认大小写不敏感；离线模式下玩家名由客户端上报，名称规则适合反滥用/风控，不能替代 UUID 或 IP 作为强安全边界
- 管理方式：
  - 游戏内命令：`/blacklist list|add|remove <pattern>`，玩家名规则为 `/blacklist add|remove player <type> <value>`（别名 `/bl`）
  - Bot 命令：`$d <IP>` / `$d -<IP>`，玩家名规则为 `$d player <type> <value>` / `$d -player <type> <value>`（`<type>` 支持 `exact`/`prefix`/`suffix`/`contains`/`glob`/`regex`，示例见上方）

### 5.3 登录验证集成
- 反射调用 LoginSecurity API
- 未登录玩家不能使用跨服传送门
- 兼容 LoginSecurity 多个 API 版本

### 5.4 命令权限
- 命令可配置为仅管理员可用（OP 或 `orzmc.admin` 权限）
- 非管理员看不到管理员命令的 Tab 提示

### 5.5 危险命令拦截与审计（guard）
- 高危命令 deny-list 拦截（默认 `op` / `publish` / `seed`，支持子命令项如 `plugman reload`）：命中即阻止；`guard.notify_admins` 开启时私信管理员
- 运维命令（`stop` / `reload` / `deop` / `plugman` 等）不默认拦截——原生即受 OP 权限限制，避免管理员也无法停服/重载/管理 OP
- `guard.audit_enabled` 开启时命令审计落盘 `audit/command_audit.log`；危险命令 WARN 不再重复刷控制台，细节由审计文件承载
- 总开关：`guard.enabled`（关闭后拦截与审计全部停用）

### 5.6 聊天反垃圾（chat）
- 聊天限流：60s 滑动窗口每玩家最多 20 条（`chat.max_messages_per_minute`）
- 链接检测（`chat.detect_links`）与重复内容检测（`chat.detect_repeat`）：命中取消消息并提示
- 提示语可配置（`chat.message`）；总开关：`chat.enabled`

### 5.7 进服限流（login_rate_limit）
- 登录防爆破：每 IP 每分钟最多 20 次登录尝试（`max_login_attempts_per_minute`）；同 IP 并发上限 5（`max_concurrent_per_ip`）
- 超限拒绝进入并提示；`notify_admins` 开启时私信管理员
- 总开关：`login_rate_limit.enabled`

### 5.8 已知漏洞加固（exploit_hardening）
- 书与笔：每本最多 100 页（`book_max_pages`）
- 物品属性：单个物品属性修饰符上限 6 个（`item_max_attribute_modifiers`）
- 实体：单区块最多 128 实体（`entity_max_per_chunk`）
- 命中自动清除异常内容/实体并可告警管理员；总开关：`exploit_hardening.enabled`

---

## 六、传送弓 🏹

### 6.1 获取方式
- 命令：`/tpbow`（别名 `/tpb`）
- 获得一把带有无限附魔的特殊弓

### 6.2 传送逻辑
- 射出的箭矢落地位置即为传送目标
- 沿箭的飞行路径 **force-load 区块**（提前约 24 格异步加载，路径不留缺口），远射时箭不会因进入未加载区块而冻结，保证命中事件正常触发、传送落点准确
- 自动检测落点安全：
  - 不在水、岩浆、仙人掌、火、细雪等危险方块中
  - 在世界高度范围内
  - 有实体站立地面 + 上方 2 格空气
- 落点不安全时自动搜索最近的**安全位置**
- 传送成功播放猫咕噜声

### 6.3 实体传送策略
- 默认**限制**命令/插件触发的实体传送（`entity_teleport_enabled: false`），仅白名单内实体可被传送，防 `@e` 选择器误用把海量实体传送到虚空/岩浆造成地图灾难
- **下界传送门穿越不受限制**（`EntityPortalEvent` 始终放行）：掉落物/矿车/船/任意生物照常过传送门
- 白名单项支持：
  - 特殊键：`TAMEABLE`（按接口判定，覆盖猫/狗/鹦鹉 + 全部马科）、`ENDERMAN`、`ARMOR_STAND`、`SHULKER`
  - 任意大写 `EntityType` 名（如 `VILLAGER`）
- 设为 `entity_teleport_enabled: true` 后所有实体均可被命令/插件传送
- 默认白名单（16 项，仅被动/友好实体）：`TAMEABLE` / `ENDERMAN` / `ARMOR_STAND` / `SHULKER` / `VILLAGER` / `WANDERING_TRADER` / `COW` / `PIG` / `SHEEP` / `CHICKEN` / `RABBIT` / `GOAT` / `MOOSHROOM` / `AXOLOTL` / `BEE` / `IRON_GOLEM`

---

## 七、世界维护（备份与优化）

### 7.1 世界备份
- 命令：`$b`（管理员）
- 执行流程：踢出所有玩家 → `save-off` → 压缩世界为 ZIP → `save-on` → 恢复服务
- 备份存储位置：**服务器核心根目录 `backup/`**（如 `~/papermc-test/backup/`，非插件数据目录）
- 目录使用：input=世界目录（`getWorldFolder()`）；backup-core 中间目录 = `backup/tempDir/`（backup-core Cleanup 阶段自动删除）；zip 直接落 `backup/`（output 父目录）；崩溃/断电残留由**启动清理**兜底（MaintenanceModule.setup 清 `backup/tempDir`）
- 自动清理旧备份，保留最近 N 个（`maintenance.backup_retention_count`，默认 5）
- ⚠️ **备份为"优化式备份"**：基于 backup-core（InhabitedTime 阈值过滤，阈值= `maintenance.optimize_tick_time_threshold` 默认 300 秒），活跃 ≤ 阈值（15 秒）的区块不进入备份 zip——备份体积远小于世界（实测 17G 世界 → zip ~1.4G），适合日常快照；如需逐字节全量，请用外部快照/全量备份工具

### 7.2 世界优化
- 命令：`$o`（管理员，需先启用 `maintenance.optimize_enabled`）
- 执行流程：踢出所有玩家 → `save-off` → 优化（剔除低活跃区块，InhabitedTime 阈值同上）→ `save-on` → 恢复服务
- input=世界目录（与备份一致）；in-place 优化（backup-core 内部临时目录处理）
- 实测（2026-08-20，裁剪后世界）：$o 31 秒完成 190,526/190,526 区块，剔除 5.6 万低活跃区块（22.8%），世界正常加载

### 7.3 进度报告
- 实时推送备份/优化进度到 Bot
- 报告内容：阶段名称、完成百分比、处理速率、预计完成时间

### 7.4 维护期间体验
- 维护时服务器列表 MOTD 替换为自定义提示信息
- 玩家被踢出时显示维护提示

---

## 八、玩家加入/退出/踢出通知

### 8.1 推送内容
- 玩家名称（含显示名格式）
- 所在世界（支持别名映射）
- 坐标（支持缩放、精度、单位配置）
- 权限组中文名（访客 / 成员 / 建造者 / 管理员）
- 当前在线人数及在线玩家列表（每行：`玩家名(op) 游戏模式 权限组`）

### 8.2 模板定制
- 三个独立模板：`player_join`、`player_quit`、`player_kick`
- 支持变量：`{name}`、`{world_alias}`、`{online_count}`、`{x}`、`{y}`、`{z}` 等

---

## 九、新手指南书 📖

### 9.1 自动发放
- 玩家首次进入服务器时自动获得一本指南书
- 丢弃后可通过 `/guide` 命令重新获取

### 9.2 内容配置
- 通过 YAML 配置指南书内容
- 支持丰富格式：纯文本、超链接、悬停提示文字、样式（粗体/下划线/颜色）、分页

---

## 十、运行时配置管理

### 10.1 命令总览
`/config`（别名 `/cfg`）支持以下子命令：

| 子命令 | 功能 | 示例 |
|--------|------|------|
| `list` | 列出所有可配置项 | `/config list` |
| `get <path>` | 查看某项配置的值、类型、默认值、所在文件 | `/config get tnt.enable` |
| `set <path> <value>` | 修改并持久化配置 | `/config set tnt.enable true` |
| `reset <path>` | 恢复为默认值 | `/config reset tnt.enable` |
| `dump` | 打印完整配置树 | `/config dump` |
| `reload [name]` | 热重载指定或所有配置文件 | `/config reload` |

### 10.2 可配置项（29 项）

**白名单**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `whitelist.force_whitelist` | Boolean | true | 启用强制白名单 |
| `whitelist.cleanup_inactive_days` | Integer | 90 | 白名单不活跃清理天数 |
| `whitelist.pagination_delay_ticks` | Integer | 5 | 白名单翻页延迟（tick） |

**维护**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `maintenance.optimize_enabled` | Boolean | false | 启用地图自动优化 |
| `maintenance.optimize_tick_time_threshold` | Long | 300 | 优化触发 tick 阈值（ms） |
| `maintenance.backup_retention_count` | Integer | 5 | 地图备份保留数量 |

> **维护场景文案/进度行模板**（2026-09-02 起迁移至 templates.yml，MOTD/登录拦截/踢人统一读取）：
> `maintenance_motd_backup`（服务器地图备份中）、`maintenance_motd_optimize`（服务器地图优化中）、
> `maintenance_motd_manual`（服主手动 `/maintenance on`）、`maintenance_motd_progress_line`（追加进度行）。
> 支持占位符 `{stage}` `{percent}` `{eta}`：场景模板写入占位符即内联展示进度（不再追加独立进度行）；
> 未写占位符且场景有进度（backup/optimize）时自动追加 progress_line 行。模板缺失时用代码内置默认文案。
>
> ⚠️ **升级迁移**：旧版 `config.yml` 的 `maintenance.backup/optimize/manual_maintenance_motd` 自定义文案已废弃——
> 升级后需手动把自定义值搬运到 `templates.yml` 的 `maintenance_motd_*` 对应键；且不再支持
> `/orzmc config set` 修改 motd 文案（该路径已移出注册表），请直接编辑 `templates.yml` 后执行 `/config reload` 生效。

**TNT**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `tnt.enable` | Boolean | false | 启用 TNT 放置检测 |
| `tnt.enable_respawn_anchor` | Boolean | false | 启用重生锚检测 |
| `tnt.place_cooldown` | Integer | 5 | TNT 放置冷却（秒） |
| `tnt.notify_aggregate_ms` | Long | 3000 | TNT/爆炸告警聚合窗口（毫秒） |

**插件自更新**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `update.enabled` | Boolean | true | 自更新总开关（false 后不再自动检查；`/update check\|now` 仍可手动使用） |
| `update.channel` | String | release | 更新通道：release（正式版）/ beta（开发版） |
| `update.check_interval_hours` | Long | 12 | 自动检查间隔（小时）；0 = 仅启动后检查一次 |
| `update.auto_download` | Boolean | false | 发现新版本自动下载到 plugins/update（重启生效） |

**上下线通知**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `player_notify.enabled_join` | Boolean | true | 上线消息通知开关 |
| `player_notify.enabled_quit` | Boolean | true | 下线消息通知开关 |
| `player_notify.enabled_kick` | Boolean | true | 被踢消息通知开关 |
| `player_notify.window_ms` | Long | 1000 | 上下线通知聚合窗口（毫秒） |
| `player_notify.max_list_items` | Integer | 6 | 聚合摘要最多列出的玩家数 |

**命令策略**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `command_policies.tpbow.cooldown_secs` | Integer | 3 | 传送弓冷却（秒） |
| `command_policies.tpbow.admin_only` | Boolean | false | 传送弓仅管理员 |
| `command_policies.menu.cooldown_secs` | Integer | 0 | 菜单冷却（秒） |
| `command_policies.menu.admin_only` | Boolean | false | 菜单仅管理员 |
| `command_policies.portal.cooldown_secs` | Integer | 5 | 传送门冷却（秒） |
| `command_policies.portal.admin_only` | Boolean | true | 传送门仅管理员 |

**聊天反垃圾**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `chat.max_messages_per_minute` | Integer | 20 | 60s 滑动窗口内最多发言条数 |

**登录限流**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `login_rate_limit.max_login_attempts_per_minute` | Integer | 20 | 60s 滑动窗口内最多登录尝试次数 |
| `login_rate_limit.max_concurrent_per_ip` | Integer | 5 | 同 IP 最大在线上限 |

**危险命令拦截**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `guard.blocked_commands` | List\<String\> | op, publish, seed | 高危命令 deny-list（小写命令名，支持子命令项）。默认仅拦提权/泄露类（op/publish/seed）；stop/reload/deop/plugman 等运维生命周期命令不默认拦截 |
| `guard.audit_enabled` | Boolean | true | 是否写 `audit/command_audit.log`；开启时 WARN 不再重复刷控制台 |

**玩家名颜色**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `rank_colors.tab_enabled` | Boolean | false | Tab 列表着色开关 |

**Bot（来源：easybot.yml）**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `cmd_prompt_char` | String | $ | Bot 命令前缀符 |
| `discord_server_link` | String | null | Discord 邀请链接 |
| `qq_group_id` | String | null | QQ 群号 |

**模板（来源：templates.yml）**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `templates.locale` | String | zh-CN | 本地化语言 |
| `templates.coord.scale` | Double | 1.0 | 坐标缩放比例 |
| `templates.coord.precision` | Integer | 2 | 坐标小数位数 |
| `templates.coord.unit_label` | String | block | 坐标单位标签 |

---

## 十一、游戏内指令总表

| 命令 | 别名 | 功能 | 权限 |
|------|------|------|------|
| `/tpbow` | `/tpb` | 获取传送弓 | 通用 |
| `/guide` | — | 打开新手指南书 | 通用 |
| `/menu` | — | 打开 OrzMC 菜单 GUI（开发中） | 通用 |
| `/bot` | — | 查看 Bot 连接状态 | 通用 |
| `/portal <host> [port]` | — | 创建跨服传送门 | 管理员 |
| `/portal remove <host> [port]` | — | 删除传送门 | 管理员 |
| `/blacklist list\|add\|remove` | `/bl` | IP 黑名单与玩家名规则管理 | 管理员 |
| `/config list\|get\|set\|reset\|dump\|reload` | `/cfg` | 运行时配置管理 | 管理员 |
| `/orzdebug <Bot命令>` | — | 模拟群里用户发 Bot 命令（调试用） | 通用 |
| `/rank` | — | 查看自己的权限组/时长进度/下一步可申请 | 通用 |
| `/apply [类型] [理由]` | — | 提交权限晋升申请（`/apply builder` / `/apply admin`） | 通用 |
| `/review approve\|reject <玩家>` | — | 审核通过/拒绝玩家的晋升申请 | 管理员 |
| `/update check\|now` | `/upd` | 检查/下载插件新版本（下载到 `plugins/update`，重启生效） | 管理员 |

---

## 十二、OrzMC 菜单（开发中）

- 命令 `/menu` 打开一个箱子 GUI
- 目前为占位界面，点击提示"功能开发中"
- 后续计划逐步增加快捷功能

---

## 十三、基础设施能力

| 组件 | 说明 |
|------|------|
| **Bot 消息路由** | OrzEasyBot 根据 PUBLIC / PRIVATE 统一路由至玩家群或管理员私聊 |
| **多文件配置** | config.yml、easybot.yml、templates.yml、portals.yml、access_rules.yml，支持热重载 |
| **样式系统** | 可配置颜色调色板（成功/信息/警告/错误/坐标/玩家等），CSS 十六进制色值 |
| **模板系统** | 变量替换、坐标格式化（缩放/精度/单位）、世界别名/角色别名/i18n |
| **健康注册表** | 线程安全的服务健康状态追踪 |
| **WebSocket 客户端** | 带心跳检测和自动重连的健壮 WS 客户端 |
| **限流日志** | 高频事件限流日志，防止控制台刷屏 |
| **安全调度器** | 包装 Bukkit 调度器，统一异步任务异常日志 |
| **命令拦截器链** | PlayerOnly（仅玩家）、AdminOnly（仅管理员）、Cooldown（冷却） |

---

## 十四、配置插件

插件配置文件位于 `plugins/OrzMC/` 目录：

- **config.yml** — 核心配置（白名单、TNT、维护、GeoIP、命令策略、上下线通知、实体传送、危险命令拦截 guard、聊天反垃圾、进服限流、漏洞加固）
- **easybot.yml** — Bot 通用设置与 EasyBot IM Gateway 连接配置（多平台消息路由、WebSocket + HTTP）
- **guide_book.yml** — 新手指南书内容配置（链接/悬停/样式/分页）
- **permission.yml** — 权限系统配置（`config` 阈值节 + `reviews` 申请记录节，运行时修改）
- **templates.yml** — 通知模板、样式配色、坐标格式、世界别名、权限组显示名、i18n 覆盖
- **portals.yml** — 传送门数据（运行时修改）
- **access_rules.yml** — IP 黑名单与玩家名规则数据（运行时修改；取代旧 ip_blacklist.yml）

> 大部分配置可通过 `/config` 命令在运行时修改并立即生效，无需重启服务器。

---

## 十五、权限管理系统（Rank & Review）

基于 [LuckPerms](https://luckperms.net/) 的玩家权限晋级系统，提供「自动晋升 + 申请审核 + 手动升降级」全链路。

### 15.1 权限链（4 级）

| 等级 | 权限组 | 中文名 | 获得方式 |
|:--|:--|:--|:--|
| L0 | `default` | 访客 | 默认（所有新玩家） |
| L1 | `member` | 成员 | **自动晋升**：在线时长达到阈值（默认 10 小时，可配置）后下次上线自动晋升 |
| L2 | `builder` | 建造者 | **申请审核**：成员提交 `/apply builder`，管理员审核通过 |
| L3 | `admin` | 管理员 | **申请审核**：建造者提交 `/apply admin`，管理员审核通过 |

玩家在任意时刻只处于一个权限组（LP track 单一事实源），升级 = 在权限链上移动一格。

### 15.2 装即用（无需手动配置 LP）

- **软依赖 LuckPerms**：无 LP 时插件照常运行（权限功能自动降级不可用，其余功能不受影响）
- **自动初始化**：有 LP 时启动自动创建 track `rank`（default→member→builder→admin）与缺失的权限组；**继承链与 track 链序由插件保证正确**——已存在但链序不一致的 track 会重建校正，已有组的继承关系与设计不符会校正 parent（**只动继承，不碰任何权限节点**），组内具体权限以线上定义为准
- 权限节点内容不内置：新组继承链落到 LP 内置 `default` 组，基础权限由各服 `default` 组定义

### 15.3 游戏内命令（玩家）

| 命令 | 说明 |
|:--|:--|
| `/rank` | 查看自己的权限组、在线时长进度、下一步可申请项（按当前组动态展示） |
| `/apply` | 查看可申请的审核类型（按当前权限组过滤） |
| `/apply builder [理由]` | 提交晋升建造者申请（需为成员） |
| `/apply admin [理由]` | 提交晋升管理员申请（需为建造者） |
| `/apply status` | 查看自己的申请历史与状态 |
| `/apply cancel <类型>` | 撤回自己的待审申请 |

### 15.4 游戏内命令（管理员）

| 命令 | 说明 |
|:--|:--|
| `/review approve <玩家>` | 通过该玩家的晋升申请（自动执行 LP 晋升，成功后通知双方） |
| `/review reject <玩家>` | 拒绝该玩家的晋升申请 |

### 15.5 群聊指令（管理员）

| 命令 | 说明 |
|:--|:--|
| `$v l` | 查看全部待审申请（含申请人当前权限组） |
| `$v y <玩家>` | 通过该玩家的申请 |
| `$v n <玩家>` | 拒绝该玩家的申请 |
| `$p u <玩家>` | 手动升级（如 `$p u 张三`） |
| `$p d <玩家>` | 手动降级 |

> 群指令与游戏内命令等效；审核/升降级结果会同步推送到群聊。

### 15.6 完整流程示例

**玩家晋升建造者**：

```
1. 玩家（成员）在线时输入：/apply builder 想用 WorldEdit 建东西
   → 收到「申请已提交，等待管理员审核」
2. 管理员在群聊：$v l
   → [晋升建造者] 张三（当前组：member）：想用 WorldEdit 建东西（刚刚 提交）
3. 管理员在群聊：$v y 张三
   → 「已通过 张三 的「晋升建造者」申请。」；LP 自动晋升为 builder
4. 玩家下次上线看到：你的权限已升级：建造者。
```

**手动升降级**：`$p u 张三`（成员→建造者）、`$p d 张三`（降一级）。不在权限链上的玩家首次升级会自动入链并直达成员。

**说明**：
- 权限组只应通过本系统（`/apply` 审核 / `$p` 升降级）管理，请勿用 `lp user X parent add` 手动叠加组，否则会造成权限判定异常
- 结案申请记录每玩家自动保留最近 10 条，历史记录自动裁剪（文件大小有上限）
- 详细设计见 [权限系统方案文档](reports/permission-system-v2.md)

### 15.7 坐牢治理（作弊玩家隔离，prison）

作弊玩家可被强制移入独立的 `prison` 组（**不参与** default→member→builder→admin 四级 track）：

- **权限全禁**：仅保留基础连接权限，所有开放命令被执行前被拦截（`PrisonDenyInterceptor`）
- **传送牢房**：入狱即传送至 `prison.cell_location`（`world,x,y,z[,yaw,pitch]`）；未配置或世界未加载时回退玩家当前世界出生点
- **防自动回四级**：prison 玩家不参与自动晋升/申请审核，出狱后从 default 重新开始

| 命令 | 权限 | 说明 |
|:--|:--|:--|
| `/prison <玩家> on` | 管理员 | 将玩家关入监狱（LP 异步执行，结果回显命令发起者） |
| `/prison <玩家> off` | 管理员 | 释放玩家出狱 |

> prison 功能依赖 LuckPerms；无 LP 时自动降级（判定恒非囚犯）。完整权限组设计见 [permission-groups.md](permission-groups.md) 的 P0 节。

## 十六、插件自更新

插件内置自更新（默认开启），从 [Hangar](https://hangar.papermc.io/) 查询 OrzMC 新版本：

- **`update.channel`**：`release`（正式版，默认）或 `beta`（开发版 `-dev` 构建）
- 启动后经 `update.check_interval_hours`（默认 12 小时）异步检查一次；发现新版本时：
  - `update.auto_download: false`（默认）→ 控制台提示，管理员 `/update now` 手动下载
  - `update.auto_download: true` → 自动下载到 `plugins/update/`（sha256 校验通过才落盘）
- 下载完成后**重启服务器即生效**（Paper 自动替换旧 jar）；管理员可随时 `/update check` 查询状态
- 所有检查与下载均走异步线程，不卡主线程；无法识别本地构建信息等异常场景自动降级为仅提示

> 各通道最新版本以 Hangar 发布时间为准；插件本地构建时间晚于远程发布（本地更新尚未发版）时不误判。

---

> 完整信息请参阅：[README](../README.md) | [架构文档](architecture.md) | [贡献指南](../CONTRIBUTING.md)
