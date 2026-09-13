#!/usr/bin/env bash
# OrzMC 视频分集 唯一入口：源（epNN.yml）→ 派生物（人读稿 / SRT / 卡片 / TTS 样音）
#
# 用法：
#   videos/tools/make.sh <ep00|ep01|all> [--subs] [--cards] [--tts] [--all] [--check]
#     默认动作 = --subs --cards --tts（全部派生物）
#     --check   只做预算/事实校验（CI 用，不写任何文件）
#
# 产物落点：$VIDEO_WORKDIR（默认 videos/.build/<id>/），已在 .gitignore；原始录屏建议放仓库外。
# 依赖：python3 + PyYAML（校验/字幕）、Pillow（卡片）、ffmpeg + macOS `say`（TTS 样音，可选）。

set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VIDEOS_DIR="$(dirname "$TOOLS_DIR")"
REPO_ROOT="$(dirname "$VIDEOS_DIR")"
WORKDIR="${VIDEO_WORKDIR:-$VIDEOS_DIR/.build}"

do_subs=0 do_cards=0 do_tts=0 do_check=0
targets=()
for arg in "$@"; do
  case "$arg" in
    --subs)  do_subs=1 ;;
    --cards) do_cards=1 ;;
    --tts)   do_tts=1 ;;
    --all)   do_subs=1; do_cards=1; do_tts=1 ;;
    --check) do_check=1 ;;
    -*)      echo "未知参数：$arg" >&2; exit 2 ;;
    *)       targets+=("$arg") ;;
  esac
done
if [ ${#targets[@]} -eq 0 ]; then
  echo "用法：make.sh <ep00|all> [--subs] [--cards] [--tts] [--check]" >&2
  exit 2
fi
if [ "$do_subs$do_cards$do_tts" = "000" ]; then do_subs=1; do_cards=1; do_tts=1; fi

# 解析目标 → 源文件列表
sources=()
for t in "${targets[@]}"; do
  if [ "$t" = "all" ]; then
    while IFS= read -r f; do sources+=("$f"); done < <(find "$VIDEOS_DIR/episodes" -name 'ep*.yml' | sort)
  else
    matches=("$VIDEOS_DIR/episodes/${t}"-*.yml)
    if [ ! -e "${matches[0]}" ]; then
      matches=("$VIDEOS_DIR/episodes/${t}"*.yml)
    fi
    if [ ! -e "${matches[0]}" ]; then
      echo "❌ 找不到分集源：videos/episodes/${t}-*.yml" >&2; exit 2
    fi
    for f in "${matches[@]}"; do sources+=("$f"); done
  fi
done
if [ ${#sources[@]} -eq 0 ]; then
  echo "⚠️ 没有分集源（videos/episodes/*.yml），跳过" ; exit 0
fi

echo "== OrzMC 视频构建：${#sources[@]} 集（workdir=${WORKDIR}）=="
for src in "${sources[@]}"; do
  ep_id="$(basename "$src" | cut -d- -f1)"
  ep_dir="$WORKDIR/$ep_id"

  if [ "$do_check" = "1" ]; then
    python3 "$TOOLS_DIR/build-episode.py" "$src" --check
    continue
  fi

  # 1) 校验 + 文本派生物（人读稿 / SRT / 卡片 spec / 口播稿）
  if [ "$do_subs" = "1" ]; then
    python3 "$TOOLS_DIR/build-episode.py" "$src" --out "$ep_dir"
  fi
  # 2) 卡片 PNG（Pillow）
  if [ "$do_cards" = "1" ]; then
    spec="$ep_dir/$ep_id-cards.spec.json"
    if [ -f "$spec" ]; then
      python3 "$TOOLS_DIR/build-cards.py" --spec "$spec" --out "$ep_dir/cards"
      python3 "$TOOLS_DIR/build-cards.py" --spec "$spec" --out "$ep_dir/cards" --aspect portrait
    else
      echo "   ⚠️ 缺少卡片 spec（先跑 --subs）：$spec"
    fi
  fi
  # 3) TTS 样音（仅本机校验口播时长，不入库）
  if [ "$do_tts" = "1" ]; then
    bash "$TOOLS_DIR/tts-preview.sh" --ep "$ep_id" --dir "$ep_dir" || true
  fi
done

echo "== 完成：产物在 ${WORKDIR}（已被 .gitignore，不入库）=="
