# i18n 遗留缺口补齐（P6）交接
> 状态：**P6 归档；P7 代码全部完成（#425–#428；E 撤销）**，收尾仅 docs 归档与 owner 真机抽验；候选/台账见 `docs/dev/i18n-gap-audit-p7.md`：P6 全卡与低影响记录项（G3b-2）合入 develop（#414–#423）；D 面经复核为非低影响大工程 → 按 owner 决定豁免（决策与边界见 `docs/dev/i18n-plan.md` §8 豁免表）。收尾残留：真机视觉对照（owner 抽验，非阻塞）｜ 最后更新：2026-09-10
> 上级规划：docs/dev/i18n-plan.md §8（一期完成）；本文为「二期遗留审查 → 补齐」工程卡。

## 任务与目标
一期（P0–P5 + D7 + 真机双语验收）完成后，全面审查发现三类代码直出中文未 i18n。
目标：A（游戏内命令提示/描述）→ B（builtin IM 未绑定引导）→ C（/config 运维树）全部语言包化；
每卡验收 = affected :test 绿 + `spotlessApply && ./gradlew test` + `:compileIntegrationTestJava` + PR(base develop) CI 绿 squash。
已知 bug（已修 PR #414）：BotModule 未注入 I18nService → $ 群帮助恒 zh fallback（4ec7d53）。

## 决议政策（定稿）
- **游戏命令 description**（Paper `Commands.register(node, desc, aliases)` 的 desc → 玩家 /help）：注册期静态 → **default_lang（R1）**，用 `CommandFeedbackService.commandDescription(key)`。
- **游戏命令运行时提示**（「仅玩家可用」等 sendMessage）：**sender locale**（player → locale，否则 default），用 `CommandFeedbackService.message(sender,key,vars)` / `playerRequiredMessage(sender)`。
- 复用现成：`CommandFeedbackService`（features/command，common.* 键 P1）+ 拦截器已走它。
- 键命名空间：`cmd.desc.<name>`、`cmd.error.*`、`cmd.<topic>_<verb>`；MessageKeys 常量同步；语言包尾部追加（串行链式合入）。

## P7 进行（docs/dev/i18n-gap-audit-p7.md）
- [x] **P7-A** /bot 面板 botstatus.* 词表（#425 @ 844dc66）
- [x] **P7-B** 复核修正：分页已走 paginatePages 键化 → 删 dead paginate（#426 @ d7b6ed7）
- [x] **P7-C** $e 回显（#427 @ 28579e7）：assemble {count} 模板参数（bot.e.truncated）+ execStateText（bot.e.exec_ok/not_found）；ExecResult.message 仅 logger 消费
- [x] **P7-D**（核心子集 #428 @ 34a5c64）：WorldMaintenanceService 注入 i18n；mode 词（maintenance.mode.backup/optimize）+ 时长单位词（duration.ms/hour/min/sec）；formatDuration 实例化；eventKey 判断改 backupMode；事件 {label}/{duration_human} 不再 zh 硬注入
- [x] **P7-D2**（#430 @ 8fa046d）：maintenance.cmd.* 8 键；mt() null 回退 zh—— P7 三审候选全部清零
- [x] **P7-E** 撤销（审计误报）：UnavailableBotMessageService/BotMessageServiceProvider 文本均为控制台启动告警（logger 豁免）
- P7 收尾：audit 文档勾验 + CHANGELOG + 真机视觉对照

## 已完成（按时间倒序）
- PR #427 @ 28579e7：P7-C $e 回显语言化
- PR #426 @ d7b6ed7：P7-B 删 Paginator zh 页脚 dead code
- PR #425 @ 844dc66：P7-A /bot 状态面板语言化（botstatus.* 15 键）
- PR #423 @ f66ca11：G3b-2 review 摘要渲染层语言化（summary_prefix/reason_sep 词表，4 UI 处，zh 逐字保持）
- PR #422 @ b42bfa9：G3b review type 展示名词表化（review.type.<id>；ReviewService 9 处 + ReviewCommandService/RankCommandService/ReviewCommandHandler；PlayerRankDisplayService 系玩家昵称豁免；summary 前缀 zh 动词残留 G3b-2 记录）
- PR #421 @ 4c3433a：G6c OrzConfigCommand 主树 31 键 cmd.config.*（C 面收口；parseValue 异常不再 UI 直显；registry cp.description() 豁免）
- PR #420 @ 68375da：G6b /config im status/setup 面板行（25 键 cmd.config_im_panel_*/setup_*/state.* 词表；describe 实例化；lastError 原样）
- PR #419 @ f65014b：G6a /config im 子树 + desc.config（ConfigCommandRegistrar desc、ImCommandRegistrar 3 usage、ImAdminService 绑定/投递/权限/校验 15 键 cmd.config_im_*/cmd.console_op_only；bindError 实例化；ImAdminService 注入 i18n）。教训：本地最后一次 patch 后漏跑 spotlessApply → CI spotlessCheck 红一次（补 style commit）
- PR #418 @ 747cf91：G3u UpdateCommandService /update 状态 12 键 cmd.update_*（describeCheck/download 模板化，不再透服务层 detail）
- PR #417 @ fdf49ff：G3 Rank/Prison/Update/Blacklist Registrar desc×4 + 提示键（+ handoff 顺带刷新）
- PR #416 @ 7819f3f：G2 FeatureCommandRegistrar（desc ×6 → cmd.desc.*、orzdebug/maintenance 运维提示 defaultMessage R1、registerSimple 仅玩家可用 → common.player_required；CommandFeedbackService.defaultMessage；FeatureModule 仅日志豁免）
- PR #415 @ b11fc20：G1 ReviewCommandRegistrar 样板（CommandFeedbackService 扩展 commandDescription/message/playerRequiredMessage；desc×2、仅玩家可用×7、review_failed 外壳）
- PR #414 @ b25d8d3：$ 群帮助注入真实 I18nService（装配断点修复；前置）
- 已知（**已修复 PR #436**，2026-09-10）：WS 重连类 CI 偶发 flake（`QqGatewayClientTest.op9_invalidSession…`×2、`DiscordGatewayClientTest.close4004…`、`OrzEasyBotTest` Mockito timeout）——根因 `TestWsServer` 在握手完成前就把连接暴露给 `connections()`；已加握手闸门 + 帧写入串行化，并新增回归护栏 `TestWsServerTest`（不再需要 `gh run rerun --failed` 兜底）
- FeatureModule review type 展示名（builder-promotion「晋升建造者」等 + 申请列表 data->"申请"+name）→ 新增卡 G4b 评估（ReviewService 渲染层）

## 卡规划（依赖序；每卡单 PR，语言包尾部追加需串行）
- [x] **G1** ReviewCommandRegistrar 样板（#415）
- [x] **G2** FeatureCommandRegistrar 描述/提示（#416）
- [x] **G3** Blacklist/Rank/Prison/Update Registrar（#417；真实规模：Blacklist 1 desc、Rank 3、Prison 3、Update 2——此前「45 处」为注释误报）
- [x] **G3u** /update 状态文案（#418）
- [x] （见上移 G3b）
- [ ] **G4** Rank/Portal/Prison Registrar（Portal desc「传送门…」/rank desc/prison desc/usage 提示）
- [ ] **G4u** UpdateCommandService /update 状态输出（4-5 条 styles.success 中文）+ UpdateCommandRegistrar desc
- [ ] **G5** B 面：builtin 未绑定会话绑定引导文本语言包化（Qq/Telegram/Feishu/Discord InboundProcessor 同构；公共渲染点或 4 平台键）
- [ ] **G6** C 面 /config 树（量最大）：ConfigCommandRegistrar 26 + ImCommandRegistrar 14 + ImAdminService 24 + OrzConfigCommand 46（/config 各子树说明）+ /orzdebug；可能拆 2-3 卡
- [ ] **收尾（待做）**：① 真机双语验证（/help desc、/apply 类型名、/rank、/update、/config 树 zh↔en）；② docs 更新 i18n-plan §8 台账 + features 语言小节 + CHANGELOG「i18n P6 补齐」；③ handoff 终态（本任务归档）
- 豁免面（不补，记录在案）：logger/异常/内部健康描述（ConfigPath/ConfigHealthCheck/ConfigUpgrader）、config 校验消息、数据内容（templates/guidebook/config 值）、平台适配器内部状态。

## 语言包现状
zh-CN/en-US 双语 parity（I18nCatalogConsistencyTest）；common.* P1、cmd.* P6 起始；改语言包 PR 串行。

## D 面（未豁免但非低影响——独立工程评估，2026-09-09 复核）
原标「豁免 D」现复核为**配置帮助/健康数据面**，如需 i18n 化属大工程（预计 2-3 PR）：
- D1：ConfigPath.all() 70 条 desc → 语言包键（zh 值=现说明原样迁移 + en 翻译；ConfigPath API description→按 lang resolve；消费者仅 OrzConfigCommand list/get + ConfigPathTest）
- D2：ConfigHealthCheck + 20+ 配置 record validate() 的 issues.add 中文消息 → 键化（health 无 sender → R1 default_lang 决议；issues 文案存 health 前需带语言或 health 面板层 resolve）
- D3：ConfigUpgrader/TemplatesBodyMigration log 消息维持豁免（logger 面）
- 建议：独立会话承接（大工程需交接续作），或决策「配置帮助/健康报告为 admin 数据文档保持 zh」并文档化入 i18n-plan §8。

## 下一棒开场指令
「读 docs/dev/i18n-gap-handoff.md，从卡 G2 开始：checkout 分支（基 origin/develop），处理 FeatureCommandRegistrar/FeatureModule 中文直出，
desc → feedback.commandDescription(cmd.desc.<name>)，提示 → feedback.message(sender,…)，加 MessageKeys + 双语 cmd.desc.*；
`spotlessApply && :test --tests …` 全绿 → PR develop → CI 绿 squash → 刷新本文 → 卡 G3」
