# OrzMC 文档导航

> 仓库所有文档的总索引。按「读者角色 + 文档时效」组织——**现行手册**在 `docs/` 根目录直接可见，
> **历史快照与验收记录**见 [`reports/`](./reports/)（精简索引，完整见 git）、**进行中路线图**在 [`roadmap/`](./roadmap/）、
> **开发者治理红线**在 [`dev/`](./dev/)。每份文档头部标注「状态 / 最后更新」，先看状态再决定是否采信内容。

## 按读者角色

### 🎮 服主 / 玩家（使用插件）
| 文档 | 内容 | 位置 |
|:--|:--|:--|
| [README](../README.md) | 项目总览、安装、机器人接入、更新 | 仓库根 |
| [功能清单](features.md) | **全部功能的唯一权威描述**（含配置项、命令、权限组；bot §2 为概览与手册索引） | docs/ |
| [Bot 接入手册](manuals/README.md) | 两通道选型（含[双通道对比](manuals/channel-comparison.md)）+ 分步接入/验收手册（EasyBot 网关 / builtin QQ·飞书·Telegram·Discord；公共骨架） | docs/manuals/ |
| [权限组节点表](permission-groups.md) | LP 各组权限节点明细与设计决策 | docs/ |
| [新手指南书手册](guide-book.md) | guide_book.yml 3 分钟上手：速查表 / 全写法 / 上限降级 / 旧格式迁移 / 排查清单 | docs/ |
| [视频系列](../videos/README.md) | V1 宣传短片 + V2 快速上手（零产物、可重生成）；教程诉求 [#128](https://github.com/OrzMC/OrzMCPlugin/issues/128) → [#490](https://github.com/OrzMC/OrzMCPlugin/issues/490)、总纲 [#481](https://github.com/OrzMC/OrzMCPlugin/issues/481) | videos/ |
| [故障排查](troubleshooting.md) | 常见报错的判定与处置（含「启动完成前敲命令报 `CommandSourceStack.getLevel()` NPE」——Paper 上游缺陷，非插件问题） | docs/ |

### 🧑💻 开发者（改代码）
| 文档 | 内容 | 位置 |
|:--|:--|:--|
| [AGENTS.md](../AGENTS.md) | **仓库协作单一事实源**：构建命令、架构速览、开发红线、AI 协作约定 | 仓库根 |
| [贡献指南](../CONTRIBUTING.md) | 环境要求、双轨三支分支模型（默认 `develop`）、PR 流程与版本发布规则 | 仓库根 |
| [架构设计](architecture.md) | 分层/模块/生命周期/依赖/设计原则 + 「改 X → 读 Y」编辑路径 | docs/ |
| [代码质量路线图](roadmap/code-quality-roadmap.md) | 待办质量问题清单（P0/P1/P2）与任务拆分 | docs/roadmap/ |
| [配置 Schema 治理](dev/config-schema-governance.md) | config.yml 结构、版本门控迁移规则、允许/禁止事项 | docs/dev/ |
| [Folia × LuckPerms 红线](dev/folia-luckperms-gotchas.md) | 线程模型/LP 集成实战教训（**改 rank/review/prison 前必读**） | docs/dev/ |
| [IM 网关内建方案](dev/im-gateway-inhouse.md) | EasyBot ↔ builtin 双通道切换方案定稿（backend/im.yml/决策记录/实施路线/§10 遗留清单） | docs/dev/ |
| 包级 javadoc（`package-info.java`） | 逐包职责/关键类型/依赖方向，定位最快入口 | src/ |

### 🧪 测试 / 质量
| 文档 | 内容 | 位置 |
|:--|:--|:--|
| [测试计划与质量体系](quality-testing-plan.md) | 测试分层策略、覆盖地图、质量指标 | docs/ |
| [E2E 套件说明](../e2e/README.md) | 真实服务器端到端套件：环境/执行/用例清单 | e2e/ |
| [E2E Bug 记录](../e2e/buglog.md) | 套件运行中发现的问题登记 | e2e/ |

### 🚀 发布 / 运维
| 文档 | 内容 | 位置 |
|:--|:--|:--|
| [发布平台运维手册](publishing-platforms.md) | Hangar/Modrinth 项目信息、自动发布（含 tag 发布后 bump 自动化 §5.5）、Token 管理 | docs/ |
| [更新日志](../CHANGELOG.md) | 版本变更明细 | 仓库根 |

### 📚 历史快照 / 验收记录（已归档，见精简索引）
| 文档 | 说明 |
|:--|:--|
| [历史快照与验收记录索引](reports/README.md) | 安全加固 / E2E / Folia 适配 / 权限二期 / i18n / Folia 迁移 的精简摘要（完整报告见 git 历史） |

## 维护约定（文档工程师）

1. **时效可见**：每份文档头部必须有「状态：现行 / 归档（被 X 取代）+ 最后更新」；新读者先读状态头。
2. **单一事实源**：功能描述唯一权威是 [`features.md`](features.md)；其他文档只链接不复述清单（含 README、CHANGELOG、测试计划）。
3. **完结即归档**：路线图/验收文档勾完即由合入 PR 顺带标注 ✅ 并移入 [`reports/`](./reports/)。
4. **同步义务**：新增/变更功能、e2e 用例、配置字段，必须同步本索引指向的现行文档（见 AGENTS.md 文档纪律）。
5. **商业内容不入库**：定价/渠道等经营策略不进公开仓库（如历史文件 `commercialization.md`，已于 2026-09-03 移出工作树，需要时经 git 历史恢复）。
