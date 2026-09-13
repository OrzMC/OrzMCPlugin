/**
 * 新手指南（独立特性，无特性依赖）。
 * - 关键类型：GuideService（/guide 打开指南书 + 首次进服发放；解析结果进程内缓存，
 *   {@code /orzmc config reload} 触发的 invalidate 回调使其失效重解析）。
 * - 依赖：infra（guidebook 解析/渲染 + config；guide_book.yml 为运行时数据文件，格式升级走代码内迁移）。
 */
package com.jokerhub.paper.plugin.orzmc.features.guide;
