# OrzMC 视频（源与工具）

> **状态：现行** ｜ **最后更新**：2026-10-02
> 教程诉求（V2）：[#128](https://github.com/OrzMC/OrzMCPlugin/issues/128) → [#490](https://github.com/OrzMC/OrzMCPlugin/issues/490) ｜ 总纲：[#481](https://github.com/OrzMC/OrzMCPlugin/issues/481)

本目录是 OrzMC **宣传与上手视频**的唯一事实源，**只做 2 条**：

- **V1 宣传短片**（≤90s，吸引）：源 `episodes/ep00-promo.yml`（已就绪）
- **V2 快速上手**（≤180s，教会）：源 `episodes/ep25-quickstart.yml`（待建，承接 #490/#128）

其余功能不做独立视频（`docs/features.md` 与 `manuals/` 已覆盖）；确有需要时，从 V1/V2 录屏素材剪 9:16 ≤60s 切片。

## 仓库策略：只跟踪源与工具，产物一律不入库

| ✅ 入库 | ❌ 不入库（可随时重生成） |
|:--|:--|
| `episodes/*.yml`（唯一事实源：镜头/口播/字幕/锚点/facts） | `*.mp4 / *.mov / *.mkv / *.webm / *.mp3 / *.wav / *.srt / *.ass` |
| `tools/*`（生成器与校验器） | 卡片·片头 PNG、动画 MP4、TTS 样音、生成的 `*.md` |
| `brand/brand.yml`（色值/字体/尺寸 token） | 原始录屏、剪辑工程、平台导出物（封面/字幕包/成片） |
| 系列文档（`README/series/status/checklist-recording/_template`） | `videos/.build/**`、`videos/.preview/**` |

- 产物统一生成到 **`videos/.build/<id>/`**（已在 `.gitignore`，跑完 `git status` 必须干净）。
- 原始录屏放仓库外（或 `VIDEO_WORKDIR=/path/outside/repo`），避免大文件误提交。

## 目录与命令

```
videos/
├── README.md                 # 本文件（策略 + 用法）
├── series.md                 # 2 条视频蓝本（V1/V2 结构与卖点）
├── status.md                 # 看板（状态 / 基线版本 / 成片链接 / 最后更新）
├── checklist-recording.md    # 录制清单（含敏感信息遮蔽、9:16 安全框、镜号级命名）
├── _template/episode.yml     # 视频源模板（复制后填写）
├── brand/brand.yml           # 品牌 token
├── episodes/                 # 视频源（ep00-promo.yml / ep25-quickstart.yml）
├── tools/                    # make / build-episode / build-cards / tts-preview / verify
└── .build/  .preview/        # 全部产物（gitignored）
```

```bash
videos/tools/make.sh ep00 --all      # 单条重建（md + SRT + 卡片 + TTS 样音）→ .build/ep00/
videos/tools/make.sh all --check     # 全部源：预算/事实/锚点/隐私校验（CI 用，不写文件）
videos/tools/verify.sh               # 零产物 + 源校验 + 幂等（本地/CI）
```

## 单条推进（一条一片一 PR）

1. 写源：V1 已有 `ep00-promo.yml`；V2 复制 `_template/episode.yml` 为 `episodes/ep25-quickstart.yml`；
2. `videos/tools/make.sh <id> --all` 本地校验（时长/语速/行宽/锚点/事实）；
3. 按 `checklist-recording.md` 录屏（产物不入库）→ 配音/字幕 → 合成；
4. 发布后在 `status.md` 回填成片链接与适用版本。

## 硬约束（CI 门禁校验）

| 约束 | 值 |
|:--|:--|
| 单条时长 | **硬上限 180s**（V1 ≤90s；V2 ≤180s） |
| 口播预算 | ≈ 时长 × 3.8 字/秒（= 4.5 字/秒 × 0.85 留白；150s ≈ 570 字） |
| 语速校验 | 3.4–5.2 字/秒（含停顿折算；实测 `say -v Tingting` ≈4.48 字/秒） |
| 结构 | 信息点 ≤3、镜头 ≤10；**超限即拆片**（不压缩语速） |
| 字幕 | 单行 ≤18 字、单句 ≤6s |
| 事实 | `facts`（命令/配置键/模板键）与 `documentation_anchors` 必须真实存在（防脚本与实现漂移） |
| 隐私 | 内容中不得出现真实 IP/域名/QQ 群号/openid/会话 key/Token（合规用合成值） |

## 常见坑

| 坑 | 说明 |
|:--|:--|
| 时长写太短/太长 | 校验会拦：语速须落 3.4–5.2 字/秒、总时长 ≤ 硬上限；**装不下就拆片**，不要靠加速语音 |
| 字幕单句过长 | 长镜头把 `subtitle` 写成**多句列表**（每句 ≤18 字、≤6s），否则校验报错 |
| 事实漂移 | `facts` 里的命令/配置键/模板键会被校验真实存在；功能改名时同步改源，否则 CI 红 |
| 敏感信息 | 源与字幕不得出现真实 IP/域名/QQ 号/openid/会话 key/Token，录制前按 `checklist-recording.md` 清场 |
| 产物误提交 | 生成物落 `.build/`（已忽略）；`verify.sh` 会检查 `git ls-files videos/` 是否混入产物 |
| 本机 ffmpeg 无文本滤镜 | 文本渲染走 Pillow；字幕以独立 SRT 交付（平台上传或剪辑导入），不做烧录 |

## 边界（本仓库不产出）

真机游戏画面录制（需客户端 + OBS）、真人配音、成片剪辑、版权 BGM、平台上传——由 owner 侧完成；
本目录交付「源 + 工具 + 看板 + 门禁」，产物可随时重生成。

详见 [`../docs/dev/video-series-handoff.md`](../docs/dev/video-series-handoff.md)（跨会话交接）与 [`../docs/README.md`](../docs/README.md)（文档索引）。
