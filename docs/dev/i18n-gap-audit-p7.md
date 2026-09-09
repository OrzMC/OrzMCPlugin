# i18n 三审残留审计（P7）
> 状态：**处理完毕归档（2026-09-09）**：A–D 已合入，E 撤销（审计误报）；台账见下表｜ 审计日期：2026-09-09
> 前置：P6（#414–#423）合入后 fresh 全量审计。产出仅供决策，**不处理**。

## 审计方法
1. `src/main/java` 全量非注释中文字面量扫描（671 行）→ 滤 logger/注释 → 按输出通道人工分类；
2. en-US 语言包**值级**中文残留检测（parity 测试只查 key 集，值不查——本维度此前未扫）。

## 确认干净（本轮复验）
- **en-US 语言包值零中文残留**（含 P6 全部新增键均带 en 翻译）✓
- 豁免复确认：logger/异常（平台适配器、LP、guard 告警、ReviewService severe 等全为内部日志）；config record validate issues（D2 面）；升级链比对常量（TemplatesBodyMigration OLD_* / LegacyDefaultFlips）；数据默认（worldAlias「主世界/下界/末地」、portal label、review type displayName fallback、FeatureModule「申请」闭包 fallback）
- BotStatusService 之外的 Registrar/Service 面已清（ReviewService/FeatureModule/WorldMaintenanceService/PlayerEventAggregator 等行内注释误报为主）

## 真缺口候选（用户可见，按确定性/影响排序）

| # | 面 | 证据 | 影响与说明 |
|:--|:--|:--|:--|
| A | **/bot 命令输出（BotStatusService）** | `buildMinimalMessage/buildHttpDetail/buildWsDetail`：`styles.success("enabled")`「enabled/wsOk」硬编码**英**状态词 + 「已连接/已断开/正常/异常/未检查/失败平台 (x/y):/错误: /点击查看…详情」中文混排 | 游戏内管理命令输出未语言化（缺 zh/en 词表与结构键）。**最明确缺口，建议 P7 首卡** |
| B | **分页页脚（Paginator）** | `Paginator` 硬编码 `"第" + (idx+1) + "/" + total + "页"`（30/40 行）→ 直接拼进 `$w`/`$v` 分页消息（WhitelistCommandHandler/ReviewCommandHandler 回调 headerText 已含 zh 页脚） | en 服 $w/$v 翻页仍显示「第 1/3 页」。`bot.list.page_meta` 键（Page {page}/{total}）已存在但 Paginator 未接 → 修复 = Paginator 页脚参数化/键化 |
| C | **$e 命令回显（ServerFacade ExecResult / CommandOutputAssembler）** | `ExecResult.message()`：「命令已执行: {cmd}」/「命令不存在或执行失败: {cmd}」；`CommandOutputAssembler`：「…（输出过长，已截断，共 N 行）」 | 待验证实际消费通道（群 $e / 控制台 / 守卫通知）；若群可见则键化 |
| D | **维护进度回调（WorldMaintenanceService）** | 时长单位「毫秒/小时/分/秒」拼接、「正在备份/优化地图…」「地图备份失败（备份文件未生成…）」「发现…损坏区块…」等 `callback.accept` 文本 | 待定 callback 消费通道（进度广播/控制台/结果行）；与 maintenance.* 事件键边界需厘清（部分在 event 正文，此处为事件前回调直文本） |
| E | **IM 停用引导（UnavailableBotMessageService / BotMessageServiceProvider）** | 「IM backend=builtin 无可用平台…已停用群功能——请将 im.yml backend 改回 easybot…」 | 群入站消息在 bot 不可用时的回复引导；平台会话可见 → R1 词表 |

## 数据/边界（维持豁免，记录在案）
- PortalLabelRenderer 默认标题「跨服传送/传送门」（portals label 数据默认；ArmorStandCleanup 按文本匹配）
- TemplateResolvers worldAlias 默认（templates.yml 数据）
- ReviewType displayName / FeatureModule「申请」闭包（词表命中时仅作 fallback）

## P7 处理台账（2026-09-09）
| # | 处理 | PR |
|:--|:--|:--|
| A /bot 面板 | botstatus.* 词表（R1） | #425 |
| B 分页 | 复核修正：分页已走 paginatePages 键化；删 dead paginate | #426 |
| C $e 回显 | assemble {count} 模板 + execStateText | #427 |
| D 维护 label/duration | mode/时长词表 + formatDuration 实例化（eventKey 改布尔） | #428 |
| D2 维护低频直行 | maintenance.cmd.* 8 键（starting/chunk/map_failed/dir/no_zip…；mt() null 回退 zh） | #430 |
| E IM 停用引导 | **撤销（审计误报）**：UnavailableBotMessageService/BotMessageServiceProvider 文本均为控制台启动告警（logger 面豁免），非群回复 |
| F 数据/边界 | 维持豁免（portal label/worldAlias/review type fallback） | — |

