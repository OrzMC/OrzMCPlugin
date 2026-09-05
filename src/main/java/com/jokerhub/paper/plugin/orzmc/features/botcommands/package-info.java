/**
 * Bot 群聊 $ 命令层（BotCommandService 分派 + 各 $cmd 独立处理器）。
 * - 关键类型：BotCommandService（735→195 行纯分派）、BotCommandDependencies（跨模块注入聚合）、
 * 每命令一个 Handler 文件（Permission/Whitelist/Review/Maintenance/Blacklist/Console…）。
 * - 依赖方向：本包是<b>跨特性消费方</b>（uses maintenance/rank/review/security/whitelist）——其余包不反向依赖它（仅 server 用其反馈）。
 * - 设计要点：组合根在 bot 连接前一次性 injectDependencies（避免半初始化窗口）；feature 服务经 BotCommandDependencies 注入而非 setter。
 */
package com.jokerhub.paper.plugin.orzmc.features.botcommands;
