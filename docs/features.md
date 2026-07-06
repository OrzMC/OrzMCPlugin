# OrzMC 插件功能清单

> 多平台机器人集成的 Paper 服务器管理插件
>
> 本文档系统梳理插件的所有功能模块，方便用户快速了解插件能力。

---

## 一、白名单管理

### 1.1 强制白名单
- 启动时自动开启服务器白名单（`whitelist.force_whitelist`，默认 `true`）
- 未添加到白名单的玩家无法进入服务器
- 检测到白名单被意外关闭时，通过 Bot 发送告警通知

### 1.2 踢出提示消息
- 非白名单玩家被踢出时显示自定义消息
- 支持配置 QQ 群号、Discord 邀请链接（可点击）、最多 5 个联系方式（名称 + 平台）

### 1.3 Bot 命令管理
- `$a <玩家>` — 添加玩家到白名单（支持批量：空格或逗号分隔）
- `$r <玩家>` — 从白名单移除玩家（支持批量）
- `$w` — 查看白名单列表（分页显示，带在线/离线状态）

### 1.4 不活跃玩家清理
- `$w` 命令触发清理超过 N 天（默认 90）未上线的白名单玩家
- 自动踢出当前在线的被移除玩家

---

## 二、多平台 Bot 系统

### 2.1 支持的平台

| 平台 | 通信方式 | 能力 |
|------|---------|------|
| **QQ Bot** | WebSocket 长连接（NapCatQQ） | 双向：接收命令 + 推送通知 |
| **Discord Bot** | Discord Bot API | 双向：接收命令 + 推送通知 |
| **Lark（飞书）** | Webhook | 单向：仅推送通知 |
| **EasyBot 网关** | WebSocket 长连接 + HTTP API | 双向：接收命令 + 推送通知（支持 QQ / Telegram / Discord / 飞书 / 微信） |

> QQ Bot 支持配置 NapCatQQ 鉴权 token，增强安全性。

> **⚠️ 飞书 WebSocket 多实例限制：** 飞书开放平台 WebSocket 事件订阅使用**集群模式**——同一飞书应用**只随机推送到一个 WebSocket 客户端**。部署多个 EasyBot 实例时，需确保：
> - **方案一：单实例独占**——只启动一个 EasyBot 实例接收飞书事件，其他实例通过配置 `enabled: false` 停用飞书平台；
> - **方案二：多应用隔离**——每个 EasyBot 实例注册不同的飞书应用（不同的 `app_id` / `app_secret`），各自独立接收事件。

### 2.2 Bot 命令一览

所有命令使用可配置前缀（`bot.cmd_prompt_char`，默认 `$`），在命令后加 `?` 可查看详细用法。

| 命令 | 功能 | 权限 |
|------|------|------|
| `$l` | 查看在线玩家 | 通用 |
| `$w` | 查看/清理白名单 | 通用 / 管理员 |
| `$h` | 查看帮助信息 | 通用 |
| `$a` | 添加白名单 | 管理员 |
| `$r` | 移除白名单 | 管理员 |
| `$b` | 触发世界备份 | 管理员 |
| `$o` | 世界优化 | 管理员 |
| `$e` | 执行控制台命令 | 管理员 |
| `$d` | IP 黑名单管理 | 管理员 |

### 2.3 通知系统

插件将服务器事件实时推送到 Bot 群/频道：

- **玩家事件**：加入、退出、踢出（含坐标、世界、角色信息）
- **安全事件**：TNT 爆炸告警、GeoIP 拦截告警
- **维护事件**：备份/优化进度（阶段、百分比、速率、ETA）
- **系统事件**：服务器启动/停止、异常告警、白名单开关告警
- **空闲提示**：最后一名玩家离开时提醒可进行维护

所有通知消息通过 **可配置模板** 渲染（16 个事件模板），支持变量替换。

### 2.4 Bot 健康状态

- 游戏内 `/bot` 命令查看各 Bot 连接状态
- 字段：enabled、httpOk、wsConnected、apiReady、lastError
- 执行命令时自动尝试重连 WebSocket

---

## 三、跨服传送门

### 3.1 创建传送门
- 命令：`/portal <host> [port]`
- 在玩家当前位置生成下界合金风格传送门（4×5 黑曜石框架）
- 传送门上方生成文字标签显示目标服务器地址

### 3.2 删除传送门
- 命令：`/portal remove <host> [port]`
- 清除对应传送门方块及文字标签
- 需要 OP 或 `orzmc.admin` 权限

### 3.3 跨服传送
- 玩家走进传送门触发 Paper `transfer` 指令，跨服传送
- 集成 LoginSecurity 插件，**未登录玩家禁止使用传送门**
- 传送门数据持久化到 `portals.yml`

---

## 四、TNT 保护系统

### 4.1 放置控制
- 全局开关：`tnt.enable` 控制是否允许放置/激活 TNT（默认关闭）
- 区域白名单：可在指定世界 + 坐标范围内允许 TNT
- 放置冷却：每玩家默认 5 秒冷却（`tnt.place_cooldown`）

### 4.2 重生锚控制
- 独立开关 `tnt.enable_respawn_anchor` 控制是否允许放置重生锚

### 4.3 爆炸通知
- 所有方块/实体爆炸事件自动通知到 Bot
- 通知附带爆炸坐标
- 可配置豁免实体（默认：苦力怕、火球、风弹、末影龙、末地水晶、凋灵、凋灵骷髅、史莱姆、流浪者等）
- 同区块爆炸 1 秒内限流一次（`tnt.notify_throttle_ms`）

---

## 五、安全与访问控制

### 5.1 GeoIP 国家限制
- 玩家登录前异步查询 IP 地理位置
- 仅允许配置的国家代码（`allow_country_code`）通过
- 被拒玩家踢出消息中显示其所在国家及允许的国家列表
- 拦截时 Bot 推送通知

### 5.2 IP 黑名单
- 持久化存储于 `ip_blacklist.yml`
- 支持多种匹配模式：
  - 精确 IP：`192.168.1.1`
  - CIDR：`192.168.1.0/24`
  - 通配符：`10.*`、`192.168.*`
- 管理方式：
  - 游戏内命令：`/blacklist list|add|remove <pattern>`（别名 `/bl`）
  - Bot 命令：`$d`

### 5.3 登录验证集成
- 反射调用 LoginSecurity API
- 未登录玩家不能使用跨服传送门
- 兼容 LoginSecurity 多个 API 版本

### 5.4 命令权限
- 命令可配置为仅管理员可用（OP 或 `orzmc.admin` 权限）
- 非管理员看不到管理员命令的 Tab 提示

---

## 六、传送弓 🏹

### 6.1 获取方式
- 命令：`/tpbow`（别名 `/tpb`）
- 获得一把带有无限附魔的特殊弓

### 6.2 传送逻辑
- 射出的箭矢落地位置即为传送目标
- 自动检测落点安全：
  - 不在水、岩浆、仙人掌、火、细雪等危险方块中
  - 在世界高度范围内
  - 有实体站立地面 + 上方 2 格空气
- 落点不安全时自动搜索最近的**安全位置**
- 传送成功播放猫咕噜声

### 6.3 生物传送策略
- 阻止大多数生物的实体传送事件
- 豁免：已驯服动物、末影人、盔甲架、潜影贝

---

## 七、世界维护（备份与优化）

### 7.1 世界备份
- 命令：`$b`（管理员）
- 执行流程：踢出所有玩家 → `save-off` → 压缩世界为 ZIP → `save-on` → 恢复服务
- 备份存储位置：`plugins/OrzMC/backup/`
- 自动清理旧备份，保留最近 N 个（`maintenance.backup_retention_count`，默认 5）

### 7.2 世界优化
- 命令：`$o`（管理员，需先启用 `maintenance.optimize_enabled`）
- 使用 OrzMCWorld 优化器就地优化世界区块文件
- 支持按区块 tick 耗时过滤（`maintenance.optimize_tick_time_threshold`，默认 300ms）

### 7.3 进度报告
- 实时推送备份/优化进度到 Bot
- 报告内容：阶段名称、完成百分比、处理速率、预计完成时间

### 7.4 维护期间体验
- 维护时服务器列表 MOTD 替换为自定义提示信息
- 玩家被踢出时显示维护提示
- 最后一名玩家离开时 Bot 自动提示可进行维护

---

## 八、玩家加入/退出/踢出通知

### 8.1 推送内容
- 玩家名称（含显示名格式）
- 所在世界（支持别名映射）
- 坐标（支持缩放、精度、单位配置）
- 角色标识：管理员 / 成员（按权限组）
- 当前在线人数及在线玩家列表

### 8.2 模板定制
- 三个独立模板：`player_join`、`player_quit`、`player_kick`
- 支持变量：`{name}`、`{world_alias}`、`{online_count}`、`{x}`、`{y}`、`{z}` 等

---

## 九、新手指南书 📖

### 9.1 自动发放
- 玩家首次进入服务器时自动获得一本指南书
- 丢弃后可通过 `/guide` 命令重新获取

### 9.2 内容配置
- 通过 YAML 配置指南书内容
- 支持丰富格式：纯文本、超链接、悬停提示文字、样式（粗体/下划线/颜色）、分页

---

## 十、运行时配置管理

### 10.1 命令总览
`/config`（别名 `/cfg`）支持以下子命令：

| 子命令 | 功能 | 示例 |
|--------|------|------|
| `list` | 列出所有可配置项 | `/config list` |
| `get <path>` | 查看某项配置的值、类型、默认值、所在文件 | `/config get tnt.enable` |
| `set <path> <value>` | 修改并持久化配置 | `/config set tnt.enable true` |
| `reset <path>` | 恢复为默认值 | `/config reset tnt.enable` |
| `dump` | 打印完整配置树 | `/config dump` |
| `reload [name]` | 热重载指定或所有配置文件 | `/config reload` |

### 10.2 可配置项（24 项）

**白名单**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `whitelist.force_whitelist` | Boolean | true | 启用强制白名单 |
| `whitelist.cleanup_inactive_days` | Integer | 90 | 白名单不活跃清理天数 |
| `whitelist.pagination_delay_ticks` | Integer | 5 | 白名单翻页延迟（tick） |

**维护**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `maintenance.optimize_enabled` | Boolean | false | 启用地图自动优化 |
| `maintenance.optimize_tick_time_threshold` | Long | 300 | 优化触发 tick 阈值（ms） |
| `maintenance.backup_retention_count` | Integer | 5 | 地图备份保留数量 |
| `maintenance.backup_maintenance_motd` | String | 服务器维护中，稍后再试 | 维护 MOTD 提示 |

**TNT**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `tnt.enable` | Boolean | false | 启用 TNT 放置检测 |
| `tnt.enable_respawn_anchor` | Boolean | false | 启用重生锚检测 |
| `tnt.place_cooldown` | Integer | 5 | TNT 放置冷却（秒） |
| `tnt.notify_throttle_ms` | Long | 1000 | TNT 通知限流（毫秒） |

**命令策略**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `command_policies.tpbow.cooldown_secs` | Integer | 3 | 传送弓冷却（秒） |
| `command_policies.tpbow.admin_only` | Boolean | false | 传送弓仅管理员 |
| `command_policies.menu.cooldown_secs` | Integer | 0 | 菜单冷却（秒） |
| `command_policies.menu.admin_only` | Boolean | false | 菜单仅管理员 |
| `command_policies.portal.cooldown_secs` | Integer | 5 | 传送门冷却（秒） |
| `command_policies.portal.admin_only` | Boolean | true | 传送门仅管理员 |

**Bot**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `cmd_prompt_char` | String | $ | Bot 命令前缀符 |
| `discord_server_link` | String | null | Discord 邀请链接 |
| `qq_group_id` | String | null | QQ 群号 |

**模板**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `templates.locale` | String | zh-CN | 本地化语言 |
| `templates.coord.scale` | Double | 1.0 | 坐标缩放比例 |
| `templates.coord.precision` | Integer | 2 | 坐标小数位数 |
| `templates.coord.unit_label` | String | block | 坐标单位标签 |

---

## 十一、游戏内指令总表

| 命令 | 别名 | 功能 | 权限 |
|------|------|------|------|
| `/tpbow` | `/tpb` | 获取传送弓 | 通用 |
| `/guide` | — | 打开新手指南书 | 通用 |
| `/menu` | — | 打开 OrzMC 菜单 GUI（开发中） | 通用 |
| `/bot` | — | 查看 Bot 连接状态 | 通用 |
| `/portal <host> [port]` | — | 创建跨服传送门 | 管理员 |
| `/portal remove <host> [port]` | — | 删除传送门 | 管理员 |
| `/blacklist list\|add\|remove` | `/bl` | IP 黑名单管理 | 管理员 |
| `/config list\|get\|set\|reset\|dump\|reload` | `/cfg` | 运行时配置管理 | 管理员 |

---

## 十二、OrzMC 菜单（开发中）

- 命令 `/menu` 打开一个箱子 GUI
- 目前为占位界面，点击提示"功能开发中"
- 后续计划逐步增加快捷功能

---

## 十三、基础设施能力

| 组件 | 说明 |
|------|------|
| **多文件配置** | config.yml、bot.yml、templates.yml、portals.yml、ip_blacklist.yml、notifications.yml，支持热重载 |
| **样式系统** | 可配置颜色调色板（成功/信息/警告/错误/坐标/玩家等），CSS 十六进制色值 |
| **模板系统** | 变量替换、坐标格式化（缩放/精度/单位）、世界别名/角色别名/i18n |
| **健康注册表** | 线程安全的服务健康状态追踪 |
| **WebSocket 客户端** | 带心跳检测和自动重连的健壮 WS 客户端 |
| **限流日志** | 高频事件限流日志，防止控制台刷屏 |
| **安全调度器** | 包装 Bukkit 调度器，统一异步任务异常日志 |
| **命令拦截器链** | PlayerOnly（仅玩家）、AdminOnly（仅管理员）、Cooldown（冷却） |

---

## 十四、配置插件

插件配置文件位于 `plugins/OrzMC/` 目录：

- **config.yml** — 核心配置（白名单、TNT、维护、GeoIP、命令策略）
- **bot.yml** — QQ/Discord/Lark Bot 连接配置
- **templates.yml** — 通知模板、坐标格式、世界别名、角色别名、i18n 覆盖
- **portals.yml** — 传送门数据（运行时修改）
- **ip_blacklist.yml** — IP 黑名单数据（运行时修改）
- **notifications.yml** — 各事件的通知策略（是否推送、推送到哪些频道）
- **commands.yml** — 命令策略配置

> 大部分配置可通过 `/config` 命令在运行时修改并立即生效，无需重启服务器。

---

> 完整信息请参阅：[README](../README.md) | [架构文档](./architecture.md) | [贡献指南](../CONTRIBUTING.md)
