# Bot 内置直连（backend=builtin）公共骨架

> **状态：现行** ｜ **最后更新**：2026-09-06
>
> 本页是 **builtin 内置直连**四平台（QQ / 飞书 / Telegram / Discord）共用的接入流程骨架。
> **接入任一平台前先读本页**，平台特有步骤见对应平台册：QQ / 飞书 / Telegram / Discord。
> EasyBot 网关通道见 [`bot-easybot.md`](bot-easybot.md)。

---

## 1. 概念：两通道与会话模型

`im.yml` 顶部 `backend` 选择消息通道：

| 通道 | 会话值 | 适用 |
|------|--------|------|
| `easybot`（默认） | EasyBot 后台「会话 key」（`qq:conv_xxx`） | 已部署 EasyBot；见 bot-easybot |
| `builtin` | **平台原生标识**：`<platform>:group:<群标识>` / `<platform>:user:<用户标识>` | 免网关直连；本骨架 |

三类绑定会话（`im_bindings.yml` 的 `sessions.<平台>.*`）：

| 会话 | 含义 | 用途 |
|------|------|------|
| `admin_group` | 管理群/管理频道 | 群主/管理员可发管理指令；公开通知无 player_group 时降级发这里 |
| `player_group` | 玩家群/玩家频道 | 公开通知（PUBLIC）广播 |
| `admin_dm` | 管理员私聊 | 私密通知（PRIVATE）；绑定后可上行问答（非管理命令） |

## 2. 涉及文件

- `plugins/OrzMC/im.yml` —— 通道（`backend`）与平台凭据（`platforms.<id>`）+ 可选顶层 `proxy` 段；**改后重启生效**（backend/凭据/代理在启动时装配，`/config reload im` 只重载文件不重建通道）；
- `plugins/OrzMC/im_bindings.yml` —— 会话绑定（`sessions.<id>.*`）；由 `/config im bind` 写入，**即时生效**，一般不用手改。

## 3. 管理命令（仅控制台 / 游戏内 op，D10）

| 子命令 | 功能 |
|--------|------|
| `/config im setup` | 首次接入 checklist（backend/凭据/绑定引导） |
| `/config im status` | 通道健康 + 会话绑定 + **未绑定候选**（D11）一览 |
| `/config im bind <平台> group\|user <会话id> <admin_group\|player_group\|admin_dm>` | 绑定会话并持久化 |
| `/config im test <平台> group\|user <会话id> <文本>` | 向指定会话发测试文本验证下行 |

> 群内自动发现（D11）：未绑定会话的消息只进**控制台日志 + status 候选列表**，不向陌生群回消息；
> 候选日志会直接给出**可复制的 bind 命令**，复制执行即完成绑定（即时生效，绑定后自动从候选清除）。

## 4. 通用配置文件形态（四平台一致）

```yaml
backend: builtin

# 可选：网络代理（D13）——全局兜底；platforms.<id>.proxy 可平台级覆盖
# proxy:
#   enabled: false          # false/缺省 = 直连
#   type: http              # 默认 http
#   host: ''
#   port: 0

platforms:
  <平台id>:                  # qq / feishu / telegram / discord
    enabled: false
    ...                      # 平台凭据见对应平台册
    # proxy: { enabled: false, host: '', port: 0 }   # 可选平台级覆盖
```

**proxy 用途**：Telegram/Discord 国内服务器出墙必需（域名不可达）；QQ/飞书国内直连即可，
**海外部署**（访问国内 QQ/飞书 API 不稳/被挡）可配代理回国。不配置一律直连。

## 5. 通用接入流程（阶段 B–D 骨架）

**阶段 B · 插件侧配置**：改 `im.yml`（backend: builtin + 目标平台 enabled + 凭据）→ **重启服务器**。

**阶段 C · 会话发现与绑定**：
1. 在目标群/频道与 bot 私聊各发一条任意消息触发发现（D11）；
2. 控制台/status 出现未绑定候选与 bind 命令 → 复制执行：
   ```
   /config im bind <平台> group <群会话id> admin_group
   /config im bind <平台> group <群会话id> player_group   # 可略；留空公开通知降级发管理群
   /config im bind <平台> user <用户会话id> admin_dm       # 管理员私聊（可选）
   ```
3. 成功提示「会话绑定已写入并持久化」，即时生效，无需重启。

**阶段 D · 通用验收基线**（各平台册只列平台差异项）：

| # | 验证项 | 操作 | 期望 |
|---|--------|------|------|
| 1 | 通道健康 | `/config im status` | `<平台> 平台: 启用` + 连接正常 |
| 2 | 上行问答 | 群内发 `$h` | bot 回复帮助 |
| 3 | 管理指令权限 | 群主/管理员发 `$e help` | 成功；非管理被拒（fail-closed） |
| 4 | 下行 | `/config im test <平台> group <id> 你好` | 目标会话收到 |
| 5 | 绑定持久化 | 重启后 `/config im status` | 绑定仍在 |
| 6 | 通知推送 | 触发服务器通知（玩家上下线/启动停止） | player_group/admin_dm 收到 |

## 6. 通用 FAQ

| 现象 | 原因 / 处理 |
|------|------------|
| 重启日志 `IM backend=builtin…无任何可用平台…已停用群功能` | im.yml 平台未启用或凭据缺失（D3 停群告警不自动回退）；修好后**重启** |
| 平台凭据改了不生效 | `backend`/凭据/代理启动时装配——必须重启（`/config reload im` 不重建通道） |
| 与 EasyBot/其它程序同时连同一平台 | 一个凭据只允许一个实例消费事件（R3）；切换前先停用其一 |
| 陌生会话发消息 bot 不回 | 未绑定（D11）：绑定命令在控制台/status 候选给出，复制执行即生效 |

## 7. 通用能力边界（平台各自边界见平台册）

- **仅文本**：媒体/富文本不支持（D6）；发送**尽力一次不重试**（D7，失败经健康告警）；
- 会话绑定（bind）**仅控制台/游戏内 op**（D10）；健康 key：`builtin.<平台id>`；
- 线程纪律：网络/轮询线程不触 Bukkit API，命令落服务器线程（R12）——管理员判定失败一律按非管理处理（fail-closed）。

> 平台差异（入站通道形态 / 会话值 / 角色判定 / @提及形态 / 域名 / 专属 FAQ）见各平台册「0 差异速览」。
