#!/usr/bin/env bash
# OrzMC 插件 E2E 测试套件一键入口
# 用法:
#   bash e2e/run-all.sh            # 全量（01-04 全部用例）
#   bash e2e/run-all.sh -c 01 -c 03  # 只跑指定用例（前缀匹配）
#   bash e2e/run-all.sh -h         # 帮助
# 环境要求:
#   - Folia 测试服在线（~/folia-test/，端口 25565，RCON 25575/orztest2026）
#   - node + ~/minecraft-bot/node_modules（mineflayer）
set -uo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CASES_DIR="$E2E_DIR/cases"
NODE_PATH="${NODE_PATH:-$HOME/minecraft-bot/node_modules}"
REPORT_DIR="$E2E_DIR/reports"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
REPORT_FILE="$REPORT_DIR/e2e-report-$TIMESTAMP.md"

usage() {
  cat <<'EOF'
OrzMC 插件 E2E 测试套件
用法: bash e2e/run-all.sh [选项]

选项:
  -c <前缀>   只跑匹配前缀的用例（可多次，如 -c 01 -c 03）
  -r          生成 Markdown 报告（默认落盘 reports/）
  -h          显示帮助

用例:
  01-bot-cmds.js        Bot 命令（$h/$l/$w/$a/$r/$d/$e）
  02-player-cmds.js     玩家命令（/guide /menu /bot /rank /apply /config 权限隔离）
  03-security.js        安全拦截（黑名单登录 / 聊天过滤 / 命令守卫）
  04-maintenance.js     世界维护（$b 备份三阶段+落盘）
  05-groupmsg.js        群消息发送（白名单拦截/上下线/聚合/IP黑名单拦截，日志断言）
EOF
}

SELECTED=()
REPORT=false
while getopts "c:rh" opt; do
  case "$opt" in
    c) SELECTED+=("$OPTARG") ;;
    r) REPORT=true ;;
    h) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
done

# 前置检查（环境变量支持双核心: ORZMC_TEST_PORT 默认 25565）
ORZMC_TEST_PORT="${ORZMC_TEST_PORT:-25565}"
if ! nc -z 127.0.0.1 "$ORZMC_TEST_PORT" 2>/dev/null; then
  echo "❌ 测试服未在线（127.0.0.1:${ORZMC_TEST_PORT}）——Paper 用 ORZMC_TEST_PORT=25566 等" >&2
  exit 1
fi
# 日志路径按端口推断（双核心适配：25565=Folia，25566=Paper，其余可 ORZMC_LOG_PATH 覆盖）
if [ -z "${ORZMC_LOG_PATH:-}" ]; then
  case "$ORZMC_TEST_PORT" in
    25565) ORZMC_LOG_PATH="$HOME/folia-test/logs/latest.log" ;;
    25566) ORZMC_LOG_PATH="$HOME/papermc-test/logs/latest.log" ;;
    *) ORZMC_LOG_PATH="" ;; # 未知端口：交给用例默认值
  esac
fi
export ORZMC_LOG_PATH
# RCON 端口按端口推断（双核心适配：25565→25575，25566→25576，可 ORZMC_RCON_PORT 覆盖）
if [ -z "${ORZMC_RCON_PORT:-}" ]; then
  case "$ORZMC_TEST_PORT" in
    25565) ORZMC_RCON_PORT=25575 ;;
    25566) ORZMC_RCON_PORT=25576 ;;
    *) ORZMC_RCON_PORT="" ;;
  esac
fi
export ORZMC_RCON_PORT
if [ ! -d "$NODE_PATH" ]; then
  echo "❌ 缺少 mineflayer 依赖: $NODE_PATH（请确认 ~/minecraft-bot/node_modules 存在）" >&2
  exit 1
fi

# 模板一致性检查（防配置漂移：群消息模板与仓库不同步 → 消息格式回归，2026-08-19 实测踩坑）
TEMPLATE_REPO="$E2E_DIR/../src/main/resources/templates.yml"
if [ -f "$TEMPLATE_REPO" ]; then
  TEMPLATE_SERVER=""
  case "$ORZMC_TEST_PORT" in
    25565) TEMPLATE_SERVER="$HOME/folia-test/plugins/OrzMC/templates.yml" ;;
    25566) TEMPLATE_SERVER="$HOME/papermc-test/plugins/OrzMC/templates.yml" ;;
  esac
  if [ -n "$TEMPLATE_SERVER" ]; then
    if [ ! -f "$TEMPLATE_SERVER" ]; then
      echo "⚠️ 测试服模板缺失: $TEMPLATE_SERVER（插件启动时将从 jar 提取默认，通常与仓库一致）" >&2
    elif ! diff -q "$TEMPLATE_REPO" "$TEMPLATE_SERVER" >/dev/null 2>&1; then
      echo "❌ 模板配置漂移：测试服 templates.yml 与仓库不一致！" >&2
      echo "   仓库: $TEMPLATE_REPO" >&2
      echo "   测试服: $TEMPLATE_SERVER" >&2
      echo "   差异预览:" >&2
      diff "$TEMPLATE_REPO" "$TEMPLATE_SERVER" | head -15 >&2
      echo "   修复: cp $TEMPLATE_REPO $TEMPLATE_SERVER && RCON '/config reload'" >&2
      echo "   临时跳过: ORZMC_SKIP_TEMPLATE_CHECK=1" >&2
      if [ -z "${ORZMC_SKIP_TEMPLATE_CHECK:-}" ]; then
        exit 1
      fi
    else
      echo "✅ 模板一致性检查通过（templates.yml 与仓库一致）"
    fi
  fi
fi

# 选择用例（macOS bash 3.2 无 mapfile，用 while read）
CASES=()
while IFS= read -r c; do CASES+=("$c"); done < <(ls "$CASES_DIR"/[0-9]*.js 2>/dev/null | sort)
if [ "${#SELECTED[@]}" -gt 0 ]; then
  FILTERED=()
  for c in "${CASES[@]}"; do
    base="$(basename "$c")"
    for sel in "${SELECTED[@]}"; do
      if [[ "$base" == "$sel"* ]]; then FILTERED+=("$c"); break; fi
    done
  done
  CASES=("${FILTERED[@]}")
fi

if [ "${#CASES[@]}" -eq 0 ]; then
  echo "❌ 没有匹配的用例" >&2
  exit 1
fi

echo "===== OrzMC E2E 测试套件 ====="
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')  用例: ${#CASES[@]} 个"
echo "------------------------------------------"

TOTAL_PASS=0; TOTAL_FAIL=0; TOTAL_KNOWN=0
REPORT_BODY=""

for c in "${CASES[@]}"; do
  base="$(basename "$c")"
  echo ""
  echo "▶ 运行: $base"
  OUTPUT="$(NODE_PATH="$NODE_PATH" node "$c" 2>&1)"
  EXIT_CODE=$?
  echo "$OUTPUT"
  REPORT_BODY+="
### ${base}（exit=${EXIT_CODE}）

\`\`\`
${OUTPUT}
\`\`\`
"
  # 统计（从输出解析「通过 X/Y（KNOWN-BUG Z 项）」）
  PASS_N=$(echo "$OUTPUT" | grep -oE "通过 [0-9]+/[0-9]+" | tail -1 | grep -oE "[0-9]+" | head -1)
  TOT_N=$(echo "$OUTPUT" | grep -oE "通过 [0-9]+/[0-9]+" | tail -1 | grep -oE "[0-9]+" | tail -1)
  KNOWN_N=$(echo "$OUTPUT" | grep -oE "KNOWN-BUG [0-9]+" | tail -1 | grep -oE "[0-9]+" || echo 0)
  TOTAL_PASS=$((TOTAL_PASS + ${PASS_N:-0}))
  TOTAL_FAIL=$((TOTAL_FAIL + ${TOT_N:-0} - ${PASS_N:-0}))
  TOTAL_KNOWN=$((TOTAL_KNOWN + ${KNOWN_N:-0}))
done

echo ""
echo "=========================================="
echo "E2E 汇总: 通过 $TOTAL_PASS / 总计 $((TOTAL_PASS + TOTAL_FAIL))（KNOWN-BUG $TOTAL_KNOWN 项）"
echo "=========================================="

if [ "$REPORT" = true ]; then
  mkdir -p "$REPORT_DIR"
  {
    echo "# OrzMC E2E 测试报告"
    echo ""
    echo "- **时间**: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "- **结果**: 通过 $TOTAL_PASS / $((TOTAL_PASS + TOTAL_FAIL))（KNOWN-BUG ${TOTAL_KNOWN}）"
    echo "$REPORT_BODY"
  } > "$REPORT_FILE"
  echo "报告已保存: $REPORT_FILE"
fi

[ "$TOTAL_FAIL" -gt 0 ] && exit 1 || exit 0
