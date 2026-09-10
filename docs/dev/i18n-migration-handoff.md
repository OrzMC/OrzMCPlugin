# i18n 多语言迁移 交接
> 状态：现行（一期 P0–P5 完成；P6/P7 遗留补齐见 [i18n-gap-handoff.md](i18n-gap-handoff.md)，均已合入 main，随 **1.0.25 正式版**发布） ｜ 最后更新：2026-09-10

## 任务与目标
仓库级 i18n 一期（中英双语 + 可扩展语言包）：游戏内 + Bot 交互 + 事件通知全部用户可见文案迁入语言包（zh 原文零回归基线）。方案：docs/dev/i18n-plan.md（§4 决策全拍板，§8 完成台账）。验收：方案 §7 全部勾验；本文件列出残余项。

## 已完成（倒序；develop 历史 @ 5cacf33）
- PR #405 fix（冒烟缺陷）：validator 跳过 lang-backed body 变量集校验（存量盘残留 {message} 壳误告警）；
  语言包 access_rule.added/exists 重复键去重（YamlConstructor duplicate 告警）；测试语义化 + handoff/CHANGELOG 冒烟结论
- 真机双语验收（2026-09-08 本机 runServer，纯验证无代码改动）：QQ 平台开启（凭据填入 run/ im.yml，gitignored）→
  qq 网关 READY + 群事件下行（server_load/security_audit/whitelist_block/player_join/player_quit zh↔en 逐字对照，
  QQ 群 F73A 经 /config im bind 切换旧注销群 A57A→F73A 后投递零错误）；ViaVersion/ViaBackwards 5.11.0 桥接
  mineflayer(774)→Paper 26.2(776) 玩家侧（白名单踢出/op bot/playermode 词汇）；kick 数据标题保留（D9 预期）
- PR #404 docs：P5 收尾文档同步（plan §7 勾验 + §8 台账、交接终态、features 语言本地化、README/CHANGELOG）
- PR #403 P5-5：玩家行游戏模式词汇语言化（playermode.* 4 键，I18nServiceHolder R1）；OrzConstants 告警前缀孤儿常量删除
- PR #402 P5-4：/blacklist 游戏命令域去内联 zh（access_rule.* 新增 18 键；安全运维命令统一 R1）
- PR #401 P5-3：Paginator 空列表正文 emptyBody 参数化（bot.list.empty_whitelist；Review 空态 guard 早已用 bot.v.list_empty）
- PR #400 P5-2：server_load/server_stop 迁语言包 event.*（磁盘 {message} 壳 → 回落语言包）；serverlife.*/audit.* var 值词汇（MOTD 外壳/安全自检描述值）
- PR #399 P5-1：孤儿安全告警域键清理 5 键（login/guard/exploit/ratelimit alert_*，零消费）
- PR #397 P4d-2：Templates 记录 vestige 收编删除（renderEvent 直读磁盘→语言包）
- PR #395 P4d-1：templates.yml 存量盘旧正文升级链（config-version 13→14）
- PR #394/#396/#398 docs 阶段刷新
- PR #391/#393 P4c-1/2：维护事件正文 + 场景/MOTD/阶段名迁语言包（MaintenanceTexts）
- PR #386–#390 P4a/b：命令回复直通壳 + 事件正文 24 键（event.*）
- PR #3xx P3/P2/P1/P0：botcommands 双语 / 各功能域迁移 / 通用文案 / i18n 基础设施

## 进行中卡
- 无（代码侧 P0–P5 全完成并合入 develop）

## 未完成清单（均为 owner 动作）
1. **en 校对（owner，D7）**：en-US 为 AI 初译，已完成两轮统一校对（#408/#409）与 zh 措辞修正（#410）；继续逐域精校走串行语言包 PR（非发布门槛）
2. **正式版发布**：i18n 一期 + P6/P7 已随里程碑合入 main（#411/#432/#434）；1.0.25 正式版由 owner 打 tag 发布（CHANGELOG 已归拢 [1.0.25] 节）
3. 已知 out-of-scope 残留（文档已记，D1/数据内容白名单）：
   - 控制台/日志 zh（D1）
   - 内容数据文件（config.yml 各 message 默认值、guide_book、portal 名牌「传送门/跨服传送」、world_alias 数据键默认值）——管理员可配内容非 i18n
   - Paginator 老入口 paginate()（生产零调用）页标签 zh；保持现状
   - 事件 var 值词汇（playermode/serverlife/audit）已语言化；bot.*/bot.v 等域按 P3 各平台 lang 决议

## 风险/注意
- config-version = 14（P4d-1）；升级链幂等。语言包与 MessageKeys 尾追加文件：后续改动串行链式合入
- 触碰语言包/配置升级链的 PR：affected :test → spotlessApply+全量 test → :compileIntegrationTestJava → CI 绿 squash
- 禁止直接 commit/push develop（#395 教训：先建 feature 分支再落码）

## 下一棒开场指令
读本文件后：i18n 代码侧无待办（P0–P5 + 冒烟修复已全部合入 develop @ 5cacf33）。
owner 后续动作见「未完成清单」：QQ/玩家视觉冒烟、en 校对（串行语言包 PR）、里程碑发布决策；任一完成后刷新本文件。
