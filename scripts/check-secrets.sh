#!/usr/bin/env bash
# 明文私钥/凭据扫描（防 2026-09 私钥误入库事故复发）
#
# 用途：扫描**已跟踪文件**（git ls-files）中的高信号密钥模式；命中即失败（exit 1）。
# 接入点：build.gradle.kts 的 `checkSecrets` 任务 ← `./gradlew check` ← CI（build workflow）。
#
# 用法：
#   bash scripts/check-secrets.sh            # 扫描工作树（CI/本地门禁）
#   bash scripts/check-secrets.sh --all-history  # 额外扫描全部历史提交内容（人工排查用，慢）
#
# 设计取舍：只做「高信号」模式（私钥头、云厂商密钥、平台 token 前缀、赋值式长密钥），
# 避免把文档里的示例值/占位符误报；占位符与测试夹具经 ALLOW 白名单放行。

set -uo pipefail

ALL_HISTORY=0
[[ "${1:-}" == "--all-history" ]] && ALL_HISTORY=1

# 高信号模式（ERE）
PATTERNS=(
  '-----BEGIN [A-Z ]*PRIVATE KEY-----'
  '-----BEGIN OPENSSH PRIVATE KEY-----'
  'ghp_[A-Za-z0-9]{20,}'
  'github_pat_[A-Za-z0-9_]{20,}'
  'AKIA[0-9A-Z]{16}'
  'xox[baprs]-[A-Za-z0-9-]{10,}'
  'sk-[A-Za-z0-9]{20,}'
  '(client_secret|clientSecret|app_secret|appSecret|api_key|apiKey|secret|password|passwd|token|authorization)[[:space:]]*[:=][[:space:]]*["'"'"'][A-Za-z0-9/+_.-]{16,}["'"'"']'
)

# 放行：占位符/示例（大小写不敏感）
ALLOW='(""|'"''"'|xxx+|your[-_]|example|placeholder|change[-_]?me|\$\{|\*\*\*|<[a-z_-]+>|redacted|dummy|fake|test-|tok-[0-9]|secret-[0-9]|app-[0-9])'

scan_text() { # stdin → 输出命中行
  local pat
  for pat in "${PATTERNS[@]}"; do
    grep -nEe "$pat" || true
  done | grep -viE "$ALLOW" || true
}

fail=0
echo "== 扫描已跟踪文件（$(git ls-files | wc -l | tr -d ' ') 个）=="
while IFS= read -r f; do
  [[ -f "$f" ]] || continue
  # 跳过二进制
  grep -Iq . "$f" 2>/dev/null || continue
  hits=$(scan_text < "$f")
  if [[ -n "$hits" ]]; then
    echo "❗ $f"
    echo "$hits" | sed 's/^/     /'
    fail=1
  fi
done < <(git ls-files)

if [[ $ALL_HISTORY -eq 1 ]]; then
  echo "== 扫描全部历史 blob（排查用）=="
  git cat-file --batch-all-objects --batch-check='%(objecttype) %(objectname)' \
    | awk '$1=="blob"{print $2}' > /tmp/orzmc_blobs.$$
  while read -r b; do
    hits=$(git cat-file -p "$b" 2>/dev/null | scan_text)
    if [[ -n "$hits" ]]; then
      echo "❗ blob $b（用 git log --all -S '<片段>' 定位提交）"
      echo "$hits" | head -3 | sed 's/^/     /'
      fail=1
    fi
  done < /tmp/orzmc_blobs.$$
  rm -f /tmp/orzmc_blobs.$$
fi

if [[ $fail -ne 0 ]]; then
  cat >&2 <<'EOT'

❌ 检测到疑似明文私钥/凭据（详见上方 file:line）。
   - 私钥/密钥文件**绝不能入库**（本仓库为公开仓库）；如已提交：立即轮换该密钥（改 authorized_keys / 平台后台重置），
     删除文件后按 docs/dev/security-incidents.md 处置（必要时重写历史）。
   - 误报（文档示例值）：确认无泄露风险后，把该值改为占位符（如 your-key-here）或补进本脚本的 ALLOW 白名单。
EOT
  exit 1
fi
echo "✅ 未发现明文私钥/凭据"
