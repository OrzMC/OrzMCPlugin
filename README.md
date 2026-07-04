# <img src="assets/avatar.png" alt="" width="32" style="vertical-align: middle;"> OrzMCPlugin

[![Pull Request Build Check](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/OrzMC/OrzMCPlugin/branch/main/graph/badge.svg?token=QV5RJRNKW0)](https://codecov.io/gh/OrzMC/OrzMCPlugin)
[![Test Count](https://img.shields.io/badge/tests-700+-blue.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Coverage](https://img.shields.io/badge/coverage-64%25-green.svg)](https://github.com/OrzMC/OrzMCPlugin/actions)
[![Dependabot Updates](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates)
[![Publish](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml)

多平台机器人集成的 Paper 服务器管理插件

> 插件针对 [PaperMC](https://papermc.io/) 服务器进行开发，由于
> `PaperAPI`兼容`BukkitAPI`和`SpigotAPI`，
> 所以插件开发对有 Bukkit 和 Spigot 插件开发经验的开发者也比较友好
> 
> 目前主要安装在我的 [私服](https://orzmc.jokerhub.cn) 用来辅助管理员运维，并发布在：[Hangar](https://hangar.papermc.io/OrzMC/OrzMC) 和 [modrinth](https://modrinth.com/plugin/orzmc) 两个平台

## 插件能力

| 功能模块 | 能力说明 |
|---------|---------|
| 白名单管理 | 控制服务器准入，管理员可通过 Bot 命令（$a/$r/$w）添加/移除白名单，自动清理不活跃玩家，非白名单玩家踢出时附带提示 |
| 多平台 Bot 系统 | 接入 QQ、Discord、Lark 三端，9 个 Bot 命令实现玩家管理/查询/互动，16 个可定制通知模板将服务器事件推送到对应群聊或频道 |
| 跨服传送门 | 管理员可创建或删除传送门，玩家踩踏传送门时跨服 transfer 跳转，可选集成 LoginSecurity 验证身份后再传送 |
| TNT 保护 | 限制 TNT 放置范围，允许区域白名单豁免，TNT 爆炸时群聊通知，并可控制重生锚的爆炸行为 |
| 安全控制 | 按 GeoIP 判断玩家所在国家限制加入，精确 IP/CIDR 段/通配符三种黑名单模式，可选集成 LoginSecurity 二次验证 |
| 传送弓 | 射箭即可传送至落点，自动检测落点安全性（固体方块/不危险），落点不安全时就近搜索安全位置，可配置生物传送策略 |
| 世界维护 | 一键备份或优化世界地图文件，实时进度报告，维护期间 MOTD 自动切换提示玩家 |
| 玩家通知 | 玩家加入/退出/被踢出时向群聊推送详情（含角色名、原因），玩家消息前显示角色标识/头衔 |
| 新手指南书 | 首次进服自动发放一本指南书，内容通过 YAML 配置，服主可自定义引导信息 |
| 运行时配置 | 使用 /config 命令在游戏内管理 24 项配置，修改后热重载生效，无需重启服务器 |
| OrzMC 菜单 | 游戏内呼出功能菜单，集成各项操作的便捷入口（开发中） |

详情可阅读：[插件全部功能](./docs/features.md)

## 安装插件
下载插件后，放到PaperMC服务端插件目录`plugins/`下，启动服务端后，插件会创建相同名称的数据目录。本插件在运行期间，配置被加载到内存中，服务端停止时会写回配置文件。

## 更新插件
PaperMC服务端在插件目录下提供一个名称为`update/`的目录，把需要更新的插件jar文件放到这个目录下面。下次服务端重启时，插件会被自动移到`plugins/`目录下面，完成插件升级。

## 问题反馈
如果你在使用过程中发现问题，欢迎给项目提建议：[issue](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose)

也可以进入QQ频道反馈问题：<br/> ![lark_issue_feedback_group](./images/lark_issue_feedback.png)

## 参与贡献
- [贡献指南](CONTRIBUTING.md)（含开发说明、迭代规范）
- [插件架构](./docs/architecture.md)
- [更新日志](./CHANGELOG.md)