# GeoIP：网络受限机房的推荐配置（fail-close 不变）

> 状态：现行 ｜ 最后更新：2026-11-03
> 适用：服务器出海访问第三方 GeoIP 服务不稳定（超时/被墙/DNS 污染），玩家被踢「地区解析服务暂时不可用」
> 关联：`docs/features.md` §5.1、`src/main/resources/config.yml` 的 `geoip:` 段、issue #538 / PR #543 #544

## 0. 先说结论：不要用 `fail_open` 换可用性

`geoip.fail_open: true` 的语义是「**地区验证不了就放进来**」——等于地区白名单在故障期整体失效，
**地区不明的玩家（包括本应被拦住的）都能进服**。这不是"网络调优开关"，只是调试/应急用的**临时**手段。

因此本项目的默认姿态是：

| 场景 | 处置 |
|---|---|
| 单家第三方抖动 | 多源兜底自动切换（无需操作，日志里出现 `兜底源 xxx` 即证明它在工作） |
| 本机房出海整体不稳 | **按本文改造出口可达性**（自建 / 换源 / 调超时），**保持 `fail_open: false`** |
| 极端情况仍解析失败 | 拒绝进入（**这是预期行为**，不要为了让人进服而关掉地区限制） |

## 1. 先确认到底是不是"网络不可达"

看日志（`latest.log`）：

```
[geoip] 1.2.3.4 解析失败（按 fail-close 拒绝）: countryis: HttpTimeoutException(request timed out); geojs: ConnectException(connection refused)
```

- 全是 `HttpTimeoutException` / `ConnectException` / `UnknownHostException` → **出口网络问题**（走第 2 节）
- 全是 `HTTP 429` → 被限流（换源或自建）
- 出现 `空国家码`（无异常，但国家码为空）→ 该 IP 段在第三方库里没有数据（保留段/特殊段），走第 4 节
- `countryis` 正常、`geojs` 失败但玩家没被踢 → 兜底在工作，**什么都不用做**

## 2. 首选：自建 `country.is`（零第三方、零费用、保持 fail-close）

`api.country.is` 开源，可 Docker 自托管（数据源 MaxMind GeoLite2，每日更新）：

```bash
# 任意能出海的机器（或本机 + 端口映射）
docker run -p 3000:3000 ghcr.io/... # 具体镜像/参数见 country.is 官网 "Self-hosting" 一节
```

然后让插件只打自己的实例（先把自建域名填在最前，第三方作为次选兜底）：

```yaml
geoip:
  fail_open: false                      # ← 不动，保持 fail-close
  providers: [countryis, geojs]         # countryis 指向自建时，用 hosts 或反代把 api.country.is 指到自建实例
  per_provider_timeout_ms: 1200
  failure_cache_seconds: 30
```

> 说明：`providers` 目前只接受内置源名（`countryis` / `geojs` / `ipwhois`），**不支持自定义 URL**。
> 若确需指向自建实例，标准做法是在服务器 `/etc/hosts`（或内网 DNS）把 `api.country.is` 解析到自建地址——
> 这样无需改插件、也无需放开 `fail_open`。若你希望支持「自定义 provider URL」配置项，可提 issue 单独立项。

## 3. 次选：调整源顺序/增删源（不改策略）

```yaml
geoip:
  fail_open: false
  # 机房到哪家通就把哪家放前面；ipwho.is 免费额度有限，需要时再加
  providers: [geojs, countryis, ipwhois]
  per_provider_timeout_ms: 2000   # 若网络"可达但慢"，可放宽到 2000（仍须 < 3000 决策窗）
```

- `per_provider_timeout_ms` 上限受登录决策窗约束：**必须 < 3000ms**，启动健康检查会校验；
  总预算 = 决策窗 3s − 400ms，超时留太久等于只试一家就耗尽预算。
- `failure_cache_seconds`（默认 30s）只影响"失败后多久重试"，与是否放行无关；抖动严重时可调到 10~15s。

## 4. 个别 IP 就是查不到国家码（不是网络问题）

这种情况下限制照旧生效（拒绝），且**不建议**用 `fail_open` 绕过。可选做法：

1. 先用 https://ipinfo.io 之类人工确认该 IP 真实归属，判断是"第三方库缺数据"还是"确实来自不允许的地区"；
2. 若确认是**可信的国内玩家且第三方缺数据**，当前版本没有"按 IP 豁免地区校验"的开关——
   只能等第三方数据更新，或提 issue 讨论新增 `geoip.bypass_ips`（显式、可审计，比全局 `fail_open` 安全得多）；
3. 若确认来自不允许的地区 → **按预期拒绝**，不要放宽。

## 5. 观测：怎么知道兜底在工作

成功兜底（主源失败、次源命中）会打 INFO：

```
[geoip] 1.2.3.4 → CN（主源失败，兜底源 geojs，180ms）
```

全失败会打 WARN 并带**逐条**原因（哪家超时、哪家 5xx、哪家空国家码），可直接据此决定第 2/3 节怎么调。
