/**
 * Bot 健康状态（只读查询）。不含连接/收发——那是 infra/bot。
 * - 关键类型：BotStatusService（enabled/http/ws 三态 + 详情消息）。
 * - 依赖：无特性依赖；消费 PlatformModule/组装层注入（/bot 命令在 assembly/FeatureCommandRegistrar）。
 */
package com.jokerhub.paper.plugin.orzmc.features.bot;
