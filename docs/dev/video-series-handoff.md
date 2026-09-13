# 视频系列 交接文档

> 状态：现行 ｜ 最后更新：2026-09-13
> 总纲：[Epic #481](https://github.com/OrzMC/OrzMCPlugin/issues/481) ｜ 教程诉求：[#128](https://github.com/OrzMC/OrzMCPlugin/issues/128) ｜ 源与工具入口：[`../../videos/README.md`](../../videos/README.md)

## 任务与目标

把 OrzMC 的宣传与教学视频做成 **可持续迭代的系列**：**EP0 宣传短片**（聚焦核心功能、小而美，≤90s）+
**EP1–EP25 功能分集**（一集一个功能点、讲清讲透、**单集 ≤180s**）。

**完成验收定义**（12 步全部落地）：

1. 规格与跟踪锁定（epic #481 + P0 子 issue #482–#490 + 本文件）
2. `videos/` 骨架与源模板可复制即用，`.gitignore` 保证**零产物**（跑完 `make.sh` 后 `git status` 干净）
3. 工具链幂等：`make.sh all --check` 通过且两次产物哈希一致
4. 单集独立推进 SOP（`videos/UPDATE.md`）可照做
5. 覆盖映射 + 过期看板（`coverage.yml` / `status.md`）双向完整
6. 系列蓝本（`videos/series.md`）26 集清单与拆集规则
7–8. EP0 / EP1 源落地并通过全部预算与事实校验
9. 录制清单覆盖 EP0/EP1 全部镜号（含敏感信息遮蔽）
10. CI 门禁：`VideoScriptConsistencyTest` + `verify.sh` + 非阻塞 `video impact` 步骤 + PR 模板 checkbox
11. PR1/PR2/PR3 串行合入 `develop`
12. 看板/issue 状态更新，新会话可凭本文件直接续作

**边界（不由 agent 产出）**：真机游戏画面录制（客户端 + OBS）、真人配音、成片剪辑、版权 BGM、平台上传。

## 已完成（按时间倒序）

- 2026-09-13 PR2（分支 `feat/video-series-tools`）：**工具链 + EP0/EP1 源 + 录制清单**
  - 工具：`make.sh`（唯一入口）、`build-episode.py`（校验 + 人读稿/SRT/卡片 spec/口播稿）、`build-cards.py`（Pillow 渲染 16:9 与 9:16 卡片）、`tts-preview.sh`（`say` 样音）、`verify.sh`（零产物 + 源校验 + 幂等）、`tools/README.md`（环境事实）
  - 源：`episodes/ep00-promo.yml`（5 镜 / 70s / 242 字 / 3.46 字/秒）、`episodes/ep01-bot-commands.yml`（6 镜 / 145s / 500 字 / 3.45 字/秒）
  - 录制清单：`videos/checklist-recording.md`（按集分节 + 敏感信息遮蔽 + 9:16 安全框 + 镜号级命名）
  - 实测：`make.sh all --check` 两集全绿；卡片 1920×1080 / 1080×1920；TTS 样音 EP0 55.6s、EP1 122.1s（均装得下分镜时长）；`verify.sh --with-cards` 幂等通过
- 2026-09-13 PR1（已合入 develop `f6de881`）：`promo-video/` → `videos/`，旧 8–10 分钟长稿归档为 `videos/archive/script-full-v1.md`，删除素材占位目录（零产物策略），新增 `videos/README.md`、`videos/_template/episode.yml`、`videos/brand/brand.yml`、`.gitignore` 零产物白名单、本交接文件
- 2026-09-13 跟踪锁定：建 epic **#481**（规格/26 集蓝本/发布矩阵/12 步计划）；建并关联 P0 子 issue **#482 EP0 · #483 EP1 · #484 EP3 · #485 EP7 · #486 EP8 · #487 EP15 · #488 EP20 · #489 EP22 · #490 EP25**；在 **#128** 留言说明「纳入系列、EP25 承接教程诉求」；新建 `video` 标签

## 进行中卡

- **卡：PR2 工具链 + EP0/EP1 源 + 录制清单**（分支 `feat/video-series-tools`，基 `origin/develop`）
  - 已完成：工具链五个脚本 + EP0/EP1 源 + 录制清单 + 本地门禁全绿（见上）
  - 下一步：提交 → push → PR（base=`develop`）→ CI 绿 → squash 合入 → 删分支 + `git fetch origin --prune` + 基线对齐
  - 风险：CI（Ubuntu）无 Pillow 时卡片渲染不可用——`verify.sh` 默认不渲染卡片（CI 只跑零产物 + 源校验 + 文本派生物幂等）

## 未完成清单（按依赖排序）

1. **步骤 3 工具链**：`affected-episodes.py`（`--base` 影响检测 + `--update-status`）——其余工具（make/build-episode/build-cards/tts-preview/verify）已在 PR2 落地
2. **步骤 4 SOP**：`videos/UPDATE.md`（一集一卡一 PR + L1/L2/L3 处置 + 平台版本标注/勘误写法）
3. **步骤 5 映射与看板**：`videos/coverage.yml`（章/命令/配置键/模板键 ↔ 集号）、`videos/status.md`（EP0–EP25 初始行）
4. **步骤 6 系列蓝本**：`videos/series.md`（26 集清单 + 拆集规则示例 + 发布矩阵）
5. **步骤 7–8 EP0/EP1 源**：`episodes/ep00-promo.yml`（75s/≈290 字）、`episodes/ep01-bot-commands.yml`（150s/≈570 字）
6. **步骤 9 录制清单**：`videos/checklist-recording.md`（按集分节 + OBS 参数 + 9:16 安全框 + 敏感信息遮蔽 + 镜号级命名）
7. **步骤 10 门禁**：`src/test/java/.../video/VideoScriptConsistencyTest.java`、`build.yml` 的 `video impact` 步骤、`PULL_REQUEST_TEMPLATE.md` checkbox
8. **步骤 11**：PR2（EP0/EP1 源 + 工具链 + 录制清单）、PR3（coverage/status/UPDATE + 检测/verify + 单测 + CI/模板 + 文档索引/CHANGELOG）
9. **步骤 12**：`docs/README.md` 索引行、`docs/dev/im-gateway-inhouse.md:275` E2 条目更新、CHANGELOG、epic/子 issue 状态、成片 checklist（录屏 → 配音 → 合成 → 横竖屏分发 → 回填 README/官网/Hangar+Modrinth → 标注适用版本 → 关闭子 issue）

## 环境事实（避免重复考古）

- 本机有 `ffmpeg 8.1.1` / `ffprobe` / macOS `say`（中文 TTS）；**无 HyperFrames**、无 brave-search 等检索工具
- 中文字体：`/System/Library/Fonts/PingFang.ttc`、`Hiragino Sans GB.ttc`、`STHeiti Medium.ttc`（品牌指定 Noto Sans SC 缺失时按顺序降级）
- 仓库：`OrzMC/OrzMCPlugin`，默认分支 `develop`，Java 25；PR 门禁 = build + folia-smoke；合并方式 squash
- 画面制作建议：以 **QQ 群录屏为主**（成本最低、卖点最强），游戏内画面仅少量

## 下一棒开场指令

> 读本文件（`docs/dev/video-series-handoff.md`）与 `videos/README.md`，从**步骤 5（覆盖映射 `coverage.yml` + 看板 `status.md`）**开始，
> 随后做步骤 6（`videos/series.md`：26 集蓝本 + 拆集规则 + 发布矩阵）、步骤 4（`videos/UPDATE.md`：L1/L2/L3 更新 SOP）、
> 步骤 3 余项（`videos/tools/affected-episodes.py`）与步骤 10（一致性单测 `VideoScriptConsistencyTest` + CI `video impact` 步骤 + PR 模板 checkbox），
> 最后步骤 12（文档索引 / CHANGELOG / epic 与子 issue 状态）。
> 每个 PR 完成后更新本文件的「已完成 / 进行中卡」；提交前 `./gradlew spotlessApply && ./gradlew test` 全绿；PR base=`develop`，CI 绿后 squash。
