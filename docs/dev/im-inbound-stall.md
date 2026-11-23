# 内置 IM 长连接「入站断流」排查手册（issue #616）

> 状态：现行 ｜ 最后更新：2026-11-23
>
> 相关：issue **#616**（现象与实测证据）、#610/#612（静默看门狗判活口径与窗口配置）、
> `im.yml → platforms.<id>.proxy`、`docs/manuals/bot-qq.md`。
>
> 一句话：**出站正常、入站一帧不来的长连接**，在应用层看就是「静默」。TCP 可能还是 ESTABLISHED，
> 平台侧也不会重投（长连接没有 ACK）——所以表现为「机器人偶发不理人」。

## 1. 现象与判读口径

典型日志（1.0.31-dev.437 起，`remote` 与「同址零入站」计数已进日志与 `/config im status`）：

```text
[qq] 心跳静默超时，静默 123748ms（窗口 92811ms，本轮已发心跳 4 帧，累计入站 1 帧，远端 1.2.3.4:443），强制断开触发重连
[qq] 连续 3 次在远端 1.2.3.4:443 上「连上但零入站」后静默断流：多为本机出网路径（NAT / HTTP 代理 / 中间设备）对该长连接端点不友好…
[feishu] 建连超时（15000ms 内未完成 TCP/TLS/WS 握手，对端或中间设备不回包），按失败退避重连
```

**两个计数一对就能定性**：

| 观测 | 结论 |
|---|---|
| 已发心跳 > 0、入站 = 0、TCP 仍 ESTABLISHED | **入站断流**：出站通、对端或链路不回（本 issue 的场景） |
| 入站 > 0 但静默时长已超窗 | 本地定时任务被长任务拖住（调度问题，不是网络） |
| 建连阶段就超时（`建连超时 … 握手`） | TLS/HTTP Upgrade 被吃：中途设备/代理拦截，或命中黑洞 A 记录（#617 看门狗会退避重连，不再永久 `CONNECTING`） |

现场取数（不必翻日志）：

```text
/config im status
  --- 网关诊断（issue #616：入站断流排查用）---
  qq: OPEN | 远端 1.2.3.4:443 | 入站 0 帧 / 已发心跳 6 帧 | 静默 271s | 本段静默重连 2 次 | 同址零入站 2 次 | 重连尝试 1 次
```

## 2. 插件已经做了什么（1.0.31-dev.437 之后的代码侧）

| 行为 | 说明 |
|---|---|
| 建连看门狗（#617） | `connect()` 非阻塞、库 timeout 只管 `socket.connect()`；握手被黑洞时看门狗 15s 判失败并退避，不再永久 `CONNECTING` |
| 判活口径（#612） | WS 控制帧 ping/pong 计入入站活性；窗口可配 `platforms.<id>.heartbeat_timeout_seconds` |
| 远端地址诊断（#616） | 连接记录 `host:port`，静默告警与 `/config im status` 一起给出——**同一地址反复零入站**才指向出网路径 |
| 同址零入站升级（#616） | 连续 3 次「同一远端 + 零入站」→ 额外 WARN（带本文链接）+ 抬高重连退避（不再 5s 一轮反复重鉴权） |
| 稳定 ≠ 健康（#616） | 连接稳定但**从未收到入站帧**时，不再清零静默计数（否则每次都能「重置」掉可疑模式） |

**没做的：`SO_KEEPALIVE`。** Java-WebSocket 1.6.0 的 `setSocketFactory()` 一旦设置，
`prepareSocket()` 返回的「需要 TLS 升级」标志为 false，`wss` 会**跳过 TLS 包装**；而 `setSocket()`
（自己连好再交给库）同样跳过 TLS 包装，且需要自己处理 SNI/主机名校验。要在 wss 上设置 socket 选项，
必须自建 TCP → 手动 TLS（SNI）→ 再交给库，属高风险改动且本地无法端到端验证（需要真实平台凭证 + 真实出网路径）。
**待维护者决定**是否值得为「更早暴露半开连接」换这条路径；当前用应用层心跳窗口（默认 3× 心跳周期）做判活。

## 3. 宿主侧三个实验（需要真机，按顺序做）

判据统一为：**做实验前后 `/config im status` 的「同址零入站」次数是否归零**（或日志里静默 WARN 是否消失）。

### 实验 A：换出网路径（最省事）

```yaml
# im.yml
platforms:
  qq:
    proxy: { host: 172.17.0.1, port: 7890, type: SOCKS }   # 容器里 127.0.0.1 是容器自己，要用宿主可达地址
```

`/config reload im` 后观察 30 分钟。代理稳定则静默消失 → 定论：宿主 Clash/TUN 的透明代理对长连接不友好。

### 实验 B：绕开 docker NAT

把测试实例的容器改成 `network: host`（MCSManager 实例设置 → 网络模式），重启实例后同样观察 30 分钟。
与实验 A 对比可区分「NAT 层」与「代理层」。

### 实验 C：同网络参考客户端对照

关键不是「用什么语言写探针」，而是**让另一个客户端走插件那条出网路径**。仓库里备了三种运行时，
按容器里现成的挑（容器通常没有 pip/npm 可用，Node/JDK 版按此设计）：

```bash
# ① Python（有 python3 的环境）
docker cp scripts/dev/im-longlink-probe.py <容器>:/tmp/probe.py
docker exec -it <容器> python3 /tmp/probe.py --url wss://msg-frontier.feishu.cn/ws/v2 --seconds 180

# ② Node（Node 22+ 自带全局 WebSocket，无需 npm；如 MCSManager 面板镜像）
docker run --rm --network orzmc_default \
  -v "$PWD/scripts/dev/im-longlink-probe.mjs:/probe.mjs:ro" \
  githubyumao/mcsmanager-web:latest node /probe.mjs wss://api.sgroup.qq.com/websocket 60

# ③ JDK（Minecraft 实例容器里通常只有 JRE；能发 ping 控制帧，最接近插件行为）
javac -d /tmp/probe scripts/dev/ImLonglinkProbe.java && cp -r /tmp/probe ./.probe-classes
docker run --rm --network orzmc_default -v "$PWD/.probe-classes:/p:ro" eclipse-temurin:25-jre \
  java -cp /p ImLonglinkProbe wss://api.sgroup.qq.com/websocket 60 10
```

判读与局限（2026-11-23 实测）：

- 参考客户端也收不到入站 → 与插件无关，是路径问题；
- 参考客户端正常而插件静默 → 回到插件侧（帧构造/读线程），把两份日志贴到 #616；
- **端点局限**：`wss://msg-frontier.feishu.cn/ws/v2` 不接受「不带业务握手」的裸连接（握手即被拒）；
  QQ 端点若不发 identify，约 2 分钟后服务端会以 `4009 Session timed out` 主动关闭。
  所以参考客户端只能证明「**短窗口内该路径可用**」，**不能**替代下面的 A/B 实验给长期抖动定性。

> Telegram 走长轮询（无长连接），不在本手册范围。

## 4. 上报 #616 时需要附的信息

1. `/config im status` 的网关诊断整段（含远端地址与同址零入站次数）；
2. 静默 WARN 行（含 `remote` 与计数）；
3. 三个实验各自的结果（跑了哪个、前后计数变化）；
4. 环境：宿主系统、是否代理（Clash TUN/系统代理）、容器网络模式、平台端点。
