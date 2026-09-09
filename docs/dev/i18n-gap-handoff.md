# i18n 遗留缺口补齐（P6）交接
> 状态：现行 ｜ 最后更新：2026-09-08
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

## 已完成（按时间倒序）
- PR #421 @ 4c3433a：G6c OrzConfigCommand 主树 31 键 cmd.config.*（C 面收口；parseValue 异常不再 UI 直显；registry cp.description() 豁免）
- PR #420 @ 68375da：G6b /config im status/setup 面板行（25 键 cmd.config_im_panel_*/setup_*/state.* 词表；describe 实例化；lastError 原样）
- PR #419 @ f65014b：G6a /config im 子树 + desc.config（ConfigCommandRegistrar desc、ImCommandRegistrar 3 usage、ImAdminService 绑定/投递/权限/校验 15 键 cmd.config_im_*/cmd.console_op_only；bindError 实例化；ImAdminService 注入 i18n）。教训：本地最后一次 patch 后漏跑 spotlessApply → CI spotlessCheck 红一次（补 style commit）
- PR #418 @ 747cf91：G3u UpdateCommandService /update 状态 12 键 cmd.update_*（describeCheck/download 模板化，不再透服务层 detail）
- PR #417 @ fdf49ff：G3 Rank/Prison/Update/Blacklist Registrar desc×4 + 提示键（+ handoff 顺带刷新）
- PR #416 @ 7819f3f：G2 FeatureCommandRegistrar（desc ×6 → cmd.desc.*、orzdebug/maintenance 运维提示 defaultMessage R1、registerSimple 仅玩家可用 → common.player_required；CommandFeedbackService.defaultMessage；FeatureModule 仅日志豁免）
- PR #415 @ b11fc20：G1 ReviewCommandRegistrar 样板（CommandFeedbackService 扩展 commandDescription/message/playerRequiredMessage；desc×2、仅玩家可用×7、review_failed 外壳）
- PR #414 @ b25d8d3：$ 群帮助注入真实 I18nService（装配断点修复；前置）
- 已知：DiscordGatewayClientTest reconnectRequest 偶发 flake（重跑 PASSED，CI 侧 gh run rerun --failed）
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
- [ ] 真机双语验证（/help desc zh↔en、/apply /rank /update 提示）→ docs 更新 i18n-plan §8 台账 + features 小节 + CHANGELOG
- 豁免面（不补，记录在案）：logger/异常/内部健康描述（ConfigPath/ConfigHealthCheck/ConfigUpgrader）、config 校验消息、数据内容（templates/guidebook/config 值）、平台适配器内部状态。

## 语言包现状
zh-CN/en-US 双语 parity（I18nCatalogConsistencyTest）；common.* P1、cmd.* P6 起始；改语言包 PR 串行。

## 下一棒开场指令
「读 docs/dev/i18n-gap-handoff.md，从卡 G2 开始：checkout 分支（基 origin/develop），处理 FeatureCommandRegistrar/FeatureModule 中文直出，
desc → feedback.commandDescription(cmd.desc.<name>)，提示 → feedback.message(sender,…)，加 MessageKeys + 双语 cmd.desc.*；
`spotlessApply && :test --tests …` 全绿 → PR develop → CI 绿 squash → 刷新本文 → 卡 G3」
