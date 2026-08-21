# OrzMC 插件功能清单

> 多平台机器人集成的 Paper / Folia 服务器管理插件
>
> 本文档系统梳理插件的所有功能模块，方便用户快速了解插件能力。
>
> **运行环境**：Paper 26.x 或 Folia（`folia-supported: true`，同一 JAR 双运行时兼容）。适配细节与测试策略见 [Folia 迁移评估文档](folia-migration.md)。

> **相关测试文档**：
> - [插件功能测试用例](test-cases.md)（28 项端到端用例，含前置条件/步骤/预期/实际）
> - [端到端测试报告（2026-08-06）](e2e-test-report-20260806.md)（真实环境：机器人 + 真实玩家 + RCON）
> - [端到端测试报告（2026-08-20 双核心）](e2e-test-report-20260820.md)（Paper + Folia 62/62 用例，E2E 套件 `plugin/e2e/`）
> - E2E 自动化套件：`plugin/e2e/run-all.sh`（01-06 用例：Bot 命令/玩家命令/安全拦截/备份维护/群消息/权限审核，双核心自动检测）

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

插件统一通过 **EasyBot 网关** 接入 QQ、Telegram、Discord、飞书和微信。EasyBot 使用
WebSocket 向插件推送入站消息，插件通过 HTTP API 发送回复和服务器通知。

### 2.2 Bot 命令一览

所有命令使用可配置前缀（`easybot.cmd_prompt_char`，默认 `$`），在命令后加 `?` 可查看详细用法。

| 命令 | 功能 | 权限 |
|------|------|------|
| `$l` | 查看在线玩家 | 通用 |
| `$w` | 查看/清理白名单 | 通用 / 管理员 |
| `$h` | 查看帮助信息 | 通用 |
| `$a` | 添加白名单 | 管理员 |
| `$r` | 移除白名单 | 管理员 |
| `$b` | 触发世界备份 | 管理员 |
| `$o` | 世界优化 | 管理员 |
| `$e` | 执行控制台命令，输出完整回传群聊（含异步输出，日志窗口捕获 + 噪音过滤 + 30 行截断） | 管理员 |
| `$v` | 查看/处理审核申请（`$v l` 列表 / `$v y <玩家>` 通过 / `$v n <玩家>` 拒绝） | 管理员 |
| `$p` | 权限升降级（`$p u <玩家>` 升级 / `$p d <玩家>` 降级） | 管理员 |
| `$d` | IP 黑名单管理 | 管理员 |

### 2.3 通知系统

插件将服务器事件实时推送到 Bot 群/频道：

- **玩家事件**：加入、退出、踢出（含坐标、世界、权限组、在线人数）
- **安全事件**：TNT 爆炸告警（突发聚合）、GeoIP 拦截告警、GeoIP 上游异常告警（私信管理员）
- **维护事件**：备份/优化进度（阶段、百分比、速率、ETA）
- **系统事件**：服务器启动/停止、异常告警、白名单开关告警
- **权限事件**：晋升申请提交/撤回/通过/拒绝、权限升降级通知

所有通知消息通过 **可配置模板** 渲染（50 余个消息模板，涵盖事件通知、Bot 命令反馈、审核/晋升与维护进度），支持变量替换。

### 2.4 Bot 健康状态

- 游戏内 `/bot` 命令查看 EasyBot 连接状态：`enabled`、`http`（Ok / Unknown / NotOk）、`ws`（Ok / NotOk）三个彩色状态词，http 与 ws 异常时可点击跳转 `/bot http`、`/bot ws` 查看详情
- 执行命令时自动尝试重连 WebSocket

### 2.5 EasyBot 网关配置指南

> EasyBot 是一个统一的 IM 网关服务，对外暴露一套 REST API + WebSocket 事件推送接口，
> 屏蔽了各 IM 平台（QQ / Telegram / Discord / 飞书 / 微信）的协议差异。
> 项目地址：[https://github.com/easyIndie/EasyBot](https://github.com/easyIndie/EasyBot)

#### 安装 EasyBot

1. 参考 [EasyBot 官方文档](https://github.com/easyIndie/EasyBot) 完成网关服务部署
2. 启动后通过浏览器访问 EasyBot 管理后台（默认 `http://<部署地址>`）
3. 在管理后台创建 **客服类 API Key**，用于插件与 EasyBot 之间的接口鉴权
4. 在管理后台为各平台添加会话并获取对应的 **会话 key**，填入插件配置

#### 获取配置值

EasyBot 的配置值并非平台原生 ID，均需从 EasyBot 管理后台获取：

| 配置项 | 获取方式 | 示例值 |
|--------|---------|--------|
| `api_key` | EasyBot 后台 → API 密钥 → 创建「客服类」密钥 | `sk-xxxxxxxxxxxx` |
| `admin_group` | EasyBot 后台 → 会话管理 → 创建/查看会话 → 复制**会话 key** | `qq:conv_xxxxxxxx` |
| `player_group` | 同上 | `qq:conv_yyyyyyyy` |
| `admin_dm` | 同上（管理员私聊会话） | `qq:conv_zzzzzzzz` |

> ⚠️ **注意：** `admin_group` / `player_group` / `admin_dm` 的值不是 QQ 群号、Discord 频道 ID 等平台原生标识，而是 EasyBot 管理后台为每个会话分配的 **会话 key**。易混淆时请以 EasyBot 后台显示的值为准。

#### 配置 OrzMC 对接 EasyBot

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

启用需要接入的平台（例如同时启用 QQ 和 Telegram）：

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

#### 消息路由规则

EasyBot 适配器只保留公开与管理员私聊两类路由：

```
消息发送请求（MessageEnvelope）
    │
    ├─ PUBLIC 类型 → 遍历所有已启用平台的 player_group
    │                  ↓ 为空则降级为 admin_group
    │
    └─ PRIVATE 类型 → 遍历所有已启用平台的 admin_dm
```

事件投递目标由代码固定：玩家状态、服务器状态、TNT、GeoIP 和白名单事件走
PUBLIC；异常告警（含 GeoIP 上游异常私信）与维护失败事件走 PRIVATE。

#### 飞书多实例注意事项

> **⚠️ 飞书 WebSocket 多实例限制：** 飞书开放平台 WebSocket 事件订阅使用**集群模式**——同一飞书应用**只随机推送到一个 WebSocket 客户端**。部署多个 EasyBot 实例时，需确保：
> - **方案一：单实例独占**——只启动一个 EasyBot 实例接收飞书事件，其他实例通过配置 `enabled: false` 停用飞书平台；
> - **方案二：多应用隔离**——每个 EasyBot 实例注册不同的飞书应用（不同的 `app_id` / `app_secret`），各自独立接收事件。

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
- 所有方块/实体爆炸事件自动通知到 Bot，通知附带爆炸坐标
- 可配置豁免实体（默认：苦力怕、火球、风弹、末影龙、末地水晶、凋灵、凋灵骷髅、史莱姆、流浪者等）
- **突发聚合防刷屏**：同一区域（128×128×64 方块）+ 同类型事件在聚合窗口内合并，窗口尾部只发一条告警（带 `×N` 次数与首个事件坐标）；批次内事件不立即发送，避免「立即发送 + 尾部汇总」双条刷屏
- 聚合窗口由 `tnt.notify_aggregate_ms` 控制（默认 3000ms），既是突发合并也限制持续刷屏频率
- 方块爆炸统一归并为「方块爆炸」标签，不再按方块材质拆分
- 上下线消息限流由 `player_notify.window_ms`（3s 聚合窗口）承担（`tnt.notify_throttle_ms` 已废弃移除）

---

## 五、安全与访问控制

### 5.1 GeoIP 国家限制
- 玩家登录前异步查询 IP 地理位置
- 仅允许配置的国家代码（`allow_country_code`）通过；未配置时放行所有 IP
- 内网/私有地址（RFC1918、环回、CGNAT、链路本地及 IPv6 内网段）直接放行，不触发 GeoIP 查询
- 上游查询失败/超时/返回空国家码时 **fail-open 放行**（可用性优先），并私信告警管理员（1 分钟限频，日志始终保留完整现场），告警不入玩家群
- 被拒玩家踢出消息中显示其所在国家及允许的国家列表；拦截时 Bot 推送通知

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
- 沿箭的飞行路径 **force-load 区块**（提前约 24 格异步加载，路径不留缺口），远射时箭不会因进入未加载区块而冻结，保证命中事件正常触发、传送落点准确
- 自动检测落点安全：
  - 不在水、岩浆、仙人掌、火、细雪等危险方块中
  - 在世界高度范围内
  - 有实体站立地面 + 上方 2 格空气
- 落点不安全时自动搜索最近的**安全位置**
- 传送成功播放猫咕噜声

### 6.3 实体传送策略
- 默认**限制**命令/插件触发的实体传送（`entity_teleport_enabled: false`），仅白名单内实体可被传送，防 `@e` 选择器误用把海量实体传送到虚空/岩浆造成地图灾难
- **下界传送门穿越不受限制**（`EntityPortalEvent` 始终放行）：掉落物/矿车/船/任意生物照常过传送门
- 白名单项支持：
  - 特殊键：`TAMEABLE`（按接口判定，覆盖猫/狗/鹦鹉 + 全部马科）、`ENDERMAN`、`ARMOR_STAND`、`SHULKER`
  - 任意大写 `EntityType` 名（如 `VILLAGER`）
- 设为 `entity_teleport_enabled: true` 后所有实体均可被命令/插件传送
- 默认白名单（16 项，仅被动/友好实体）：`TAMEABLE` / `ENDERMAN` / `ARMOR_STAND` / `SHULKER` / `VILLAGER` / `WANDERING_TRADER` / `COW` / `PIG` / `SHEEP` / `CHICKEN` / `RABBIT` / `GOAT` / `MOOSHROOM` / `AXOLOTL` / `BEE` / `IRON_GOLEM`

---

## 七、世界维护（备份与优化）

### 7.1 世界备份
- 命令：`$b`（管理员）
- 执行流程：踢出所有玩家 → `save-off` → 压缩世界为 ZIP → `save-on` → 恢复服务
- 备份存储位置：**服务器核心根目录 `backup/`**（如 `~/papermc-test/backup/`，非插件数据目录）
- 目录使用：input=世界目录（`getWorldFolder()`）；backup-core 中间目录 = `backup/tempDir/`（backup-core Cleanup 阶段自动删除）；zip 直接落 `backup/`（output 父目录）；崩溃/断电残留由**启动清理**兜底（MaintenanceModule.setup 清 `backup/tempDir`）
- 自动清理旧备份，保留最近 N 个（`maintenance.backup_retention_count`，默认 5）
- ⚠️ **备份为"优化式备份"**：基于 backup-core（InhabitedTime 阈值过滤，阈值= `maintenance.optimize_tick_time_threshold` 默认 300 秒），活跃 ≤ 阈值（15 秒）的区块不进入备份 zip——备份体积远小于世界（实测 17G 世界 → zip ~1.4G），适合日常快照；如需逐字节全量，请用外部快照/全量备份工具

### 7.2 世界优化
- 命令：`$o`（管理员，需先启用 `maintenance.optimize_enabled`）
- 执行流程：踢出所有玩家 → `save-off` → 优化（剔除低活跃区块，InhabitedTime 阈值同上）→ `save-on` → 恢复服务
- input=世界目录（与备份一致）；in-place 优化（backup-core 内部临时目录处理）
- 实测（2026-08-20，裁剪后世界）：$o 31 秒完成 190,526/190,526 区块，剔除 5.6 万低活跃区块（22.8%），世界正常加载

### 7.3 进度报告
- 实时推送备份/优化进度到 Bot
- 报告内容：阶段名称、完成百分比、处理速率、预计完成时间

### 7.4 维护期间体验
- 维护时服务器列表 MOTD 替换为自定义提示信息
- 玩家被踢出时显示维护提示

---

## 八、玩家加入/退出/踢出通知

### 8.1 推送内容
- 玩家名称（含显示名格式）
- 所在世界（支持别名映射）
- 坐标（支持缩放、精度、单位配置）
- 权限组中文名（访客 / 成员 / 建造者 / 管理员）
- 当前在线人数及在线玩家列表（每行：`玩家名(op) 游戏模式 权限组`）

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

### 10.2 可配置项（29 项）

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
| `tnt.notify_aggregate_ms` | Long | 3000 | TNT/爆炸告警聚合窗口（毫秒） |

**上下线通知**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `player_notify.enabled_join` | Boolean | true | 上线消息通知开关 |
| `player_notify.enabled_quit` | Boolean | true | 下线消息通知开关 |
| `player_notify.enabled_kick` | Boolean | true | 被踢消息通知开关 |
| `player_notify.window_ms` | Long | 3000 | 上下线通知聚合窗口（毫秒） |
| `player_notify.max_list_items` | Integer | 6 | 聚合摘要最多列出的玩家数 |

**命令策略**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `command_policies.tpbow.cooldown_secs` | Integer | 3 | 传送弓冷却（秒） |
| `command_policies.tpbow.admin_only` | Boolean | false | 传送弓仅管理员 |
| `command_policies.menu.cooldown_secs` | Integer | 0 | 菜单冷却（秒） |
| `command_policies.menu.admin_only` | Boolean | false | 菜单仅管理员 |
| `command_policies.portal.cooldown_secs` | Integer | 5 | 传送门冷却（秒） |
| `command_policies.portal.admin_only` | Boolean | true | 传送门仅管理员 |

**Bot（来源：easybot.yml）**
| 配置路径 | 类型 | 默认值 | 描述 |
|---------|------|--------|------|
| `cmd_prompt_char` | String | $ | Bot 命令前缀符 |
| `discord_server_link` | String | null | Discord 邀请链接 |
| `qq_group_id` | String | null | QQ 群号 |

**模板（来源：templates.yml）**
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
| `/orzdebug <Bot命令>` | — | 模拟群里用户发 Bot 命令（调试用） | 通用 |
| `/rank` | — | 查看自己的权限组/时长进度/下一步可申请 | 通用 |
| `/apply [类型] [理由]` | — | 提交权限晋升申请（`/apply builder` / `/apply admin`） | 通用 |
| `/review approve\|reject <玩家>` | — | 审核通过/拒绝玩家的晋升申请 | 管理员 |

---

## 十二、OrzMC 菜单（开发中）

- 命令 `/menu` 打开一个箱子 GUI
- 目前为占位界面，点击提示"功能开发中"
- 后续计划逐步增加快捷功能

---

## 十三、基础设施能力

| 组件 | 说明 |
|------|------|
| **Bot 消息路由** | OrzEasyBot 根据 PUBLIC / PRIVATE 统一路由至玩家群或管理员私聊 |
| **多文件配置** | config.yml、easybot.yml、templates.yml、portals.yml、ip_blacklist.yml，支持热重载 |
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

- **config.yml** — 核心配置（白名单、TNT、维护、GeoIP、命令策略、上下线通知、实体传送、危险命令拦截 guard、聊天反垃圾、进服限流、漏洞加固）
- **easybot.yml** — Bot 通用设置与 EasyBot IM Gateway 连接配置（多平台消息路由、WebSocket + HTTP）
- **guide_book.yml** — 新手指南书内容配置（链接/悬停/样式/分页）
- **permission.yml** — 权限系统配置（`config` 阈值节 + `reviews` 申请记录节，运行时修改）
- **templates.yml** — 通知模板、样式配色、坐标格式、世界别名、权限组显示名、i18n 覆盖
- **portals.yml** — 传送门数据（运行时修改）
- **ip_blacklist.yml** — IP 黑名单数据（运行时修改）

> 大部分配置可通过 `/config` 命令在运行时修改并立即生效，无需重启服务器。

---

## 十五、权限管理系统（Rank & Review）

基于 [LuckPerms](https://luckperms.net/) 的玩家权限晋级系统，提供「自动晋升 + 申请审核 + 手动升降级」全链路。

### 15.1 权限链（4 级）

| 等级 | 权限组 | 中文名 | 获得方式 |
|:--|:--|:--|:--|
| L0 | `default` | 访客 | 默认（所有新玩家） |
| L1 | `member` | 成员 | **自动晋升**：在线时长达到阈值（默认 10 小时，可配置）后下次上线自动晋升 |
| L2 | `builder` | 建造者 | **申请审核**：成员提交 `/apply builder`，管理员审核通过 |
| L3 | `admin` | 管理员 | **申请审核**：建造者提交 `/apply admin`，管理员审核通过 |

玩家在任意时刻只处于一个权限组（LP track 单一事实源），升级 = 在权限链上移动一格。

### 15.2 装即用（无需手动配置 LP）

- **软依赖 LuckPerms**：无 LP 时插件照常运行（权限功能自动降级不可用，其余功能不受影响）
- **自动初始化**：有 LP 时启动自动创建 track `rank`（default→member→builder→admin）与缺失的权限组；**继承链与 track 链序由插件保证正确**——已存在但链序不一致的 track 会重建校正，已有组的继承关系与设计不符会校正 parent（**只动继承，不碰任何权限节点**），组内具体权限以线上定义为准
- 权限节点内容不内置：新组继承链落到 LP 内置 `default` 组，基础权限由各服 `default` 组定义

### 15.3 游戏内命令（玩家）

| 命令 | 说明 |
|:--|:--|
| `/rank` | 查看自己的权限组、在线时长进度、下一步可申请项（按当前组动态展示） |
| `/apply` | 查看可申请的审核类型（按当前权限组过滤） |
| `/apply builder [理由]` | 提交晋升建造者申请（需为成员） |
| `/apply admin [理由]` | 提交晋升管理员申请（需为建造者） |
| `/apply status` | 查看自己的申请历史与状态 |
| `/apply cancel <类型>` | 撤回自己的待审申请 |

### 15.4 游戏内命令（管理员）

| 命令 | 说明 |
|:--|:--|
| `/review approve <玩家>` | 通过该玩家的晋升申请（自动执行 LP 晋升，成功后通知双方） |
| `/review reject <玩家>` | 拒绝该玩家的晋升申请 |

### 15.5 群聊指令（管理员）

| 命令 | 说明 |
|:--|:--|
| `$v l` | 查看全部待审申请（含申请人当前权限组） |
| `$v y <玩家>` | 通过该玩家的申请 |
| `$v n <玩家>` | 拒绝该玩家的申请 |
| `$p u <玩家>` | 手动升级（如 `$p u 张三`） |
| `$p d <玩家>` | 手动降级 |

> 群指令与游戏内命令等效；审核/升降级结果会同步推送到群聊。

### 15.6 完整流程示例

**玩家晋升建造者**：

```
1. 玩家（成员）在线时输入：/apply builder 想用 WorldEdit 建东西
   → 收到「申请已提交，等待管理员审核」
2. 管理员在群聊：$v l
   → [晋升建造者] 张三（当前组：member）：想用 WorldEdit 建东西（刚刚 提交）
3. 管理员在群聊：$v y 张三
   → 「已通过 张三 的「晋升建造者」申请。」；LP 自动晋升为 builder
4. 玩家下次上线看到：你的权限已升级：建造者。
```

**手动升降级**：`$p u 张三`（成员→建造者）、`$p d 张三`（降一级）。不在权限链上的玩家首次升级会自动入链并直达成员。

**说明**：
- 权限组只应通过本系统（`/apply` 审核 / `$p` 升降级）管理，请勿用 `lp user X parent add` 手动叠加组，否则会造成权限判定异常
- 结案申请记录每玩家自动保留最近 10 条，历史记录自动裁剪（文件大小有上限）
- 详细设计见 [权限系统方案文档](./permission-system-v2.md)

---

> 完整信息请参阅：[README](../README.md) | [架构文档](./architecture.md) | [贡献指南](../CONTRIBUTING.md)
