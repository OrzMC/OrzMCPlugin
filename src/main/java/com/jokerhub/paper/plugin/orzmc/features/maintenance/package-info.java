/**
 * 维护模式 + 世界备份/优化（独立特性；被 player/botcommands/server 消费状态）。
 * - 关键类型：MaintenanceModeService（MOTD/登录拦截状态机）、WorldMaintenanceService（备份/优化执行 + run 内计数器复位）、
 * MaintenanceCommandService（/maintenance on|off|status）、ScheduledBackupService。
 * - 依赖：infra（scheduler/server facade）。设计要点：错误计数器每次 runExclusive 复位（见 roadmap N1 已闭环）。
 */
package com.jokerhub.paper.plugin.orzmc.features.maintenance;
