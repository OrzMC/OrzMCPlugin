package com.jokerhub.paper.plugin.orzmc.assembly;

import io.papermc.paper.command.brigadier.Commands;

/**
 * 特性级 Brigadier 命令注册组：一个文件只承载一个特性的命令树（语法/拦截器/渲染），
 * 由 {@link FeatureCommandRegistrar} 在同一个 {@code LifecycleEvents.COMMANDS} 事件里统一编排。
 *
 * <p>拆分动机：原 FeatureCommandRegistrar 把 11 个特性的命令揉在一文件（970 行），任何特性
 * 的命令改动都要把无关特性的命令树读进上下文。按特性独立成文件后，改 {@code /rank} 只需读
 * rank 的注册器（及其 CommandService），不触碰 portal/blacklist/config 等无关命令。</p>
 */
interface CommandGroup {

    void register(Commands commands);
}
