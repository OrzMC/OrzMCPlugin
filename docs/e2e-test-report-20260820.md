# OrzMC 插件双核心验收报告（2026-08-20）

> **范围**：OrzMC 1.0.19-dev（源码 `OrzMC/plugin` 最新 main e12ca5d 本地构建）双核心（Paper + Folia）真实环境验收
> **方式**：`plugin/e2e/` 自动化套件（run-all.sh，01-06 共 62 项断言）+ RCON + Mineflayer bot + 日志/落盘断言
> **结论**：**Paper 62/62 + Folia 62/62 全部通过**；发现 1 个 backup-core 缺陷（symlink 世界 $b 空备份，修复 PR 已提）与若干环境观察项

---

## 一、测试环境

| 项 | Paper | Folia |
|:--|:--|:--|
| 测试服目录 | `~/papermc-test` | `~/folia-test` |
| 核心 | paper-26.2-112 | folia-26.2-4 |
| 端口 / RCON | 25565 / 25575（两服统一） | 同左 |
| 登录插件 | LoginSecurity | SimpleLogin |
| 世界 | 共享物理地图（已裁剪：17G/317 万 → 2.1G/24.7 万 chunk），`folia-test/world` 为 symlink → `papermc-test/world` | 同左 |
| 插件版本 | **OrzMC-1.0.19-dev.jar**（sha256 94536e31…，backup-core 0.3.1） | 同左 |
| CI 门禁 | `./gradlew check`（spotless + 1317 单测 + MockBukkit 集成测试 + shadowJar）全绿 | 同左 |

> ⚠️ **两服共用世界、严禁同跑**（session.lock 互斥）：Paper 验收完成后停服 → 启 Folia → 验收 → 停 Folia → 恢复 Paper。全程无并发冲突。

## 二、验收结果总览

| 用例 | 覆盖功能 | Paper | Folia |
|:--|:--|:--:|:--:|
| 01-bot-cmds.js | $h/$l/$w/$a/$r/$d/$e Bot 命令全链路（写操作带还原） | 8/8 ✅ | 8/8 ✅ |
| 02-player-cmds.js | /guide /menu /bot /rank /apply /config 权限隔离、新手书自动发放 | 10/10 ✅ | 10/10 ✅ |
| 03-security.js | IP 黑名单登录拦截 / 聊天过滤（重复+链接）/ 命令守卫（$e seed 拦截） | 10/10 ✅ | 10/10 ✅ |
| 04-maintenance.js | $b 备份触发→阶段进度→zip 落盘→服务恢复 | 4/4 ✅ | 4/4 ✅（功能链路；symlink 空备份缺陷见 §四） |
| 05-groupmsg.js | 群消息：白名单拦截/上下线/双 bot 聚合摘要/IP 黑名单拦截（日志断言 + 占位符残留检查） | 11/11 ✅ | 11/11 ✅ |
| 06-permission-msg.js | 权限/审核消息：申请发起/通过/晋升（🎉建造者）/拒绝/撤回（LP 自建 + op 自建审核人） | 19/19 ✅ | 19/19 ✅ |
| **合计** | | **62/62** | **62/62** |

**补充说明**：
- 06 用例 Folia 侧关键坑（19/19 双绿已覆盖）：LP 设组须**先建后设**（先进服建用户→quit→parent+group set→重进）；登录节流 20s（login_rate_limit + SimpleLogin 冷却）；异常路径 try/finally quit 防 bot 残留触发 per-IP 限流
- Paper 05 首跑 whitelist_block 断言超时：根因为 04 触发备份的**收尾期**（维护模式拒绝登录），非插件缺陷；备份完成后重跑 11/11 ✅（O7）

## 三、待真实环境验收项核对（folia-acceptance.md）

| 项 | 内容 | 状态 | 说明 |
|:--|:--|:--|:--|
| TC-F1 | 白名单管理（$w/$a/$r + 非白名单踢出） | ✅ | 2026-08-18 真实环境通过；本次 E2E 01 用例再次覆盖（双核心 8/8） |
| TC-F2 | 备份/优化（$b/$o 链路） | 🟡 | $o 触发链路 2026-08-18 验证过（0 region 异常）；$b 功能链路本次 04 用例双核心通过；**完整全量备份受磁盘硬约束**（O5）+ symlink 空备份缺陷（BUG-E2E-004）已修 |
| TC-F3 | TNT 保护与爆炸通知 | ✅ | 2026-08-18 真实环境通过（TNTPrime 拦截 + ×N 聚合告警 + 区域白名单放行） |
| TC-F4 | 传送弓（/tpbow） | ✅ | 2026-08-18 真实环境通过（force-load 越权 bug 已修复复测 0 异常） |
| TC-F5 | 跨服传送门（/portal + transfer） | ✅ | 2026-08-18 真实环境通过（Folia PlayerPortalEvent 补偿方案，日志 `Transferring X to host:port` 决定性证据） |
| TC-F6 | 长稳运行（8h+ 无死锁） | ⬜ | 未完成：双核心切换验收累计运行 ~3h，期间 0 次 region 线程异常/死锁（失败判据未命中）；完整 8h 长稳建议在 Folia 恢复常驻后安排专项窗口 |

> 失败判据（`IllegalThreadStateException` / `not the correct region` / 死锁）全程 0 命中。

## 四、发现的问题

### BUG-E2E-004：symlink 世界 $b 备份空跑假完成（backup-core，P1，修复 PR 已提）

| 项 | 内容 |
|:--|:--|
| 现象 | Folia（world 为 symlink）`$b` 431ms "完成"、进度 2/2、zip 22 字节空包，无任何报错 |
| 根因 | backup-core `RealFileSystem.walk` 用 `Files.walk` **不跟随符号链接** → symlink 世界内 `dimensions/*/region/*.mca` 不可见 → 0 chunk 备份；进度 2/2 来自 zip 输出固定项 |
| 影响 | 仅 symlink 世界部署（本地 Folia 测试服）；线上三端 world 为真实目录不受影响；**静默失败无报错是最大风险** |
| 修复 | backup-core `Files.walk(path, FOLLOW_LINKS)`：**PR #50**（main 线，将随 0.3.1 发布）+ **PR #51**（0.2.x 兼容线，将随 0.2.4 发布）；新增 `RealFileSystemSymlinkTest` 回归护栏 |
| 验证 | 修复版 Folia 真实环境 `$b`：**3170460/3170460 chunk 全量、6分16秒、zip 1.42GB**（修复前 22 字节） |

### 观察项（非插件缺陷）

| # | 观察 | 说明 |
|:--|:--|:--|
| O5 | 完整备份磁盘硬约束 | 全量保留需 ~17G+ 临时空间，本机 13Gi 不够 → 本机只能验证备份功能链路；线上磁盘充足不受影响 |
| O6 | 备份为"优化式备份"（设计语义） | backup-core 对备份应用 InhabitedTime 阈值过滤（300 秒，剔除活跃 ≤15 秒 chunk，zip ~1.4GB vs 世界 17G）——backup-core 设计（优化备份 + 全量槽位合并恢复），**非缺陷**；建议文档如实标注 |
| O7 | 备份收尾期登录被拒 | $b 维护模式踢人+拒登，E2E 05/06 需等备份完成再跑（run-all.sh 汇总假绿陷阱已写入 e2e/README） |
| O8 | 测试服世界含线上 temp_zone 命令方块链 | CB_SAVE_TEST 刷屏仅 Paper（Folia 无），勿再全量扫描（既有认知） |

## 五、验收结论

1. **双核心功能验收通过**：OrzMC 1.0.19-dev 在 Paper 26.2-112 与 Folia 26.2-4 上 62/62 用例全绿，含 Bot 命令、玩家命令、安全拦截、备份维护、群消息、权限审核六大类
2. **发现并修复 1 个备份可靠性缺陷**（backup-core symlink walk，P1）：静默空备份会在任何 symlink 世界部署上丢备份数据；修复 PR #50/#51 已提，发布后插件 bump 依赖即可
3. **遗留**：TC-F6 8h 长稳未完成（建议专项窗口）；backup-core 升级方案（0.3.1 vs 0.2.4）待定后插件侧对应 bump

## 五.1、backup-core 0.3.1 升级复验（2026-08-20 22:30）

- **背景**：老板确定升级 0.3.1（PR #50 已合并发布）；测试服世界已裁剪（17G → 2.1G，磁盘 28Gi）
- **插件适配**（最小集，阈值保持原逻辑 300 不动）：
  1. `IOOptions` 补第三参 `syncOnFinalize=true`（0.3.0+ API）
  2. **input 改为世界目录**（`getWorldFolder()`，优先含 dimensions/region 的真实世界目录）——output=`backup/tempDir` 与世界目录成兄弟路径，天然通过 0.3.x overlap 校验（不再依赖系统临时目录）
  3. **备份中间目录统一在 `backup/` 内处理**：tempDir 由 backup-core Cleanup 自动删除；zip 直接落 `backup/`（output 父目录）；启动清理兜底崩溃/断电残留（MaintenanceModule.setup 异步清理）
  4. **备份目录迁移：插件数据目录 `plugins/OrzMC/backup/` → 服务器核心根目录 `backup/`**（老板指示，便于快照/迁移整体打包）；E2E 04 用例 + run-all.sh 路径同步
- **复验结果**（裁剪后世界 2.1G / 246,963 chunk）：
  - Paper：62/62 ✅（01:8 02:10 03:10 04:4 05:11 06:19）
  - Folia：62/62 ✅（02 的 /bot wsOk 正常）
  - $b 备份：**1分51秒完成、zip 1.3G 落盘根目录 backup/、tempDir 零残留**；Folia（symlink 世界）walk 修复生效（246,963/246,963 chunk 全量）
  - **$o 优化验收：31 秒完成 190,526/190,526 区块，剔除 5.6 万低活跃区块（22.8%），世界正常加载**
  - EasyBot 网关恢复（之前磁盘爆满致 Docker 崩溃 → 502；重启 Docker 后 22:19 插件 ws 自动重连）——02 首跑失败（Paper 9/10）为环境问题，非回归

## 六、环境恢复状态

- Paper 测试服常驻运行（原逻辑 1.0.19-dev.jar，sha256 6eff9d51…），RCON 25575
- Folia 测试服已停（需要时按 testing.md 切换：停 Paper → 启 Folia）
- 备份目录已清理（双服 backup/ 空，磁盘 13Gi 可用）
- E2E 报告原始输出：`plugin/e2e/reports/e2e-report-20260820-*.md`（3 份）
- bug 记录：`plugin/e2e/buglog.md`（BUG-E2E-004 + O5-O8）
