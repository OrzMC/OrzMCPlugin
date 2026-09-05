/**
 * 传送弓 + 实体传送策略（独立特性）。
 * - 关键类型：TeleportBowService（发弓/命中传送）、TeleportBowEventService/Texts/FlightTracker、
 * EntityTeleportPolicyService（白名单实体传送）、ForceLoadedChunkLease。
 * - 依赖：infra/server（调度）/config 的 entity_teleport 段。
 */
package com.jokerhub.paper.plugin.orzmc.features.teleport;
