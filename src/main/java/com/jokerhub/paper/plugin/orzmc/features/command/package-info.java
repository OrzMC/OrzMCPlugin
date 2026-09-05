/**
 * 命令拦截器与反馈文案（跨特性共享的基础件，非业务特性）。
 * - 关键类型：binding 子包 = CommandInterceptor + 4 拦截器（PlayerOnly/AdminOnly/Cooldown/PrisonDeny）+ CooldownRegistry；
 * 根 = CommandFeedbackService（统一提示文案常量）。
 * - 依赖方向：AdminOnlyInterceptor 用 security.CommandPermissionService 判权（唯一 command→security 引用，
 * 见 P1 破循环计划：CommandPermissionService 将迁入 binding）；portal/security 用本包文案。
 * - 执行模型：requires() 判 AdminOnly（不可见）；guardedExec 运行时跑其余链。
 */
package com.jokerhub.paper.plugin.orzmc.features.command;
