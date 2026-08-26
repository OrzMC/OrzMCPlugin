#!/usr/bin/env bash
# OrzMC 插件 E2E 测试套件一键入口
# 用法:
#   bash e2e/run-all.sh            # 全量（01-04 全部用例）
#   bash e2e/run-all.sh -c 01 -c 03  # 只跑指定用例（前缀匹配）
#   bash e2e/run-all.sh -h         # 帮助
# 环境要求:
#   - 测试服在线（~/papermc-test 或 ~/folia-test，端口统一 25565，RCON 25575/orztest2026）
#   - 核心自动检测（进程），可用 ORZMC_CORE=folia|paper 显式指定
#   - node + ~/minecraft-bot/node_modules（mineflayer）
set -uo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CASES_DIR="$E2E_DIR/cases"
if [ -z "${NODE_PATH:-}" ] && [ -d "$E2E_DIR/node_modules" ]; then
  NODE_PATH="$E2E_DIR/node_modules"
else
  NODE_PATH="${NODE_PATH:-$HOME/minecraft-bot/node_modules}"
fi
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
  06-permission-msg.js  权限/审核消息（申请发起/通过/晋升/拒绝/撤回，LP+op 自建）
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

# 前置检查（端口统一 25565/25575，核心自动检测）
ORZMC_TEST_PORT="${ORZMC_TEST_PORT:-25565}"
if ! nc -z 127.0.0.1 "$ORZMC_TEST_PORT" 2>/dev/null; then
  echo "❌ 测试服未在线（127.0.0.1:${ORZMC_TEST_PORT}）——先启动 ~/papermc-test 或 ~/folia-test" >&2
  exit 1
fi
# 核心检测：ORZMC_CORE 显式指定 > 进程检测（2026-08-20 端口统一后无法靠端口区分核心）
# ⚠️ 用 grep -c 而非 grep -q：set -o pipefail 下 grep -q 提前退出触发上游 SIGPIPE(141) → 误判失败
# ⚠️ 进程检测依赖命令行含 <core>-test/<core>.*jar 路径片段（标准启动脚本满足）；非标准路径启动或
#    双服同跑时可能误判（共享地图严禁同跑；同跑时固定优先 Folia）——一律可用 ORZMC_CORE 显式覆盖
detect_core() {
  if [ "$(ps aux | grep -v grep | grep -c '[f]olia-test/folia.*jar')" -gt 0 ]; then echo folia
  elif [ "$(ps aux | grep -v grep | grep -c '[p]apermc-test/paper.*jar')" -gt 0 ]; then echo paper
  else echo ""; fi
}
if [ -n "${ORZMC_CORE:-}" ]; then
  # 显式指定：统一小写（macOS bash 3.2 无 ${var,,} 展开）
  ORZMC_CORE="$(echo "$ORZMC_CORE" | tr '[:upper:]' '[:lower:]')"
else
  ORZMC_CORE="$(detect_core)"
fi
if [ -z "$ORZMC_CORE" ]; then
  echo "❌ 无法自动识别测试服核心（folia/paper），用 ORZMC_CORE=folia|paper 显式指定" >&2
  exit 1
fi
# 显式指定与实际运行核心不一致 → 警告（日志/备份将指向显式核心的测试服目录，便于快速定位）
AUTO_CORE="$(detect_core)"
if [ -n "$AUTO_CORE" ] && [ "$AUTO_CORE" != "$ORZMC_CORE" ]; then
  echo "⚠️ 显式 ORZMC_CORE=${ORZMC_CORE} 与实际运行核心 ${AUTO_CORE} 不一致（路径将指向 ${ORZMC_CORE} 测试服目录）" >&2
fi
echo "✅ 检测到测试服核心: ${ORZMC_CORE}（端口 ${ORZMC_TEST_PORT}）"
# 测试服目录映射（核心名 → 目录：folia→folia-test，paper→papermc-test，勿拼成 paper-test）
case "$ORZMC_CORE" in
  folia) TEST_DIR="$HOME/folia-test" ;;
  paper) TEST_DIR="$HOME/papermc-test" ;;
  *) TEST_DIR="" ;;
esac
if [ -z "$TEST_DIR" ] || [ ! -d "$TEST_DIR" ]; then
  echo "❌ 核心 ${ORZMC_CORE} 对应测试服目录不存在: ${TEST_DIR:-（非法核心名，仅支持 folia|paper）}（可用 ORZMC_CORE=folia|paper）" >&2
  exit 1
fi
# 日志路径按核心推断（可 ORZMC_LOG_PATH 覆盖）
if [ -z "${ORZMC_LOG_PATH:-}" ]; then
  ORZMC_LOG_PATH="$TEST_DIR/logs/latest.log"
fi
export ORZMC_LOG_PATH
# RCON 端口统一 25575（可 ORZMC_RCON_PORT 覆盖）
ORZMC_RCON_PORT="${ORZMC_RCON_PORT:-25575}"
export ORZMC_RCON_PORT
# 备份目录按核心推断（可 ORZMC_BACKUP_DIR 覆盖；04-maintenance 落盘断言仅 ORZMC_ASSERT_COMPLETE=1 时执行）
if [ -z "${ORZMC_BACKUP_DIR:-}" ]; then
  ORZMC_BACKUP_DIR="$TEST_DIR/backup"
fi
export ORZMC_BACKUP_DIR
if [ ! -d "$NODE_PATH" ]; then
  echo "❌ 缺少 mineflayer 依赖: ${NODE_PATH}（请确认 ~/minecraft-bot/node_modules 存在）" >&2
  exit 1
fi

# 模板一致性检查（防配置漂移：群消息模板与仓库不同步 → 消息格式回归，2026-08-19 实测踩坑）
TEMPLATE_REPO="$E2E_DIR/../src/main/resources/templates.yml"
if [ -f "$TEMPLATE_REPO" ]; then
  TEMPLATE_SERVER="$TEST_DIR/plugins/OrzMC/templates.yml"
  # TEST_DIR 已校验非空，TEMPLATE_SERVER 恒非空（无死代码分支）
  if [ ! -f "$TEMPLATE_SERVER" ]; then
      echo "⚠️ 测试服模板缺失: ${TEMPLATE_SERVER}（插件启动时将从 jar 提取默认，通常与仓库一致）" >&2
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
  CASES=("${FILTERED[@]+"${FILTERED[@]}"}")
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
