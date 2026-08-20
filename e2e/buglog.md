# E2E 测试发现 Bug 记录

> 本文件由 e2e 测试套件执行时发现的问题自动/手动登记。每条记录含复现方式与验证状态。
> 优先级：P0=功能不可用（阻塞） / P1=功能异常（需修复） / P2=健壮性/体验 / P3=优化项

## BUG-E2E-001：`$w` 白名单分页在 Folia 上抛异常（第一页即炸）—— ✅ 已修复并双核心验证（2026-08-19）

- **环境**：Folia 26.2-4 测试服（~/folia-test/）+ OrzMC 1.0.18-dev.jar
- **现象**：`orzdebug $w` 只回「debug 已受理」，收不到白名单列表；日志抛
  `java.lang.IllegalArgumentException: Delay ticks may not be <= 0`
- **根因**：`Paginator.paginatePages`/`paginate` 循环 `i * delayTicks`，**i=0 时 delay=0**。
  Paper BukkitScheduler 允许 0，Folia `FoliaGlobalRegionScheduler.runDelayed` 要求 ≥1。
  `delayTicks <= 0 ? 5L : delayTicks` 只保护配置值，不保护 i=0 首页。
- **修复**：
  1. `Paginator.java` 两处 delay 计算 → `Math.max(1L, (long) i * (delayTicks <= 0 ? 5L : delayTicks))`
  2. `ServerFacade.runLater` 防御性钳位 `Math.max(1, delayTicks)`（覆盖所有调用点）
  3. `PaginatorTest` 回归护栏（首页 delay ≥ 1 / 负 delay 兜底 / 单页）
- **验证**：Folia 26.2-4 ✅（`$w` 输出 123 人白名单列表）+ Paper 26.2-112 ✅（01 用例 8/8 全过）

## BUG-E2E-002：大世界（17GB/316万chunk）$b 备份极慢+失败误报—— ✅ 已修复并端到端验证（2026-08-19）

- **现象**：Paper 测试服（317 万 chunk）执行 `$b` 备份数小时未完成，`OptimizeError: Pattern matching failed: unknown compression: 31` 反复触发「地图备份 失败」通知
- **根因（全量扫描 3,163,860 chunks + 局部复现确认，三层）**：
  1. 99.998% chunk 是标准 ZLIB(2)，另有 ~56+ 个**损坏 chunk 条目**——**不是 MC 26.2 新压缩格式**
  2. 长度荒谬+compression 合法的损坏 chunk：旧版 `dataBytes` 尝试分配+读取 20 亿字节 → 单 chunk 卡死数分钟
  3. **损坏 chunk offset 越过文件末尾 → BufferedRafAccess.readFully avail<=0 时 remaining 不减 → 无限循环（CPU 100% 无进度，卡 49%）**
  4. errorHandler 对每个错误触发「地图备份 失败」→ 误报风暴
- **修复（OrzMCBackup PR #46 + #47，v0.2.1/v0.2.2）**：
  1. `McaEntry`：未知 compression → `UNKNOWN`；dataBytes/serializedBytes 长度 >8MB 短路返回空（秒级跳过）
  2. `DimensionProcessor` pattern 异常 → 安全保留原始 chunk，错误仅记录不中断
  3. **`BufferedRafAccess.readFully` EOF 保护（avail<=0 抛 EOFException 而非死循环）**
  4. 插件 errorHandler 聚合：chunk 级错误（Pattern/Write-损坏）计数不报失败，Done 时汇总
- **端到端验证（Paper 服 317 万 chunk 真实世界）**：`$b` 完成 + zip 生成 + 通知「（备份含 N 个损坏区块，已安全保留原始数据）」✓（修复前：卡死数小时）
- **测试**：CorruptedChunkKeepTest（4）+ RealCorruptedChunkTest（2：损坏跳过 + EOF 不死循环）
- **并行化配套**：备份 parallelism 0→CPU 核数（backup-core RuntimeOptions），Paper 服 $b **14分21秒 → 5分59秒**（速率 16.6万→83.3万 chunk/min，5 倍）
- **待办**：损坏区块（~58-264 个）建议后续用世界修复工具清理（备份已安全保留，不影响服务）

## BUG-E2E-003：CommandGuard 审计日志洪泛 —— ✅ 已修复并验证（2026-08-19）

- **现象**：Paper 测试服命令方块循环每 tick 触发 ~20 条「危险命令放行」WARN → 21 万条/53MB/20 分钟，挤爆日志轮转窗口（E2E waitLog 200 行窗口被挤出，连带 $e 用例 FAIL）
- **根因**：测试服世界含巨型命令方块系统（CB_SAVE_TEST 链）；CommandGuard 对「放行」命令每条记 WARN
- **修复（OrzMCPlugin PR #198）**：
  1. WARN 日志节流：`ThrottledNotifier` 5 秒最多 1 条 warning（其余降级 fine）；审计记录不受影响（audit.record 写文件不刷日志）
  2. BLOCK 管理员通知节流：10 秒最多 1 条（防命令方块循环触发 BLOCK 刷爆通知）
- **验证**：Paper 服 4 分钟仅 13 条 warning（修复前同窗口 4800+，~370 倍降幅）；单测 +3（WARN 节流 / 通知抑制 / 通知放行）
- **连带**：E2E waitLog 默认 tail 200→3000 已缓解

## 观察项（非插件缺陷，环境事实）

| # | 观察 | 说明 |
|:--|:--|:--|
| O1 | Paper 测试服世界有巨型命令方块循环 | CB_SAVE_TEST 链每 tick 执行 ~20 条命令，触发 BUG-E2E-003 放大 |
| O2 | Paper 测试服线程名 `Folia Async Scheduler Thread` | Paper 26.2 实现了 Folia 调度 API，OrzMC 统一调度器代码路径，线程名相同属正常 |
| O3 | Paper 测试服备份目录 retention=1 | 旧 zip 被清理，E2E 断言须用文件名差异而非数量 |
| O4 | shadowJar 12 个编译警告 | Predicate 泛型 raw type（P3，CI 会标 ##[warning]，建议清零） |
| O5 | **完整备份磁盘硬约束** | 世界 17G（317 万 chunk），完整保留全部 chunk 的备份需 ~17G+ 临时空间（region 重写 + zip），本机磁盘 13Gi 可用 → 本机测试服无法跑"全量完整备份"，仅能验证功能链路（触发→阶段进度→zip 落盘）；线上（Exaroton/MCSM）磁盘充足不受影响 |
| O6 | **备份为"优化式备份"（设计语义）** | backup-core 对备份也应用 InhabitedTime 阈值过滤（`FilterOptions.inhabitedThresholdSeconds`，插件传 `optimizeTickTimeThreshold` 默认 300 秒）：活跃 ≤15 秒的 chunk 从备份中剔除（zip 约 1.4GB vs 世界 17G，region 文件 946/6380）。这是 backup-core 的优化备份设计（README：优化 + 全量槽位合并恢复），**非缺陷**；文档需如实说明备份为"优化式备份"，非逐字节全量 |
| O7 | 备份期间登录被"地图备份中"拒绝 | $b 进入维护模式（踢人+拒登），E2E 05/06 用例在备份收尾期登录会失败（whitelist_block 断言超时）——用例需等备份完成再跑，非插件缺陷 |

## BUG-E2E-004：symlink 世界 $b 备份空跑假完成（backup-core，✅ 修复 PR 已提）—— 2026-08-20 双核心验收发现

- **环境**：Folia 测试服（world 为符号链接 `world -> ~/papermc-test/world`，共享地图）+ OrzMC 1.0.19-dev（backup-core 0.2.2）
- **现象**：`orzdebug $b` → 431ms 完成、进度 2/2、zip 22 字节空包，无任何报错（"地图备份 完成 用时:431毫秒"）
- **根因**：backup-core `RealFileSystem.walk` 用 `Files.walk(path)` **不跟随符号链接** → symlink 世界内部 `dimensions/<dim>/region/*.mca` 全部不可见 → 0 chunk 备份；`progressTotal = 0 + 0 + 2(zip 固定项)` → 假"2/2 完成"
- **修复**：`Files.walk(path, FileVisitOption.FOLLOW_LINKS)`（backup-core PR #50 main 线 / #51 0.2.x 线）——插件零改动
- **验证**：backup-core 新增 `RealFileSystemSymlinkTest`（链接目标内 region 可见性）+ 全量测试全绿；Folia 真实环境修复版 $b **3170460/3170460 chunk 全量完成（6分16秒，zip 1.42GB）**
- **连带**：Folia 上 walk 跟随链接后，worldContainer 内历史残留的备份中间目录（旧布局 `plugins/OrzMC/backup/tempDir`）会被扫入备份源 → 需保持世界目录内无备份残留（新布局：中间目录 `backup/tempDir` 在世界目录外——`backup/` 是世界目录的兄弟路径；崩溃/断电残留由插件启动清理兜底）

## E2E 套件双核心适配（PR #199，2026-08-19）

- **问题**：Paper 测试服（25566）跑 E2E 时 01/02/03 用例 waitLog 默认读 Folia 日志路径（lib/rcon.js `DEFAULT_LOG_PATH`）→ 断言等待超时 FAIL；04 备份用例与备份锁冲突（备份进行中 bot 被踢、$b 被锁拒绝）会连带后续用例零输出
- **修复**：run-all.sh 按 `ORZMC_TEST_PORT` 推断 `ORZMC_LOG_PATH` 并 export（25565→folia-test、25566→papermc-test，可显式覆盖）
- **验证**：**Folia 32/32 + Paper 32/32 全绿**（Paper 需等备份完成后再跑全套，避免与 $b 备份锁冲突）
