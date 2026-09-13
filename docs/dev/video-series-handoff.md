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

- 2026-09-13 PR3b（分支 `feat/video-series-gate`）：**CI 门禁 + 流程挂点 + 文档联动**
  - `src/test/java/.../video/VideoScriptConsistencyTest.java`（6 测试类）：预算（时长/语速/镜头/信息点/字幕行宽与单句）、
    事实与锚点（命令表 / 配置键 / 模板键 / features.md 章节）、映射完整（26 集、已产出源已登记、命令表全登记、
    映射值合法）、零产物（`git ls-files videos`）、隐私（IP/域名/QQ 号/会话 key/Token + 官方域名白名单）、模板必备字段
  - 负向验证：把 `duration_cap_s` 改为 10s → 预算测试 FAIL；把 PNG 加入索引 → 零产物测试 FAIL（均已还原）
  - `build.yml` 新增**非阻塞** `video impact` 步骤（PR 中输出受影响集与等级到 step summary）；
    `PULL_REQUEST_TEMPLATE.md` 新增「视频分集影响」勾选（已评估/不适用）
  - 文档联动：`docs/README.md` 新增视频系列索引行；`docs/dev/im-gateway-inhouse.md` E2 已收口为系列；CHANGELOG Unreleased 记录
- 2026-09-13 PR3a（已合入 develop `ca68e4c`）：系列蓝本 `series.md` + 映射 `coverage.yml` + 看板 `status.md` + SOP `UPDATE.md` + 影响检测 `affected-episodes.py`（5 个历史场景实测：文案键 L1、config 重构 L3、features 章节 L2、im.yml L2、无关文件无影响）
- 2026-09-13 PR2（已合入 develop `57b962c`）：工具链（make/build-episode/build-cards/tts-preview/verify）+ EP0/EP1 源 + 录制清单
- 2026-09-13 PR1（已合入 develop `f6de881`）：`promo-video/` → `videos/`、旧长稿归档、零产物策略与模板/品牌 token
- 2026-09-13 跟踪锁定：epic **#481**；P0 子 issue **#482 EP0 · #483 EP1 · #484 EP3 · #485 EP7 · #486 EP8 · #487 EP15 · #488 EP20 · #489 EP22 · #490 EP25**；#128 留言（EP25 承接教程诉求）；`video` 标签

## 进行中卡

- （无）12 步计划已全部落地；下一步是 **owner 侧的成片制作**与按卡片推进 EP2–EP25。

## 未完成清单（按依赖排序）

1. **成片清单（owner 侧）**：按 `videos/checklist-recording.md` 录屏（EP0/EP1）→ 配音（可用 `tts-preview.sh` 样音或真人）→ 合成（对齐 `.build/<epNN>/*.srt`）→ 横竖屏分发 → 在 `videos/status.md` 回填成片链接与适用版本 → 关闭子 issue
2. **后续分集 EP2–EP25**：一集一卡（新增 `videos/episodes/epNN-*.yml` → `make.sh epNN --all` 自检 → PR），优先 P0：EP3 白名单 / EP7 四级权限 / EP8 审核闭环 / EP15 传送门 / EP20 备份 / EP22 运行时配置 / EP25 快速上手
3. **发版联动**：每次 tag 发版后跑 `videos/tools/affected-episodes.py --since <上一 tag>`，把「本版影响的集数」附到 epic #481 评论

## 环境事实（避免重复考古）

- 本机有 `ffmpeg 8.1.1` / `ffprobe` / macOS `say`（中文 TTS）；**无 HyperFrames**、无 brave-search 等检索工具
- 中文字体：`/System/Library/Fonts/PingFang.ttc`、`Hiragino Sans GB.ttc`、`STHeiti Medium.ttc`（品牌指定 Noto Sans SC 缺失时按顺序降级）
- 仓库：`OrzMC/OrzMCPlugin`，默认分支 `develop`，Java 25；PR 门禁 = build + folia-smoke；合并方式 squash
- 画面制作建议：以 **QQ 群录屏为主**（成本最低、卖点最强），游戏内画面仅少量

## 下一棒开场指令

> 读本文件（`docs/dev/video-series-handoff.md`）与 `videos/README.md`，**从 EP2 卡片开始**：
> 在 epic #481 下建/取 EP2 子 issue → 从 `origin/develop` 拉 `feat/video-ep02` 分支 → 复制 `videos/_template/episode.yml`
> 为 `videos/episodes/ep02-im-channels.yml`（痛点 → 两通道对比 → 绑定与验证 → 选型建议 → 要点卡，≤150s）→
> `videos/tools/make.sh ep02 --all` 与 `verify.sh` 自检 → PR（base=`develop`）→ CI 绿后 squash。
> 若本次是功能改动收尾：先跑 `videos/tools/affected-episodes.py --base origin/main --update-status` 看是否有集需更新。
> 每个 PR 完成后更新本文件的「已完成 / 进行中卡」；提交前 `./gradlew spotlessApply && ./gradlew test` 全绿。
