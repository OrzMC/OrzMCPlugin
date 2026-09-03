#!/usr/bin/env bash
# OrzMC 插件 E2E 测试套件一键入口
# 用法:
#   bash e2e/run-all.sh            # 全量（01-04 全部用例）
#   bash e2e/run-all.sh -c 01 -c 03  # 只跑指定用例（前缀匹配）
#   bash e2e/run-all.sh -h         # 帮助
# 环境要求（2026-09-03 迁 MCSM 后：测试服 = MCSM 实例，路径/凭据全部环境注入，仓库零本机路径假设）:
#   - 测试服在线（MCSM 实例映射 127.0.0.1:${ORZMC_TEST_PORT:-25565}）
#   - ORZMC_CORE=paper|folia（必填显式——Docker 实例 java 进程在容器内，宿主 ps 不可见，无法自动检测）
#   - ORZMC_TEST_DIR=<测试服根目录>（必填——MCSM 实例 = /Users/Shared/orzmc/mcsmanager/daemon/data/InstanceData/<uuid>）
#   - ORZMC_RCON_MODE=http（默认）+ ORZMC_CONSOLE_URL/ORZMC_API_KEY（MCSM 面板 console API）或 =rcon（原生协议）
#   - ORZMC_LOG_PATH / ORZMC_BACKUP_DIR 由入口注入（wrapper: 技能 scripts/e2e-mcsm-wrapper.sh）
#   - node_modules（仓库内 npm install；无外部依赖）
# ⚠️ 本机 MCSM 实例对接 = 技能 wrapper `scripts/e2e-mcsm-wrapper.sh [paper|folia] [run-all 参数]`
#    （查实例状态 + 注入全部环境变量 + 调本脚本），仓库侧不感知 MCSM 布局
set -uo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CASES_DIR="$E2E_DIR/cases"
if [ -d "$E2E_DIR/node_modules" ]; then
  NODE_PATH="$E2E_DIR/node_modules"
else
  echo "❌ 缺少依赖: $E2E_DIR/node_modules（请先执行 cd e2e && npm install）——套件依赖在仓库内（package.json/lock），不引用仓库外目录" >&2
  exit 1
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

环境变量（入口注入，wrapper 见 orzmc 技能 scripts/e2e-mcsm-wrapper.sh）:
  ORZMC_CORE=paper|folia          测试服核心（必填，显式指定）
  ORZMC_TEST_DIR=<目录>           测试服根目录（必填，MCSM 实例 InstanceData/<uuid>）
  ORZMC_TEST_PORT=25565           测试服端口（默认 25565）
  ORZMC_RCON_MODE=http|rcon       控制台模式（默认 http=MCSM API）
  ORZMC_CONSOLE_URL / ORZMC_API_KEY   http 模式的 console API 端点与 key
  ORZMC_LOG_PATH / ORZMC_BACKUP_DIR  日志与备份目录（可省略则由 TEST_DIR 推断）
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

# 前置检查
ORZMC_TEST_PORT="${ORZMC_TEST_PORT:-25565}"
if ! nc -z 127.0.0.1 "$ORZMC_TEST_PORT" 2>/dev/null; then
  echo "❌ 测试服未在线（127.0.0.1:${ORZMC_TEST_PORT}）——先经面板启动 MCSM 实例（或 wrapper 会提示）" >&2
  exit 1
fi
# 核心：显式必填（2026-09-03 起无自动检测——Docker 实例 java 在容器内，宿主 ps 不可见）
if [ -z "${ORZMC_CORE:-}" ]; then
  echo "❌ 未指定测试服核心：export ORZMC_CORE=paper|folia（或直接用技能 wrapper scripts/e2e-mcsm-wrapper.sh）" >&2
  exit 1
fi
ORZMC_CORE="$(echo "$ORZMC_CORE" | tr '[:upper:]' '[:lower:]')"
case "$ORZMC_CORE" in
  folia|paper) ;;
  *) echo "❌ 非法 ORZMC_CORE=${ORZMC_CORE}（仅支持 folia|paper）" >&2; exit 1 ;;
esac
# 测试服目录：显式必填（MCSM 实例 = InstanceData/<uuid>，宿主侧目录）
TEST_DIR="${ORZMC_TEST_DIR:-}"
if [ -z "$TEST_DIR" ] || [ ! -d "$TEST_DIR" ]; then
  echo "❌ 未指定/不存在测试服目录 ORZMC_TEST_DIR=${TEST_DIR:-（空）}——MCSM 实例为 /Users/Shared/orzmc/mcsmanager/daemon/data/InstanceData/<uuid>（wrapper 自动注入）" >&2
  exit 1
fi
echo "✅ 测试服: ${ORZMC_CORE}（端口 ${ORZMC_TEST_PORT}）目录: ${TEST_DIR}"
# 日志路径：优先环境注入，否则按测试服目录推断（可 ORZMC_LOG_PATH 覆盖）
if [ -z "${ORZMC_LOG_PATH:-}" ]; then
  ORZMC_LOG_PATH="$TEST_DIR/logs/latest.log"
fi
export ORZMC_LOG_PATH
# RCON 模式（默认 http = MCSM console API；rcon 模式需 ORZMC_RCON_PASS）
export ORZMC_RCON_MODE="${ORZMC_RCON_MODE:-http}"
export ORZMC_CONSOLE_URL ORZMC_API_KEY
if [ "$ORZMC_RCON_MODE" = "http" ] && [ -z "${ORZMC_CONSOLE_URL:-}" ]; then
  echo "❌ http 控制台模式缺少 ORZMC_CONSOLE_URL（由技能 wrapper e2e-mcsm-wrapper.sh 注入 MCSM console API 地址）" >&2
  exit 1
fi
if [ "$ORZMC_RCON_MODE" = "rcon" ] && [ -z "${ORZMC_RCON_PASS:-}" ]; then
  echo "❌ rcon 模式缺少 ORZMC_RCON_PASS（原生 RCON 密码；MCSM 实例场景请用默认 http 模式）" >&2
  exit 1
fi
# 备份目录：优先环境注入，否则按测试服目录推断（04-maintenance 落盘断言仅 ORZMC_ASSERT_COMPLETE=1 时执行）
if [ -z "${ORZMC_BACKUP_DIR:-}" ]; then
  ORZMC_BACKUP_DIR="$TEST_DIR/backup"
fi
export ORZMC_BACKUP_DIR
if [ ! -d "$NODE_PATH" ]; then
  echo "❌ 缺少 mineflayer 依赖: ${NODE_PATH}（cd e2e && npm install）" >&2
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
      echo "   修复: cp $TEMPLATE_REPO $TEMPLATE_SERVER && 控制台 '/config reload'" >&2
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
