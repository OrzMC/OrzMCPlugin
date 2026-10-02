# 视频系列工具链

> **状态：现行** ｜ **最后更新**：2026-09-13
> 策略与目录：[`../README.md`](../README.md) ｜ 总纲：[#481](https://github.com/OrzMC/OrzMCPlugin/issues/481)

| 工具 | 作用 |
|:--|:--|
| `make.sh` | **唯一入口**：`make.sh <ep00\|ep01\|all> [--subs] [--cards] [--tts] [--check]`；默认全做 |
| `build-episode.py` | 校验源 + 生成人读稿 / SRT / 卡片 spec / 口播稿（纯 stdlib + PyYAML） |
| `build-cards.py` | 用 Pillow 渲染卡片 PNG（16:9 与 9:16），色值/字体取自 `brand/brand.yml` |
| `tts-preview.sh` | macOS `say` 生成口播样音 mp3（校验时长是否装得下），仅本机产物 |
| `verify.sh` | 门禁：零产物检查 + 源校验 + 幂等（`--with-cards` 连卡片一起校验确定性） |
| `affected-episodes.py` | 功能改动 → 受影响集与等级（L1 文案 / L2 单镜重录 / L3 加集归档）；`--update-status` 更新看板 |

## 环境事实（本机实测，避免重复考古）

| 事实 | 说明 |
|:--|:--|
| ffmpeg 8.1.1（Homebrew）**无 `drawtext` / `subtitles` / `ass` 滤镜**（未编译 libfreetype / libass） | 文本渲染一律走 **Pillow**；字幕以**独立 SRT 外挂交付**（平台上传或剪辑软件导入），不做烧录 |
| Pillow 11.3.0 可用 | 卡片渲染依赖；缺失时 `make.sh --cards` 会失败，可只用 `--subs` |
| `say -v Tingting`（zh_CN）可用，实测 **4.48 字/秒** | 与预算模型 4.5 字/秒一致；样音时长可直接对比分镜时长 |
| PyYAML 6.0.3 可用 | 源解析与事实校验（`config.yml` / `templates.yml` 键存在性） |
| 中文字体 | `PingFang.ttc`（首选）→ `Hiragino Sans GB.ttc` → `STHeiti Medium.ttc`；不把字体文件入库 |

## 产物落点

- 默认 `videos/.build/<epNN>/`（已在 `.gitignore`，跑完 `git status` 必须干净）
- 可用 `VIDEO_WORKDIR=/path/outside/repo` 指到仓库外（**原始录屏建议放仓库外**，避免大文件误提交）
- 卡片：`.build/<epNN>/cards/*.png`（16:9 与 `-9x16.png`）；字幕：`.build/<epNN>/<epNN>.zh-CN.srt`

## 常用命令

```bash
videos/tools/make.sh ep01 --all                 # 单集全量派生物
videos/tools/make.sh all --check                # 全部集：仅校验（CI 用）
videos/tools/verify.sh                          # 门禁：零产物 + 校验 + 幂等
videos/tools/verify.sh --with-cards             # 连卡片 PNG 一起校验确定性（需 Pillow）
videos/tools/affected-episodes.py --base origin/main   # 改动影响的集数与等级
```
