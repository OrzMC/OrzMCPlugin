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

## 架构重构（进行中）

- 服务层抽象
    - features.whitelist: WhitelistService（增删查与清理策略）
    - features.maintenance: Backup/Optimize（互斥执行、进度与错误回调）
    - features.teleport: TeleportBowService（安全落点与视角控制）
    - features.guide: GuideService（新手指南生成、打开与首登发放）
    - features.menu: MenuService（菜单构建与点击处理）
- 类型化配置
    - infra.config.TypedConfigs 对 config.yml 与 tnt.yml 建立类型映射与默认值集中化
    - commands.* 策略：cooldown_secs / admin_only 可配置命令冷却与权限要求
    - notifications.* 策略：private.enabled / private.admin_only / public.enabled；支持事件 channel_key 精确推送
- 适配层职责
    - server.commands / server.events 仅做参数与数据采样，调用服务
    - infra.binding.CommandBinder / EventBinder 集中注册命令与事件
    - infra.notify.Notifier 统一服务器消息与机器人通知（支持公共与私聊，维护完成/失败会私聊提醒）
    - infra.scheduler.Schedulers 统一主线程与异步、延迟调度
- 迁移说明
    - 目前白名单查看/清理已接入 WhitelistService 与 TypedConfigs，后续将逐步迁移其它模块以提升复用性与可测试性
    - 维护服务增加阶段枚举输出（Running/Done/Error），并结构化输出“阶段 + 进度”，便于外部观测
    - 命令冷却挂点：示例为 tpbow 命令 3s 冷却，统一由 CooldownExecutor 控制

## 五阶段优化清单

- 阶段一：服务层化与职责收敛（完成）
    - 将白名单/指南/菜单/TNT策略/维护服务封装为服务；事件/命令仅采集参数并调用
- 阶段二：类型化配置与策略（完成）
    - Main/TNT/Commands/Notifications/Templates/TemplateOptions 类型映射与默认值
- 阶段三：统一调度与分页（完成）
    - 引入 Schedulers 与 Paginator，白名单分页输出统一调度
- 阶段四：通知与路由（完成）
    - Notifier.event 策略化分发；BotManager 支持 QQ/Discord/Lark 指定频道
- 阶段五：模板化与跨平台呈现（进行中）
    - 玩家/TNT/维护事件模板化与单位/别名；继续补充更多模板项与测试

## 配置拆分指南

- 模块化文件
    - templates.yml
        - 键路径前缀: templates.*
        - 示例: templates.player_join, templates.world_alias.world, templates.coord.unit_label, templates.i18n.*
    - notifications.yml
        - 键路径前缀: notifications.*
        - 示例: notifications.tnt_alert.public.enabled, notifications.whitelist_cleanup.channel_key
    - commands.yml
        - 键路径前缀: commands.*
        - 示例: commands.tpbow.cooldown_secs, commands.menu.admin_only
    - whitelist.yml
        - 顶层键: force_whitelist, cleanup_inactive_days, pagination_delay_ticks
    - maintenance.yml
        - 顶层键: optimize_enabled, optimize_on_shutdown, optimize_tick_time_threshold, backup_retention_count,
          backup_maintenance_motd
    - styles.yml
        - 键路径前缀: styles.colors.*
        - 示例: styles.colors.success, styles.colors.warn, styles.colors.tnt_alert
    - ip_whitelist.yml
        - 顶层键: allow_country_code (列表，ISO 两位大写国家码)
        - 示例: allow_country_code: [CN, JP, TW]
    - portals.yml
        - 键路径前缀: portals."<world:cx:cy:cz>"
        - 字段: target("host:port"), axis("X"/"Z")
        - 示例:
            - portals."world:100:64:200".target: example.com:25565
            - portals."world:100:64:200".axis: X
- 覆盖策略
    - 以模块化文件为准；若缺失关键键，将在启动时记录告警并使用默认值
    - 旧 config.yml 中相关键逐步废弃，迁移后建议删除冗余键以避免混淆
    - config.yml 仅保留说明性内容，不再承载任何功能模块配置

## 架构/模块职责

- Infra 层（基础设施能力）
    - config：配置加载、包装与健康检查
        - AdvancedConfigManager/ConfigManager/ConfigWrapper/ConfigHealthCheck
    - notify：限流与事件派发
        - ThrottledNotifier、Notifier.event(策略见 notifications.yml)
    - health：健康状态注册与查询
        - HealthRegistry(Status: enabled/httpOk/wsConnected/apiReady/lastError/lastUpdated)
    - styles：统一文本样式与颜色
        - OrzTextStyles（读取 styles.yml）
    - server：服务端交互
        - OrzUtil（控制台命令执行、成功/失败/警告文本）
    - net：HTTP 客户端封装
        - AsyncHttp（超时/重试/退避）
    - ws：WebSocket 客户端封装
        - RobustWebSocketClient/WebSocketEventListener（指数退避与抖动、稳定期重置）
    - core：通用常量
        - OrzConstants（TPBOW_KEY、告警前缀）
    - notify 接口化
        - NotifierSink：统一派发接口
        - Notifier：默认使用 BotNotifierSink，可注入自定义实现
- Feature 层（业务编排）
    - 维护、传送门、玩家/TNT/白名单事件、菜单、传送弓、新手指南等
    - 依赖 Infra 能力进行配置读取、通知派发、样式渲染与服务端交互

## 依赖关系图（简述）

- OrzMC（入口）
    - 依赖 infra.config 管理配置
    - 依赖 infra.bot.OrzBotManager 派发机器人消息
    - 触发 infra.notify.Notifier 根据策略派发
- 事件/功能（features.*）
    - 读取配置：infra.config.AdvancedConfigManager
    - 渲染样式：infra.styles.OrzTextStyles
    - 服务端交互：infra.server.OrzUtil
    - 通知派发：infra.notify.Notifier
    - 健康状态：infra.health.HealthRegistry
    - 网络与WS：infra.net.AsyncHttp、infra.ws.RobustWebSocketClient

## 设计原则

- 分层清晰：Feature 只编排业务，Infra 提供能力
- 接口优先：通过 ServiceRegistry 获取服务，避免静态耦合
- 配置类型化：集中 TypedConfigs/默认值与迁移
- 可测试性：外部交互（命令、网络、WS）通过接口抽象，便于替身
- 线程安全：Bukkit 主线程进行方块与实体操作；异步任务做 I/O

## 类型化配置示例

- Styles（styles.yml）
    - [TypedConfigs.Styles](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/TypedConfigs.java#L174-L195)
      统一颜色键与默认值
    - [OrzTextStyles.java](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/styles/OrzTextStyles.java#L11-L19)
      通过类型化读取颜色
- Portals（portals.yml）
    - [TypedConfigs.Portals](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/TypedConfigs.java#L196-L235)
      统一中心坐标/轴向与目标地址
    -
    健康检查：端口范围与键格式校验 [ConfigHealthCheck.validatePortals](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/ConfigHealthCheck.java#L22-L42)
    -
    写入器抽象：[PortalsWriter](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/PortalsWriter.java)
    支持未来替换存储方式
        -
        接入位置：[PortalService.saveToStorage](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L261-L270)

## 策略拦截器示例

- 定义拦截器
    - AdminOnlyInterceptor：管理员权限校验
    - CooldownInterceptor：命令冷却控制
- 注册与使用
    - 在 OrzMC.setupCommandHandler 中，根据 commands.yml 的策略为每个命令注入拦截器链
    -
    参见 [InterceptorExecutor.java](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/binding/InterceptorExecutor.java)

## 序列图（文本示意）

```
OrzMC.onEnable
  -> AdvancedConfigManager.load(all)
  -> ServiceRegistry.register(PortalService)
  -> setupEventListener(EventBinder.bind)
  -> setupCommandHandler
     -> TypedConfigs.CommandPolicies.from(commands.yml)
     -> for each command: InterceptorExecutor(AdminOnly + Cooldown) -> CommandBinder.bind
  -> setupBotManager(异步)
```

## 示例代码段

- 类型化策略读取

```java
TypedConfigs.CommandPolicies cp = TypedConfigs.CommandPolicies.from(configManager.getConfig("commands"));
TypedConfigs.CommandPolicy p = cp.policies.getOrDefault("portal", new TypedConfigs.CommandPolicy(0, false));
```

- 命令拦截器接入

```java
List<CommandInterceptor> list = new ArrayList<>();
list.

add(new AdminOnlyInterceptor(p.adminOnly));
        list.

add(new CooldownInterceptor("portal", p.cooldownSeconds));
        CommandBinder.

bind(this,Map.of("portal", new InterceptorExecutor("portal", new OrzPortalCommand(),list)));
```

## 调用示例

## 命令用法

- /bot
    - 查看机器人健康状态
- /portal
    - 创建传送门：/portal <host> [port]
    - 移除传送门：/portal remove <host> [port] 或 /portal rm <host> [port]
    - 需 OP 权限

## 命令策略（冷却/权限）

- 配置示例（commands.yml）

```yaml
commands:
  bot:
    cooldown_secs: 0
    admin_only: false
  portal:
    cooldown_secs: 5
    admin_only: true
```

- 加载与注入
    -
    类型化解析：[TypedConfigs.CommandPolicies](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/TypedConfigs.java)
    -
    注册拦截器：[OrzMC.setupCommandHandler](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/OrzMC.java)
        - AdminOnlyInterceptor：基于 OP 或权限节点 orzmc.admin
        - CooldownInterceptor：按 commandName|senderName 维度进行秒级冷却
    -
    执行器：[InterceptorExecutor.java](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/binding/InterceptorExecutor.java)

## Portals 配置结构（按服务器地址分组）

```
portals:
  "example_com:25565":
    "world:100:64:200": "X"
    "world:200:64:300": "Z"
```

- 说明
    - 为避免 YAML 将 '.' 识别为层级分隔，写入时对地址进行安全编码：将 '.' 替换为 '_'，如 mc.jokerhub.cn:25565 →
      mc_jokerhub_cn:25565
    - 读取时自动解码为原始地址使用
    -
    参考：[SafeKeys.java](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/SafeKeys.java) [PortalsWriter.java](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/PortalsWriter.java) [TypedConfigs.Portals.from](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/infra/config/TypedConfigs.java#L225-L241)

## 调用示例

- 事件派发
    - com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier.event("tnt_alert", "TNT被点燃")
- 控制台命令
    - com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil.executeConsoleCmd(() -> {}, "save-all")
- 样式渲染
    - com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles.warn("仅 OP 可用")
- 注入自定义通知器
    - com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier.registerSink(new
      com.jokerhub.paper.plugin.orzmc.infra.notify.sinks.BotNotifierSink())

## 测试指南

- 单元测试
    - 对服务类（如 PortalService）注入替身 IServerExecutor/NotifierSink，验证逻辑与路由
    - 对配置接口 TypedConfigs 使用内存配置对象，验证默认值与路径解析
- 集成测试
    - 使用 Paper 的 TestServer 启动环境，验证命令与事件行为
    - 对高频事件（TNT/爆炸）启用 ThrottledLogger/Notifier 限流，验证日志与通知频率

## 实操示例

- 覆盖传送弓冷却
    - 编辑 [commands.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/commands.yml)
    - 设置: commands.tpbow.cooldown_secs: 5
- 修改世界别名与坐标单位
    - 编辑 [templates.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/templates.yml)
    - 设置: templates.world_alias.world: 主世界A；templates.coord.unit_label: meter；templates.coord.scale: 1.0
- 调整 TNT 告警通知频道
    - 编辑 [notifications.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/notifications.yml)
    - 设置: notifications.tnt_alert.channel_key: safety-alerts
- 强制白名单与分页
    - 编辑 [whitelist.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/whitelist.yml)
    - 设置: force_whitelist: true；pagination_delay_ticks: 5；cleanup_inactive_days: 90
- 维护优化与备份
    - 编辑 [maintenance.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/maintenance.yml)
    - 设置: optimize_enabled: true；optimize_tick_time_threshold: 300；backup_retention_count: 5

## 常见故障检查清单

- 模块文件未加载
    - 检查插件数据目录是否存在对应文件: plugins/OrzMC/*.yml
    - 启动日志是否有“缺失关键配置”告警
- YAML 缩进与键路径错误
    - 对照 README 的键路径示例修正，保持前缀一致（如 templates.* / notifications.*）
- 频道路由无效
    - 检查 notifications.*.channel_key 是否匹配机器人映射
    - 确认 BotManager 初始化成功，并检查机器人平台配置
- 命令策略未生效
    - 检查 commands.* 对应命令名是否正确（tpbow/menu）
    - 重启服务器或执行插件重载
- 白名单策略未生效
    - 检查 whitelist.force_whitelist 与服务器实际白名单状态
    - 检查 cleanup_inactive_days 与 pagination_delay_ticks 数值合法
- 维护/优化未执行
    - 检查 maintenance.optimize_enabled 与阈值 optimize_tick_time_threshold
    - 确认无备份/优化并发（插件已做互斥）

## 日志引导

- 启动时会输出关键键缺失的告警，包含文件与键路径，例如：
    - 缺失关键配置: templates.templates.player_join
    - 文件: plugins/OrzMC/templates.yml
    - 请参考 README 的“配置拆分指南”与“实操示例”进行修复

## 快速迁移步骤

- 将原 config.yml 中与模板/通知/命令相关的键迁移到对应模块文件
- 确认 whitelist.yml 与 maintenance.yml 已按示例提供关键键
- 启动后查看日志的“配置健康检查”，若存在问题按提示修复
- 验证功能：
    - 玩家上线/下线模板渲染是否正确
    - TNT 告警是否按照频道路由发送
    - 命令冷却与权限策略是否生效
    - 白名单分页与清理是否按期工作
    - 维护进度模板与优化/备份参数是否正确
    - 传送门中心映射是否存在且加载成功（重启仍可用）

## 跨服传送门

- 命令与权限
    - 仅 OP 可用命令：/portal <host> [port]（默认端口 25565）
    - 冷却与权限在 [commands.yml](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/resources/commands.yml)
      中配置：commands.portal.cooldown_secs, commands.portal.admin_only
- 建造规格
    - 框架：4×5 黑曜石；内层：2×3 下界传送门
    - 仅添加底部单层金块底座，不添加顶部萤石、中部玻璃与末地烛
    - 内层紫色方块 Axis 与边框平行，支持对角朝向自动贴近最近轴向
    - 低高度地形自动抬升，尝试寻找清晰的 2×3 空间
    -
    代码参考：[PortalService.createPortal](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L46-L170)
- 装饰与识别
    - 当前仅保留底部装饰，悬浮标识仍保留（跨服传送/host:port）
    -
    代码参考：装饰与标识生成 [PortalService.createPortal](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L103-L170)
    与 [spawnLabel](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L307-L338)
- 目标匹配与事件
    - 进入传送门时触发 PlayerPortalEvent，使用 3×3×3 邻域查找传送目标，提升稳健性
    - 回退到控制台命令：/transfer [host] [port] [player]
    -
    代码参考：[OrzPortalEvent.onPortal](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/events/OrzPortalEvent.java#L14-L26)
    与 [PortalService.findTarget](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L176-L194)
- 持久化与加载
    - portals.yml 只存中心坐标与轴向、目标；运行时自动推导内层方块映射
    - 启动时加载映射并重建标识
    -
    代码参考：[OrzMC.setupConfigManager](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/OrzMC.java#L192-L201)
    与 [PortalService.loadFromStorage/saveToStorage](file:///Users/bytedance/Documents/OrzMC/plugin/src/main/java/com/jokerhub/paper/plugin/orzmc/features/portal/PortalService.java#L227-L260)

## 传送门存储与清理

- 存储格式
    - portals."<world:cx:cy:cz>".target = "host:port"
    - portals."<world:cx:cy:cz>".axis = "X" 或 "Z"
- 清理方法
    - 删除对应中心键（例如 "world:100:64:200"）节点并保存
    - 重启或执行插件重载后，内层方块映射会自动重建
- 迁移建议
    - 若之前版本使用每个内层方块持久化（world:x:y:z -> host:port），建议清理旧格式并改为中心坐标格式，减少存储体量与维护复杂度

## 状态流转与通知链路

- 运行状态
    - WorldMaintenanceService.running 为唯一维护状态源，登录拦截与 MOTD 统一使用该状态
- 渲染与派发
    - 维护阶段/进度变量由 TemplateResolvers/Options 生成，TemplateRenderer 渲染模板
    - Notifier.event 根据 notifications.yml 策略派发至公共/私聊与指定频道
- 入口调用
    - OrzMessageParser 调用 WorldMaintenanceService 执行 backup/optimize
    - OrzMC.onDisable 按 maintenance.yml 控制关服优化

## 事件与通知配置指南

- 事件键
    - whitelist_cleanup：白名单清理结果
    - tnt_alert：TNT 告警与爆炸
    - maintenance_backup_stage / maintenance_backup_done / maintenance_backup_error
    - maintenance_optimize_stage / maintenance_optimize_done / maintenance_optimize_error
    - player_join / player_quit / player_kick：玩家上线/下线/被踢
    - geoip_block：玩家因地区限制被拒绝
    - exception_alert：适配层异常告警
    - server_maintenance_hint：无人在线的维护提示

## 模板配置

- 在 config.yml 中定义玩家事件模板（默认已提供示例）：
    - templates.player_join: "{name} 上线\n世界:{world} 坐标:{x},{y},{z}\n角色:{role}\n------当前在线(
      {online_count}/{max_count})------\n{online_list}"
    - templates.player_quit: "{name} 下线\n世界:{world} 坐标:{x},{y},{z}\n角色:{role}\n------当前在线(
      {online_count}/{max_count})------\n{online_list}"
    - templates.player_kick: "{name} 被踢\n世界:{world} 坐标:{x},{y},{z}\n角色:{role}\n------当前在线(
      {online_count}/{max_count})------\n{online_list}"
- 可用占位符：{name},{world},{x},{y},{z},{role},{online_count},{max_count},{online_list}
- 配置示例（config.yml）
    - notifications.whitelist_cleanup.channel_key: ops-alert
    - notifications.whitelist_cleanup.private.enabled: true
    - notifications.whitelist_cleanup.public.enabled: false
    - notifications.tnt_alert.channel_key: safety-alerts
    - notifications.tnt_alert.private.enabled: true
    - notifications.tnt_alert.public.enabled: true
    - notifications.maintenance_backup_stage.channel_key: ops-alert
    - notifications.maintenance_backup_done.channel_key: ops-alert
    - notifications.maintenance_backup_error.channel_key: ops-alert
    - notifications.maintenance_optimize_stage.channel_key: ops-alert
    - notifications.maintenance_optimize_done.channel_key: ops-alert
    - notifications.maintenance_optimize_error.channel_key: ops-alert
- 平台频道映射（bot.yml）
    - discord_channels.ops-alert: 123456789012345678
    - discord_channels.safety-alerts: 234567890123456789
    - qq_channels.ops-alert: 987654321
    - lark_channels.ops-alert.webhook: https://open.lark/xyz
