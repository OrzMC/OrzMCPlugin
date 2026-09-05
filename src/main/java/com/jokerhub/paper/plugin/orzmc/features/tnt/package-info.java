/**
 * TNT 爆炸检测与聚合告警（独立特性）。
 * - 关键类型：TntPolicy（配置/判定）、TntEventService（329 行：区域合并/窗口尾部单条冲刷/配置回退）。
 * - 依赖：infra（config tnt 段 / notifier）。
 */
package com.jokerhub.paper.plugin.orzmc.features.tnt;
