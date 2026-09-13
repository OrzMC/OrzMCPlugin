#!/usr/bin/env bash
# 视频系列门禁（CI 与本地同用）：
#   1) 零产物检查：`git ls-files videos/` 不得出现产物/中间产物（媒体、卡片、原盘文件）
#   2) 源校验：make.sh all --check（时长 / 语速 / 结构 / 字幕 / 事实 / 锚点 / 隐私）
#   3) 幂等（可选 --with-cards）：同一源连续生成两次，产物目录逐字节一致
#
# 用法：verify.sh [--with-cards]
#   CI 只跑 1+2（不依赖 Pillow）；本地可加 --with-cards 连卡片一起校验确定性。

set -uo pipefail

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VIDEOS_DIR="$(dirname "$TOOLS_DIR")"
REPO_ROOT="$(dirname "$VIDEOS_DIR")"
with_cards=0
[ "${1:-}" = "--with-cards" ] && with_cards=1

fail=0
echo "== 1/3 零产物检查（videos/ 只应有源与文档）=="
bad="$(cd "$REPO_ROOT" && git ls-files videos/ | grep -E '\.(mp4|mov|mkv|webm|mp3|wav|m4a|srt|ass|png|jpg|jpeg|psd|ai|aep)$' | grep -v '^videos/assets/' || true)"
if [ -n "$bad" ]; then
  echo "❌ 检测到被跟踪的产物/中间产物（应改为生成物，落 .build/ 且不入库）："
  echo "$bad" | sed 's/^/   · /'
  fail=1
else
  echo "✅ 无产物入库"
fi

echo "== 2/3 源校验（预算 / 事实 / 锚点 / 隐私）=="
if ! bash "$TOOLS_DIR/make.sh" all --check; then
  fail=1
fi

echo "== 3/3 幂等校验${with_cards:+（含卡片）} =="
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
gen_rc=0
if [ "$with_cards" = "1" ]; then
  VIDEO_WORKDIR="$tmp/a" bash "$TOOLS_DIR/make.sh" all >/dev/null || gen_rc=1
  VIDEO_WORKDIR="$tmp/b" bash "$TOOLS_DIR/make.sh" all >/dev/null || gen_rc=1
else
  VIDEO_WORKDIR="$tmp/a" bash "$TOOLS_DIR/make.sh" all --subs >/dev/null || gen_rc=1
  VIDEO_WORKDIR="$tmp/b" bash "$TOOLS_DIR/make.sh" all --subs >/dev/null || gen_rc=1
fi
if [ "$gen_rc" != "0" ]; then
  echo "❌ 生成器执行失败（见上方输出）"
  fail=1
fi
if diff -r "$tmp/a" "$tmp/b" >/dev/null 2>&1; then
  echo "✅ 两次生成产物完全一致（确定性通过）"
else
  echo "❌ 两次生成产物不一致："
  diff -r "$tmp/a" "$tmp/b" | head -20 | sed 's/^/   /'
  fail=1
fi

[ "$fail" = "0" ] && echo "== 视频系列门禁通过 ==" || echo "== 视频系列门禁失败 =="
exit "$fail"
