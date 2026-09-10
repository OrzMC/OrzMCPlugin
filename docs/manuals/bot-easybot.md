# EasyBot 网关接入手册（backend=easybot，默认通道）

> **状态：现行** ｜ **最后更新**：2026-09-10
>
> 默认通道：通过自建 **EasyBot 网关服务**统一接入多平台 IM（QQ / Telegram / Discord / 飞书 / 微信）。
> EasyBot 对外暴露 REST API + WebSocket 事件推送，屏蔽各平台协议差异。
> 项目地址：<https://github.com/easyIndie/EasyBot>
> 想免网关进程直连各平台官方 API → 用内置直连：先读 [`bot-builtin-common.md`](bot-builtin-common.md)。

---

## 1. 安装 EasyBot

1. 参考 [EasyBot 官方文档](https://github.com/easyIndie/EasyBot) 完成网关服务部署
2. 启动后浏览器访问 EasyBot 管理后台（默认 `http://<部署地址>`）
3. 后台创建**客服类 API Key**（插件 ↔ EasyBot 接口鉴权）
4. 后台为各平台添加会话并获取对应**会话 key**，填入插件配置

## 2. 获取配置值

EasyBot 的配置值**非平台原生 ID**，均从 EasyBot 管理后台获取：

| 配置项 | 获取方式 | 示例值 |
|--------|---------|--------|
| `api_key` | 后台 → API 密钥 → 创建「客服类」 | `sk-xxxxxxxxxxxx` |
| `admin_group` / `player_group` / `admin_dm` | 后台 → 会话管理 → 创建/查看会话 → 复制**会话 key** | `qq:conv_xxxxxxxx` |

> ⚠️ `admin_group`/`player_group`/`admin_dm` 的值是 EasyBot 后台为每个会话分配的**会话 key**，不是 QQ 群号、Discord 频道 ID 等平台原生标识。易混淆时以 EasyBot 后台显示为准。

## 3. 配置 OrzMC 对接

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

启用平台（例：同时启用 QQ 与 Telegram）：

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

## 4. 消息路由规则

EasyBot 适配器只保留公开与管理员私聊两类路由：

```
消息发送请求（MessageEnvelope）
    │
    ├─ PUBLIC 类型 → 遍历所有已启用平台的 player_group
    │                  ↓ 为空则降级为 admin_group
    │
    └─ PRIVATE 类型 → 遍历所有已启用平台的 admin_dm
```

事件投递目标由代码固定：玩家状态、服务器状态、TNT、GeoIP 与白名单事件走 PUBLIC；
异常告警（含 GeoIP 上游异常私信）与维护失败事件走 PRIVATE。

## 5. 多实例注意事项

> **⚠️ 飞书 WebSocket 多实例限制：** 飞书开放平台事件订阅为**集群模式**——同一飞书应用只随机推送到
> 一个 WebSocket 客户端。部署多个 EasyBot 实例时：
> - **方案一：单实例独占**——只启动一个实例接收飞书事件，其余实例对飞书平台 `enabled: false`；
> - **方案二：多应用隔离**——每实例注册不同飞书应用（不同 `app_id`/`app_secret`），各自独立接收。

---

## 与 builtin 通道的差异

> 全量差异、优缺点与实例多开能力见 [`channel-comparison.md`](channel-comparison.md)；下表仅速览。

| 维度 | EasyBot（本页） | builtin（平台册） |
|------|----------------|------------------|
| 会话值 | 后台会话 key（`qq:conv_xxx`） | 平台原生标识 |
| 网络形态 | 插件连 EasyBot（WS/REST）；EasyBot 连各平台 | 插件直连各平台官方 API |
| 微信支持 | ✅ | ❌（官方无个人/公共 API） |
| 切换 | 切 builtin 后需按新通道**重新绑定会话** | — |
