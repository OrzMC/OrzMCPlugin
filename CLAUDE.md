# CLAUDE.md — Claude Code 入口

> **仓库统一 AI 指引见 `AGENTS.md`**（单一事实源，Claude Code 自动加载）。本文件仅为 Claude Code 桥接入口，不重复内容。

## Claude Code 使用约定

1. **仓库指引**：构建/测试命令、架构概览、开发红线（Folia 线程模型 / LuckPerms / 群消息防刷屏）、多 AI Agent 协作约定 → 全部在 `AGENTS.md`。
2. **实战案例**（红线背景、修复时间线、测试服验证方法论）→ `docs/dev/folia-luckperms-gotchas.md`。
3. **提交纪律**：`./gradlew spotlessApply && ./gradlew test` 全绿后提交；commit message 遵循仓库风格（conventional commits，中文）。
4. **审查**：本地改动可用 `/review` 自查，或换其他厂商 agent 交叉审查（见 AGENTS.md 协作约定）。
5. **修改指引**：需要更新仓库指引时只改 `AGENTS.md`（本文件是桥接，不新增内容）。
