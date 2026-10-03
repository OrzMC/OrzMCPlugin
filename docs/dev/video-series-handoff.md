# 视频系列 交接文档

> 状态：现行 ｜ 最后更新：2026-09-13
> 总纲：[Epic #481](https://github.com/OrzMC/OrzMCPlugin/issues/481) ｜ 教程诉求：[#128](https://github.com/OrzMC/OrzMCPlugin/issues/128) ｜ 源与工具入口：[`../../videos/README.md`](../../videos/README.md)

## 任务与目标

把 OrzMC 的宣传与上手视频做成 **可持续迭代的小系列**：**V1 宣传短片**（聚焦核心功能、小而美，≤90s）+
**V2 快速上手**（下载 → 接机器人 → 上线，≤180s，承接 #128）+ **按需短视频库**（backlog，不排产）。

**完成验收定义**：

1. 系列蓝本简化为「2 旗舰 + 短视频库」（`videos/series.md`）
2. 覆盖映射/看板收敛为 V1/V2/B1（`coverage.yml` / `status.md`）
3. 工具链/门禁/零产物策略复用（`make.sh` / `verify.sh` / `VideoScriptConsistencyTest` 改为 3 项）
4. V1 源已就绪（`ep00-promo.yml`）；V2 源待建（`ep25-quickstart.yml`）
5. 远端 issue 收口：关 #483–#490，改 epic #481 为新方案，#128 承接 V2

**边界（不由 agent 产出）**：真机游戏画面录制（客户端 + OBS）、真人配音、成片剪辑、版权 BGM、平台上传。

## 已完成（按时间倒序）

- 2026-10-02 简化（本 PR）：系列从 26 集收敛为 **V1 宣传 + V2 上手 + 短视频库**；`series.md`/`status.md`/`coverage.yml`（V1/V2/B1）/`README`/`UPDATE`/`_template`/`checklist-recording`/`brand` 同步；`VideoScriptConsistencyTest` 由 26 集改 3 项；`affected-episodes.py` 看板正则适配 V/B 编号
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

1. **V2 快速上手源**：建 `videos/episodes/ep25-quickstart.yml`（下载 → 装 LP → 接机器人 → `$h` → `$a` 加白 → 上线，≤180s）→ `make.sh ep25 --all` + `verify.sh` 自检 → PR
2. **成片清单（owner 侧）**：录屏 V1（按 `checklist-recording.md`）+ V2 → 配音 → 合成 → 横竖屏分发 → 回填 `status.md` → 关 issue
3. **issue 收口**：关 #483–#490（内容并入 V1/V2/短视频库），改 epic #481 为「2 旗舰 + 短视频库」；#128 继续承接 V2
4. **发版联动**：每次 tag 发版后跑 `affected-episodes.py --since <上一 tag>`，结论附 epic #481 评论

## 环境事实（避免重复考古）

- 本机有 `ffmpeg 8.1.1` / `ffprobe` / macOS `say`（中文 TTS）；**无 HyperFrames**、无 brave-search 等检索工具
- 中文字体：`/System/Library/Fonts/PingFang.ttc`、`Hiragino Sans GB.ttc`、`STHeiti Medium.ttc`（品牌指定 Noto Sans SC 缺失时按顺序降级）
- 仓库：`OrzMC/OrzMCPlugin`，默认分支 `develop`，Java 25；PR 门禁 = build + folia-smoke；合并方式 squash
- 画面制作建议：以 **QQ 群录屏为主**（成本最低、卖点最强），游戏内画面仅少量

## 下一棒开场指令

> 读本文件（`docs/dev/video-series-handoff.md`）与 `videos/README.md`，**从 V2 快速上手开始**：
> 在 #128 下推进：从 `origin/develop` 拉 `feat/video-v2` 分支 → 复制 `videos/_template/episode.yml`
> 为 `videos/episodes/ep25-quickstart.yml`（痛点 → 下载安装 → 装 LP → 接机器人 → $h → $a 加白 → 上线验证 → 要点卡，≤180s）→
> `videos/tools/make.sh ep25 --all` 与 `verify.sh` 自检 → PR（base=`develop`）→ CI 绿后 squash。
> 若本次是功能改动收尾：先跑 `videos/tools/affected-episodes.py --base origin/main --update-status` 看是否有视频需更新。
> 每个 PR 完成后更新本文件的「已完成 / 进行中卡」；提交前 `./gradlew spotlessApply && ./gradlew test` 全绿。
