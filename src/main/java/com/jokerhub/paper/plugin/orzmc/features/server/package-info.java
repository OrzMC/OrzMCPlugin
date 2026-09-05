/**
 * 服务端生命周期 / 异常告警 / 反馈（uses maintenance/botcommands）。
 * - 关键类型：ServerLifecycleService（停服通知/刷新在线列表等生命周期动作）、ServerFeedbackService（$ 反馈）、
 * ExceptionAlertService（异常群告警）、StartupSecurityAuditService（启动期安全基线审计）。
 * - 依赖：uses maintenance（维护状态查询）+ botcommands（反馈通道）；无被依赖。
 */
package com.jokerhub.paper.plugin.orzmc.features.server;
