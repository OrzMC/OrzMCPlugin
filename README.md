# OrzMCPlugin

[![Pull Request Build Check](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/build.yml)
[![Dependabot Updates](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/dependabot/dependabot-updates)
[![Publish](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/OrzMC/OrzMCPlugin/actions/workflows/publish.yml)

多平台机器人集成的 Paper 服务器管理插件

> 插件针对 [PaperMC](https://papermc.io/) 服务器进行开发，由于
> `PaperAPI`兼容`BukkitAPI`和`SpigotAPI`，
> 所以插件开发对有 Bukkit 和 Spigot 插件开发经验的开发者也比较友好
> 
> 目前主要安装在我的 [私服](https://orzmc.jokerhub.cn) 用来辅助管理员运维，并发布在：[Hangar](https://hangar.papermc.io/OrzMC/OrzMC) 和 [modrinth](https://modrinth.com/plugin/orzmc) 两个平台


## 插件能力
- 📋服务器开启强制白名单: 未添加到白名单的玩家无法进入服务器
- 💬社交软件群内管理服务器（命令前缀来自 `config.yml` → `bot.cmd_prompt_char`，默认 `$`）
  ```
  👨‍💼 管理员命令：
  $a	添加玩家到白名单
  $r	从白名单移除玩家
  $b	地图备份
  $o	地图优化（优化地图文件大小）
  $e	执行控制台命令
  $d	添加/移除/查看 IP 黑名单
  👨🏻‍💻 通用命令:
  $l	查看在线玩家
  $w	查看白名单玩家
  $h	查看帮助信息
  ```
  > 💡 在任意指令后加 `?` 可查询该指令的详细用法（如 `$a ?`）
  - 🐧支持QQ群：需要配合使用QQ机器人服务([NapCatQQ](https://github.com/NapNeko/NapCatQQ))，可实现在QQ群内通过命令添加/移除白名单玩家
  - 🤖Discord频道：配置Discord机器人，可以在Discord文字频道把机器人拉入后管理服务器玩家，命令与QQ机器人一致
  - 🕊️飞书群：飞书群自定义机器人，由于只能通过调用webhook向群里发消息，飞书群只能接收消息，无法发命令到MC服务器，所以目前只能用来同步服务器状态，不能主动管理玩家进出白名单。
  > 2025年9月5日，NapCatQQ服务的新手用户因未正确使用token鉴权功能，被黑客利用，遭QQ群被官方大规模封号
  > 后续本插件添加了token配置能力，建议配置NapCatQQ服务器的鉴权token
- 提供玩家指令
  - `/tpbow`（别名 `/tpb`） 玩家进入服务器后，可通过此命令随时获取一把传送弓。
  - `/guide` 玩家首次进入服务器后，会获得一本玩家指南，如果后面丢掉了，可以通过此命令重新获取
  - `/bot` 查看当前机器人状态，用来随时进行机器人通知服务的状态查询（执行时自动尝试重连 WebSocket）
  - `/portal <host> [port]` 创建跨服跳转传送门
  - `/portal remove <host> [port]` 移除跨服跳转传送门（需 OP 或 orzmc.admin）
  - `/menu` 打开内置菜单(当前为占位界面，点击提示”功能开发中”，后续将逐步增加快捷功能)
  - `/blacklist list|add|remove <pattern>`（别名 `/bl`） 管理 IP 黑名单，需 OP 或 orzmc.admin 权限
  - `/config list|get|set|reset|dump|reload [name]`（别名 `/cfg`） 管理员运行时配置查看/修改/重置/导出/热重载
- IP 黑名单防护：支持按 IP 地址或通配符模式（如 `192.168.*`）添加黑名单，匹配的玩家连接时将被拒绝进入服务器
- TNT服务器防护：可通过配置文件设置，开启服务器爆炸监听、报警和防护。支持在不同世界配置TNT可用区域白名单，在设置的白名单区域内，TNT相关功能可正常生效
- 服务区域限制：为了防止一些国家玩家对服务器的扫描和破坏，可通过配置文件设置服务器允许玩家登录的国家区域

## 插件使用

安装插件:
1. 下载插件后，放到PaperMC服务端插件目录`plugins/`下，启动服务端后，插件会创建相同名称的数据目录
2. 本插件在运行期间，配置被加载到内存中，服务端停止时会写回配置文件

更新插件：
1. PaperMC服务端在插件目录下提供一个名称为`update/`的目录，把需要更新的插件jar文件放到这个目录下面
2. 下次服务端重启时，插件会被自动移到`plugins/`目录下面，完成插件升级

## 问题反馈
- 如果你在使用过程中发现问题，欢迎给项目提建议：[issue](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose)
- 也可以进入QQ频道反馈问题：<br/> ![lark_issue_feedback_group](./images/lark_issue_feedback.png)

## 参与贡献

提交代码前请阅读以下内容:
- [贡献指南](CONTRIBUTING.md)
- [更新日志](./CHANGELOG.md)
- [开发说明](./docs/development.md)
- [架构设计](./docs/architecture.md)
- [迭代规范](./docs/governance.md)