# OrzMCPlugin
[![Pull Request Build Check](https://github.com/OrzGeeker/OrzMCPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/OrzGeeker/OrzMCPlugin/actions/workflows/build.yml)
[![Publish to Hangar](https://github.com/OrzGeeker/OrzMCPlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/OrzGeeker/OrzMCPlugin/actions/workflows/publish.yml)

[![OrzMC](https://img.shields.io/hangar/dt/OrzMC?link=https%3A%2F%2Fhangar.papermc.io%2Fwangzhizhou666%2FOrzMC&style=flat)](https://hangar.papermc.io/wangzhizhou666/OrzMC)
[![OrzMC](https://img.shields.io/hangar/stars/OrzMC?link=https%3A%2F%2Fhangar.papermc.io%2Fwangzhizhou666%2FOrzMC&style=flat)](https://hangar.papermc.io/wangzhizhou666/OrzMC)
[![OrzMC](https://img.shields.io/hangar/views/OrzMC?link=https%3A%2F%2Fhangar.papermc.io%2Fwangzhizhou666%2FOrzMC&style=flat)](https://hangar.papermc.io/wangzhizhou666/OrzMC)

[![OrzMC](https://api.mcbanners.com/banner/resource/hangar/OrzMC/banner.png?background__template=DARK_GUNMETAL)](https://hangar.papermc.io/wangzhizhou666/OrzMC)

[私服](https://minecraft.jokerhub.cn)开服自研插件，用来辅助管理员运维。

本插件针对[PaperMC](https://papermc.io/)服务器进行开发，由于`PaperAPI`兼容`BukkitAPI`和`SpigotAPI`，

所以插件开发对有 Bukkit 和 Spigot 插件开发经验的开发者也比较友好。

---

## 插件配置文件

- [config.yml](./src/main/resources/config.yml)
- [bot.yml](./src/main/resources/bot.yml)
- [guide_book.yml](./src/main/resources/guide_book.yml)
- [tnt.yml](./src/main/resources/tnt.yml)

## 插件提供的能力

### 1. 服务器开启强制白名单

PaperMC 服务器添加此插件后，会自动开启强制白名单模式，不在白名单中的玩家无法进入服务器

```yaml
force_whitelist: true
```

### 2. QQ群内管理服务器

配置 QQ 机器人(搭配 [NapCatQQ](https://github.com/NapNeko/NapCatQQ) 服务)，可以在QQ群里通过命令添加/移除白名单玩家

```
👨‍💼 管理员命令：
$a	添加玩家到服务器白名单中
$r	从服务器白名单中移除玩家
👨🏻‍💻 通用命令: 
$l	查看当前在线玩家
$w	查看当前在白名单中的玩家
$h	查看QQ群中可以使用的命令信息
```

```yaml
# 命令提示字符, 可修改为与应用场景不冲突的字符，例如: /
cmd_prompt_char: '$'
```

#### QQ机器人相关配置

```yaml
# 是否启用 QQBot 机器人功能：true/false
enable_qq_bot: false
# QQBot 所在QQ群号：group_id
qq_group_id: '<QQ群号>'
# QQBot 所在QQ群管理员帐号
qq_admin_id: '<QQ群里服务器管理员对应的QQ号>'
# QQBot 机器人 HTTP/HTTPS 服务端地址，OneBot 11协议
qq_bot_api_server: 'http://127.0.0.1:3000'
# QQBot 机器人 HTTP/HTTPS 服务请求token
qq_bot_api_server_token: '<HTTP_Server_Token>'
# QQBot 机器人 WebSocket 服务端地址
qq_bot_ws_server: 'ws://127.0.0.1:3001'
# QQBot 机器人 WebSocket 服务请求Token
qq_bot_ws_server_token: '<Websocket_Server_Token>'
```

> 2025年9月5日，QQ机器人服务因安全问题被黑客利用，
> 后续添加了 token 鉴权机制，强制配置服务器 token

### 3. Discord频道服务器管理

配置 Discord 机器人，可以在 Discord 文字频道把机器人拉入后管理服务器玩家，命令与 QQ 机器人一致

#### Discord机器人相关配置

```yaml
# Discord 频道机器人开关
enable_discord_bot: false
# Discord 频道机器人 api 授权: discord_bot_token_base64_encoded = base64_encode(discord_bot_token)
# Create Token follow Link: https://discord.com/developers/applications
# Use Shell Command to Generate this value: `echo -n "discord_token_value" | base64`
discord_bot_token_base64_encoded: '<不带空格和回车的Discord机器人Token值进行base64加密后的值>'
# Discord 玩家文字频道，用来发送服务端上下线通知的频道
# 获取方法，设置 -> 高级设置 -> 开发者模式 打开，长按对应文字频道，在弹出的菜单中选择最后一项：复制频道ID
discord_player_text_channel_id: '<Discord文字频道，需要拉入上面配置的Discord机器人做为成员>'
# Discord玩家服务器链接，用在提示文案中引导玩家跳入Discord服务器
discord_server_link: 'https://discord.gg/bqvQdHnmG9'
```

### 4. 飞书群机器人通知

飞书群自定义机器人，由于只能通过调用 webhook 向群里发消息，飞书群只能接收消息，无法发命令到MC服务器，
所以目前只能用来同步服务器状态，不能主动管理玩家进出白名单。

#### 飞书机器人相关配置

```yaml
# Lark飞书群机器人开关
enable_lark_bot: false
# Lark飞书群机器人webhook地址，插件 -> lark群 单方向发消息
lark_bot_webhook: '<飞书机器人对应的webhook地址>'
```

### 5. 提供玩家指令

#### `/tpbow` 玩家进入服务器后，可通过此命令随时获取一把传送弓。

> 使用传送弓射箭，玩家会瞬移到箭落地的位置。如果箭掉落水里或岩浆里，玩家不会瞬移。

#### `/guide` 玩家首次进入服务器后，会获得一本玩家指南，如果后面丢掉了，可以通过此命令重新获取

#### `/menu` 打开内置菜单（箱界面），用于后续扩展功能入口

> 当前为占位界面，点击提示“功能开发中”，后续将逐步增加快捷功能

### 6. TNT服务器防护

可通过配置文件设置，开启服务器爆炸监听、报警和防护。支持在不同世界配置TNT可用白名单，在设置的白名单区域内，TNT相关功能可正常生效

```yaml
# 是否允许使用TNT
enable: false
# 是否允许放置重生锚
enable_respawn_anchor: false
# TNT放置的冷却时间，单位为：秒，防止TNT放置太快
place_cooldown: 5
# 爆炸警报节流时间窗口（毫秒），同一区块同类爆炸在窗口内只通知一次
notify_throttle_ms: 1000
# TNT放置区域白名单
whitelist:
  - minX: 0
    maxX: 0
    minY: 0
    maxY: 0
    minZ: 0
    maxZ: 0
    world: 'world'
  - minX: 0
    maxX: 0
    minY: 0
    maxY: 0
    minZ: 0
    maxZ: 0
    world: 'world_nether'
  - minX: 0
    maxX: 0
    minY: 0
    maxY: 0
    minZ: 0
    maxZ: 0
    world: 'world_the_end'
# 爆炸豁免实体（不会触发爆炸警报），可按需调整
exempt_entities:
  - CREEPER        # 苦力怕
  - FIREBALL       # 火球（恶魂火球等）
  - WIND_CHARGE    # 风弹
  - BREEZE_WIND_CHARGE # 微风风弹
  - ENDER_DRAGON   # 末影龙
  - END_CRYSTAL    # 末地水晶
  - WITHER         # 凋灵
  - WITHER_SKULL   # 凋灵之首（弹射物）
```

### 7. 服务区域限制

为了防止一些国家玩家对服务器的扫描和破坏，可通过配置文件设置服务器允许玩家登录的国家区域

```yaml
# IP地址拦截白名单，在列表区域中的IP被允许登录服务器
allow_country_code:
#  - CN
#  - JP
#  - TW
```

### 8. guide_book.yml 中可配置新手指南手的内容

```yaml
title: '新手指南'
author: '腐竹'
content:
  - text:
      content: '欢迎新朋友来到我的世界！'
      newline_count: 2
  - text:
      content: '服务器中一些热爱创造的小伙伴在这里花费了大量心力建造出了各种漂亮的建筑，希望刚加入的朋友不要随意对其进行破坏，尊重他人的劳动成果。做一个有素质的MC玩家!'
      newline_count: 2
  - text:
      content: '相关链接'
      style:
        bold: true
  - link:
      content: '服务器主页'
      url: 'https://minecraft.jokerhub.cn'
      hover_text: '点击前往主页'
  - link:
      content: '玩家手册'
      url: 'https://minecraft.jokerhub.cn/user/'
      hover_text: '点击查看玩家手册'
  - link:
      content: MC插件使用百科书
      url: 'https://mineplugin.org/'
      hover_text: 点击跳转插件百科
      page_break: true
```

### 9. 备份与维护增强（管理员）

- 命令 `$b` 触发备份：踢出在线玩家、关闭自动保存、异步备份
- 命令 `$o` 优化地图：原地优化世界文件体积，需在配置中启用
- 备份完成后自动压缩为时间戳 ZIP，并按创建时间保留最近 N 份
- 备份期间禁止玩家加入，可显示维护 MOTD，结束后恢复白名单状态

```yaml
# config.yml
# 地图备份与归档
backup_retention_count: 10
# 维护 MOTD 文案（多行与彩色展示）
backup_maintenance_motd: "服务器维护中，稍后再试"
# 地图优化（需手动启用）
optimize_enabled: true
optimize_tick_time_threshold: 300
optimize_on_shutdown: false
```

### 10. 机器人统一网络与健康状态

- 统一异步 HTTP 工具：支持连接/请求超时与指数退避重试
- 日志限流工具：网络异常等仅每周期输出一次，避免刷屏
- 健康状态接口与命令：`/botstatus` 查看 QQ/Discord/Lark 状态与最近错误

```yaml
# bot.yml
# HTTP 超时与重试
http_connect_timeout_seconds: 3
http_request_timeout_seconds: 3
http_max_retries: 3
# WebSocket 重试设置
ws_max_retries: 10
ws_base_retry_ms: 5000
# 日志限流周期（毫秒）
log_throttle_ms: 5000
```

#### Discord 网络容错与代理

- 支持在中国大陆不可达时自动禁用 Discord 机器人，避免持续重试刷屏
- 可配置 HTTP 或 SOCKS 代理，修复连接到 gateway.discord.gg 的网络问题

```yaml
# bot.yml
# Discord 网络与容错
discord_auto_disable_on_connect_error: true
discord_connect_grace_seconds: 10
discord_proxy_type: 'none'   # 可选: none/HTTP/SOCKS
discord_proxy_host: ''
discord_proxy_port: 0
```

### 11. 维护 MOTD（多行彩色）

- 备份期间在服务器列表展示彩色多行 MOTD（包含 QQ 群与 Discord 文案）
- 在较新 API 环境优先使用 Adventure 组件；旧 API 自动回退到传统字符串

---

## 插件使用

- 首次使用插件
    1. 下载本插件后，直接放到 PaperMC 服务器插件目录 `plugins/` 下，启动服务端后，本插件的数据目录就会出现
    2. 修改插件数据目录下的`config.yml`配置文件，重启服务
    3. 首次启动会自动复制默认配置文件（config.yml、bot.yml、tnt.yml、guide_book.yml），并加载默认值

- 更新插件：
    1. PaperMC 插件目录下提供一个名称 `update/`的目录，把要更新的插件文件放到这个目录下面
    2. 下次服务端重启时，插件会被自动移到`plugins/`目录下面，完成插件升级

## 问题反馈

- 如果你在使用过程中发现问题，欢迎给项目提建议：[issues](https://github.com/OrzGeeker/OrzMCPlugin/issues)

- 也可以进入QQ频道进行问题反馈：

  ![lark_issue_feedback_group](./images/lark_issue_feedback.png)

---

## 开发
本插件仅支持 Gradle 构建方式

支持命令行方式构建，也支持使用IDE开发，推荐使用
**[IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download)** +
**[Minecraft Development插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)**
进行插件开发

> 以下假设你在MacOS上进行插件开发

### 使用 Gradle 构建

使用 Gradle Wrapper 进行命令行构建，执行以下命令进行打包：

```bash
$ ./gradlew clean build
```

命令行本地运行调试服务器(自动下载服务端并启动，需要同意EULA协议)：

```bash
$ ./gradlew runServer  # 已默认添加 --nojline --nogui，避免终端特性告警
```

使用 IntelliJ IDEA CE(社区免费版) 构建和运行插件，可以打断点调试，参考文档
[README.md](https://github.com/jpenilla/run-task#basic-usage)
和 [Wiki](https://github.com/jpenilla/run-task/wiki)

![gradle build](./images/gradle_build_guide.png)

 

## 相关链接


- [PaperAPI文档](https://papermc.io/javadocs)

- [SpigotAPI文档](https://hub.spigotmc.org/javadocs/spigot/)

- [Bukkit Wiki](https://bukkit.fandom.com/wiki/Main_Page)

- [TextComponent](https://docs.adventure.kyori.net/text.html#creating-components)
