/**
 * 聊天内容过滤（独立特性，无入/出向特性依赖）。
 * - 关键类型：ChatSpamFilterService（规则判定）、ChatSpamFilterEventService（聊天事件编排）。
 * - 依赖：infra（config 的 chat 段 / styles）。
 */
package com.jokerhub.paper.plugin.orzmc.features.chat;
