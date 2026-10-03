# OrzMC 视频系列（源与工具）

> **状态：现行** ｜ **最后更新**：2026-10-02
> 系列总纲：[Epic #481](https://github.com/OrzMC/OrzMCPlugin/issues/481) ｜ 教程诉求（V2）：[#128](https://github.com/OrzMC/OrzMCPlugin/issues/128)

本目录是 **OrzMC 宣传与上手视频**的**唯一事实源**：一条 **V1 宣传短片**（吸引用户）+ 一条 **V2 快速上手**（教会用户）+
按需**短视频库**（backlog，不排产）。

## ⚠️ 仓库策略：只跟踪源与工具，**产物与中间产物一律不入库**

| ✅ 入库 | ❌ 不入库（可随时重生成） |
|:--|:--|
| `episodes/epNN-*.yml`（唯一事实源：镜头/口播/字幕/锚点/facts） | `*.mp4 / *.mov / *.mkv / *.webm / *.mp3 / *.wav / *.srt / *.ass` |
| `tools/*`（生成器与校验器） | 卡片·片头 PNG、动画 MP4、TTS 样音、生成的 `epNN.md` |
| `brand/brand.yml`（色值/字体/尺寸 token） | 原始录屏、剪辑工程、平台导出物（封面/字幕包/成片） |
| 系列文档（`README/series/UPDATE/status/coverage/checklist-recording/_template/archive`） | `videos/.build/**`、`videos/.preview/**` |

- 产物统一生成到 **`videos/.build/<epNN>/`**（已在 `.gitignore`，跑完 `git status` 必须干净）。
- **原始录屏别放仓库内**：默认落 `videos/.build/<epNN>/raw/`，建议用 `VIDEO_WORKDIR=/path/outside/repo` 指到仓库外，避免大文件误提交。
- 允许入库的输入素材只有小型、许可干净者（如 Logo），放 `videos/assets/`（PNG/SVG 有白名单豁免）。

## 目录与命令

```
videos/
├── README.md                 # 本文件（策略 + 用法）
├── series.md                 # 系列蓝本：2 旗舰 + 短视频库清单 / 发布矩阵
├── UPDATE.md                 # 单集独立推进 SOP + 功能迭代后的 L1/L2/L3 处置
├── status.md                 # 过期看板（状态 / 基线版本 / 成片链接 / 最后更新）
├── coverage.yml              # 功能点 ↔ 视频 双向映射（影响检测的依据）
├── checklist-recording.md    # 录制清单（含敏感信息遮蔽、9:16 安全框、镜号级命名）
├── _template/episode.yml     # 分集源模板（复制后填写）
├── brand/brand.yml           # 品牌 token（全系列共用）
├── episodes/                 # 分集源（ep00-promo.yml / ep01-*.yml …）
├── tools/                    # make / build-episode / build-cards / tts-preview / verify / affected-episodes
├── archive/                  # 归档：#145 的 8–10 分钟长稿（被本系列取代）
└── .build/  .preview/        # 全部产物（gitignored）
```

```bash
videos/tools/make.sh ep01 --all      # 单集一键重建（md + SRT + 卡片 + TTS 样音）→ .build/ep01/
videos/tools/make.sh all --check     # 全部集：预算校验 + 生成器可跑 + 幂等（两次产物哈希一致）
videos/tools/affected-episodes.py --base origin/main   # 功能改动 → 受影响视频 + L1/L2/L3 等级
```

## 单条视频独立推进（一条一片一 PR）

1. 从对应 issue 开工（V1 = 已有 `ep00-promo.yml`；V2 = #128，新建 `episodes/ep25-quickstart.yml`；短视频库按需建）；
2. 写源 → `videos/tools/make.sh <id> --all` 本地校验（时长/语速/行宽/锚点/事实）；
3. 按 `checklist-recording.md` 录屏（产物不入库）→ 配音/字幕 → 合成；
4. 发布后在 `status.md` 回填成片链接与适用版本；
5. 功能迭代后按 `UPDATE.md` 的 L1（仅文案，换字幕不重录）/ L2（单镜重录 + 该片重合成）/ L3（加片或归档）处置。

## 硬约束（CI 门禁校验）

| 约束 | 值 |
|:--|:--|
| 单条时长 | **硬上限 180s**（V1 ≤90s；V2 ≤180s） |
| 口播预算 | ≈ 时长 × 3.8 字/秒（= 4.5 字/秒 × 0.85 留白；150s ≈ 570 字） |
| 语速校验 | 3.4–5.2 字/秒（含停顿折算；实测 `say -v Tingting` ≈4.48 字/秒） |
| 结构 | 信息点 ≤3、镜头 ≤10；**超限即拆集**（不压缩语速） |
| 字幕 | 单行 ≤18 字、单句 ≤6s |
| 事实 | `facts`（命令/配置键/模板键）与 `documentation_anchors` 必须真实存在（防脚本与实现漂移） |
| 隐私 | 内容中不得出现真实 IP/域名/QQ 群号/openid/会话 key/Token（合规用合成值） |

## 边界（本仓库不产出）

真机游戏画面录制（需客户端 + OBS）、真人配音、成片剪辑、版权 BGM、平台上传——由 owner 侧完成；
本目录交付「源 + 工具 + 看板 + 门禁」，产物可随时重生成。

详见 [`../docs/dev/video-series-handoff.md`](../docs/dev/video-series-handoff.md)（跨会话交接）与 [`../docs/README.md`](../docs/README.md)（文档索引）。
