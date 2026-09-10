# Bot / IM 接入手册总览

> **状态：现行** ｜ **最后更新**：2026-09-10

插件通过 **IM 机器人**承载群聊指令与服务器通知。接入方式有两种互斥通道（`im.yml` 的 `backend` 选择），
本目录按「读者要做的任务」组织各手册。

## 手册地图

| 你要做什么 | 读哪份 |
|-----------|--------|
| 先做**通道选型**（依赖/平台/能力/多开差异） | [`channel-comparison.md`](channel-comparison.md) |
| 理解两种通道与公共会话模型 | [`bot-builtin-common.md`](bot-builtin-common.md)（builtin）或 [`bot-easybot.md`](bot-easybot.md)（EasyBot 网关） |
| 接 EasyBot 网关（默认通道，多平台统一转发） | [`bot-easybot.md`](bot-easybot.md) |
| 插件内置直连（免 EasyBot 进程）：**先读公共骨架，再读平台册** | [`bot-builtin-common.md`](bot-builtin-common.md) → 平台册 |
| —— QQ | [`bot-qq.md`](bot-qq.md) |
| —— 飞书 | [`bot-feishu.md`](bot-feishu.md) |
| —— Telegram | [`bot-telegram.md`](bot-telegram.md) |
| —— Discord | [`bot-discord.md`](bot-discord.md) |

> 功能层面的 Bot 命令 / 通知 / 健康状态见 [features.md §2](../features.md)（功能清单，唯一权威）；
> 本目录是「怎么接/怎么验」的操作手册。IM 架构方案与决策记录见 [dev/im-gateway-inhouse.md](../dev/im-gateway-inhouse.md)（§10 遗留跟进清单）。

## 两通道选型速览

一句话：**只要文本指令/通知、想少一个进程 → builtin；要微信/媒体/多服共用/集中运维 → EasyBot 网关**。

| 维度 | EasyBot 网关（`backend: easybot`，默认） | 内置直连（`backend: builtin`） |
|------|----------------------------------------|-------------------------------|
| 依赖 | 需自建 EasyBot 网关进程 | 无（插件直连各平台官方 API） |
| 支持平台 | QQ / Telegram / Discord / 飞书 / 微信 | QQ / 飞书 / Telegram / Discord |
| 会话值 | EasyBot 后台「会话 key」（`qq:conv_xxx`） | 平台原生标识（见公共骨架 §2） |
| 切通道 | 需按新通道**重新绑定会话** | 同左 |

> 全量差异、优缺点与**实例多开**能力（多平台并行 / 同平台多 bot / 多服共用）见
> [`channel-comparison.md`](channel-comparison.md)。

---

## 文档维护约定

- 涉及 backend / 平台凭据 / bind 命令 / 会话语义的行为变更：同步改公共骨架 + 对应平台册 + features.md §2 + CHANGELOG（同 PR）
- 真机踩坑（FAQ 表）只沉淀**已复现并修复**的条目，附日期
- 各平台「0 差异速览」只写**相对 builtin 公共基线**的差异，公共流程不重复
