# Changelog

## [1.0.23] - 2026-08-21

### ✨ 新功能
- **Tab 着色独立开关 `rank_colors.tab_enabled`（#221）** — 与 `nametag_enabled` 对等，单独控制 Tab 列表是否权限着色：
  - `tab_enabled: true`（默认）：Tab 名按等级/OP 着色，与头顶/聊天三处统一
  - `tab_enabled: false`：Tab 名 `playerListName` 置空，恢复服务器原计分板队伍/vanilla 渲染（**team 前缀 + 真实名**），仅头顶+聊天着色
  - **总开关 `enabled: false` 同样置空**：三处着色全停，Tab 一并恢复服务器原显示策略
  - `/orzmc config set/reset rank_colors.*` 改动后经调度线程立即对所有在线玩家热生效（消除原最长 ~60s 周期自愈延迟）
  - 旧服升级自动回填 `rank_colors.tab_enabled` 键（`/orzmc config get` 不再显示 `<null>`）

### ⚠️ 升级注意
- `rank_colors.tab_enabled` 默认 `true`（行为与 1.0.22 一致，Tab 继续着色）。若希望 Tab 恢复服务器原 team 前缀显示，设 `rank_colors.tab_enabled: false`。
- **已知固有交互**：`tab_enabled: false` 且 `nametag_enabled: true` 时，玩家仍处于 orzmc 计分板队伍（头顶着色所需），Tab 回退渲染会沿用该队伍 rank 色——计分板 team 机制决定，无法避免；三处全关或仅聊天着色请设 `nametag_enabled: false` 一并恢复。
- **昵称取舍**：置空后 Tab 显示真实名 + 原 team 前缀，EssentialsX `/nick` 昵称不再体现在 Tab（头顶名牌/聊天仍保留昵称）。

---

## [1.0.22] - 2026-08-21

### ✨ 新功能
- **权限等级玩家名颜色（#219）** — 按 LuckPerms track 四级权限（default→member→builder→admin）在头顶名牌/聊天/Tab 三处统一按等级着色，纯颜色不加前缀，玩家可一眼识别他人权限等级：
  - 头顶名牌：计分板 `orzmc-<组>` 队伍色（16 命名色，`#RRGGBB` 自动吸附最近命名色）；聊天：`AsyncChatEvent` LOWEST 设渲染器；Tab：`playerListName` 用 displayName 纯文本（保留 EssentialsX `/nick` 昵称）
  - **OP 独立体系优先**：`isOp()` 显示 `op_color`（默认金）归 `orzmc-op` 专属队伍，与等级组无关，防同组非 OP 串色
  - **冲突感知**：只管理 `orzmc-*` 队伍；外部插件已占用玩家名牌队伍即让位（聊天+Tab 照常），绝不碰任何队伍 prefix/suffix
  - **EssentialsX 兼容**：聊天/Tab 保留 `/nick` 昵称；聊天渲染器 LOWEST 设置 → EssentialsChat 等格式化插件更高优先级覆盖即让位，rank 色保留在 Tab+头顶
  - **实时刷新**：LuckPerms UserTrackEvent → 等级变更即时变色；60s 周期自愈兜底（覆盖手动 `lp user X parent set` 等不触发事件的操作）
  - **Folia 线程安全**：计分板/Tab 全部经 global 调度器；聊天异步线程只读 LP 在线缓存零阻塞；LP 缺失时全员 default 灰
  - 配置新增 `rank_colors` 段（幂等迁移，`/config reload` 热生效 + `/orzmc config set rank_colors.*` 热调）

### ⚠️ 升级注意
- `rank_colors` 默认启用：升级后首次启动自动写入默认段（不改已有配置），装上即按等级着色；不想启用请设 `rank_colors.enabled: false`。无 LuckPerms 的服务器全员显示 default 灰（OrzMC rank 体系本就依赖 LP）

---

## [1.0.21] - 2026-08-21

### 🐛 修复
- **实体传送策略默认收紧 + 下界传送门放行（#218）** — `entity_teleport_enabled` 默认由 `true` 反转为 `false`（防 `@e` 选择器误用把海量实体传送到虚空/岩浆造成地图灾难）；白名单扩充覆盖常见被动/友好实体（村民/牲畜/友好水生/傀儡，TAMEABLE 按接口判定覆盖猫狗鹦鹉+全部马科）；下界传送门穿越（EntityPortalEvent）**始终放行**，不受开关影响（掉落物/矿车/船/生物照常过门），策略只作用于命令/插件触发的传送

### ⚠️ 升级注意
- ⚠️ **`entity_teleport_enabled` 新默认值只对新装服生效**：存量 config.yml 已写入旧值 `true` 会保留（代码不覆盖已有配置）。如需收紧请在 config.yml 手动改为 `false`，**并补全白名单**（存量白名单仅 4 项，需手动追加 VILLAGER/COW/PIG 等新默认项，否则这些实体命令传送会被拦截）

---

## [1.0.20] - 2026-08-20

### 🐛 修复
- **备份/优化 input 改回世界根目录（#217）** — 26.1+ 布局下 `World#getWorldFolder()`/`getWorldPath()` 返回的是维度数据目录（`world/dimensions/minecraft/overworld`）而非世界根，直接用作 input 会漏备 level.dat/players/世界级 data/下界/末地（#215 回归）——改回 `getWorldContainer()` + server.properties `level-name` 定位世界根；backup/ 与它兄弟路径，天然满足 backup-core 0.3.x 的 input/output 不重叠校验

---

## [1.0.19] - 2026-08-20

### 🐛 修复
- **BUG-E2E-004：symlink 世界备份空跑假完成（backup-core，OrzMCBackup #50/#51）** — Folia 等以符号链接挂载世界目录的场景下，`RealFileSystem.walk` 不跟随符号链接，`$b` 备份静默产出 22B 空 zip（无任何报错）——backup-core 修复（walk 跟随符号链接），双核心复验 246,963/246,963 区块全量备份 ✅
- **备份失败静默化（0.3.x 适配）** — 备份完成但 zip 未落盘时明确报「地图备份失败」并跳过旧备份清理（防 prune 误删唯一可靠副本）；zip 落盘判定用 mtime（同名覆盖不误判）

### ✨ 新功能
- **备份目录迁移到服务器核心根目录** — `plugins/OrzMC/backup/` → `<worldContainer>/backup/`（快照/迁移整体打包）
- **备份中间目录统一在 `backup/` 内处理** — backup-core 临时目录 = `backup/tempDir/`（Cleanup 阶段自动删除），zip 直接落 `backup/`；不再依赖系统临时目录
- **启动清理** — 崩溃/断电残留的 `backup/tempDir` 启动时异步清理（MaintenanceModule.setup）
- **备份/优化 input 改为世界目录** — `getWorldFolder()`（尊重 level-name，优先含 dimensions/region 的真实世界目录）——backup-core 0.3.x overlap 校验天然满足，避免误选非主世界漏备

### 📦 依赖
- backup-core 0.2.2 → **0.3.1**（symlink walk 修复 + 0.3.x API：IOOptions 三参 syncOnFinalize）

### ⚠️ 升级注意
- 备份目录位置变化：存量 zip 位于 `plugins/OrzMC/backup/` 的服务器，升级后请手动迁移到 `<worldContainer>/backup/`（或从新备份开始）
- 备份为「优化式备份」（InhabitedTime 阈值 `maintenance.optimize_tick_time_threshold` 默认 300 秒过滤低活跃区块），如需逐字节全量请用外部快照/全量备份工具

---

## [1.0.18] - 2026-08-19

### 🐛 修复
- **BUG-001：`$w` 白名单分页 Folia 异常（#198）** — Folia 下分页命令抛异常（第一页即炸）——分页偏移钳位修复 + 回归护栏；Paper/Folia 双核心验证
- **BUG-002：地图备份三层根因（backup-core v0.2.1/v0.2.2，OrzMCBackup #46/#47）** —
  - 压缩字节垃圾值（~56 处损坏 chunk）→ UNKNOWN 枚举透传，损坏区块安全保留（不再中断备份）
  - 长度字段荒谬值（如 0x789cd12e≈20 亿）→ >8MB 双守卫短路
  - **EOF 死循环**：损坏 chunk offset 越过文件末尾 → `BufferedRafAccess.readFully` avail=0 时 remaining 不减 → CPU 100% 卡 49% → EOF 保护抛异常快速跳过
  - 插件侧 errorHandler 聚合扩展（覆盖 Write 类损坏 chunk 错误），Done 时统一汇总「含 N 个损坏区块已安全保留」，不再误报失败
- **BUG-003：CommandGuard 审计日志刷屏（#198）** — 命令方块循环注入（20 条/tick）曾产生 21 万条 WARN/53MB/20 分钟——「危险命令放行」WARN 日志 5s 限频（其余降 fine，审计记录不受影响）+ BLOCK 管理员通知 10s 限频（复用 ThrottledNotifier）；实测 4 分钟 13 条（修复前 4800+，~370 倍降幅）

### 🚀 性能
- **备份并行化（#198）** — `RuntimeOptions(0)`（单线程）→ CPU 逻辑核数并行（backup-core 按维度+区域并行）——317 万 chunk 世界备份 14分21秒 → **5分59秒**（2.4 倍提速，扫描速率 5 倍）

### ✨ 新功能
- **群消息统一日志（#201）** — Notifier.routeEvent 统一记录渲染后的群消息（`[群消息:<key>]` 前缀 + ⏎ 换行转义），所有通知类型（白名单拦截/IP黑名单/上下线/审核/异常）进服务器日志——E2E 断言 + 排查投递问题的一手证据，不再依赖 EasyBot API
- **E2E 自动化测试套件（#198-#202）** — 插件仓库 `e2e/`：6 个用例（Bot 命令 / 玩家命令 / 安全拦截 / 世界维护 / 群消息发送 / 权限审核消息）双核心（Paper + Folia）全量回归，Folia 32/32 + Paper 32/32 + 群消息 11/11×2 + 权限 19/19×2 全绿
  - run-all.sh 双核心适配：日志路径 / RCON 端口按端口自动推断（25565→Folia，25566→Paper）
  - **前置模板一致性检查（#200）** — templates.yml 与仓库 diff 拦截（防配置漂移导致群消息格式回归，如 `{online_list}` 字面量残留）
  - 占位符残留检查：所有群消息断言含「无 {xxx} 字面量」校验

### 📦 依赖
- backup-core 0.1.6 → **0.2.2**（#198）

### ⚠️ 升级注意
- 群消息通知样式（#197 表情标题+分割线版块式）已并入本版：存量服升级后需同步新 `templates.yml` 并 `/config reload`，否则仍显示旧样式（详见 #197 说明）
- `entity_teleport_enabled` 语义反转修正（2026-08-16 记录，随本版正式发布）：配置名与实际行为原先相反（设为 `true` 反而限制传送）。现修正为：`true`（默认）= 允许所有实体正常传送（兼容原版行为），`false` = 仅白名单内实体可传送。⚠️ 现存 config.yml 中的 `entity_teleport_enabled: false`（旧默认值）在新语义下将变为「仅白名单可传送」，未自定义过该键的服务器请手动改为 `true`

---

### 🎨 样式（并入本版）
- **群消息样式统一（#197）** — 四类群通知改为「表情标题 + 分割线 + 内容」版块式排版：
  - 白名单拦截：`🙅🏻‍♂️ {玩家} 尝试加入服务器，被白名单拦截`
  - 上下线/被踢：`🎮 当前玩家(N/上限)` 头部 + `🥰 上线` / `😋 下线` / `😂 被踢` 三版块；空版块连同分割线整体省略，版块内 1 人不显示人数、多人显示 `(N)`；单发与聚合摘要共用同一样式
  - 审核消息：`🙋🏻‍♂️ [申请发起]` / `↩️ [申请撤回]` / `❌ [申请拒绝]` / `✅ [申请通过]` 标题行 + 摘要正文（通过/拒绝附 `审核人：` 行）
  - 异常消息：`⚠️ 服务器异常` 外壳 + 异常项列表（支持多项同时显示；白名单关闭告警同款式）
  - 分割线统一为 33 个 `-`；`exception_alert` 消息格式由 CODE_BLOCK 改为 PLAIN（不再套代码块围栏）
  - ⚠️ **升级注意**：本次为**修改既有模板键的值**（非新增键），存量服的 `templates.yml` 不会自动更新——
    升级 jar 后需将新 `templates.yml` 同步到各端并 `/config reload`，否则仍显示旧样式；
    同时移除已失效的 `player_notify.include_online_list` 配置项（新样式头部 `🎮 当前玩家(N/上限)` 已含人数，摘要不再附带在线列表）

### 🚀 平台兼容（并入本版）
- **Folia 全面适配（#186-#190、本 PR）** — 插件现同时支持 Paper 与 Folia 双运行时：
  - `paper-plugin.yml` 声明 `folia-supported: true`，同一 shadowJar 双端兼容
  - 调度统一切到 Folia 兼容调度器（global region / async），实体、方块、区块操作按 region 线程亲和迁移，TNT/通知聚合等共享状态并发安全化
  - 本地 `./gradlew runFolia` 起真实 Folia 调试服务器；`./gradlew foliaSmoke` 无头冒烟（启动 → 插件加载 → 干净退出）
  - Modrinth 单独声明 `folia` loader（Hangar 无 FOLIA 平台，兼容性由 PAPER 条目承载）
  - CI 新增 `folia-smoke` job 每次 PR 真实启动 Folia 回归（初期 `continue-on-error: true`）
  - 详细评估与测试策略见 [docs/folia-migration.md](docs/folia-migration.md)


## [1.0.17] - 2026-08-13

### ✨ 新功能
- **`$e` 命令输出完整捕获（#172）** — 后台指令执行结果不再只是「命令已执行/执行失败」状态文本：
  - 同步代理捕获 + Log4J 日志窗口兜底（40 tick ≈ 2s 收集窗口），异步输出（Essentials `/list`、LuckPerms 查询等）也能完整回传群聊
  - 命令回显（`issued server command`）与玩家聊天行噪音过滤；同步/日志行保序合并去重；超 30 行截断防刷屏；日志缓冲溢出时输出头部提示「可能不完整」
- **备份/优化完成消息耗时中文可读化（#171）** — 新增 `{duration_human}` 模板变量：`854毫秒` / `35秒` / `2分35秒` / `1小时2分3秒`（保留 `duration_ms` 兼容旧模板）
- **群指令帮助信息统一改版** — `$h` 采用 🤖 标题 + 👨💼/👨🏻💻 分组 + ASCII 虚线分隔；`$cmd ?` 采用 🎯📚🚀 三段式用法模板；必选参数 `<xxx>` / 可选参数 `[xxx]` 语义区分

### 🐛 修复
- **`$b`/`$o` 等重量级命令误触（M1-M4 审查修复）** — `$cmd ?` 拦截放宽为前缀匹配（`$b ?x` / `$b ? 2` / 全角空格 `$b　?` 均视为帮助请求），`$e` 特判精确匹配（控制台命令可能以 `?` 开头）；U+3000 全角空格归一化防绕过
- **`$p`/`$v` 无参数回退统一** — fallback 文本与 `$x ?` usageTip 完全一致；`$d`/`$v`/`$p` 用法行对齐修复

### ✅ 测试
- 新增 LogCaptureService（多线程/容量驱逐/ANSI 剥离/缺口检测）、OrzLog4JCaptureAppender（真实 Log4J 链路）、CommandOutputAssembler（噪音/去重/截断）、`$e` 窗口流程（水位先于执行、回退、溢出提示）等测试
- 累计 973 个测试用例（`@Test`）

### 📝 文档
- 权限文档收敛校正——版本号更新、复验标注、历史快照指针（#170）
- builder 组补充 Litematica 投影权限并修正节点计数（#169）
- README / README.zh-CN 同步最新功能

### ⚙️ CI/CD
- 依赖升级：gradle-wrapper 9.6.1 → 9.7.0（#167）、setup-java 5.6.0 → 5.7.0（#168）、gradle/actions 6.2.0 → 6.3.0（#166）

---

## [1.0.16] - 2026-08-09

### ✨ 新功能
- **权限晋升系统二期（Rank & Review 通用审核框架）** — 一期自动晋升 + 二期申请审核全链路：
  - 通用审核框架：注册式审核类型（`builder-promotion` / `admin-promotion`），申请 → 审核 → 处理 → 通知全流程编排，新增审核类型零框架改动
  - 单一权限配置文件 `permission.yml`（两段式：`config` 阈值节 + `reviews` 申请记录节），权限组状态由 LuckPerms track 持有，本地不存储
  - 游戏内命令：`/apply`（提交/查询/撤回）、`/review approve|reject <玩家>`（审核）、`/rank [玩家]`（查询权限组/进度/下一步可申请）
  - 群指令：`$v l` 待审列表 / `$v y|n <玩家>` 审核、`$p u|d <玩家>` 手动升降级（`default→member→builder→admin` 四级，track 钳位）
  - `member→builder` 与 `builder→admin` 均走 `/apply` 申请审核；`default→member` 在线时长达阈值（默认 10 小时）自动晋升
  - 在线列表与上下线广播显示权限组（`玩家名(op) 游戏模式 权限组`），权限组中文名由 `RankService.groupDisplayName` 单一事实源提供
- **TNT/爆炸告警突发聚合** — 大面积爆炸或发射器快速射 TNT 不再刷屏：
  - 同一区域（128×128×64 方块）+ 同类型事件在聚合窗口内合并，窗口尾部只发一条告警（带 `×N` 次数与首个事件坐标）；批次内事件不立即发送，避免「立即发送 + 尾部汇总」双条刷屏
  - 方块爆炸统一归并为 `方块爆炸` 标签，不再按方块材质拆分
  - 新增配置 `tnt.notify_aggregate_ms`（默认 3000ms）控制聚合窗口，既是突发合并也限制持续刷屏频率
  - 仅影响 TNT/爆炸相关告警，玩家上下线等其它消息不受影响
- **实体传送可配置化** — 默认不禁止实体传送（兼容原版行为，如村民/动物过下界传送门）；开启后仅白名单实体豁免（默认 `TAMEABLE` / `ENDERMAN` / `ARMOR_STAND` / `SHULKER`，支持任意大写 `EntityType` 名）
- **GeoIP 上游异常私信告警管理员** — 上游查询失败/超时/返回空国家码时 fail-open 放行，并私信告警管理员（1 分钟限频，日志始终保留完整现场），告警不入玩家群

### 🐛 修复
- **GeoIP 频繁 fail-open** — 统一登录阻塞决策超时预算（3 秒），与上游查询超时对齐，避免预算不一致导致误放行
- **传送弓远射不传送** — 沿箭的飞行路径 force-load 区块（提前约 24 格异步加载，路径不留缺口），修复箭进入未加载区块冻结导致 `ProjectileHitEvent` 不触发、传送落点错误
- **LuckPerms 已有组校正（#163）** — 启动时校正已有权限组的继承链与 track 链序（只动继承，不碰任何权限节点；组内权限完全以线上定义为准）
- **TNT 聚合健壮性** — 窗口尾部调度成功后才入表，避免中途异常留下孤儿条目导致该区域 key 永久静默且 map 无界增长
- **聚合窗口配置健壮性** — `tnt.notify_aggregate_ms` 非正值回退默认，避免静默关闭防刷屏

### ♻️ 重构
- 移除最后一名玩家离开时的维护提示（`server_maintenance_hint` 模板及通知逻辑）
- 移除模板 `role_alias` / `role_groups` 配置，权限组显示名统一收敛至 `RankService.groupDisplayName`，删除模板系统内重复维护的映射
- 统一在线玩家列表格式（`OnlineListFormatter`：玩家名、OP 标记、游戏模式、权限组），`$l` 命令与上下线广播共用同一事实源

### ✅ 测试
- 新增 LuckPermsBootstrap / LuckPermsPromoter / PermissionStore / RankService / RankCommandService / ReviewService / ReviewCommandService / EntityTeleportPolicyService / TeleportBowFlightTracker / ForceLoadedChunkLease 等测试
- 累计 885 个测试用例（`@Test`）

### 📝 文档
- README / README.zh-CN 同步最新功能
- docs/features.md 对齐最新逻辑（TNT 聚合、实体传送策略、GeoIP 告警、权限组显示、配置项）

---

## [1.0.15] - 2026-08-06

### 🐛 修复
- **GeoIP 内网 IP 误拦截** — 内网/私有地址（192.168.x / 10.x / 172.16-31.x / 127.x / 100.64.x 运营商大内网 / IPv6 内网）直接放行，不触发 GeoIP 查询。此前 geojs.io 无法解析私有段返回未知国家码，在 `allow_country_code` 白名单模式下会误拦截内网玩家。公网 IP 仍正常走 GeoIP 区域检查（TDD：9 个测试覆盖）。

---

## [1.0.14] - 2026-08-06

### 🚀 新功能
- **结构化批量投递健康检查** — `/bot` 状态升级为结构化输出（`enabled httpOk wsOk`），批量投递后健康校验。
- **TNT 配置热重载** — TNT 保护配置改为读取时解析，运行时修改立即生效，无需重启。
- **GeoIP 拦截增强** — 拦截改为阻塞等待查询结果并加超时告警，避免异步竞态导致误放行。

### 🐛 修复
- **orzdebug 调试命令不可用（#159）** — 原 `debug` 前缀被原版 `/debug` 命令抢占（Incorrect argument），改前缀为 `orzdebug` 并由 FeatureModule 注册命令，模拟群发 Bot 命令链路恢复正常。
- **传送门配置示例清理** — 移除 portals.yml 资源中的示例条目，防止首次安装误加载测试传送门。
- **shadowJar 构建警告** — 设置 duplicatesStrategy=INCLUDE，消除 Kotlin metadata 合并警告。

### ✅ 测试
- 补齐核心逻辑测试缺口（GuideBook / Tp / Debug 事件）
- 补充 OrzPlayerEventTest 覆盖 prelogin 接线逻辑

### 📝 文档
- README / README.zh-CN 更新
- 插件文档目录新增：功能测试用例（28 项）+ 端到端测试报告（2026-08-06，28/28 通过）

### ⚙️ CI/CD
- 声明 Paper 26.2 支持（保留 26.1 编译基线）
- 依赖升级：shadow 9.6.1 / spotless 8.9.0 / gradle-actions 6.2.0 / setup-java 5.6.0 / Kotlin JVM 2.4.10

---

## [1.0.13] - 2026-08-03

### ♻️ 重构
- **统一 EasyBot 网关** — 移除 NapCatQQ、Discord JDA 与飞书 Webhook 直连适配器，机器人消息统一经 EasyBot 收发。
- **简化消息链路** — 移除多适配器 Router/Manager 与旧重连管理器，由 `OrzEasyBot` 直接实现消息服务、健康状态和重连。
- **统一配置** — 删除 `bot.yml`，将命令前缀、社区入口与日志限流迁移至 `easybot.yml`。
- **固定通知路由** — 删除低使用率的 `notifications` 与 `channels` 配置；玩家状态、服务器状态、TNT、GeoIP、白名单事件固定走 PUBLIC，异常与维护事件固定走 PRIVATE。
- **入站安全** — 仅允许已配置的管理群、玩家群或管理员私聊执行 Bot 命令。

### ⬇️ 依赖
- 移除 `net.dv8tion:JDA` 及其传递依赖，缩减插件产物体积。

---

## [1.0.12] - 2026-07-09

### 🐛 修复
- **管理命令控制台执行支持** — 移除 `blacklist` / `config` 命令的 `PlayerOnlyInterceptor`，允许控制台直接执行黑名单管理和配置热重载命令。

### ⚙️ CI/CD
- **BOT_PAT 直接推送** — CI 创建的个人访问令牌（PAT）直接推送到 `main` 分支，绕过仓库规则集限制，无需通过 PR 提交 bump commit。
- **Modrinth 重复版本检测** — 分页查询参数 `?limit=10000` 确保检查所有历史版本，避免因默认 10 条限制导致漏检重复版本号。
- **Pull Request 权限补全** — bump 版本工作流添加 `pull-requests: write` 权限，支持通过 `gh` CLI 创建和处理 PR。
- **移除 force-push** — bump 版本步骤先清理已存在的远程分支再正常推送，避免 `--force` 的安全风险。

---

## [1.0.11] - 2026-07-06

### 🚀 新功能
- **EasyBot IM 网关适配器** — 新增 `OrzEasyBot` 适配器，支持通过 EasyBot IM Gateway WebSocket 协议接入 IM 平台（QQ / Discord / Lark 等），配置于 `easybot.yml`。
- **`/bot` 命令 EasyBot 重连支持** — `/bot` 命令触发重连时，除 QQ Bot 外同时检查并重建 EasyBot 适配器的 WebSocket 连接。

### 🐛 修复
- **BotReconnectionManager 异常处理** — `tryReconnectIfDisconnected` 中 `onReconnect.run()` 抛出的异常不再阻断后续重连逻辑，正确记录重连状态。
- **BotReconnectionManager 测试修复** — `tryReconnect_qqDisabled_doesNothing` 测试修正为验证 `enable_qq_bot=false` 时方法提前返回行为。

### 📝 文档
- 新增 飞书 WebSocket 多实例限制说明及 EasyBot 网关平台信息

### ⬆️ 依赖升级
- `net.dv8tion:JDA: 6.4.2` → `6.5.0`
- `org.mockbukkit.mockbukkit:mockbukkit-v26.1.2` → `4.114.0`
- `com.diffplug.spotless: 8.7.0` → `8.8.0`
- `codecov/codecov-action: 5` → `7`

---

## [1.0.10] - 2026-07-05

### 🐛 修复
- **bump 版本回退 PR 方式** — 由于 main 分支有 branch protection 规则，bump 版本改为通过 PR 方式提交，直接推送到 main 会因保护规则被拒绝。

### ⚙️ CI/CD
- 版本号递增至 1.0.10

---

## [1.0.9] - 2026-07-05

### 🐛 修复
- **Modrinth 版本号唯一性检查** — 修复 Modrinth 发布因版本号重复失败的问题，bump 版本改为直接推送到 main（绕过 PR 限制）。

### ⚙️ CI/CD
- 版本号递增至 1.0.9

---

## [1.0.8] - 2026-07-05

### 🚀 新功能
- **Modrinth 自动发布** — 集成 Minotaur Gradle 插件，CI 支持自动发布到 Modrinth 平台，与 Hangar 对称的重试/幂等策略。
- **项目图标** — 添加 `assets/avatar.png` 项目图标，并嵌入 README 标题。
- **orzmc-api 模块独立测试** — 为 orzmc-api 纯 Java 模块补充独立测试套件。
- **README 自动同步** — 发布时自动将 README.md 同步至 Modrinth 和 Hangar 项目页面。

### 🔧 重构
- **统一 SemVer 版本号** — Hangar 与 Modrinth 统一使用标准 SemVer 版本号格式，消除两套不兼容的版本字符串。
- **代码质量提升** — JaCoCo 覆盖率阈值提升，新增 Codecov 集成，修复 6 项代码质量问题。

### 🐛 修复
- **server_maintenance_hint 模板顺序** — 交换 MOTD 与提示信息顺序，MOTD 显示在上方。
- **bump-version 失败处理** — 当 PR 创建失败时正确退出而非静默继续。
- **CI 触发修复** — push 事件跳过 PR comment 步骤，避免空操作报错。

### 📝 文档
- 新增 `docs/features.md`（完整的插件功能清单文档，14 个模块详细说明）
- 新增 `docs/publishing-platforms.md`（发布平台运维手册，含 Hangar / Modrinth 配置、Token 管理、发布检查清单）
- 合并 CONTRIBUTING.md、docs/development.md、docs/governance.md 为统一贡献指南
- 清理 images/ 中 4 个废弃文件（architecture.png、architecture.mmd、gradle_build_guide.png、puppeteer.json）
- 更新 publishing-platforms.md（完整重写，对齐当前发布配置）
- README.md 持续更新（功能表格、项目图标、贡献链接）

### 📄 许可
- 添加 GPL-3.0 开源许可证

### ⚙️ CI/CD
- bump-version 步骤在 PR 创建失败时正确退出而非继续执行
- 为 main 分支添加 push 触发构建

### ⬆️ 依赖升级
- `backup-core: 0.1.5` → `0.1.6`

---

## [1.0.7] - 2026-07-03

### ⚡ 性能优化
- **CI 工作流优化** — 去重测试执行、减少 `clean` 的过度调用、添加 Gradle 缓存、合并 release 流程到 publish 工作流。

### 🐛 修复
- **Hangar 发布重试** — 添加指数退避重试逻辑（3 次，20s / 40s / 60s），处理 504 Gateway Timeout。
- **bump 版本 PR 权限** — 为 bump 步骤添加 pull-requests 写入权限。
- **bump 分支处理** — force-push bump 分支，支持已有 PR 时自动跳过。
- **CI 门禁修复** — publish.yml 补全 write 权限声明。

### ⚙️ CI/CD
- 合并 release.yml 到 publish.yml，统一发布流程
- Add Gradle caching to speed up CI builds

---

## [1.0.6] - 2026-07-03

### ⚙️ CI/CD
- 修复 publish workflow 版本号和环境变量问题
- 调整 CI 触发条件

---

## [1.0.5] - 2026-07-03

### ⚙️ CI/CD
- 版本号递增（无代码逻辑变更）

---

## [1.0.4] - 2026-07-03

### ⚙️ CI/CD
- **移除阿里云 Maven 镜像** — 解决国内 CI 因阿里云镜像 502 导致的构建阻断，恢复从 Maven Central 直接拉取依赖。

---

## [1.0.3] - 2026-07-03

### 🐛 修复
- **deprecation / removal 警告** — 解决 Paper API 废弃方法和已移除方法的编译警告。
- **orzmc-api Javadoc** — 补充公开 API 面缺失的 Javadoc 注释，修复 Javadoc 构建警告。
- **CI bump 分支** — publish workflow bump 分支改为从 `origin/main` 创建，避免本地分支状态滞后导致 PR 冲突。

---

## [1.0.2] - 2026-07-03

### 🐛 修复
- **IP 黑名单持久化** — 修复 IP 黑名单在服务器重启后为空的问题（BlacklistService 加载逻辑修正）。
- **CI bump 版本 PR** — 发布后 bump 版本因分支保护规则导致 CI 失败，改为通过 PR 方式提交 bump commit。

### ⚙️ CI/CD
- publish.yml 增加 permissions 显式声明

---

## [1.0.1] - 2026-07-02

### 🚀 新功能
- **IP 黑名单机制** — 新增 `/blacklist` 命令（别名 `/bl`）和 `$d` 机器人命令，支持添加/移除/查看 IP 地址黑名单，匹配的玩家将被禁止加入服务器。黑名单存储于 `config.yml` → `ip_blacklist` 段。
- **Bot 命令统一分派** — 重构 `BotCommandService`，消除三条代码路径分叉，所有 `$cmd` 指令（`$a`, `$r`, `$b`, `$o`, `$e`, `$l`, `$w`, `$h`, `$d`）统一经 `parse()` 方法分派。
- **`$cmd ?` 查询指令用法** — 支持在 Bot 命令后加 `?`（或 `？`）查询该命令的具体用法说明（如 `$a ?`）。
- **指令帮助信息（游戏内）** — 使用 Brigadier 直接注册命令后，游戏内命令帮助（`/help`）正确显示参数结构，不再显示多余的 `[args]` 标记。
- **世界目录结构文档** — 新增 `docs/world-directory-structure-comparison.md`，详细对比旧版 Paper、新版 Paper（26.1+）和 Vanilla 的世界目录结构差异。

### 🔧 重构
- **命令注册迁移** — 从旧的 `CommandMap API` + `CommandBinder` 迁移到 Paper 26.1 官方 `LifecycleEvents.COMMANDS` + Brigadier `LiteralCommandNode` 注册。Tab 补全支持 subcommand 名称自然提示。
- **死代码清理** — 删除未使用的命令类（`OrzBotStatus`, `OrzGuideBook`, `OrzMenuCommand`, `OrzPortalCommand`, `OrzTPBow`）、命令绑定类（`CommandBinder`, `BasicCommandAdapter`, `TabCompleterDelegate`）和拦截器执行器（`InterceptorExecutor`），共删除 444 行死代码。
- **配置重命名** — `whitelist.kick_message.player_group_id` → `whitelist.kick_message.qq_group_id`（兼容旧 key，自动读取）。

### 🐛 修复
- **`WorldMaintenanceService.backup` dryRun 参数修复** — 备份功能 dry-run 模式因参数传递错误失效的问题。
- **Brigadier 命令帮助信息显示** — 修复 `[args]` 在帮助中错误显示的问题，改为干净的无参数 literal。
- **本地开发版本号修复** — 改为固定 `{version}-dev`，移除时间戳后缀，避免 CI 产物冲突。

### ⬆️ 依赖升级
- `backup-core: 0.1.4` → `0.1.5`

### 📝 文档
- 新增 `docs/world-directory-structure-comparison.md`
- README.md、架构文档持续更新

### ⚙️ CI/CD
- Release 成功后自动递增 patch 版本号并提交到 main
- PR 产物调整为 `-pr-#{PR_NUMBER}-{run_number}` 格式

---

## [1.0.0] - 2025-07-26

### 🚀 初始发布
OrzMC 插件首次正式发布，支持 PaperMC 服务器。

### 核心功能
- **多平台 Bot 系统** — 集成 QQ（WebSocket/NapCatQQ）、Discord（JDA）、飞书（Webhook）三端
- **白名单管理** — 强制白名单、Bot 命令远程增删（`$a` / `$r` / `$w`）、不活跃玩家自动清理
- **跨服传送门** — `/portal` 命令创建/移除 4×5 黑曜石传送门，跨服 transfer 跳转
- **TNT 保护** — 爆炸防护 + 区域白名单 + 放置冷却 + 爆炸群聊通知
- **安全控制** — GeoIP 国家限制访问、IP 黑名单（精确/CIDR/通配符）
- **传送弓** — `/tpbow` 获得传送弓，射箭传送 + 安全落点检测
- **世界维护** — `$b` 一键备份、`$o` 地图优化、维护模式 MOTD
- **玩家通知** — 加入/退出/踢出推送（含坐标、世界、角色信息）
- **新手指南书** — 首次进服自动发放，YAML 配置内容
- **运行时配置** — `/config` 命令热重载 24 项配置，无需重启
- **Bot 命令** — `$l` 在线查询、`$h` 帮助、`$e` 执行控制台、`$d` 黑名单管理

### 🔧 架构
- 六边形架构（Ports & Adapters），手工 DI
- 多模块拆分（orzmc-api + platform）
- 命令拦截器链（PlayerOnly / AdminOnly / Cooldown）
- 8 个通知模板 + 变量替换系统

### 📝 文档
- 完整 README、架构文档、配置说明
- 目录结构对比文档
- CLAUDE.md 项目指引

### ⚙️ CI/CD
- GitHub Actions 构建/测试/发布流水线
- Hangar 平台自动发布（Snapshot + Release）
- Tag 驱动版本发布（严格 SemVer）
- Dependabot 自动依赖升级
