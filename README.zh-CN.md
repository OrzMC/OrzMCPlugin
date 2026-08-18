# OrzMC

[![Pull Request Build Check](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/OrzMC/OrzMCPlugin/branch/main/graph/badge.svg?token=QV5RJRNKW0)](https://codecov.io/gh/OrzMC/OrzMCPlugin)
[![Test Count](https://img.shields.io/badge/tests-990+-blue.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Coverage](https://img.shields.io/badge/coverage-78%25-green.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Dependabot Updates](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates)
[![Publish](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml)

通过 EasyBot 统一接入多平台机器人的 Paper / Folia 服务器管理插件
> 🌐 [English](./README.md) | **简体中文**
>
> 插件针对 [PaperMC](https://papermc.io/) 服务器进行开发，同时支持
> [Folia](https://papermc.io/software/folia)（已在 `paper-plugin.yml` 声明
> `folia-supported: true`，同一 JAR 双运行时兼容）。由于
> `PaperAPI`兼容`BukkitAPI`和`SpigotAPI`，
> 所以插件开发对有 Bukkit 和 Spigot 插件开发经验的开发者也比较友好
>
> 目前主要安装在我的 [私服](https://orzmc.jokerhub.cn) 用来辅助管理员运维，并发布在：[Hangar](https://hangar.papermc.io/OrzMC/OrzMC) 和 [modrinth](https://modrinth.com/plugin/orzmc) 两个平台

## 插件能力

| 功能模块 | 能力说明 |
|---------|---------|
| 权限管理系统（Rank & Review） | 基于 LuckPerms 的四级玩家权限链（访客→成员→建造者→管理员）：自动晋升 + 申请审核（`/apply` / `/review` / `$v`）+ 手动升降级（`$p`）。装即用：启动自动创建 track 与缺失权限组；无 LuckPerms 时自动降级，其余功能不受影响 |
| 白名单管理 | 控制服务器准入，管理员可通过 Bot 命令（$a/$r/$w）添加/移除白名单，自动清理不活跃玩家，非白名单玩家踢出时附带提示 |
| 多平台 Bot 系统 | 通过 EasyBot 网关统一接入 QQ、Telegram、Discord、飞书和微信，11 个 Bot 命令实现玩家管理/查询/互动，控制台命令（`$e`）执行结果完整回传群聊（含 Essentials/LuckPerms 等异步输出，日志窗口捕获 + 噪音过滤 + 30 行截断），50 余个可定制消息模板将服务器事件推送到对应群聊或频道 |
| 跨服传送门 | 管理员可创建或删除传送门，玩家踩踏传送门时跨服 transfer 跳转，可选集成 LoginSecurity 验证身份后再传送 |
| TNT 保护 | 限制 TNT 放置范围，允许区域白名单豁免，TNT 爆炸时群聊通知，并可控制重生锚的爆炸行为。突发爆炸聚合为一条告警（带 ×N 与首个事件坐标），发射器/大面积爆炸不再刷屏 |
| 安全控制 | 按 GeoIP 判断玩家所在国家限制加入，精确 IP/CIDR 段/通配符三种黑名单模式，可选集成 LoginSecurity 二次验证 |
| 传送弓 | 射箭即可传送至落点，自动检测落点安全性（固体方块/不危险），落点不安全时就近搜索安全位置；沿飞行路径 force-load 区块，远射稳定命中落点；可配置实体传送策略（默认不禁止，开启时仅白名单豁免） |
| 世界维护 | 一键备份或优化世界地图文件，实时进度报告，维护期间 MOTD 自动切换提示玩家 |
| 玩家通知 | 玩家加入/退出/被踢出时向群聊推送详情（含世界、坐标、在线人数、权限组），在线列表展示每位玩家的游戏模式与权限组 |
| 新手指南书 | 首次进服自动发放一本指南书，内容通过 YAML 配置，服主可自定义引导信息 |
| 运行时配置 | 使用 /config 命令在游戏内管理 25 项配置，修改后热重载生效，无需重启服务器 |
| OrzMC 菜单 | 游戏内呼出功能菜单，集成各项操作的便捷入口（开发中） |

详情可阅读：[插件全部功能](./docs/features.md)

## 安装插件
下载插件后，放到 PaperMC（或 Folia）服务端插件目录`plugins/`下，启动服务端后，插件会创建相同名称的数据目录。本插件在运行期间，配置被加载到内存中，服务端停止时会写回配置文件。

> **Folia**：插件声明 `folia-supported: true`，同一 JAR 同时兼容 Paper 与 Folia。CI 的 `folia-smoke` job 会在每次 PR 真实启动 Folia 服务端，兜底单线程单测/集成测试测不出的 region 线程回归。

> **可选依赖**：[LuckPerms](https://luckperms.net/)（v5.5+）用于启用权限管理系统（Rank & Review）。未安装时插件正常运行，仅权限功能不可用；已安装时启动自动创建 track `rank` 与缺失权限组，**无需手动配置 LuckPerms**。

## 机器人服务配置

OrzMC 的机器人功能统一通过外部 EasyBot IM 网关接入。

[EasyBot](https://github.com/easyIndie/EasyBot) 统一管理 QQ / Telegram / Discord / 飞书 / 微信：

1. 部署 EasyBot 网关服务
2. 在插件 `easybot.yml` 中填入 EasyBot 连接地址
3. `api_key` 从 EasyBot 后台创建**客服类 API Key** 获取
4. `admin_group` 等目标值非平台原生 ID，需从 EasyBot 后台的**会话管理**获取**会话 key**（如 `qq:conv_xxxxxxxx`）

> 详细路由规则：[EasyBot 配置指南](./docs/features.md#25-easybot-网关配置指南)

### 从旧版直连配置升级

旧版的 `bot.yml` 不再加载。升级前请将其中仍需保留的 `cmd_prompt_char`、
`discord_server_link`、`qq_group_id` 和 `log_throttle_ms` 迁移到 `easybot.yml`，
并在 EasyBot 后台完成各平台会话配置。旧版 NapCatQQ、Discord JDA 和飞书 Webhook
直连参数不再需要，可以删除。

## 更新插件
PaperMC服务端在插件目录下提供一个名称为`update/`的目录，把需要更新的插件jar文件放到这个目录下面。下次服务端重启时，插件会被自动移到`plugins/`目录下面，完成插件升级。

## 问题反馈
如果你在使用过程中发现问题，欢迎给项目提建议：[issue](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose)

也可以进入QQ频道反馈问题：<br/> ![飞书反馈群二维码](./images/lark_issue_feedback.png)

## 参与贡献
- [贡献指南](CONTRIBUTING.md)（含开发说明、迭代规范）
- [插件架构](./docs/architecture.md)
- [更新日志](./CHANGELOG.md)
