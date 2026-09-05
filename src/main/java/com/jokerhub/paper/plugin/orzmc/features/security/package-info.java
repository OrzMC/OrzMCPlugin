/**
 * 访问安全规则与命令守卫（被 player/portal/botcommands 消费；uses command 文案）。
 * - 关键类型：AccessRuleService（IP/玩家名规则）、GeoIpAccessService、LoginRateLimitService(+Event)、
 * CommandGuardEventService/CommandAuditService、ExploitHardeningService(+Event)、CommandPermissionService（判权，供 AdminOnlyInterceptor——将迁往 features/command/binding）。
 * - 依赖方向：本包是<b>被消费的安全底座</b>；与 command 存在双向 import（见 features/command package-info P1 说明）。
 */
package com.jokerhub.paper.plugin.orzmc.features.security;
