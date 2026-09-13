/**
 * 指南书基础设施（guide_book.yml 解析与成书渲染；纯配置驱动，无业务依赖）。
 *
 * <ul>
 *   <li>关键类型：{@link com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookConfigParser}
 *       （v2 简化格式 + v1 旧格式归一化解析，产出带页号/行号的告警而非抛异常）、
 *       {@link com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideLineMarkup}（{@code **粗体**} /
 *       {@code <u>下划线</u>} / {@code [文字](url '悬停')} 简写记号）、
 *       {@link com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookRenderer}（组件拼装、分页、
 *       链接点击/悬停、原版页数/字符上限降级）、
 *       {@link com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideColorParser}（命名色 + hex 统一口径）。</li>
 *   <li>模型：{@code models} 子包为不可变记录（页列表 / 行 / 样式），解析与渲染共用一套模型。</li>
 * </ul>
 */
package com.jokerhub.paper.plugin.orzmc.infra.guidebook;
