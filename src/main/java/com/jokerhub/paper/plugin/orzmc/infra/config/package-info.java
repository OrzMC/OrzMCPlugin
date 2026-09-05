/**
 * 配置系统（类型化 records + 校验 + 版本迁移 + schema 自升级）。
 *
 * <p>关键类型：{@code ConfigService}（存取入口）、{@code DefaultTypedConfigProvider}
 *（实现 core/ports/config 的 TypedConfigProvider，返回类型化 records）、
 * {@code AdvancedConfigManager}/{@code ConfigSchema}/{@code ConfigUpgrader}/{@code LegacyDefaultFlips}
 *（schema 版本门控 + do-no-harm 深合并）、{@code ConfigHealthCheck}（630 行逐节校验，P2 计划下沉到
 * 各 config record）、{@code PortalsWriter}、{@code TemplateKeys}/{@code SafeKeys}/{@code ConfigPath}。</p>
 *
 * <p>类型化配置记录在 {@code configs/} 子包（23 个 record，默认值 + from(ConfigurationSection) 解析 +
 * 逐 record 校验段）。改一个配置字段 = 读对应 configs/ 记录 + 其解析默认 + 相关校验（现集中在
 * ConfigHealthCheck 对应方法）。</p>
 */
package com.jokerhub.paper.plugin.orzmc.infra.config;
