# 单集独立推进 SOP 与功能迭代更新

> **状态：现行** ｜ **最后更新**：2026-09-13
> 配合 [`README.md`](README.md)（策略与命令）、[`coverage.yml`](coverage.yml)（映射）、[`status.md`](status.md)（看板）

## 一、新做一集（一集一卡一 PR）

1. **取卡**：从 [`status.md`](status.md) 找 🆕 待制作 的集，对应子 issue（如 EP3 = #484；未建则在 epic [#481](https://github.com/OrzMC/OrzMCPlugin/issues/481) 下补建）。
2. **拉分支**：`git fetch origin --prune && git checkout -b feat/video-ep03 origin/develop`。
3. **写源**：`cp videos/_template/episode.yml videos/episodes/ep03-whitelist.yml`，按「一集一个功能点、信息点 ≤3、镜头 ≤10、≤180s」填写；
   `documentation_anchors` 指向权威文档（`features.md §x.y`），`facts` 填本集讲到的命令/配置键/模板键。
4. **本地自检**：`videos/tools/make.sh ep03 --all` → 看预算结论与 `.build/ep03/` 下的 SRT、卡片、口播样音；
   需要连确定性一起验：`videos/tools/verify.sh --with-cards`（跑完 `git status` 必须干净）。
5. **录屏**：按 [`checklist-recording.md`](checklist-recording.md) 该集章节；素材名与 `shots[].asset` 一致（单镜可独立重录）。
6. **配音/字幕**：口播稿 `.build/ep03/ep03-narration.txt` 供配音；字幕用 `.build/ep03/ep03.zh-CN.srt` 导入剪辑软件或平台后台上传。
7. **合成与发布**：命名 `EP03-白名单-v1.0.28`；简介与置顶注明「以 docs/ 文档为唯一事实源」+ 适用版本；横屏 16:9 主片 + 9:16 ≤60s 切片（`aspect_variants`）。
8. **收尾**：更新 [`status.md`](status.md)（状态 🎬 / 链接 / 基线版本 / 最后更新）与子 issue；提交 PR（base=`develop`）→ CI 绿 → squash。

> **仓库零产物**：源与工具入库，SRT/卡片/音频/成片/原始录屏一律不入库（`.build/` 已忽略；原始录屏建议放仓库外）。

## 二、功能迭代后怎么更新（等级判定 → 处置）

先跑检测（非阻塞，仅为提示）：

```bash
videos/tools/affected-episodes.py --base origin/main            # 只看报告
videos/tools/affected-episodes.py --base origin/main --update-status   # 顺手在看板标 ⚠️待更新
videos/tools/affected-episodes.py --since 1.0.27                # 发版视角（tag 对比）
```

| 等级 | 触发（典型） | 处置 | 成本 |
|:--|:--|:--|:--|
| **L1 仅文案** | `templates.yml` 文案键、i18n 文本变化 | 改 `epNN.yml` 的 `narration`/`subtitle` → 重生成 SRT → 平台后台**替换字幕轨** + 置顶评论勘误（**成片不重制**） | 几分钟 |
| **L2 交互/界面变** | `config.yml` 键增减、Bot 命令语义变化、`features.md` 对应章节变更 | 按镜号只**重录受影响镜头**（同名替换素材）→ 该集重新合成 → 发新版 `EP03-…-v1.0.29` | 十几分钟 |
| **L3 新增/退役** | 新增顶层配置段、新增 `$` 命令、新增功能模块 | **加集**（复制模板新建 `epNN-*.yml` + 在 `coverage.yml` 登记 + 子 issue）或**归档集**（`status.md` 标 🗄️，成片保留旧版） | 一集工时 |

处置完成后：清掉 `status.md` 的 ⚠️、更新「基线版本 / 最后更新」、在 `coverage.yml` 补齐新映射；若影响发布说明，同时在 CHANGELOG/Release 里提示「视频需更新」。

## 三、平台侧约定（成片不可改的兜底）

- **命名带版本**：`EP01-群指令-v1.0.28`；重制发新版，旧版保留并在链接旁标注「旧版」。
- **简介/置顶**：必须写「以 docs/ 文档为唯一事实源；本视频对应 OrzMC vX.Y」。
- **勘误**：L1 只改字幕时，在置顶评论写「文案已随 vX.Y 更新，字幕已于 <日期> 替换」。
- **分发**：16:9 → B站 / YouTube / 官网 / Hangar+Modrinth 描述链接；9:16 ≤60s → 抖音 / 视频号 / QQ 频道。

## 四、常见坑

| 坑 | 说明 |
|:--|:--|
| 时长写太短/太长 | 校验会拦：语速须落 3.4–5.2 字/秒、总时长 ≤ 硬上限；**装不下就拆集**，不要靠加速语音 |
| 字幕单句过长 | 长镜头必须把 `subtitle` 写成**多句列表**（每句 ≤18 字、≤6s），否则校验报错 |
| 事实漂移 | `facts` 里的命令/配置键/模板键会被校验真实存在；功能改名时同步改源，否则 CI 红 |
| 敏感信息 | 源与字幕不得出现真实 IP/域名/QQ 号/openid/会话 key/Token（官方域名白名单除外），录制前按清单清场 |
| 产物误提交 | 生成物落 `.build/`（已忽略）；`verify.sh` 会检查 `git ls-files videos/` 是否混入产物 |
| 本机 ffmpeg 无文本滤镜 | 文本渲染走 Pillow；字幕以独立 SRT 交付（平台上传或剪辑导入），不做烧录 |
