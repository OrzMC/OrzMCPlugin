/**
 * 晋升申请审核（builder/admin-promotion）——「LP 权限治理簇」成员。
 * - 关键类型：ReviewService（441 行：类型注册/状态/审核编排）、ReviewStore（接口，rank.PermissionStore 是唯一实现）、
 * ReviewNotifier（群通知）、ReviewType/ReviewRequest/ReviewHandler/ReviewCommandService、PlayerLookup。
 * - 簇内关系：review uses rank.GamemodeCorrectionService（审核通过后矫正游戏模式）；被 rank/prison/botcommands 依赖。
 * 注意：ReviewStore 的实现不在本包而在 features/rank/PermissionStore——AI 改审核持久化需同时看两包。
 * - 渲染：/apply /review 命令树在 assembly/ReviewCommandRegistrar。
 */
package com.jokerhub.paper.plugin.orzmc.features.review;
