#!/usr/bin/env bash
# QQ 单条文本上限实测探针（D2 / R7）
#
# 背景：QQ 官方**未公开**单条文本字符/字节上限，只给错误码 40054007「消息长度超限」；
#       社区实现按 UTF-8 字节保守截断（如 3KB）。本脚本用真实凭据在**测试群**上二分探测，
#       得到可固化的数字（ASCII 与中文各测一轮，确认平台按字节还是按字符计量）。
#
# 用法（在能直连 QQ 的机器上执行；推荐测试服/测试群，会真的往群里发若干条消息）：
#   APPID=xxx SECRET=yyy OPENID=zzz bash docs/dev/qq-text-limit-probe.sh
#   # OPENID = 群 openid（im_bindings.yml 里 sessions.qq.admin_group 的 group:<openid> 后半段）
#
# 输出：每个长度一行 `bytes=… ascii=… http=… code=…`；首个 40054007 即为上限所在档；
#       脚本结束时给出二分收敛区间建议。**不要把输出里的 access_token 贴到公开处**（本脚本不打印它）。
#
# 注意：
# - 主动消息有频控（官方：群 60/qpm 认证、30/qpm 未认证，单关系 20/qpm，1000 条/群/天）→ 默认每条间隔 2s；
# - 探针内容带 [probe] 前缀，便于事后在群里辨认；
# - 只读凭据与打印结果由你控制，脚本不写入任何文件。

set -euo pipefail

: "${APPID:?需要 APPID（im.yml platforms.qq.app_id）}"
: "${SECRET:?需要 SECRET（im.yml platforms.qq.client_secret）}"
: "${OPENID:?需要 OPENID（目标群 group_openid）}"

API=https://api.bot.qq.com
AUTH=$API/app/getAppAccessToken
SEND=$API/v2/groups/$OPENID/messages
SLEEP_SECS=${SLEEP_SECS:-2}

echo "== 1/3 换取 access_token =="
TOKEN=$(curl -sS -m 20 -X POST "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"appId\":\"$APPID\",\"clientSecret\":\"$SECRET\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("access_token",""))')
if [ -z "$TOKEN" ]; then echo "换取 token 失败（检查 appId/secret/网络）"; exit 1; fi
echo "token 已获取（长度 ${#TOKEN}，不打印内容）"

probe() { # $1=字节长度 $2=字符类型 ascii|zh
  local n=$1 kind=$2 body
  if [ "$kind" = "zh" ]; then
    # 中文 1 字 = 3 字节 → 字符数取 1/3
    local chars=$(( n / 3 ))
    body=$(python3 -c "print('中'*$chars)")
  else
    body=$(python3 -c "print('a'*$n)")
  fi
  local payload
  payload=$(python3 - "$body" <<'PY'
import json,sys
print(json.dumps({"content":"[probe]"+sys.argv[1],"msg_type":0}))
PY
)
  local resp
  resp=$(curl -sS -m 20 -o /tmp/qq_probe_body.json -w '%{http_code}' -X POST "$SEND" \
    -H "Authorization: QQBot $TOKEN" -H 'Content-Type: application/json' -d "$payload" || echo "000")
  local code
  code=$(python3 -c 'import json;print(json.load(open("/tmp/qq_probe_body.json")).get("code","-"))' 2>/dev/null || echo "-")
  printf 'bytes=%-6s kind=%-5s http=%-4s code=%s\n' "$n" "$kind" "$resp" "$code"
  sleep "$SLEEP_SECS"
  # 40054007 = 长度超限
  [ "$code" = "40054007" ]
}

echo "== 2/3 ASCII 探测（1 字节/字符）=="
hit=""
for n in 500 1000 1500 2000 2500 3000 3500 4000 5000 6000; do
  if probe "$n" ascii; then hit=$n; break; fi
done
if [ -z "$hit" ]; then echo "在 6000 字节以内未触发 40054007（上限更高，可扩大档位）"; else echo ">>> ASCII：首个超限档 = $hit 字节"; fi

echo "== 3/3 中文探测（3 字节/字符，验证计量口径）=="
hit_zh=""
for n in 3000 4500 6000; do
  if probe "$n" zh; then hit_zh=$n; break; fi
done
if [ -n "$hit_zh" ] && [ -n "$hit" ]; then
  if [ "$hit_zh" -lt "$hit" ]; then
    echo ">>> 结论：按【字符】计量（中文更早超限：$hit_zh 字节 ≈ $((hit_zh/3)) 字）"
  else
    echo ">>> 结论：按【字节】计量（ASCII 与中文超限位置接近：$hit vs $hit_zh 字节）"
  fi
elif [ -n "$hit" ]; then
  echo ">>> 中文档位未超限：更倾向按字节计量（中文 6000 字节未超而 ASCII $hit 已超需复核）"
fi

echo
echo "固化建议：把实测上限留 10% 余量写入 im.yml → platforms.qq.max_text_bytes（当前默认 3000），"
echo "并同步 docs/dev/im-gateway-inhouse.md 的 R7/I2 状态。"
