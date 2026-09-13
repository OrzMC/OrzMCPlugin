#!/usr/bin/env bash
# main → develop 反向同步「合并前预检」（防陈旧/破坏性同步被自动合入）
#
# 背景：同步分支 sync-content 由 workflow 构建（develop 基线 + merge main），本应只「补上 main 独有内容」。
# 但两类失配会让自动合入出错，必须在 enable auto-merge 之前拦住：
#   ① 空转：sync-content 与 develop 树相同（里程碑已把 main 内容带回 develop）→ 不该再开 PR（陈旧 PR 要清理）；
#   ② 破坏性：sync-content 相对 develop 删除/覆盖 develop 侧内容（例如陈旧分支在其构建基点上已落后，
#      或 `-X theirs` 把 develop 独有改动覆盖掉）→ 合并会抹掉 develop 的成果；
#   ③ 冲突：合并预演不干净 → 不能自动合，须人工。
#
# 用法（本地/CI 均可）：bash scripts/sync-preflight.sh <base-ref> <head-ref>
#   bash scripts/sync-preflight.sh origin/develop sync-content
# 输出：首行 `VERDICT=skip|manual|ok` + 原因（供 workflow 解析）
# 退出码：ok/skip → 0；manual → 2
set -uo pipefail

BASE="${1:?需要 base ref（如 origin/develop）}"
HEAD_REF="${2:?需要 head ref（如 sync-content）}"

if ! git rev-parse --verify -q "$BASE" >/dev/null || ! git rev-parse --verify -q "$HEAD_REF" >/dev/null; then
  echo "VERDICT=manual"
  echo "原因：ref 不存在（$BASE / $HEAD_REF）"
  exit 2
fi

# ① 空转：两树相同 → 无需同步（应清理陈旧 PR，而不是合入一个空变更）
if git diff --quiet "$BASE" "$HEAD_REF"; then
  echo "VERDICT=skip"
  echo "原因：$HEAD_REF 与 $BASE 树相同（main 内容已含于 develop）→ 无可同步内容"
  exit 0
fi

# ② 破坏性：head 相对 base 删除了文件 → 拒绝自动合入
deleted=$(git diff --diff-filter=D --name-only "$BASE" "$HEAD_REF")
if [ -n "$deleted" ]; then
  echo "VERDICT=manual"
  echo "原因：$HEAD_REF 会删除 $BASE 中存在的文件（疑似陈旧/错误构建的同步分支）："
  printf '%s\n' "$deleted" | head -20 | sed 's/^/  - /'
  exit 2
fi

# ③ 合并预演：有冲突则不许自动合
if ! merged_tree=$(git merge-tree --write-tree "$BASE" "$HEAD_REF" 2>/dev/null); then
  echo "VERDICT=manual"
  echo "原因：合并预演存在冲突（需人工解冲突）"
  exit 2
fi

# ④ 对偶保护：合并结果不得删除 base 中存在的文件（防止以任何形式抹掉 develop 内容）
post_deleted=$(git diff --diff-filter=D --name-only "$BASE" "$merged_tree" 2>/dev/null || true)
if [ -n "$post_deleted" ]; then
  echo "VERDICT=manual"
  echo "原因：合并结果会删除 $BASE 中的文件："
  printf '%s\n' "$post_deleted" | head -20 | sed 's/^/  - /'
  exit 2
fi

echo "VERDICT=ok"
echo "原因：与 $BASE 有净内容、无删除、无冲突 → 可自动合入"
exit 0
