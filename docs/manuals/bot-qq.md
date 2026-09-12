# QQ 接入手册（builtin 内置直连）

> **状态：现行** ｜ **最后更新**：2026-09-06 ｜ 平台流程已真机验收（2026-09-04，Paper 26.2）
> **接入前先读** [`bot-builtin-common.md`](bot-builtin-common.md)（公共骨架：backend/im.yml/bind 命令/通用 FAQ）。
> 本页只列 QQ 平台差异与平台侧步骤。QQ 界面名称以 [QQ 开放平台](https://q.qq.com/) 为准。

---

## 0. 与 builtin 基线的主要差异

| 维度 | QQ |
|------|-----|
| 应用形态 | QQ 开放平台机器人（个人/企业实名；测试可私域机器人 + 沙箱） |
| 凭据 | BotAppID + AppSecret（`platforms.qq.app_id` / `client_secret`） |
| 鉴权 | access_token（2h，插件预刷新 + 失效强换） |
| 入站通道 | 出站 WS 网关（服务端下发 wss 地址，无需公网） |
| 会话值 | `group:<群OpenID>` / `user:<用户OpenID>`（OpenID 平台界面查不到，D11 自动发现） |
| @提及 | 独立 AT 事件类型（无占位符/无文本前缀，无需剥离） |
| 角色判定 | 事件自带 `member_role`（owner\|admin\|member），C2C 恒非管理 |
| 被动回复 | 带 `msg_id` 的被动回复通道（短窗口）+ 主动消息（配额约束，D14） |
| 网络 | 国内可达；海外部署可配 proxy 回国 |

## 1. 平台侧准备（一次性，约 15 分钟）

1. [QQ 开放平台](https://q.qq.com/) → 注册（个人/企业实名）→ **创建机器人**；测试可选**私域机器人**（沙箱即可完整验证）；
2. **开发设置**：复制 BotAppID（→ `app_id`）；AppSecret 点「查看」复制（**仅首次可复制**，→ `client_secret`，妥善保管 R5）；
3. **IP 白名单（必须）**：加运行服务器公网出口 IP（`curl -s https://api.ipify.org`）；走代理工具时让 `api.bot.qq.com`/`bots.qq.com` 直连，或把代理出口 IP 加入白名单——漏配报 `接口访问源IP不在白名单`；
4. **沙箱测试群**：建群名含「测试」的群并设为沙箱群（开发设置→沙箱配置）；沙箱私聊白名单加测试 QQ 号；
5. **两项消息权限（必须，真机验证）**：①「群内全部消息」（否则只收 @ 事件）②「机器人主动在群聊内发言」（否则主动下行/广播被拒，被动回复不受影响）；
6. 群设置 → 群机器人 → 添加机器人进测试群。

> 完成标志：群里 @机器人 得到 QQ 自带接入反馈。

## 2. 插件侧配置

```yaml
backend: builtin
platforms:
  qq:
    enabled: true
    app_id: '你的BotAppID'
    client_secret: '你的AppSecret'
```

重启后控制台期望：

```
[OrzMC] IM backend=builtin：启用内置直连（可用平台：qq）。
[OrzMC] [qq] 网关连接已建立
[OrzMC] [qq] 发送 identify（intents=33554432）
[OrzMC] [qq] 网关 READY（会话已建立）
```

> 完成标志：`[qq] 网关 READY`；`/config im status` 显示 `QQ 平台: 启用` + `connection: 已连接`。

## 3. 会话发现与绑定（10 分钟）

QQ 群/私聊 OpenID 平台界面查不到，由插件自动发现（D11）：在测试群发任意消息 → 控制台出现
`[qq] 忽略未绑定会话消息 target=qq:group:<OpenID>…`（末尾 OpenID 即群会话值，也在 status 候选）。

```
/config im bind qq group <群OpenID> admin_group
/config im bind qq group <群OpenID> player_group   # 可略；留空公开通知降级发管理群
/config im bind qq user <用户OpenID> admin_dm      # 管理员私聊（可选，先私聊 bot 触发发现）
```

## 4. 验收差异项（基线 6 项见公共骨架，另加）

| # | 验证项 | 操作 | 期望 |
|---|--------|------|------|
| 7 | 群普通消息可达 | 群内不 @ 直接发 `$l` | 返回在线列表（依赖 1.5 权限①） |
| 8 | 主动下行 | `/config im test qq group <OpenID> 你好` | 群内收到（依赖 1.5 权限②） |

## 5. 常见问题（真机踩坑）

| 现象 | 原因 / 处理 |
|------|------------|
| 一直不见 `[qq] READY` / 报 `接口访问源IP不在白名单` | 1.3 IP 白名单漏配或代理未放行 QQ 域名 |
| 群里普通消息收不到（只收到 @ 事件） | 1.5 权限①「群内全部消息」未开 |
| 一问一答 OK 但 `/config im test`/广播无动静 | 1.5 权限②「主动发言」未开 |
| 健康 `builtin.qq` 未连接 + `token not exist or expire`(11244) | access_token 失效：插件自动强换重试；持续则核对 app_id/client_secret |
| 广播/通知主动消息失败（日志为 `投递失败（HTTP xxx...）`） | 平台返回了响应：QQ 主动消息有配额（D14），控制频率；被动回复（一问一答）不受影响 |
| 广播/通知失败且日志为 `投递失败（连接阶段异常，已重试一次）: ... java.net.http.HttpConnectTimeoutException` | **连接阶段**失败（5s 内 TCP/TLS 未完成，或连接被拒/DNS/TLS 报错）：插件已自动重试一次仍失败。查服务器出站网络——QQ 白名单是否含**实际出口 IP**（走代理则是代理出口 IP）、防火墙/安全组是否放行 `api.bot.qq.com:443`；同时可用 `curl -w '%{time_connect} %{time_appconnect}\n' -o /dev/null -s https://api.bot.qq.com/` 多次采样看链路抖动 |
| 同样的失败但日志为 `投递网络异常（请求已发出但无响应，结果未知，不重试）: ... HttpTimeoutException: request timed out` | **请求阶段**超时（连上了但 10s 内无响应）：结果未知——消息可能已送达、仅响应丢失，故不重试（避免重复通知）。多为平台侧/中间链路抖动或限流，观察是否与高频主动消息同时出现（控制频率） |
| 地址解析失败但连接未断（日志 `网关地址获取失败，复用最近一次成功地址继续建连`） | 正常兜底：`/gateway/bot` 有频率限制（HTTP 400 code 100017），拿不到新地址时复用上次成功地址建连（旧地址失效会在下一轮重取） |
| 无法识别网关帧/心跳异常 | 网络抖动自动重连；持续检查代理/DNS（解析到 `198.18.x` 多为代理拦截特征） |

## 6. 出站域名放行（R11，有防火墙/白名单需放行）

| 用途 | 域名 | 方向 |
|------|------|------|
| 鉴权（换 access_token） | `bots.qq.com` | HTTPS 出站 |
| 开放 API（网关地址/消息发送） | `api.bot.qq.com` | HTTPS 出站 |
| 出站网关 WS | 由 `/gateway/bot` 下发（`wss://…`） | WSS 出站 |

## 7. 能力边界

- 仅文本（D6）；发送默认尽力一次不重试（D7）：**例外**是 token 失效（401/11244）与**连接阶段失败**（连接超时/DNS/TLS/连接被拒——请求未发出，重试不会重复投递）各重试一次；请求阶段超时（已发出但无响应）结果未知，一律不重试；
- 出站超时：连接 5s（含 TCP/TLS）、请求 10s（`QqSender`/`QqApiClient` 内置常量）；
- 被动回复窗口、主动消息配额、单条文本上限按 QQ 官方当前规则（R7 常量待沙箱实测固化——见 im-gateway-inhouse §10-I2）；
- 一个机器人凭据只允许一个实例消费事件（R3）。
