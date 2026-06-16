# 贡献指南

欢迎参与 OrzMC 插件的开发。请花几分钟阅读以下规范。

## 分支策略

- `main` 是唯一的永久分支，同时也是发版分支
- 所有开发分支从 `main` 创建，PR 合入 `main`

## 开发环境

- JDK 21（CI 强制要求）
- 推荐 IDE：IntelliJ IDEA + Minecraft Development 插件
- 构建工具：Gradle（使用项目自带的 Wrapper）

## 常用命令

```bash
./gradlew spotlessApply           # 自动格式化代码
./gradlew spotlessCheck           # 代码格式检查
./gradlew test                    # 运行单元测试
./gradlew integrationTest         # 运行集成测试（MockBukkit）
./gradlew check                   # 完整质量门禁
./gradlew clean build             # 全量构建
./gradlew runServer               # 启动本地 Paper 调试服务器
```

## 分支命名

```
feat/*      新功能
fix/*       Bug 修复
refactor/*  代码重构
chore/*     构建/配置/依赖变更
docs/*      文档
```

## 提交规范

- 一类改动一个提交，避免跨模块混杂
- 提交信息简明扼要，说明改动内容和动机

## PR 流程

1. 从 `main` 创建你的特性/修复分支
2. 在本地完成开发和测试
3. 提交 PR 到 `main`
4. CI 自动运行：`spotlessCheck` → `test` → `integrationTest` → `shadowJar`
5. Maintainer Review
6. Squash merge 到 `main`

## 配置兼容

- 新增配置需要同时在 `resources/` 默认配置文件和 `TypedConfigs` 中注册
- 废弃旧配置需给出迁移说明和默认兼容策略

## 问题反馈

- Bug 报告和建议请通过 [GitHub Issues](https://github.com/OrzMC/OrzMCPlugin/issues/new/choose) 提交
- 其他问题可通过项目主页的 QQ 频道联系
