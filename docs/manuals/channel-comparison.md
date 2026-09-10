# 双通道选型对比（EasyBot 网关 ↔ builtin 内置直连）

> **状态：现行** ｜ **最后更新**：2026-09-10
>
> 面向服主/运维的选型决策参考。方案定稿与决策记录（D1–D14 / R1–R13）见
> [dev/im-gateway-inhouse.md](../dev/im-gateway-inhouse.md)；分步接入见 [bot-easybot.md](bot-easybot.md)、
> [bot-builtin-common.md](bot-builtin-common.md) 及各平台册。功能清单权威描述在 [features.md §2](../features.md)。

## 1. 两条通道

`im.yml` 顶部 `backend` **全局单选**（决策 D1，v1 不做平台级混合），装配点
`BotMessageServiceProvider.create()`：

| backend | 实现 | 网络路径 |
|---|---|---|
| `easybot`（默认） | `OrzEasyBot` | 插件 ──WS 事件 + REST 发送──▶ 外部 EasyBot 进程 ──适配器──▶ 各平台 IM |
| `builtin` | `BuiltinImDriver`（每平台一个 `PlatformSlot`） | 插件 ──平台官方 API（WS 网关 / 长轮询 / REST）──▶ 各平台 IM |

两条通道对业务层**语义一致**（`BotMessageService` / `MessageEnvelope` 抽象）：11 个 `$` 指令、通知模板、
PUBLIC/PRIVATE 路由、群主/管理员判定（平台官方数据，不配置 ID 白名单）行为相同——切换 backend 时业务侧零改动。

## 2. 差异对照（全量）

| 维度 | EasyBot 外接（`backend: easybot`） | builtin 内置直连（`backend: builtin`） |
|---|---|---|
| 部署形态 | 需常驻网关进程 + 管理后台 + 网关数据库 | 零外部进程，随插件起停 |
| 支持平台 | QQ / Telegram / Discord / 飞书 / **微信** | QQ / 飞书 / Telegram / Discord（**无微信**，设计即移出） |
| 会话值 | 网关后台分配的「会话 key」（`qq:conv_xxx`） | 平台原生标识（`group:<群ID>` / `user:<用户ID>`） |
| 会话 ID 获取 | 后台点选复制，无需发现 | D11 自动发现（控制台日志 + `status` 候选）→ `/config im bind` 绑定 |
| 消息类型 | 网关支持媒体 / 富文本（`parse_mode` markdown / html / none） | **仅纯文本**（D6）；媒体、富文本、批量发送均不支持 |
| 出站可靠性 | HTTP 重试（`http_max_retries: 3`）+ 网关侧投递对账 / 幂等持久化（D8） | **尽力一次不重试**（D7），失败只走健康告警 |
| 连接管理 | 插件只维护到网关的一条 WS（`WebSocketLifecycle` 按 fingerprint reconcile） | 每平台自管 Token 刷新 + 指数退避重连（`ReconnectingGateway`，参数代码内置） |
| 网络代理（D13） | 插件只需连到本地/内网网关；TG/DC 出墙由网关侧解决 | 插件按平台配 `proxy`（顶层全局兜底 + `platforms.<id>.proxy` 覆盖） |
| 配置 | `easybot.yml`：连接地址、`api_key`、各平台会话 key | `im.yml`：backend + 平台凭据 + proxy；`im_bindings.yml`：会话绑定 |
| 配置生效 | `/orzmc config reload` 触发 WS reconcile 重建 | backend / 凭据按手册**需重启**；裸 `/orzmc config reload` 会 reconcile 平台槽（`/config reload im` 只重载文件不重建通道） |
| 健康 / 诊断 | `/bot`（`enabled` / `http` / `ws` 三态，key=`easybot`） | `/config im status`（`builtin.<平台>` key + 绑定 + 未绑定候选） |
| 首次接入引导 | 后台创建 API Key + 会话 | `/config im setup` checklist + 自动发现候选 |
| 不可用处理 | 配置不完整 → 降级 + 告警 | 无任一平台可用 → **停群功能 + 告警，不自动回退**（D3） |
| 上游依赖风险 | 依赖 EasyBot 版本（0.0.33 与 main 的 sender 字段演进待适配，见方案 §9.3） | 跟随各平台官方 API，自主可控 |
| 插件体积 | 0（逻辑在外部进程） | shadowJar +<100KB，零新依赖（复用 JDK HttpClient + Java-WebSocket + Gson） |

## 3. 优缺点

### EasyBot 网关

- ✅ **平台最全**：含微信（builtin 无官方 API，不可实现）
- ✅ **多租户管理面**：API Key / Target Grant / 会话管理后台（方案 D8 记录），无需手工发现会话 ID
- ✅ **可靠性更强**：投递对账 / 幂等持久化 / 消息持久化；支持媒体与富文本
- ✅ **代理集中**：TG/DC 出墙只需网关侧配一次，多台 MC 服共享
- ✅ 网关可服务多台插件实例（各用独立 `api_key`）
- ❌ 多一个进程要部署 / 升级 / 监控，且是单点故障
- ❌ 需要学习后台，配置值（会话 key）非平台原生 ID，易混淆
- ❌ 依赖上游版本演进（字段漂移需适配）
- ❌ 调试链路更长（插件 → 网关 → 平台）

### builtin 内置直连

- ✅ **零外部进程 / 零额外依赖**，配置最小（填凭据即可），部署心智负担最低
- ✅ 每平台独立健康、独立代理、独立重连，故障不跨平台扩散（R10）
- ✅ 国内服务器 QQ / 飞书直连即用；四平台均已真机验收
- ✅ 上游是平台官方 API，无第三方网关版本风险
- ❌ **仅文本**，发送不重试（D7），自动发现 + 手工绑定会话
- ❌ **无微信**
- ❌ 每平台单凭据槽（`platforms.<id>` 一份），无同平台多 bot / 多群多目标
- ❌ 平台注册 / 审核门槛自己承担（QQ 开放平台、飞书企业应用等）
- ❌ 账号并发受平台单连接限制（见 §4）

## 4. 实例多开能力

四个层面结论不同：

| 场景 | EasyBot 外接 | builtin 内置 |
|---|---|---|
| 单实例内**多平台并行** | ✅ `platforms.<id>.enabled` 可同时开 QQ+TG+DC+飞书 | ✅ 每平台一个 `PlatformSlot`，并行且互不阻塞（R10） |
| 同平台**多账号**（同平台两个 bot） | ❌ 插件侧每平台一份配置、每类会话一个目标 | ❌ 每平台一个凭据槽（`PlatformSlot` 以平台名为 key） |
| **多台 MC 服共用**一个机器人 | ✅ 平台连接由网关独占；各服独立 `api_key` 接同一网关（网关侧按授权隔离） | ❌ **R3：一个凭据只允许一个实例消费事件**（飞书长连接集群单活、TG `getUpdates` 互斥、QQ/DC 单 bot 单连接）；多服需多 bot/多应用 |
| 同服**双通道并行 / 灰度** | ❌ 全局单选（D1） | ❌ 同左 |

补充说明：

- **EasyBot 多实例的两个坑**：
  1. 同一个会话 key 被多个插件实例绑定 → 多实例都收到同一入站事件，会重复应答；需在网关侧按 Target Grant 隔离（具体并发分配语义以 EasyBot 后台/官方文档为准）。
  2. 网关自身多实例部署时，飞书同应用仍是集群单活——方案一「单实例独占」或方案二「每实例注册不同飞书应用」，见 [bot-easybot.md §5](bot-easybot.md)。
- **禁止同一凭据被两方同时消费**：切换通道前必须先停用另一方（含 EasyBot 进程或其它机器人程序，R3）。

## 5. 切换须知

1. **不能并行 / 灰度**：`backend` 全局二选一，无平台级混合（D1）；
2. **无自动回退**：builtin 启动失败 = 停群功能 + 日志/`/bot` 告警，等管理员处理（D3）——不会悄悄退回 EasyBot；
3. **会话值不同，必须重新绑定**：EasyBot 用 `qq:conv_xxx`，builtin 用平台原生 ID，切后按新通道 `im_bindings.yml` 重新 bind（`/config im bind`）；
4. **生效方式**：backend 切换必须重启（driver 在启动时选定）；凭据 / 代理变更按手册口径重启。

## 6. 选型速查

- 只要**文本指令 + 通知**、想少一个进程 → **builtin**；
- 要**微信 / 媒体 / 富文本 / 多服共用 / 集中运维** → **EasyBot 网关**；
- 需要**多台服务器共用同一个机器人账号** → EasyBot（builtin 受 R3 硬限制，只能多申请 bot）。
