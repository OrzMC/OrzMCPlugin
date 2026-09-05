/**
 * 玩家上下线事件聚合 + 登录访问控制（uses maintenance/security）。
 * - 关键类型：PlayerEventService（上下线通知/聚合冲刷）、PlayerEventAggregator（窗口批量）、LoginAccessControlService（登录编排：黑名单→规则→GeoIP→维护）。
 * - 依赖方向：uses security（GeoIP/规则）+ maintenance（维护期登录拦截）；无被依赖。
 */
package com.jokerhub.paper.plugin.orzmc.features.player;
