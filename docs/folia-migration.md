# OrzMC 插件 Folia 适配评估与迁移路线

> 定位：Folia 适配的**现状缺口清单 + 决策记录 + 分 PR 落地计划**。所有改动按本文件
> 决策逐批执行，每批独立走 PR（分支 → CI 绿 → merge）。
>
> 最后更新：2026-08-18

---

## 1. 现状与目标

**现状（迁移前快照）**：`src/main/resources/paper-plugin.yml:10` 声明 `folia-supported: false`，
Folia 会直接拒绝加载本插件。即便翻牌，所有调度都经 `ServerFacade` 落到
`Bukkit.getScheduler()`（Folia 已移除该 API，调用即抛异常），另有若干实体/区块操作
在 region 线程模型下会抛「not the correct region」异常。`runServer` 仅能启动 Paper。

**目标**：双运行时（Paper 26.x + Folia 功能均正常），本地可用 `runFolia` 冒烟，
Hangar/Modrinth 同步发布 Folia loader。

**范围**：调度器统一切换 → 区域亲和迁移 → 并发安全 → 声明/构建/发布 → 测试与 CI。
全部为**插件侧**改造，不动 orzmc-api 的端口契约（见 D2）。

---

## 2. 现状缺口清单

| 缺口 | 位置 | Folia 下的后果 |
|---|---|---|
| 调度器 | `infra/server/ServerFacade.java`（runSync/runAsync/runLater/runTaskTimer） | `Bukkit.getScheduler()` 已移除，调用即抛 |
| 调度旁路 | `assembly/FeatureModule.java`（/orzdebug）、`events/OrzRankEvent.java`、`events/OrzDebugEvent.java`（RCON） | 直连 `runTaskAsynchronously`，同上 |
| 主线程判定 | `features/rank/LuckPermsPromoter.java`（`Bukkit.isPrimaryThread()`） | Folia 上恒为 false，async 线程 self-schedule + join 死锁 |
| 实体踢人 | `features/whitelist/WhitelistService.java`、`features/maintenance/WorldMaintenanceService.java` | 跨 region 调 `player.kick()` 抛异常 |
| 实体传送 | `features/teleport/TeleportBowService.java`（`player.teleport()`） | 跨 region 同步传送抛异常，应改 `teleportAsync` |
| 方块/区块 | `infra/portal/*`、`features/teleport/ForceLoadedChunkLease.java`、`TeleportBowFlightTracker.java` | 非本 region 线程访问区块抛异常 |
| 并发安全 | `TntEventService`、`PlayerEventAggregator`、`ForceLoadedChunkLease`、`TeleportBowFlightTracker`、`PortalService` | 事件按 region 并发，共享状态无保护 |
| 声明 | `paper-plugin.yml` | `folia-supported: false` 直接拒绝加载 |
| 构建/发布 | `build.gradle.kts`、Hangar/Modrinth 配置 | 无 `runFolia`；发布 platforms/loaders 仅 paper |

---

## 3. 关键设计决策

### D1 调度器：统一切到 Paper/Folia 兼容调度器（单一代码路径）

`ServerFacade` 的 4 个方法改用 `getGlobalRegionScheduler()` / `getAsyncScheduler()`：

| 方法 | 原实现 | 新实现 |
|---|---|---|
| `runSync` | `getScheduler().runTask` | `getGlobalRegionScheduler().execute(plugin, task)` |
| `runAsync` | `getScheduler().runTaskAsynchronously` | `getAsyncScheduler().runNow(plugin, t -> task.run())` |
| `runLater` | `getScheduler().runTaskLater` | `getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks)` |
| `runTaskTimer` | `getScheduler().runTaskTimer` | `getGlobalRegionScheduler().runAtFixedRate(...)`，返回 `ScheduledTask` |

- **Paper 上语义一致**：GlobalRegionScheduler 即主线程执行，tick 语义与 BukkitScheduler 相同。
- **Folia 上安全**：runSync 落在全局区域线程（≈主线程）。
- **证据（已从 jar 字节码核实）**：paper-api 26.1.2 含上述全部 API；MockBukkit 4.115.0
  已接线 Folia 调度器（`FoliaGlobalRegionScheduler.runAtFixedRate` →
  `BukkitSchedulerMock.runTaskTimer`），`server.getScheduler().performOneTick()` 照常推进。
- **⚠ MockBukkit 局限（实测修正）**：MockBukkit 4.115.0 的 `PaperScheduledTask.cancel()`
  是**未实现的桩**（抛 `UnimplementedOperationException`）。它经 `unmock()` 的
  `disablePlugins()` 触发时会让静态 mock 泄漏，集成测试连锁「Already mocking」。
  **对策**：`ScheduledBackupService` / `TeleportBowFlightTracker` 的取消改为**尽力取消**
  （try/catch RuntimeException + 告警日志）——真实 Paper/Folia 插件禁用时都会自动回收本插件任务，
  显式取消仅为了及时释放，取消失败不应中断卸载流程。这不是给测试打补丁，而是更稳的卸载行为。
- `runTaskTimer` 返回类型 `BukkitTask` → `ScheduledTask`，`cancel()` 调用点不变。

### D2 orzmc-api 端口保持最小

`ServerScheduler` 端口（runSync/runAsync/runLater）**不加** runTaskTimer / region 维度——
orzmc-api 是纯 Java 零 Bukkit 依赖子模块，`ScheduledTask` 是 Paper 类型会破坏纯净性。
两个定时器均构造注入 `ServerFacade` 具体类，不受影响。

### D3 区域亲和迁移（逐调用点）

- **踢人** → `player.getScheduler().run(plugin, t -> player.kick(...), () -> {})`：
  - `features/whitelist/WhitelistService.java`（注入 `JavaPlugin`）
  - `features/maintenance/WorldMaintenanceService.java`（runExclusive 内；`save-off`/`save-on`
    控制台命令留在 global region 的 dispatchCommand）
- **传送** → `player.teleportAsync(safe)`：`features/teleport/TeleportBowService.java`
- **方块/区块** → `Bukkit.getRegionScheduler().run(plugin, world, cx, cz, task)`，
  任务内只访问该 chunk 区域：
  - `infra/portal/PortalBuilder.java`（anchor chunk）、`PortalCleaner.java`（3×3 chunk 逐个投递 +
    实体清理改 `chunk.getEntities()` 限定本 chunk）、`PortalLabelRenderer.java`（anchor chunk）
  - `features/teleport/ForceLoadedChunkLease.java`（acquire/release 经 region scheduler；
    `isChunkLoaded`/`getPlayers` 线程安全直接调）
  - `features/teleport/TeleportBowFlightTracker.java`（已加载分支经 region scheduler；
    `getChunkAtAsync` 回调本身就在目标 region 线程，安全）
  - `ExploitHardeningEventService` **无需改**（EntitySpawnEvent 本就在 region 线程，加注释说明）
- **跨 chunk 方块操作风险**：一期基线按 anchor chunk 投递 + 跨界 warning 降级，
  二期按 chunk 分解（见 §7 分期）。
- **PR-3 落地情况（2026-08，已合并）**：抽象 `RegionSchedulerProvider`（`run(world, cx, cz, task)`，
  `@FunctionalInterface`）包住 `Bukkit.getRegionScheduler().execute(plugin, w, cx, cz, task)`，
  生产注入（`PortalModule`/`TeleportBowService` 传 `new BukkitRegionSchedulerProvider(plugin)`），
  测试注入 inline（同步直跑）或 capture（记录投递坐标）实现：
  - `PortalCleaner.clear` 改为**按足迹覆盖的 chunk 逐个投递**（只投递与传送门足迹相交的 chunk，
    避免无谓加载/生成），方块清理在每个 chunk 的 region 线程内先 `getChunkAt` 再按足迹交集过滤；
    实体清理抽成共享的 `ArmorStandCleanup`（3×3 chunk，`isChunkLoaded` 守卫 + `chunk.getEntities()`
    限定本 chunk + 与旧 `getNearbyEntities` 等价的立方体范围过滤，供 Cleaner 与 LabelRenderer 复用）。
  - `PortalLabelRenderer.spawnLabel/placeInfoSign` 投递到 anchor chunk（`cx>>4, cz>>4`）；
  - `ForceLoadedChunkLease.acquire/release` 计数与 force-load/unload 全部投递到所属 chunk 的
    region 线程（同一 chunk 的 acquire/release 经 region FIFO 天然串行）；
  - `PortalBuilder.build` 保持同步（玩家命令所在 region 线程内即可），仅对跨 chunk 足迹记 warning 降级。

### D4 主线程判定替换

`Bukkit.isPrimaryThread()` → `Bukkit.isGlobalTickThread()`：
- **Paper 主线程**与 **Folia global region 线程**上均为 true；
- async 线程（LP loadUser 等）上为 false → 回调度器执行，消除 self-schedule + join 死锁。
- 注：原计划考虑的 `getGlobalRegionScheduler().isOwnedByCurrentThread()` 在 paper-api 26.1.2
  **不存在**，已改用 `Bukkit.isGlobalTickThread()`（语义等价，编译期核实）。

### D5 runFolia（run-paper 3.1.0）

```kotlin
runPaper.folia.registerTask {
    minecraftVersion(debugServerVersion)   // 26.2，Folia 与 Paper 同一版本体系
    args("--nogui", "--online-mode=false")
    runDirectory.set(layout.projectDirectory.dir("run-folia"))  // 隔离，不与 run/plugins 共用
}
```

- `paper-plugin.yml`：`folia-supported: false` → `true`
- 运行 `./gradlew runFolia`；清缓存 `./gradlew cleanFoliaCache`
- `runDirectory` 隔离避免 Folia 尝试加载 `run/plugins` 里的 Vault/EssentialsX/EzShops 等不兼容插件
- **runServer vs runFolia 结论**：`runServer` 仅能启动 Paper（run-paper 的 platform 固定为 paper），
  **不支持 Folia**。替代方案评估：

| 方案 | 成本 | 结论 |
|---|---|---|
| `runPaper.folia.registerTask`（`runFolia`） | 零额外依赖、插件 jar 自动复制、独立缓存 | ✅ 采用 |
| 手动下载 Folia jar + 手写启动脚本 | 每次冒烟要手动放 jar/配置 | 性价比低 |
| Docker 起 Folia | 需要镜像维护 + 端口映射，冒烟改代码要重建 | 性价比低 |

- **PR-5 落地情况（2026-08，本 PR）**：
  - `paper-plugin.yml`：`folia-supported: false` → `true`（Folia 现可加载本插件）
  - `build.gradle.kts`：新增 `agreeFoliaEula`（写 `run-folia/eula.txt`）+ `runPaper.folia.registerTask`
    （`runFolia`，`minecraftVersion(debugServerVersion)`、`args("--nojline","--nogui","--online-mode=false")`、
    `runDirectory` 隔离到 `run-folia/`）。实测 26.2 可用。
  - **发布 loader**：Modrinth `loaders` 加 `folia`（与 `paper` 共用同一 shadowJar）；
    **Hangar 无 FOLIA 平台**（`PlatformContainer`/`Platforms` 仅有 PAPER/VELOCITY/WATERFALL，
    0.1.4 与 master 均无，Folia 兼容由 PAPER 平台条目 + `folia-supported` 声明承载）。
  - `foliaSmoke` 无头冒烟任务（D8 详述）：实测**通过**——Folia 26.2 约 7s 启动、
    OrzMC 全部配置加载成功、`stop` 干净退出 exit 0。
  - **关键坑 1（下载源）**：旧的 `api.papermc.io/v2` 已下线，现为 **Paper Fill API v3**
    （`https://fill.papermc.io/v3/projects/folia/versions/{ver}/builds/latest`，响应
    `downloads["server:default"].url` 即直链）。JSON ⊂ YAML，`foliaSmoke` 直接复用 buildscript
    已有的 SnakeYAML 解析，零新增依赖。
  - **关键坑 2（自阻断）**：OrzMC 安全加固 deny-list 默认拦截 `stop`/`reload` 等危险命令，
    冒烟发送 `stop` 会被拦截、服务器不退出 → `foliaSmoke` 在隔离的 `run-folia-smoke/plugins/OrzMC/config.yml`
    写 `guard.blocked_commands: []` 放行（仅影响冒烟目录，不触碰真实配置）。开发机手动
    `runFolia`/`runServer` 亦会被拦截，属插件既有安全设计。

### D6 测试策略（单元/集成测试是否需要单独 Folia 处理）

**单元测试不需要单独 Folia 套件**，三层解释：

1. **单测零改动面**：测试全部 mock 调度门面（`ServerFacade`/`ServerScheduler`），运行时无关；
   统一切到 Folia 兼容调度器后方法签名不变 → 绝大多数单测不动。只有 `ServerFacadeTest`
   因实现从 `BukkitScheduler` 换为 `GlobalRegionScheduler`/`AsyncScheduler` 需重写，
   外加 `MaintenanceModuleTest`/`ScheduledBackupServiceTest`/`TeleportBowFlightTrackerTest`
   的 `BukkitTask` mock 改 `ScheduledTask`。
2. **新增「区域亲和语义」单测**（这是 Folia 专属断言，跑普通 JUnit、不需要真实 Folia）：
   给区域亲和包一层可注入的 `RegionSchedulerProvider`（`run(world, cx, cz, task)`），
   `verify(provider).run(plugin, world, cx, cz, task)` 断言方块/区块操作投递正确坐标；
   `mock(Player.getScheduler())` 验证 kick 投递；`verify(player).teleportAsync(...)` 验证传送；
   Tnt/PlayerEventAggregator 并发单测。
3. **集成测试（MockBukkit）保持单一套件**：MockBukkit 4.115.0 已把 Folia 调度器路由进内部
   `BukkitSchedulerMock`，`performOneTick()` 照常推进。**局限需文档化**：MockBukkit 单线程模型
   无法模拟 region 隔离/跨线程竞态——这类 bug 只能靠第 2 层的调度目标断言 + 真实 Folia 冒烟兜底。
   （另见 D1 的 `PaperScheduledTask.cancel()` 未实现 → 生产代码尽力取消。）
4. **⚠ 注册表相关枚举在普通 JUnit 不可用（实测，PR-2 发现）**：`Material.isSolid()`
   走 `BlockType.isSolid()`（服务端注册表），无服务器时恒为 false → 无法用 block mock 让
   `TeleportBowService.findNearestSafe` 走到成功路径；`Sound.<clinit>` 需要完整注册表，
   引用任意 `Sound` 常量即抛 `ExceptionInInitializerError`。对策：区域亲和语义测试改为
   **直接调包私有的投递方法**（如 `teleportAndFeedback`），断言 `teleportAsync` +
   `EntityScheduler.run` 被调用，不执行回调体；踢人验证直接调 `kickInPlayerRegion` 类似。
   PR-3/PR-6 写 portal/区块相关断言时同样避开 `isSolid()`/`Sound`。
5. **⚠ Mockito 对 chunk/array 的坑（实测，PR-3 发现）**：`Chunk.getEntities()` 返回 `Entity[]`
   而非 `List`（编译期注意）；且本仓库 Mockito 版本的 `ReturnsEmptyValues` **不会**为数组类型
   返回空数组（返回 null）→ 生产代码需对 `chunk.getEntities()` 判空防御，mock 侧要
   `thenReturn(new Entity[]{...})` 显式造。另 **「最后匹配的 stub 胜出」**：`when(world.getChunkAt(anyInt(), anyInt()))`
   的泛化 stub 若注册在 `when(world.getChunkAt(0, 0))` 之后会遮蔽具体 stub → 必须**先泛化后具体**。
   `RegionSchedulerProvider` 用 capture 实现（记录 `{w, cx, cz}` 并选择同步/不执行任务体）
   即可在普通 JUnit 中断言「方块/区块操作投递到正确 chunk」。

**真实 Folia 验收**：`./gradlew runFolia` 手动冒烟（启动加载 → 白名单 `$w` → TNT 爆炸 →
传送弓 → 建门/拆门 → `$b` 备份），无 `IllegalThreadStateException`/死锁。
已固化为逐项清单：**[docs/folia-acceptance.md](folia-acceptance.md)**（FA-01~07 无头已验 ✅ +
TC-F1~F6 真实环境待验 ⬜，含通过标准与失败判据）。

### D7 并发安全（Folia 下事件按 region 并发暴露的共享状态）

| 位置 | 改法 | 落地 |
|---|---|---|
| `TntEventService.pendingAlerts`（HashMap get→put 非原子） | `ConcurrentHashMap` + `compute` | **PR-4 已合并** |
| `PlayerEventAggregator.batch`（enqueue/flush 跨线程） | 加 `synchronized` | **PR-4 已合并** |
| `ForceLoadedChunkLease.counts` | `ConcurrentHashMap` | **PR-3 已合并**（region 投递使跨 region 读写真实化） |
| `TeleportBowFlightTracker.acquired/pending` | `ConcurrentHashMap.newKeySet()` | **PR-4 已合并** |
| `PortalService.interiorTargets` | `ConcurrentHashMap` | **PR-3 已合并**（同上，含 `portalCenters`） |

- **PR-4 落地情况（2026-08，已合并）**：`TntEventService.aggregateNotify` 的建表/调度/计数
  全部收进 `pendingAlerts.compute` 映射函数（按 key 原子化；调度在 compute 返回入表前完成，
  保留「不留孤儿批次」不变量）；`PlayerEventAggregator` 的 `enqueue`/`flushPending`/`flushAndRender`
  加 `synchronized` 串行化 region 入队与 global 冲刷；`TeleportBowFlightTracker.acquired/pending`
  改 `ConcurrentHashMap.newKeySet()`（tick 与异步回调并发读写）。并发单测：64 线程同 key 爆炸
  计数精确 ×64 且单次调度；50 线程并发入队单批计数精确；tick×3 与回调并发无
  `ConcurrentModificationException` 且停时释放不泄漏。

### D8 CI 自动化（是否需要补 Folia 处理）

- 主 `check` 流水线（spotlessCheck → test → integrationTest → jacoco → shadowJar）
  **不进真实服务器**，保持不变。
- **新增独立 `folia-smoke` CI job**（Java 25，独立 job 不拖慢主构建，PR-6 落地）：
  - **PR-5 已实现 `foliaSmoke` Gradle 自定义任务**（build.gradle.kts，PR-6 接进 CI）：
    - 拆成 `downloadFoliaJar`（有缓存：`inputs.property(foliaVersion)` + `outputs.file(jar)`
      + 成功标记文件 `.folia-{ver}.ok`——校验通过才写，中断残留判 out-of-date 重下，
      输出到 `build/folia-smoke/`，SHA256 校验）+ `foliaSmoke`（每次真实启动不跳过，
      `outputs.upToDateWhen { false }`）。
    - 启动用 **Java 25 toolchain**（`javaToolchains.launcherFor(java.toolchain)`），不依赖
      Gradle 守护进程 JDK；参数 `--nogui --nojline --online-mode=false --port 25580` +
      `-Ddisable.watchdog=true`。
    - 流程：安装 shadowJar → 轮询 `Done (`（240s 超时）→ 校验 `OrzMC` 出现在日志
      （15s）→ 发 `stop` → 断言 90s 内干净退出 exit 0；任何一步失败抛
      `GradleException` 并附带日志尾部。
    - **不嵌套调 gradlew**（项目锁会死锁）：自行解析 Fill API v3 直链下载。
  - **PR-6 已接进 CI**（`.github/workflows/build.yml`）：新增 `folia-smoke` job
    （`runs-on: ubuntu-latest` + temurin Java 25 + `continue-on-error: true`，独立 job
    不拖慢主 `build`），跑 `./gradlew foliaSmoke --stacktrace`。初期不阻塞 PR，稳定后转必须。
  - 备选：文档化手动 `runFolia` 冒烟清单，CI 保持 serverless。**推荐前者**：真实启动回归
    价值高（能抓「插件在 Folia 启动即崩溃」这类集成测试测不出的回归）。

---

## 4. PR 序列

| PR | 内容 | 验收 |
|---|---|---|
| PR-1 | 调度器门面 + 3 处旁路 + `isGlobalTickThread`；`ScheduledTask` 类型波及的 4 个测试；`docs/folia-migration.md` | `./gradlew check` 全绿 |
| PR-2 | 实体区域亲和（kick → EntityScheduler、teleportAsync） | 单测 + 集成绿；runFolia 冒烟踢人/传送 |
| PR-3 | 方块/区块区域亲和（portal / force-load / flight tracker） | 单测 + 集成绿；runFolia 冒烟建门/拆门 |
| PR-4 | 并发安全（TNT / 聚合 / 租约 / tracker / portal） | `./gradlew check` 绿，无回归 |
| PR-5 | `folia-supported: true` + `runFolia` + 发布 platforms/loaders + `foliaSmoke` 任务 | `runFolia`/`foliaSmoke` 冒烟通过；发布配置可解析 |
| PR-6 | 测试补齐（区域亲和语义 + 并发）+ CI `folia-smoke` job + README/features.md 标注 | 覆盖率门禁过；CI 新 job 可执行 |

---

## 5. 验证方式

- 每个 PR：`./gradlew spotlessCheck :test :integrationTest`（根目录 `:` 前缀）
- PR-5：`./gradlew runFolia` / `foliaSmoke` 真实 Folia 冒烟（开发机手动 + CI 独立 job）
- PR-6 起：CI `folia-smoke` job 自动跑真实 Folia 启动回归（初期 `continue-on-error`）
- 回归：`./gradlew runServer` Paper 侧冒烟，确认双运行时功能不退化

---

## 6. 风险与规避

| 风险 | 规避 |
|---|---|
| Paper 上 global region 与 BukkitScheduler 行为差异 | Paper 官方实现为主线程执行，tick 语义一致；`runServer` + 集成测试回归兜底 |
| 跨 chunk 方块操作在 Folia 抛异常 | 一期 anchor chunk + warning 降级，二期按 chunk 分解 |
| runFolia 复用 `run/plugins` 加载不兼容插件 | `runDirectory` 隔离到 `run-folia/` |
| Folia 事件按 region 并发竞态 | PR-4 线程安全化 |
| `runTaskTimer` 返回类型波及测试 | PR-1 内同步改 4 个测试文件，避免中间态不绿 |
| MockBukkit `PaperScheduledTask.cancel()` 未实现 | 生产代码尽力取消（D1），集成测试不连锁失败 |
| CI 起真实 Folia 服务器的下载/启动不稳定 | `folia-smoke` 独立 job + 超时保护 + 初期 `continue-on-error`；主 `check` 流水线不受影响 |
| MockBukkit 无法模拟 region 隔离 | 区域亲和单测断言调度目标 + 真实 Folia 冒烟兜底 |

---

## 7. 分期与后续

- **一期（PR-1 ~ PR-6）**：双运行时可用，功能不退化，发布 Folia loader。
- **二期（可选）**：portal 跨 chunk 方块操作按 chunk 分解（消除 warning 降级）；
  单元测试用 `RegionSchedulerProvider` 补全区域亲和断言；`folia-smoke` job 由
  `continue-on-error` 转必须。
