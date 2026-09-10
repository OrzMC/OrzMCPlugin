# 多语言（i18n）方案 v2：中英双语起步、可扩展语言包

> **状态：全部完成——一期（P0–P5）+ P6 遗留补齐（#414–#423）+ P7 三审收尾（#425–#428/#430）均已合入 main（1.0.25 发版）；完成台账见 §8**｜**最后更新**：2026-09-10
> **范围**：一期英文 + 中文；架构上预留第三语言扩展（新增语言 = 加一个 yml 资源，业务代码零改动）。
> **配套**：本方案为计划文档；实施按 §5 拆 PR，当前进行 P0 基础设施。

## 0. 现状盘点（证据，精炼）

| 渠道 | 载体 | 现状 |
|:--|:--|:--|
| 游戏内玩家消息 | Adventure `Component` + `OrzTextStyles` 上色/hover/click | 中文硬编码在 Java（teleport/portal/tnt/whitelist/review/rank/…） |
| Bot 交互回复（`$cmd`） | 纯文本 `MessageEnvelope` | 中文硬编码在代码（`BotCommandFeedbackService` 及各 handler） |
| Bot 事件通知 | templates.yml 外置正文 + `{var}` + 每事件格式 | 已有「半成品 i18n」：仅中文一套（35 事件键 + `stage_cn` 映射表） |
| 控制台/日志 | Logger | 中文（管理员受众，默认不迁，见 §1） |
| 内容/数据文件 | guide_book / kick UP 名单 / world_alias / progress_units | 内容或非语言数据，默认不迁（见 §1） |

规模：主代码 328 个 Java 文件、276 个含中文，引号内中文字面量约 1119 处（剔除日志/异常后用户可见文案估 600–800 key）。

**已确认对方案有利的代码事实**
- `TemplateRenderer.render(template, vars)` 已是**独立的 `{var}` 渲染引擎** → 语料渲染直接复用，不新造轮子；
- 入站事件已解析 `platform`（qq/discord/telegram/feishu/wechat）+ `chat_id` → Bot 交互回复可在**分派入口**按来源平台决议语言；
- `ConfigService.reloadAll()` 与 config-version 升级链已存在 → 热重载与自定义文案迁移可挂现有路径；
- `TemplateKeys` 已集中管理事件 key 常量（少数调用点散落字面量，迁移时一并收口）；
- `NotifierSink`/`CapturingSink` 测试替身 → 文案变更不影响测试结构；zh 正文 = 现网原文 → 多数断言期望值不变。

## 1. 目标与非目标

**目标（一期，全量）**
1. 全部**用户可见文案**（游戏内 + Bot 交互回复 + Bot 事件通知）可中英切换；默认 zh-CN，老服务器零改动、中文输出与迁移前逐条一致。
2. 加语言 = 新增内置 `messages/messages_<lang>.yml` + 注册一行，业务代码零改动（代码只认 `Lang`，不认语言个数）。
3. 语言决议分层可配：游戏内（客户端 locale）→ 群（平台级）→ 服务器默认 → 内置 zh-CN 兜底，永不为空。
4. 一致性护栏：key 集 / 占位符集跨语言强校验（启动健康检查 + 单测），杜绝漏译、坏占位符。

**非目标（明确不做）**
- 控制台/日志本地化（管理员受众，默认中文，二期可议）。
- 用户自维护内容与数据：guide_book 正文、白名单踢出 UP 名单、`world_alias`（服务器专属地图名）、`progress_units`（速率/时间单位缩写，本就是英文 token）。
- 命令/权限节点/配置键本身的多语言；Bot 命令解析词（`$w` 等）的多语言别名。

## 2. 设计原则（架构审美约束）

| # | 原则 | 含义 |
|:--|:--|:--|
| P1 | **三权分离** | 文案（语言包）/ 样式（`OrzTextStyles`/配色配置）/ 格式（`templates.format`）各司其职，互不渗透 |
| P2 | **边界决议** | 语言只在「消息发往谁的屏幕」的边界解析一次并向下传递；业务层不层层现查 locale |
| P3 | **单一事实源** | zh-CN 主目录由开发随迁移从代码原文抄入 = 现网文案唯一基线；en-US 只做翻译，不允许自造结构 |
| P4 | **复用不新造** | 占位符引擎复用 `TemplateRenderer.render`；加载/热重载/健康检查复用现有 Config 体系 |
| P5 | **语言个数无关** | 业务代码只见 `Lang` 值 + key，加语言只动资源目录与一行注册 |

## 3. 核心架构

### 3.1 分层与组件（全部落在主模块 `infra/i18n/`，不新增依赖；orzmc-api 不动）

```
┌ 业务层（features/…、botcommands/…）
│   i18n.msg(lang, key, vars) ── 或接收已决议的 Lang 向下传
▼
I18nService          ← 组合根注入；只读不可变 Map + 原子热重载
  ├─ Lang            ← 值类型：归一化语言码（zh-CN / en-US）
  ├─ MessageTable    ← 单语言包：Map<String,String>，编译期无、运行时缺 key 走兜底
  ├─ I18nLoader      ← bundled(资源) ⊕ custom(数据目录 overlay，可选)
  └─ I18nConfig      ← config.yml `i18n:` 段（default_lang / platform_langs / aliases；snake_case 合规）
渲染引擎：TemplateRenderer.render(template, vars)（复用，不新建）
```

### 3.2 语言包文件模型：内置资源 ⊕ 可选管理端覆盖层

**两层分离，避免「数据目录文件随版本失同步」的经典坑**：

| 层 | 位置 | 维护者 | 说明 |
|:--|:--|:--|:--|
| bundled（内置，只读） | jar `messages/messages_zh-CN.yml`、`messages_en-US.yml` | 开发者/AI（随发布更新，天然同步） | 默认语料，完整性基线 |
| custom（可选覆盖） | 数据目录 `messages_custom_zh-CN.yml` | 服主 | **存在才读**；按 key 覆盖 bundled；值 `""` = 屏蔽该消息（渲染为空并跳过发送） |

> 不做 bundled 同名文件拷贝 + 版本合并（那会带来每版新增 key 与老文件的合并噪音）；覆盖诉求走 custom 层单文件合并（一行 putAll 语义），小而美且无版本问题。custom 缺 key 自然回落 bundled（→ zh）。

**文件形态**（嵌套按域分组便于人读，代码按完整 dot key 取值）：
```yaml
# messages_zh-CN.yml（en-US 同构）
common:
  admin_required: "仅管理员可执行该命令"
  cooldown: "命令冷却中，请 {secs} 秒后再试"
teleport:
  bow:
    name: "传送弓"
    give: "你获得了 {name}"
whitelist:
  kick:
    join_hint: "不在服务器白名单中，请先加入QQ群：{group}，联系管理员添加白名单"
event:
  review_approved: "✅ [申请通过] {player}\n{summary}\n审核人：{reviewer}"
  player_join: "🎮 当前玩家({online_count}/{max_count})\n---------------------------------\n🥰 上线：\n{name}"
```

### 3.3 消息单位与占位符规则

- **翻译单位 = 一条完整用户消息 / 一个完整句子**；动态部分一律 `{var}` 传参（禁止 `+` 拼接或 `String.format` 拼装文案——否则翻译无法调序）。
- 变量名沿用模板现有风格（`{player}`、`{message}`…），同 key 跨语言占位符集合必须一致（护栏 §3.9）。
- 个别「多色分段长句」（如白名单踢出 = QQ 提示 + Discord 入口 + UP 名单）按**语义角色拆成多 key、顺序与样式由代码决定**（现状本就是代码拼装）；A1 接受该语序限制，这类消息全仓 <10 处。

### 3.4 key 规范

- 语法：`域.路径`，小写字母数字 + 点/连字符。
- 顶层保留：`common.*`（跨渠道复用：权限/冷却/用法/无效参数…）、`event.*`（Bot 事件通知，**平移** legacy templates.yml 事件键，见 §3.8）、其余按 features 域（`teleport.*`/`whitelist.*`/`review.*`/`maintenance.*`…）。
- **渠道不进 key**：同一「无权限」游戏内与群里共用 `common.admin_required`。
- 代码引用一律走常量收口（新建 `MessageKeys` 常量类，镜像 `TemplateKeys` 做法），禁散落字面量。

### 3.5 Lang 与决议

```java
/** 值类型：归一化语言码。构造即归一（zh_CN→zh-CN、EN-us→en-US），equals/hashCode 按码。 */
public record Lang(String code) {
    public static final Lang ZH_CN = Lang.of("zh-CN");
    static Lang of(String raw);                 // 归一化，未安装码也允许构造（决议时回落）
    static Lang fromLocale(java.util.Locale l); // 仅主码且唯一命中时收敛（en→en-US）；多义/未知返回 null
}
```

| 场景 | 决议链（先命中先得） | 实现落点 |
|:--|:--|:--|
| 游戏内命令/事件反馈 | `Player.locale()` → `i18n.default_lang` → `zh-CN` | `langFor(Player)`；命令在入口决议一次 |
| Bot 交互回复 | 来源平台 → `i18n.platform_langs[平台]` → `default_lang` → `zh-CN` | 入站分派处决议，`Lang` 随上下文透传（§5 P3） |
| Bot 事件通知广播 | `default_lang`（R1，见 §3.8） | `langFor()` 无参默认 |
| 兜底 | 未安装码 → `default_lang` → `zh-CN`；zh 也缺 key → 返回 key 本体 + 按 key 去重 warn 一次 | 运行时永不空消息 |

新增配置段（config.yml，随 schema 版本升级）：
```yaml
i18n:
  default_lang: zh-CN        # 服务器默认语言（事件通知 / 非玩家受众兜底）
  platform_langs:            # 群语言覆盖（bot 交互回复）
    qq: zh-CN
    discord: en-US
```

### 3.6 API 与边界决议原则

```java
public final class I18nService {
    // 决议
    Lang langFor();                      // 服务器默认
    Lang langFor(Player player);         // 客户端 locale → default → zh-CN
    Lang langFor(String platformId);     // platform_langs → default → zh-CN
    // 读取（只收 Lang，不收裸串，防码漂移）
    String msg(Lang lang, String key);
    String msg(Lang lang, String key, Map<String, String> vars);
    String msg(Lang lang, String key, Map<String, String> vars, String def); // 显式兜底
    boolean has(Lang lang, String key);
    // 生命周期 / 健康
    void reloadCustom();                 // 重读数据目录 overlay（挂现有 reload 链）
    List<String> health();               // key/占位符差异 → 启动 warning（PlatformModule.setup，与 ConfigService 同风格）
}
```

- **边界决议原则（P2）**：命令处理器入口 `Lang lang = i18n.langFor(sender)`；消息组装函数签名带 `Lang`；不含 Player 的服务端广播用 `langFor()`。不缓存玩家语言（客户端 locale 固定，逐条解析开销可忽略）。
- **样式纯化（新增约束）**：`OrzTextStyles` / `OrzConstants` 内嵌文案（`unknownLabel()`「未知玩家」、`tpbowPrefix()`「[传送弓]」、`[TNT警报]` 前缀…）全部迁出为 catalog key，样式类只留颜色/样式参数——样式与文案彻底解耦（P1）。

### 3.7 Bot 事件通知的终态（templates.yml 瘦身）

| 内容 | 去向 |
|:--|:--|
| 事件正文（review_approved/player_join/geoip_block/maintenance_motd_*/…） | 迁入语言包 `event.<name>`（`<name>` = 现事件键，如 `event.review_approved`） |
| `stage_cn` 手工映射表（Region/Chunk/File/Done） | 迁入语言包 `maintenance.stage.<名称>`（渲染点已有 `stage_i18n` 占位符铺垫） |
| `templates.format` 格式表 | **留在 templates.yml**（非文案：CODE_BLOCK/PLAIN 路由语义） |
| `styles.colors` 配色 | 留在 templates.yml（非文案） |
| `world_alias` / `progress_units` / `coord` | 留在 templates.yml（服务器数据/单位 token，非 UI 文案） |
| `{message}` 直通壳（command_output 等） | 原地不迁（纯格式载体） |

终态 = **全部可翻译文案单一来源（语言包）**；templates.yml 只含格式/配色/数据。事件键常量 `TemplateKeys` 同步改为 `event.*` 形态并收口散落字面量；`templates.format` 表键同步改 `event.<name>`。
**多语言形态采用 R1**：事件通知在调用侧按 `default_lang` 渲染一次广播（跨平台群共享服务器语言）；交互回复不受影响（§3.5 已按平台分语言）。R2（MessageEnvelope 携带 key+vars、发送边界延迟渲染）列为二期选项，架构不阻塞。

### 3.8 一致性护栏

| 机制 | 内容 | 时机 |
|:--|:--|:--|
| key 集校验 | 每 bundled 包与 zh 主目录 key 集**完全一致**（缺/多均报）；值不得为空 | 启动 warning（PlatformModule.setup，随 ConfigService 风格）+ 单测 |
| 占位符校验 | 同 key 跨语言 `{...}` 集合一致 | 同上 |
| 运行时兜底 | 缺 key → zh → key 本体 + 去重 warn 一次；custom 空串 = 屏蔽 | 运行时 |
| 单测 | `I18nCatalogConsistencyTest`（资源两包 key/占位符/空值）+ Lang 归一化/决议链用例 | `./gradlew test` |
| 残留审计 | `rg` 引号内中文逐个清零（日志/异常白名单除外） | 每域 PR + P5 总审 |

### 3.9 线程与热重载

- 语料表启动时构建为**不可变 Map**；`reloadCustom()` 合并新 overlay 后**原子替换引用**（volatile 引用 + 不可变值），读线程无锁无感——适配 Folia 多 region 线程，无同步等待、不新增调度。
- 语言包**无需 config-version 升级链**（bundled 随发布整体替换；custom 是纯覆盖层，永远向后兼容）。

## 4. 决策记录

| # | 项 | 决策 | 状态 |
|:--|:--|:--|:--|
| D1 | 一期范围 | 游戏内 + Bot 交互回复 + Bot 事件通知全做；控制台日志、内容数据文件不做 | ✅ 已拍板 |
| D2 | 默认语言 | zh-CN（老服零改动、兼容现网） | ✅ 已拍板 |
| D3 | 渲染模型 | A1 文案抽取（不引入 MiniMessage；复用 TemplateRenderer） | ✅ 已拍板 |
| D4 | 语言包形态 | **内置资源（bundled）+ 可选数据目录覆盖层（custom）**（修订原「数据目录同名文件」方案，免版本同步坑） | ✅ 已拍板 |
| D5 | 兜底 | 未装码/缺 key → default_lang → zh-CN → key 本体 | ✅ 已拍板 |
| D6 | 游戏内语言 | 跟随客户端 locale（Paper `Player.locale()`），不做 per-player 手动切换 | ✅ 已拍板 |
| D7 | 英文来源 | AI 初译 + owner 校对，随各域 PR 分批交付 | ✅ 已拍板 |
| D8 | 事件通知多语言 | R1（default_lang 渲染一次）；R2 延迟渲染二期 | ✅ 已拍板 |
| D9 | 样式纯化 | `OrzTextStyles`/`OrzConstants` 内嵌文案迁出，样式类不含任何文案 | ✅ 已拍板 |
| D10 | 事件正文 key | 平移为 `event.<name>`，`TemplateKeys` 同步改名并收口散落字面量 | ✅ 已拍板 |
| D11 | custom 空值语义 | 覆盖值为 `""` = 屏蔽该消息 | ✅ 已拍板 |

## 5. 分阶段实施 PR 计划（遵守「单 PR < ~500 行」成本红线）

| 阶段 | 内容 | 规模 | 验收 |
|:--|:--|:--|:--|
| P0 基础设施 | `infra/i18n/`（Lang/MessageTable/I18nService/Loader/Options）+ zh/en 空语料骨架 + `i18n:` 配置段 + custom 覆盖层 + `MessageKeys` 常量类 + 健康检查 & 一致性单测 + 组合根装配 | 1 PR ~500–650 行 | 决议链/兜底/覆盖/护栏单测全绿；未迁移文案时零缺 key |
| P1 通用文案（拦截器链） | `common.*`（cooldown/admin/player/prison）+ `MessageKeys` 常量；拦截器/权限服务经 i18n 按发送者语言渲染（I18nService 注入 BrigadierSupport 工厂与 7 个注册器）；`OrzTextStyles`/`OrzConstants` 文案迁出归入各自功能域 PR，`OrzConfigCommand` 反馈归入 P2 分域 | 1 PR | 拦截器提示双语可见（zh 原文零回归）；`CommandFeedbackService` 通用提示零中文 |
| P2 分域迁移（每域 1–3 PR，域内测试同步改） | teleport → whitelist → portal/tnt → player 事件聚合 → security/chat → review/rank/prison → maintenance → guide/menu → update/feature 命令 | 8–12 PR | 每域：中文输出逐条与迁移前 diff 一致；en 包同步补齐 |
| P3 botcommands | 11 个 `$cmd` 帮助/反馈/列表/分页；入站分派处按平台决议 Lang 并透传 | 2–4 PR | 交互回复按 `platform_langs` 双语；`$cmd ?` 全量双语 |
| P4 事件通知正文平移 | 正文 → `event.*`；stage 名 → `maintenance.stage.*`；`TemplateKeys` 改名 `event.*`；templates.yml 瘦身；**legacy 自定义正文自动迁移到 custom 层**（复用 config-version 升级链，防服主定制丢失） | 1–2 PR | 通知格式/路由不变；双语样例实机验证 |
| P5 收尾 | 全量 key 审计（无孤儿 key/无残留中文）、README/docs/features/CHANGELOG 同步 | 1 PR | `rg` 残留清零（白名单除外）；文档同步 |

估算 **15–22 个小 PR** 全走 develop；zh 随迁移抄原文（零回归），en 由 AI 初译 + owner 校对随各 PR 交付（可滞后合入，不阻塞 zh）。

## 6. 风险与边界（迁移期红线）

1. **测试回归**：zh 包抄原文 → 断言现网中文的测试期望值不变；风险在「代码内联串改 i18n 调用后测试硬编码漂移」，靠占位符校验 + 每域 PR 自测兜住。
2. **漏网中文**：每域 PR 用 `rg '"…中文…"'` 清零；日志/异常消息白名单除外（§1）。
3. **频控红线不回归**：通知仍走 `ThrottledNotifier`/聚合节流，i18n 只换文案来源，不碰节流路径。
4. **Folia 线程**：只读不可变表 + 原子换引用，无锁无调度，天然适配。
5. **服主定制保护**：P4 的 legacy 通知正文自动迁入 custom 层（升级链内完成），不接受「升级后定制文案静默丢失」。
6. **翻译质量**：en 面向玩家社区，D7 owner 校对门槛；结构性错误由护栏拦截，语义由人审。
7. **成本控制**：P2 分域小 PR 单审；迁移为机械替换但**禁止一次全量大 PR**；每域独立提交可回滚。
8. **冲突面**：不触碰 im.yml 绑定表与消息路由；P4 裁剪 Templates 记录字段时与在途分支同步注意。

## 7. 验收标准（一期完成定义）

- [x] 内置 `messages/messages_zh-CN.yml` / `messages_en-US.yml`；第三语言仅新增 yml + 注册一行（I18nLoader.CODES）。
- [x] 游戏内：命令反馈/事件提示/踢出消息全量按语言决议（D6 langFor(Player)）；代码级无残留中文（控制台/内容数据白名单除外）。**真机双语对照见交接残余 1（owner）。**
- [x] Bot：交互回复按 `platform_langs` 决议（P3）；事件通知默认语言 R1；`$cmd ?` 帮助全量双语。
- [x] 事件通知正文迁入 `event.*`，templates.yml 仅剩直通壳/格式/配色/数据键；`stage_cn`/`maintenance_motd_*` 段与 Templates 记录消失（P4c/P4d-2）；存量盘正文由升级链迁移（P4d-1，config-version 14）。
- [x] 一致性护栏全绿：I18nHealth + `I18nCatalogConsistencyTest`（双包 key/占位符一致）过 `./gradlew test`。
- [x] 服主 custom 覆盖：`messages_custom_<lang>.yml` + reload 即时生效；空串可屏蔽；缺 key 可定位（key 本体返回 + 日志）。
- [x] 中文输出与迁移前逐条一致（P4c/§8 zh 逐字断言）；README / docs/features.md / CHANGELOG / 交接同步（docs PR #404）。

## 8. 实施完成台账（2026-09-08）

| 阶段 | 内容 | PR（develop，squash） |
|:--|:--|:--|
| P0 | i18n 基础设施（Lang/MessageTable/I18nService/Loader/custom 覆盖/MessageKeys/健康检查） | P0 系列 |
| P1 | 通用文案（common.* 拦截器链按发送者语言） | P1 系列 |
| P2 | 各功能域分域迁移（teleport→whitelist→portal/tnt→player→security→review/rank/prison→maintenance→guide/menu） | #3xx 系列 |
| P3 | botcommands 11 命令双语（bot.* / bot.list.* / bot.v.* 按 platform_langs） | #3xx 系列 |
| P4a | 命令回复正文 → `{message}` 直通壳 | #386 |
| P4b | 事件正文 24 键 → `event.*`（EVENT_LANG_BACKED + renderEvent 磁盘优先→语言包） | #388/#389 |
| P4c-1/2 | 维护事件 6 键 + 场景/MOTD/阶段名（maintenance.* / MaintenanceTexts） | #391/#393 |
| P4d-1 | 存量盘正文升级链（config-version 13→14，TemplatesBodyMigration 幂等） | #395 |
| P4d-2 | Templates 记录 vestige 收编删除 | #397 |
| P5 | 孤儿键清理/var 值词汇（serverlife/audit/playermode）/Paginator 空态//blacklist 域/文档同步 | #399–#404 |

P6 遗留补齐（2026-09-08/09，#414–#423）：$ 群帮助装配断点（#414）；游戏命令面 desc/提示（#415–#418，cmd.desc.*/cmd.update_*）；运维 /config 树（#419–#421，cmd.config_*/cmd.config_im_*，R1）；review type 展示名与摘要（#422–#423，review.type.<id>/summary_prefix）。真机双语冒烟已执行（一期收尾阶段，含 13→14 升级对照）。

P7 三审收尾（2026-09-09，#425–#428/#430）：`/bot` 状态面板（botstatus.*）、Paginator zh 页脚 dead code 删除、`$e` 回显（bot.e.truncated/exec_ok/exec_not_found）、维护事件 mode/duration 变量（maintenance.mode.*/duration.*）与维护低频直行回调文本（maintenance.cmd.*）；en 值零中文残留复核通过，候选台账清零（#431），随 1.0.25 正式版发布。

**i18n 边界豁免（2026-09-09 复核定稿）**：
| 面 | 决策 |
|:--|:--|
| 配置帮助说明（`ConfigPath.all()` 70 条 description） | 数据文档豁免：admin 运维帮助数据，不随 default_lang；如需英文化另立 D1 工程（单一 PR 可控，见交接） |
| 健康报告（`ConfigHealthCheck` + 20+ config record `validate().issues.add` 中文） | 半内部 admin 报告豁免：需 health 语言决议设计（D2 评估中，成本高收益低） |
| logger/异常消息、数据内容（templates/guidebook/config 值）、平台适配器内部状态 | 豁免（非 UI 文案） |

## 附录 A：迁移示例（before → after）

```java
// before —— 样式与文案耦合、语言写死
player.sendMessage(styles.success("你获得了" + name));
Component.text("不在服务器白名单中，请先加入QQ群:")

// after —— 边界决议一次，样式只上色，文案进包
Lang lang = i18n.langFor(sender);
player.sendMessage(styles.success(i18n.msg(lang, "teleport.bow.give", Map.of("name", name))));
Component.text(i18n.msg(lang, "whitelist.kick.join_hint", Map.of("group", qqGroupId)))
```

## 附录 B：Bot 事件通知 legacy 定制迁移（服主视角）

升级到含 P4 的版本时，若服主曾改过 templates.yml 事件正文：升级链自动把**与内置默认不同的正文**写入 `messages_custom_zh-CN.yml` 的对应 `event.<name>` key（原文保留，行为不变）；此后改动走 custom 层，不再回写 templates.yml。
