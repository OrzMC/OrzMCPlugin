/**
 * Bukkit 事件适配器（每文件一个事件，逐特性薄转发）。
 *
 * <p>每个 {@code OrzXxxEvent} 只做：监听 Bukkit 事件 → 委托对应特性的 EventService。
 * 特性逻辑在 {@code features/} 对应包；事件目录仅为适配器，AI 改特性时需连带看
 * 本目录中该特性的事件适配器（如改 rank 特性 = features/rank + events/OrzRankEvent
 * + events/OrzRankDisplayEvent + assembly/FeatureModule 接线段）。</p>
 *
 * <p>注册入口：{@code assembly/FeatureModule.setupEventListeners} 经
 * {@code infra/binding/EventBinder} 一次性绑定。</p>
 */
package com.jokerhub.paper.plugin.orzmc.events;
