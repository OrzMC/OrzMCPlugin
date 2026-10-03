/**
 * 多语言（i18n）基础设施：内置语言包（bundled）⊕ 数据目录覆盖层（custom）的语料加载、
 * 语言决议与渲染（复用 {@link com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer}）。
 *
 * <p>语言包 = 翻译单元（完整消息/句子 + {@code {var}} 占位符），
 * 代码侧只保留样式与组装；zh-CN 为 key 完整性基线，跨语言 key/占位符一致性由
 * {@code I18nHealth} 与一致性单测守护。</p>
 */
package com.jokerhub.paper.plugin.orzmc.infra.i18n;
