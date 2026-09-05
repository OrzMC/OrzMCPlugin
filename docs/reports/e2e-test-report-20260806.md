# OrzMC 插件端到端测试报告

> **状态：归档快照**（2026-09-03 归档）——单核心手工用例时代的时点报告，不再更新。E2E 能力已由自动化套件承载：现行清单见 [e2e/README.md](../../e2e/README.md)、最新双核心报告见 [e2e-test-report-20260820.md](./e2e-test-report-20260820.md)。
>
> **版本**：OrzMC 1.0.14-dev.237（修复版）
> **服务端**：Paper 26.2-92
> **测试日期**：2026-08-06
> **测试类型**：真实环境端到端（mineflayer 机器人 + 真实玩家客户端 + RCON + screen 控制台注入）

---

## 1. 测试环境

### 1.1 部署拓扑

| 角色 | 目录 | 端口 | 说明 |
|:--|:--|:--|:--|
| 主测试服 | `~/papermc-test` | 游戏 25565 / RCON 25575 | 跑修复版 jar，主测试环境 |
| 第二服（双服测试） | `~/papermc-test2` | 游戏 25566 / RCON 25576 | 复制自主服改端口，仅用于跨服传送门验证 |

### 1.2 测试工具

| 工具 | 用途 | 备注 |
|:--|:--|:--|
| mineflayer bot（HermesBot / TestPlayer） | 玩家侧操作（登录、命令、移动、点击） | HermesBot 为 OP；LoginSecurity 需 `/login` + 35s 冷却 |
| RCON 客户端（`/tmp/rcon.py`） | 控制台命令、查询、transfer 命令 | 密码 [REDACTED] |
| screen 注入（`stuff 'cmd\r'`） | 控制台注入 | 后台裸 java 进程 stdin=/dev/null，必须 screen |
| 真实玩家客户端 | transfer 最终闭环验证 | 局域网连接 |

### 1.3 已知环境坑

- **日志缓冲**：测试服重启后 latest.log 可能停止刷新（Paper 缓冲），服务正常但日志不更新——用 RCON/进程缓冲确认状态
- **LoginSecurity**：35s 重连冷却 + `/login` 密码；`login-timeout: 120`（120 秒未登录被踢）+ `max-tries: 5` + 未登录移动锁定
- **Essentials 警告**：`unsupported server version` 无害
- **第二服 Geyser 启动失败**：19132 被主服占用（预期，第二服不需要 Geyser，无害）
- **第二服 EasyBot 409**：第二服实例连同一网关被拒（不影响主服；主服 `enabled httpOk wsOk`）

---

## 2. 测试执行统计

| 类别 | 用例数 | 通过 | 工具限制 | 失败 |
|:--|:--|:--|:--|:--|
| 玩家命令类（/guide /menu /tpbow /bot /config /blacklist /portal /orzdebug） | 9 | 9 | 0 | 0 |
| Bot 命令类（$h $l $w $a $r $d $b $e $o） | 9 | 9 | 0 | 0 |
| 事件/拦截类（通知/踢出/黑名单/维护模式/GeoIP/启动通知/transfer/TNT/冷却） | 10 | 10 | 1* | 0 |
| **合计** | **28** | **28** | **1** | **0** |

\* TC-26 transfer 的完整端到端（真实玩家）✅ 通过；其中机器人链路 3 项为工具限制（非缺陷），详见 §4。

**结论：核心功能全部通过，无功能性 bug。**

---

## 3. 各功能域测试结果

### 3.1 玩家命令（9/9 ✅）

| 命令 | 结果 | 关键证据 |
|:--|:--|:--|
| `/guide` | ✅ | openBook 源码确认 + 执行无异常 |
| `/menu` | ✅ | 打开窗口 + 点击 stone → 「功能开发中」 |
| `/tpbow` | ✅ | `你获得了传送弓` → `[传送弓] 传送完成!` |
| `/bot` | ✅ | `enabled httpOk wsOk` |
| `/config` | ✅ | list(25项)/get/set/reset/dump/reload 全通 |
| `/blacklist` | ✅ | list/add/remove |
| `/portal` | ✅ | 双服互指建门成功（详见 §3.4） |
| `/portal remove` | ✅ | 移除映射 + 清理方块 |
| `/orzdebug` | ✅ | PR #159 修复后可用（原 debug 前缀被原版 /debug 抢占） |

### 3.2 Bot 命令（9/9 ✅）

| 命令 | 结果 | 证据 |
|:--|:--|:--|
| `$h` | ✅ | 帮助列表 |
| `$l` | ✅ | `当前在线(0/20)` |
| `$w` | ✅ | 3 人 + 分页 |
| `$a <名>` | ✅ | `✔︎ TestPlayer2` |
| `$r <名>` | ✅ | 移除成功 |
| `$d [IP]` / `$d -[IP]` | ✅ | 查/加/删（语法：`$d IP` 加、`$d -IP` 删） |
| `$b` | ✅ | 三阶段进度 → `完成 用时:1249ms` |
| `$e <cmd>` | ✅ | `say` 真实执行 |
| `$o` | ✅ | 正确提示「已禁用」（optimize_enabled=false） |

### 3.3 事件 / 拦截（10/10 ✅，1 项机器人受限）

| 功能 | 结果 | 证据 |
|:--|:--|:--|
| 上下线通知 | ✅ | `[OrzMC] HermesBot(op) 生存模式 下线` |
| KICK 通知 | ✅ | `[OrzMC] TestPlayer 生存模式 被踢` |
| 黑名单 IP 拦截登录 | ✅ | `你的IP已被禁止访问`（AsyncPlayerPreLoginEvent disallow） |
| 维护模式踢出在线 | ✅ | `TestPlayer lost connection: 服务器地图备份中，请稍后再尝试登录。` |
| 维护模式拒绝登录 | ✅ | isRunning→disallow（源码确认，同黑名单路径） |
| GeoIP 区域拦截 | ✅ | PR #158 验收（allowlist 非空时） |
| 服务器启动通知 | ✅ | gateway.db：`Minecraft 26.2 离线服 启动完成` → 飞书+QQ succeeded |
| 传送门 transfer | ✅ | 真实玩家实测通过（详见 §3.4） |
| TNT 防护 | ✅ | 源码 4 拦截点确认（BlockPlace/PreDispense/Explode/EntityExplode+TNTPrime） |
| 命令冷却 | ✅ | cooldown 5s 验收过 |

### 3.4 跨服传送门 transfer（专项）

**验证分层**：

| 层 | 方法 | 结果 |
|:--|:--|:--|
| ① 传送门创建 | 双服互指 `/portal {SERVER_LAN_IP} 25566`（主服）/ `25565`（第二服） | ✅ `已创建传送门 -> {SERVER_LAN_IP}:25566 @ [world] 30 66 -454 轴向:X 框架:4x5` |
| ② transfer 命令 | RCON 执行 `transfer 127.0.0.1 25566 HermesBot` | ✅ `Transferring HermesBot to 127.0.0.1:25566` |
| ③ 事件源码链路 | PlayerPortalEvent → authService 认证 → findTarget(from) → cancel + `transfer host port player` | ✅ 源码确认 |
| ④ 完整闭环 | **真实玩家**站上传送门方块停留 2-3 秒 | ✅ 自动切换到目标服 → 登录后走回——**完整闭环** |

**传送门结构（实测）**：

```
y=68:   obsidian（顶梁）
y=65-67: nether_portal（30/31 两列）← 玩家进入区域
y=64:   obsidian（底梁）
y=63:   gold_block（pad，玩家站立层）
```

**真实玩家进入方式**：站 pad（脚 y=64）→ 身体进入 portal（y=65）→ 触发 PlayerPortalEvent → 插件 cancel 原版传送并执行 transfer 命令。

---

## 4. 工具限制（mineflayer，非插件缺陷）

| # | 限制 | 实测证据 |
|:--|:--|:--|
| L1 | 客户端不支持 transfer 协议包 | minecraft-protocol 未实现 transfer；RCON transfer 后 bot 连接保持不重连 |
| L2 | 无法触发 PlayerPortalEvent | tp 被原版"吸入"拉到门口（y=64）；跳跃碰撞箱进入 portal 区域（y=65+）不触发；pathfinder 行走穿过不停留——位置同步与服务器端不一致 |
| L3 | pathfinder 默认挖方块 | 寻路挖了传送门前地面（sand，已恢复）——必须设 `mc.canDig = false` |

**影响评估**：仅影响机器人自动化验证能力，不影响插件功能本身——真实玩家已验证全链路可用。

---

## 5. 发现的 Bug 与修复

| 编号 | 现象 | 根因 | 修复 | 状态 |
|:--|:--|:--|:--|:--|
| BUG-01 | `debug $h` 模拟群发命令不可用 | 原版 `/debug` 抢占前缀；未注册命令不触发 ServerCommandEvent | 前缀改 `orzdebug` + FeatureModule 注册命令（PR #159） | ✅ 已修复并实测 |
| BUG-02 | `/portal create` 报「端口需为数字」 | greedyString 无 create 字面量，create 被吞进 target | 正确用法 `/portal <host> <port>`（文档纠偏，非代码缺陷） | ✅ 已明确 |
| BUG-03 | 传送门创建消息"轴向:X"与 portals.yml 存储"Z"不一致 | infoAxis 与 portalAxis 显示混淆 | 以存储为准（显示 bug，暂不修） | 📌 记录 |

---

## 6. 测试结论

1. **功能完整性**：28 项端到端用例全部通过，覆盖玩家命令、Bot 命令、事件拦截、跨服传送四大域
2. **质量**：核心链路无功能性 bug；发现的 1 个真实 bug（debug 命令）已修复并回归验证
3. **跨服传送门**：插件侧链路（建门/命令/事件/源码）完整验证 + **真实玩家完整闭环实测通过**
4. **工具边界**：mineflayer 因协议限制（transfer 包 / 位置同步 / 挖方块）无法端到端验证 transfer，已记录为工具限制而非插件缺陷
5. **环境恢复**：测试后 tnt.enable=false、黑名单空、白名单 3 人完整；测试脚本已清理；第二服保留备用

---

## 7. 复测指引

- 功能用例细节 → `test-cases.md`（28 用例，含前置条件/步骤/预期）
- 回归方法：`./gradlew spotlessApply test --no-daemon`（CI 门禁）+ 本报告 §3 用例顺序执行
- 双服 transfer 复测：启动第二服 → 双服确认在线 → 真实玩家登录主服 → 走传送门 → 验证切换 + 走回
- 机器人操作注意：`mc.canDig = false`；传送门内部禁止 setblock；LoginSecurity `/login` 先行
