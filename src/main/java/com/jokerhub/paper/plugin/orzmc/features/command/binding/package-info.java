/**
 * 命令拦截器链基础件（跨特性共享，非业务特性）。
 *
 * <p>责任链语义：AdminOnly 在 Brigadier {@code .requires()} 阶段过滤（非管理员不可见）；guardedExec
 * 运行时按序跑 PlayerOnly / Cooldown / PrisonDeny。绑定在 {@code assembly/BrigadierSupport}。</p>
 *
 * <p>关键类型：{@code CommandInterceptor} 接口 + 4 拦截器 + {@code CooldownRegistry}。
 * AdminOnlyInterceptor 依赖 security.CommandPermissionService 判权（见 features/command
 * package-info：该依赖计划迁入本包以破 command↔security 循环）。</p>
 */
package com.jokerhub.paper.plugin.orzmc.features.command.binding;
