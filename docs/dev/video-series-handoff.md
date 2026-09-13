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

- 2026-09-13 PR3a（分支 `feat/video-series-insights`）：**系列蓝本 + 映射 + 看板 + 更新 SOP + 影响检测工具**
  - `videos/series.md`（26 集清单含时长/字数/锚点/优先级/默认等级/录屏成本 + 拆集规则 + 发布矩阵 + 品牌规范）
  - `videos/coverage.yml`（功能点 ↔ 集号 双向映射：features.md 章节 / Bot 命令 / config 键 / templates 键 / 代码路径）
  - `videos/status.md`（过期看板：26 行初始状态 + 维护约定）· `videos/UPDATE.md`（一集一卡一 PR + L1/L2/L3 处置 + 平台版本约定 + 常见坑）
  - `videos/tools/affected-episodes.py`：依 `git diff` 判定受影响集与等级（章节行号定位 / 配置键结构图定位 / 命令表 / literal 路径），支持 `--update-status`（幂等标记看板）、`--json`、`--fail-on-impact`
  - 实测：templates.yml 文案键 → EP1/EP5/EP20 **L1**；config.yml 重构 → 12 集 **L3**（含顶层段增删）；features.md §9 → EP18 **L2**；im.yml → EP2 **L2**；无关文件 → 无影响
- 2026-09-13 PR2（已合入 develop `57b962c`）：工具链 + EP0/EP1 源 + 录制清单（`make.sh` / `build-episode.py` / `build-cards.py` / `tts-preview.sh` / `verify.sh`；ep00 70s·242 字·3.46 字/秒、ep01 145s·500 字·3.45 字/秒；TTS 55.6s/122.1s；卡片 16:9 与 9:16；幂等全绿）
- 2026-09-13 PR1（已合入 develop `f6de881`）：`promo-video/` → `videos/`，旧 8–10 分钟长稿归档，零产物策略与 `_template`/`brand`/`.gitignore` 落地
- 2026-09-13 跟踪锁定：建 epic **#481**；建并关联 P0 子 issue **#482 EP0 · #483 EP1 · #484 EP3 · #485 EP7 · #486 EP8 · #487 EP15 · #488 EP20 · #489 EP22 · #490 EP25**；在 **#128** 留言（EP25 承接教程诉求）；新建 `video` 标签

## 进行中卡

- **卡：PR3b CI 门禁（剩余步骤）**（分支待开，基合入 PR3a 后的 `origin/develop`）
  - 待做：`src/test/java/.../video/VideoScriptConsistencyTest.java`（snakeyaml 已在 test classpath：校验两集预算、facts/锚点存在性、`coverage.yml` 双向完整、敏感信息、零产物）；`build.yml` 增非阻塞 `video impact` 步骤；`PULL_REQUEST_TEMPLATE.md` 加 checkbox；`docs/README.md` 索引 + `docs/dev/im-gateway-inhouse.md:275` E2 更新 + CHANGELOG
  - 风险：CI（Ubuntu）无 Pillow → 门禁只跑文本派生物与源校验（`verify.sh` 默认不渲染卡片）

## 未完成清单（按依赖排序）

1. **步骤 10 CI 门禁与流程挂点**：一致性单测 + `build.yml` 的 `video impact` 步骤 + PR 模板 checkbox
2. **步骤 11**：PR3b 合入（步骤 10 的内容）
3. **步骤 12 文档联动**：`docs/README.md` 索引行、`docs/dev/im-gateway-inhouse.md:275` E2 条目更新、CHANGELOG、epic/子 issue 状态
4. **成片清单（owner 侧）**：录屏 → 配音 → 合成 → 横竖屏分发 → 回填 README/官网/Hangar+Modrinth 描述 → 标注适用版本 → 关闭子 issue
5. **后续分集**：EP2–EP25 按一集一卡推进（步骤 4 SOP：`_template/episode.yml` → `make.sh epNN --all` → PR）

## 环境事实（避免重复考古）

- 本机有 `ffmpeg 8.1.1` / `ffprobe` / macOS `say`（中文 TTS）；**无 HyperFrames**、无 brave-search 等检索工具
- 中文字体：`/System/Library/Fonts/PingFang.ttc`、`Hiragino Sans GB.ttc`、`STHeiti Medium.ttc`（品牌指定 Noto Sans SC 缺失时按顺序降级）
- 仓库：`OrzMC/OrzMCPlugin`，默认分支 `develop`，Java 25；PR 门禁 = build + folia-smoke；合并方式 squash
- 画面制作建议：以 **QQ 群录屏为主**（成本最低、卖点最强），游戏内画面仅少量

## 下一棒开场指令

> 读本文件（`docs/dev/video-series-handoff.md`）与 `videos/README.md`，从**步骤 10（CI 门禁与流程挂点）**开始：
> 写 `VideoScriptConsistencyTest`（snakeyaml 校验预算/facts/锚点/coverage 双向完整/敏感信息/零产物）、
> `build.yml` 加非阻塞 `video impact` 步骤、`PULL_REQUEST_TEMPLATE.md` 加视频影响 checkbox，
> 再做步骤 12（`docs/README.md` 索引 + `docs/dev/im-gateway-inhouse.md:275` E2 + CHANGELOG + epic/子 issue 状态）。
> 每个 PR 完成后更新本文件的「已完成 / 进行中卡」；提交前 `./gradlew spotlessApply && ./gradlew test` 全绿；PR base=`develop`，CI 绿后 squash。
