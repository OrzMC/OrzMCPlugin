# 迭代规范

## 分支策略

- **`main`** 是唯一的永久分支，同时也是发版分支
- 所有开发分支从 `main` 创建，PR 合入 `main`
- PR 目标分支仅限 `main`

## 分支与提交

- 分支命名：feat/*, fix/*, refactor/*, chore/*, docs/*
- 提交粒度：一类改动一个提交，避免跨模块混杂

## 设计与实现

- 新能力优先落在服务层；入口层仅做参数转发
- 新依赖通过构造注入，由 OrzMC 统一装配
- 不新增静态全局依赖，避免隐式状态

## 配置与兼容

- 新配置先写入 resources 默认配置，并在 TypedConfigs 中建立类型映射，透出到 TypedConfigProvider
- 旧配置废弃需给出迁移说明与默认兼容策略

## 版本发布

- 推送 Strict SemVer 标签（如 `1.0.0`，**无 `v` 前缀**）到 GitHub 自动触发 CI 构建并创建 GitHub Release
- 版本命名规则见 CLAUDE.md

## 质量与验证

- 本地构建：`./gradlew spotlessApply && ./gradlew build`
- 关键流程需补齐日志与通知事件（如维护、玩家上下线）
- 变更需同步更新 README 文档中的功能与配置说明
