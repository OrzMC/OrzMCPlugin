/**
 * 跨服传送门配置与触发（uses command/security）。
 * - 关键类型：PortalCommandService（/portal host [port]/remove）、PortalEventService（传送触发判定）。
 * - 依赖：security（AccessRule/GeoIP 校验）；infra/portal 持久化 portals.yml。
 */
package com.jokerhub.paper.plugin.orzmc.features.portal;
