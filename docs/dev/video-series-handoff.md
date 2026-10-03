# 视频系列 交接文档

> 状态：现行 ｜ 最后更新：2026-10-02
> 总纲：[Epic #481](https://github.com/OrzMC/OrzMCPlugin/issues/481) ｜ 教程诉求：[#128](https://github.com/OrzMC/OrzMCPlugin/issues/128) → [#490](https://github.com/OrzMC/OrzMCPlugin/issues/490) ｜ 源与工具入口：[`../../videos/README.md`](../../videos/README.md)

## 任务与目标

把 OrzMC 的视频做成**最小、可专注推进的小系列**，只做 **2 条旗舰视频**：

- **V1 宣传短片**（≤90s，吸引）：三个最强卖点——群聊管服 / 权限晋升审核 / 安全+备份兜底。
- **V2 快速上手**（≤180s，教会）：下载 → 装 LuckPerms → 接机器人 → `$h` → `$a` 加白 → 上线（承接 #490/#128）。

其余功能不做独立视频（`docs/features.md` 与 `manuals/` 已覆盖）；确有需要时从 V1/V2 素材剪 9:16 ≤60s 切片。

**完成验收定义**：

1. V1 源已就绪（`videos/episodes/ep00-promo.yml`）
2. V2 源落定（`videos/episodes/ep25-quickstart.yml`）
3. 工具链可跑通：`make.sh <id> --all` + `verify.sh` 零产物/校验/幂等
4. 成片（owner 侧）：录屏 → 配音 → 合成 → 发布 → 回填 `status.md`

**边界（不由 agent 产出）**：真机游戏画面录制（客户端 + OBS）、真人配音、成片剪辑、版权 BGM、平台上传。

## 已完成（按时间倒序）

- 2026-10-02 再精简（本 PR）：删除短视频库源 `ep01-bot-commands.yml`、归档长稿 `archive/`、影响检测 `coverage.yml` + `tools/affected-episodes.py` + `UPDATE.md`；`VideoScriptConsistencyTest` 删除「覆盖映射完整」测试；`build.yml` 删除非阻塞 `video impact` 步骤；PR 模板删除「视频影响」勾选
- 2026-10-02 简化：系列从 26 集收敛为 V1 宣传 + V2 上手 + 短视频库；issue 收口（关 #483–#489、#128；#490 升级为 V2）
- 2026-09-13 PR3b：CI 门禁（预算/事实锚点/零产物/隐私/模板）+ 非阻塞 video impact + PR 模板联动
- 2026-09-13 PR3a：系列蓝本 + coverage 映射 + 看板 + UPDATE SOP + affected-episodes.py
- 2026-09-13 PR2：工具链（make/build-episode/build-cards/tts-preview/verify）+ EP0/EP1 源 + 录制清单
- 2026-09-13 PR1：`promo-video/` → `videos/`、旧长稿归档、零产物策略与模板/品牌 token

## 进行中卡

- （无）当前只待「V2 源」与「owner 侧成片制作」。

## 未完成清单（按依赖排序）

1. **V2 快速上手源**：建 `videos/episodes/ep25-quickstart.yml`（痛点 → 下载安装 → 装 LP → 接机器人 → `$h` → `$a` 加白 → 上线验证 → 要点卡，≤180s）→ `make.sh ep25 --all` + `verify.sh` 自检 → PR（#490）
2. **成片清单（owner 侧）**：录屏 V1（按 `checklist-recording.md`）+ V2 → 配音 → 合成 → 横竖屏分发 → 回填 `status.md` → 关 #490

## 环境事实（避免重复考古）

- 本机有 `ffmpeg 8.1.1` / `ffprobe` / macOS `say`（中文 TTS，`say -v Tingting` ≈4.48 字/秒）；**无 HyperFrames**、无检索工具
- 中文字体：`/System/Library/Fonts/PingFang.ttc`、`Hiragino Sans GB.ttc`、`STHeiti Medium.ttc`
- 仓库：`OrzMC/OrzMCPlugin`，默认分支 `develop`，Java 25；PR 门禁 = build + folia-smoke；合并方式 squash
- 画面制作建议：以 **QQ 群录屏为主**（成本最低、卖点最强），游戏内画面仅少量

## 下一棒开场指令

> 读本文件（`docs/dev/video-series-handoff.md`）与 `videos/README.md`，**从 V2 快速上手开始**：
> 在 #490 下推进：从 `origin/develop` 拉 `feat/video-v2` 分支 → 复制 `videos/_template/episode.yml`
> 为 `videos/episodes/ep25-quickstart.yml`（痛点 → 下载安装 → 装 LP → 接机器人 → $h → $a 加白 → 上线验证 → 要点卡，≤180s）→
> `videos/tools/make.sh ep25 --all` 与 `verify.sh` 自检 → PR（base=`develop`）→ CI 绿后 squash。
> 每个 PR 完成后更新本文件的「已完成 / 进行中卡」；提交前 `./gradlew spotlessApply && ./gradlew test` 全绿。
