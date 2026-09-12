#!/usr/bin/env bash
# QQ 单条文本上限实测探针（D2 / R7）
#
# 背景：QQ 官方**未公开**单条文本字符/字节上限，只给错误码 40054007「消息长度超限」；
#       社区实现按 UTF-8 字节保守截断（如 3KB）。本脚本用真实凭据在**测试群**上按档位探测，
#       得到可固化的数字（ASCII 与中文各测一轮，确认平台按字节还是按字符计量）。
#
# 用法（在能直连 QQ 的机器上执行；推荐测试服/测试群，会真的往群里发若干条消息）：
#   1) 自动读插件配置（推荐，无需手填凭据）：
#        bash docs/dev/qq-text-limit-probe.sh --config-dir run/plugins/OrzMC
#      —— app_id/client_secret 取自 im.yml 的 platforms.qq；
#         target 取自 im_bindings.yml 的 sessions.qq.admin_group（缺失则 player_group）
#   2) 手工指定：
#        APPID=xx SECRET=yy OPENID=zz bash docs/dev/qq-text-limit-probe.sh
#
# 输出：每个长度一行 `bytes=… kind=… http=… code=…`；首个 40054007 即上限所在档。
# 注意：**不要把输出里的 access_token 贴到公开处**（本脚本不打印它，仅打印长度）。
#
# 安全/频控：
# - 主动消息有频控（官方 2026：群 60/qpm 认证、30/qpm 未认证，单关系 20/qpm，1000 条/群/天）→ 默认每条间隔 2s；
# - 探针内容带 [probe] 前缀，便于事后在群里辨认；
# - 脚本不落盘凭据、不打印 secret/token。

set -euo pipefail

CONFIG_DIR=""
TARGET=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --config-dir) CONFIG_DIR="${2:-}"; shift 2 ;;
    --target)     TARGET="${2:-}";     shift 2 ;;
    -h|--help)    sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

# ---------- 配置读取（indent-based 最小 YAML 取值，避免依赖 PyYAML） ----------
read_yaml() { # $1=file $2=key路径（如 platforms.qq.app_id）
  python3 - "$1" "$2" <<'PY'
import sys
path, keypath = sys.argv[1], sys.argv[2].split('.')
try:
    lines = open(path, encoding='utf-8').read().splitlines()
except OSError:
    sys.exit(0)
stack = []          # [(indent, key)]
for raw in lines:
    if not raw.strip() or raw.lstrip().startswith('#'):
        continue
    indent = len(raw) - len(raw.lstrip(' '))
    line = raw.strip()
    if ':' not in line:
        continue
    key, _, val = line.partition(':')
    key, val = key.strip(), val.strip().strip('\'"').strip()
    while stack and stack[-1][0] >= indent:
        stack.pop()
    stack.append((indent, key))
    if [k for _, k in stack] == keypath and val:
        print(val)
        sys.exit(0)
PY
}

if [[ -z "${APPID:-}" || -z "${SECRET:-}" ]]; then
  : "${CONFIG_DIR:=run/plugins/OrzMC}"
  IM_YML="$CONFIG_DIR/im.yml"
  [[ -f "$IM_YML" ]] || { echo "找不到 $IM_YML（或用 APPID/SECRET 环境变量指定）"; exit 1; }
  APPID="${APPID:-$(read_yaml "$IM_YML" platforms.qq.app_id)}"
  SECRET="${SECRET:-$(read_yaml "$IM_YML" platforms.qq.client_secret)}"
fi
if [[ -z "${OPENID:-}" ]]; then
  BIND_YML="${CONFIG_DIR:-run/plugins/OrzMC}/im_bindings.yml"
  TARGET="${TARGET:-$(read_yaml "$BIND_YML" sessions.qq.admin_group)}"
  [[ -n "$TARGET" ]] || TARGET="$(read_yaml "$BIND_YML" sessions.qq.player_group)"
  # 绑定值是 target 形态（group:<openid>）→ 取 openid
  OPENID="${TARGET#*:}"
fi

[[ -n "${APPID:-}" && -n "${SECRET:-}" ]] || { echo "缺少 app_id / client_secret（检查 im.yml platforms.qq 或环境变量）"; exit 1; }
[[ -n "${OPENID:-}" ]] || { echo "缺少目标群 openid（检查 im_bindings.yml sessions.qq.admin_group，或 OPENID=... 指定）"; exit 1; }

echo "配置确认：app_id 长度=${#APPID}、secret 长度=${#SECRET}、目标群 openid=（尾部）…${OPENID: -6}"
echo "（为安全起见不打印完整凭据/openid；探针将向该群发送若干条 [probe] 消息）"

API=https://api.bot.qq.com
AUTH=$API/app/getAppAccessToken
SEND=$API/v2/groups/$OPENID/messages
SLEEP_SECS=${SLEEP_SECS:-2}

echo "== 1/3 换取 access_token =="
TOKEN=$(curl -sS -m 20 -X POST "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"appId\":\"$APPID\",\"clientSecret\":\"$SECRET\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("access_token",""))' 2>/dev/null || true)
if [ -z "$TOKEN" ]; then echo "换取 token 失败（检查 app_id/secret/网络，或机器人状态）"; exit 1; fi
echo "token 已获取（长度 ${#TOKEN}，不打印内容）"

probe() { # $1=字节长度 $2=ascii|zh
  local n=$1 kind=$2 body payload resp code
  if [ "$kind" = "zh" ]; then
    body=$(python3 -c "print('[probe]'+'中'*($n//3))")
  else
    body=$(python3 -c "print('[probe]'+'a'*$n)")
  fi
  payload=$(python3 - "$body" <<'PY'
import json,sys
print(json.dumps({"content":sys.argv[1],"msg_type":0}))
PY
)
  resp=$(curl -sS -m 20 -o /tmp/qq_probe_body.json -w '%{http_code}' -X POST "$SEND" \
    -H "Authorization: QQBot $TOKEN" -H 'Content-Type: application/json' -d "$payload" || echo "000")
  code=$(python3 -c 'import json;print(json.load(open("/tmp/qq_probe_body.json")).get("code","-"))' 2>/dev/null || echo "-")
  printf 'bytes=%-6s kind=%-5s http=%-4s code=%s\n' "$n" "$kind" "$resp" "$code"
  sleep "$SLEEP_SECS"
  [ "$code" = "40054007" ] # 长度超限
}

echo "== 2/3 ASCII 探测（1 字节/字符）=="
hit=""
for n in 500 1000 1500 2000 2500 3000 3500 4000 5000 6000; do
  if probe "$n" ascii; then hit=$n; break; fi
done
if [ -z "$hit" ]; then echo "在 6000 字节内未触发 40054007（上限更高，可扩大档位或把档位改密）"; else echo ">>> ASCII：首个超限档 = $hit 字节"; fi

echo "== 3/3 中文探测（3 字节/字符，判定计量口径）=="
hit_zh=""
for n in 3000 4500 6000; do
  if probe "$n" zh; then hit_zh=$n; break; fi
done
if [ -n "$hit_zh" ] && [ -n "$hit" ]; then
  if [ "$hit_zh" -lt "$hit" ]; then
    echo ">>> 结论：按【字符】计量（中文更早超限：$hit_zh 字节 ≈ $((hit_zh/3)) 字）"
  else
    echo ">>> 结论：按【字节】计量（ASCII $hit 字节 vs 中文 $hit_zh 字节，位置接近）"
  fi
elif [ -n "$hit" ]; then
  echo ">>> 中文档位未超限：倾向按字节计量（请复核 ASCII 结果 $hit 字节）"
fi

echo
echo "固化建议：实测上限留 ~10% 余量写入 im.yml → platforms.qq.max_text_bytes（当前默认 3000），"
echo "并同步 docs/dev/im-gateway-inhouse.md 的 R7/I2 状态。"
