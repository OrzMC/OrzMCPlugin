/**
 * 插件自更新（独立特性，2026 新增，纯后台无 Bukkit 依赖核心）。
 * - 关键类型：UpdateService（版本判定/下载校验）、UpdateCommandService（/update check|now）、HangarClient/BuildInfo 在 infra/version+net。
 * - 依赖：infra（net/hangar/server）；/update 命令树在 assembly/UpdateCommandRegistrar。
 */
package com.jokerhub.paper.plugin.orzmc.features.update;
