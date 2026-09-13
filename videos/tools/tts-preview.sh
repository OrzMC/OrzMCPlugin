#!/usr/bin/env bash
# 口播样音生成（macOS `say`）→ mp3，用于校验「时长是否装得下」与文案口语化程度。
# 产物仅本机（.build/，不入库）；语音可用 `say -v '?'` 查看，默认 Tingting（zh_CN）。
#
# 用法：tts-preview.sh --ep ep00 --dir videos/.build/ep00 [--voice Tingting] [--rate 190]

set -euo pipefail

ep="" dir="" voice="Tingting" rate="190"
while [ $# -gt 0 ]; do
  case "$1" in
    --ep) ep="$2"; shift 2 ;;
    --dir) dir="$2"; shift 2 ;;
    --voice) voice="$2"; shift 2 ;;
    --rate) rate="$2"; shift 2 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done
[ -n "$ep" ] && [ -n "$dir" ] || { echo "用法：tts-preview.sh --ep ep00 --dir <产物目录>" >&2; exit 2; }

nar="$dir/$ep-narration.txt"
[ -f "$nar" ] || { echo "   ⚠️ 缺少口播稿：$nar"; exit 0; }
command -v say >/dev/null || { echo "   ⚠️ 非 macOS 或缺 say，跳过 TTS 样音"; exit 0; }
command -v ffmpeg >/dev/null || { echo "   ⚠️ 缺 ffmpeg，跳过 TTS 转码"; exit 0; }

out_dir="$dir/preview"
mkdir -p "$out_dir"
i=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  i=$((i+1))
  aiff="$out_dir/$(printf '%02d' "$i").aiff"
  say -v "$voice" -r "$rate" -o "$aiff" "$line"
  ffmpeg -y -loglevel error -i "$aiff" -codec:a libmp3lame -b:a 128k "$out_dir/$(printf '%02d' "$i").mp3"
  rm -f "$aiff"
done < "$nar"

total=0
for f in "$out_dir"/*.mp3; do
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f")
  total=$(python3 -c "print($total + $d)")
done
echo "   · TTS 样音：$i 句 / 合计 $(python3 -c "print(f'{$total:.1f}')")s → $out_dir"
