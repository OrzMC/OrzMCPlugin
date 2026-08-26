# Folia 与 LuckPerms 集成红线（实战案例）

> 2026-08-19 测试服（Folia 26.2-4）真机验证沉淀。AI 迭代前必读——本文件的每条红线都对应一次真实的事故或调试长跑。
> 短版索引见 `plugin/CLAUDE.md`「开发红线」。

## 1. 服务器调度线程绝不能同步等待 LuckPerms 异步 future（最高优先级）

### 现象（两阶段）

| 阶段 | 症状 | 根因 |
|:--|:--|:--|
| 修复前 | `/review approve` → region 线程死锁 132s+，Watchdog 报 `chunk [29,-2] has not responded`，全服阻塞，只能强杀 | region 线程同步 `.get(3s)` 等 LP `loadUser` 异步加载；LP 回调需要调度回服务器线程 → 环 |
| 第一轮修复后 | 不再死锁，但 `runSync` 的 `done.get(3s)` 超时；**状态漂移**：promote 实际成功（SUCCESS）但申请保持 PENDING，下次 approve 会重复晋升越级 | promote 转 global 线程后，global 线程同步等 LP future——**回调排在自己后面**，3s 必超时；超时返回失败但 global 线程上的操作继续执行完 |

### 根因本质

**LuckPerms 的异步 future（`loadUser`/`saveUser`）完成回调调度回服务器同步调度线程执行**（LP 的 Folia/Paper 适配器行为）。因此在任何服务器调度线程（global / region）上 `.get()/.join()` 等待 LP future，等价于让回调排队在自己身后——永远等不到（自锁）。

### 红线

1. **任何服务器调度线程（global/region）不得同步等待 LP 异步 future**（`loadUser`/`saveUser`/`track.promote` 返回的 future 等）。
2. **授权处理（promote/demote）必须异步化**：`ReviewHandler` 返回 `CompletableFuture<Boolean>`（见 `features/review/ReviewHandler.java`），LP 操作在**自己管理的异步线程**（如 `Bukkit.getAsyncScheduler().runNow`）执行，审核框架异步等待结果后再落状态。
3. **状态一致性**：授权结果决定业务状态，二者必须原子一致。LP 已晋升 + 申请 PENDING = 漂移（危险：重复 approve 会把 member 再 promote 到 admin，越级）。
4. 读路径（查当前组/在线缓存）优先 `api().getUserManager().getUser(uuid)` 在线缓存（不阻塞、不调度）；仅离线加载才转异步。
5. 回调里需要更新业务状态时，调度回 global 线程执行（`ServerFacade.runSync`）。

### 正确模式（bf2f588 落地）

```
approve 命令（region 线程）
  └─ ReviewService.review → type.handler() 返回 CompletableFuture<Boolean>
       └─ LuckPermsPromoter.promoteAsync：异步线程执行
            ├─ loadUser（future 正常完成——服务器线程未被占用）
            ├─ normalizeSingleGroup + trk.promote（global 上下文）
            └─ saveUser（future 正常完成）
       └─ 结果回调（调度回 global）：落 APPROVED + 发 review_approved/rank_promoted
```

## 2. 线程调度工具

- `ServerFacade.runSync` = `Bukkit.getGlobalRegionScheduler().execute`（Paper 上即主线程，Folia 上即 global region 线程）。**不要用 removed 的 BukkitScheduler**。
- 嵌套 `runSync` 安全：已在同步线程（`Bukkit.isGlobalTickThread()`）时直接内联，不自调度。
- `done.join()` 必须带超时（`done.get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS)`），否则调度器停摆时调用线程永久挂起。
- `Bukkit.getOfflinePlayer(name)` 在 Folia 部分版本需在同步线程调用（`resolvePlayerId` 走 runSync）。

## 3. LuckPerms 集成坑

- **track 节点必须 global 上下文**：`ImmutableContextSet.empty()`。在线玩家（world/gamemode 上下文）与离线操作（global）混存 → track 节点重叠 → `AMBIGUOUS_CALL`（promote/demote 报歧义）。统一 global 后所有场景一致。
- **promote/demote 只改内存态**：必须显式 `saveUser` 落库，失败视为操作失败（不能报成功）。
- **操作前归一组**：`normalizeSingleGroup` 把用户多余 track 组节点归一，否则歧义。
- LP 命令经 RCON **无回显**（`lp user X parent set member` 之类），验证走日志或实际行为（`$p`/`/apply` 反馈）。
- 测试 mock：`LuckPermsPromoterTest` 已有完整 mock 范式（12+ 用例），新增逻辑先看它。

## 4. 群消息防刷屏（QQ 频控 40034100）

### 事故

未白名单玩家反复登录（恶意脚本）→ 每次触发一条 `whitelist_block` 群通知 → 48 次被拦 → 40+ 条消息 → **QQ 主动消息频控（40034100）被打爆**，后续所有群消息发送失败（飞书无此频控，正常）。

### 规则

1. **高频事件必须节流/聚合**：
   - 玩家上下线 → `PlayerEventAggregator` 3s 窗口聚合（`player_notify.window_ms`）
   - TNT 告警 → `notify_aggregate_ms: 3000`
   - 其余高频通知 → `ThrottledNotifier.shouldRun(key, periodMs)`（固定周期限频，窗口内丢弃）
2. **`ThrottledNotifier.shouldRun` 判定+更新必须原子**（`ConcurrentHashMap.compute`）：Folia 多 region 线程并发触发时，check-then-act 会在同窗口放行多条。
3. **限频 key 选择**：防「换马甲刷」场景用**全局 key**（per-player key 会放行每马甲一条）；副作用是窗口内其他真实玩家的通知被吞——玩家侧提示（如踢出消息）不受节流影响，可接受，注释里写明取舍。
4. 新增通知类型先评估触发频率：单事件直发 → 必须限频/聚合。

## 5. 审核/命令流程

- `/apply` 资格预检（`isEligible`）在命令层返回「当前没有可申请的审核类型」：builder 申请要求当前组 member（default 组无资格）。测试前先 `lp user X parent set member`。
- 授权处理失败必须保持申请 PENDING 并提示（避免「已通过但未生效」）。
- **AuthMe 时序**：登录完成前玩家命令被**静默拦截**（无输出无通知）。自动化测试（mineflayer）必须等 spawn + `/login` 完成后再发命令。

## 6. 测试/验证方法论（测试服 Folia）

- 事件类消息用游戏内 bot 真实触发（`~/minecraft-bot/exec-cmds.js`：登录→命令→打印 chat 响应；`stay-for-kick.js`：驻留供 RCON kick）。
- **orzdebug 控制台命令只回显日志、不发群**（callback 写死 `logger.info`），无法用其模拟群用户命令。
- 投递记录查询：`~/.hermes/skills/gaming/orzmc/scripts/easybot_deliveries.py [N]`（QQ+飞书双平台实际渲染）。
- 高频登录测试：`login_rate_limit`（每 IP 20 次/分钟）会踢 bot——IPv4/IPv6（127.0.0.1 vs ::1）分流或临时关限流；bukkit.yml `connection-throttle: 4000` 4s 内重连被拒。
- 服务器重启流程：RCON stop → 失败则强杀 → `rm -f world/session.lock` → `screen -dmS folia ./start.sh`（测试服 screen 会话名 `folia`）。

## 7. 修复提交时间线（可 git 追溯）

| 提交 | 内容 |
|:--|:--|
| `f0fbe1b` | 死锁首修（LP 操作转 global 线程）+ 文案中文化 + whitelist_block 节流 |
| `8000f2f` | 审查修复：runSync 超时、读路径免 G 往返、节流原子化、$v l 中文化、删死代码 |
| `bf2f588` | 异步化重写：ReviewHandler 返回 CompletableFuture，杜绝自锁超时与状态漂移 |

**教训**：第一轮「转 global 线程」只是把死锁变成自锁超时+漂移——因为**根因不是「在哪个线程等」，而是「服务器线程根本不能等 LP future」**。修线程问题先问：这里能不能不等？
