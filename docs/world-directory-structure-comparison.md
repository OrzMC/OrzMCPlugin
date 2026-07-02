# 世界目录结构三方对比

> 记录时间：2026-07-02
>
> 对比对象：
> - **旧版 Paper**（26.1 之前）
> - **新版 Paper**（26.1+）
> - **Vanilla 原版**（26.1+）

---

## 1. 顶层结构对比

```
服务端根目录（可执行 jar 所在目录）：
```

| 目录/文件 | 旧版 Paper | 新版 Paper | Vanilla | 说明 |
|-----------|-----------|-----------|---------|------|
| `world/` | ✅ 主世界 | ✅ 唯一世界目录 | ✅ 唯一世界目录 | 新版 Paper = Vanilla |
| `world_nether/` | ✅ 下界独立目录 | ❌ 不存在 | ❌ 不存在 | 旧版 Paper 特有 |
| `world_the_end/` | ✅ 末地独立目录 | ❌ 不存在 | ❌ 不存在 | 旧版 Paper 特有 |
| `world/dimensions/` | ❌ 不存在 | ✅ 维度容器 | ✅ 维度容器 | 新版 Paper = Vanilla |

---

## 2. 维度发现方式对比

### 旧版 Paper — 3 个平级世界目录

```
run/
├── world/                  ← 主世界（region 在根目录）
├── world_nether/           ← 下界（DIM-1）
└── world_the_end/          ← 末地（DIM1）
```

- 各世界在 `server.properties` 中用 `level-name` + 硬编码后缀区分
- 下界目录名固定为 `{level-name}_nether`
- 末地目录名固定为 `{level-name}_the_end`
- 维度子目录 `DIM-1`、`DIM1` 在内部（Bukkit 遗产）

### 新版 Paper = Vanilla — 单目录嵌套维度

```
run/world/
└── dimensions/
    └── minecraft/
        ├── overworld/
        ├── the_nether/
        └── the_end/
```

- 所有维度统一在 `dimensions/minecraft/` 下
- 维度名使用游戏内注册名（`the_nether`、`the_end`）
- 每维度独立 `region/entities/poi/data/` 子目录
- 支持自定义维度（数据包或 mod 添加）

---

## 3. 目录树完整对比

### 3.1 `world/` 根目录

| 文件/目录 | 旧版 Paper | 新版 Paper | Vanilla | 说明 |
|-----------|-----------|-----------|---------|------|
| `level.dat` | ✅ | ✅ gzip NBT | ✅ gzip NBT | 新版 Paper = Vanilla |
| `level.dat_old` | ✅ | ✅ | ✅ | |
| `session.lock` | ❌ | ✅ | ✅ | 新版 Paper = Vanilla |
| `uid.dat` | ✅ | ❌ | ❌ | 旧版 Paper 特有，已废弃 |
| `paper-world.yml` | ✅ | ❌ | ❌ | 旧版 Paper 特有 |
| `.thanos` | ✅（插件） | ✅（插件） | ❌ | OrzMC 插件（Thanos 优化标记） |
| `death-chests.yml` | ✅（插件） | ✅（插件） | ❌ | 插件文件 |
| `level*.dat` 零字节 | ✅（遗留） | ✅（遗留） | ❌ | 历史垃圾，建议清理 |

### 3.2 `world/` 根目录子文件夹

| 目录 | 旧版 Paper | 新版 Paper | Vanilla | 说明 |
|------|-----------|-----------|---------|------|
| `region/` | ✅ | ❌ | ❌ | 旧版 Paper 在主世界根目录 |
| `entities/` | ✅ | ❌ | ❌ | 同上 |
| `poi/` | ✅ | ❌ | ❌ | 同上 |
| `data/` | ✅ | ✅ | ✅ | 新版 Paper = Vanilla |
| `players/` | ❌ | ✅ | ✅ | 新版 Paper = Vanilla |
| `playerdata/` | ✅ | ❌ | ❌ | 旧版 Paper 特有 |
| `stats/` | ✅ | ❌ | ❌ | 旧版 Paper，归入 `players/` |
| `advancements/` | ✅ | ❌ | ❌ | 旧版 Paper，归入 `players/` |
| `generated/` | ✅ | ✅ | ✅ | |
| `datapacks/` | ✅ | ✅ | ✅ | |
| `dimensions/` | ❌ | ✅ | ✅ | 新版 Paper = Vanilla |

### 3.3 `data/` 目录

```
world/data/
```

| 结构 | 旧版 Paper | 新版 Paper | Vanilla |
|------|-----------|-----------|---------|
| 顶级文件（`chunks.dat`、`idcounts.dat` 等） | ✅ 扁平堆积 | ❌ | ❌ |
| `data/minecraft/` | ❌ | ✅ | ✅ |
| `data/minecraft/maps/` | ❌ | ✅ | ✅ |
| `data/minecraft/scoreboard.dat` 等 | ❌ | ✅ | ✅ |

说明：新版 Paper = Vanilla，所有数据按命名空间归入 `data/minecraft/`。旧版 Paper 的 `chunks.dat`、`idcounts.dat` 等功能已合并到其他位置。

### 3.4 玩家数据

| 数据 | 旧版 Paper | 新版 Paper | Vanilla |
|------|-----------|-----------|---------|
| 玩家 NBT | `playerdata/<UUID>.dat` | `players/data/<UUID>.dat` | `players/data/<UUID>.dat` |
| 统计数据 | `stats/<UUID>.json` | `players/stats/<UUID>.json` | `players/stats/<UUID>.json` |
| 进度 | `advancements/<UUID>.json` | `players/advancements/<UUID>.json` | `players/advancements/<UUID>.json` |

新版 Paper = Vanilla，三者统一归入 `players/`。

### 3.5 维度内部结构

#### Overworld

```
旧版 Paper:                            新版 Paper = Vanilla:
                                       
world/                                  world/
├── region/                             ├── dimensions/minecraft/overworld/
├── entities/                           │   ├── region/
├── poi/                                │   ├── entities/
├── data/                               │   ├── poi/
│   ├── chunks.dat                      │   └── data/
│   ├── Mansion_index.dat               │       ├── minecraft/
│   ├── map_*.dat                       │       │   ├── world_gen_settings.dat
│   └── ...                             │       │   ├── game_rules.dat
├── playerdata/                         │       │   ├── weather.dat
├── stats/                              │       │   ├── ...
├── advancements/                        │       └── paper/
│                                       │           ├── level_overrides.dat
│                                       │           ├── metadata.dat
│                                       │           └── persistent_data_container.dat
│                                       ├── data/ (世界级)
│                                       ├── players/
│                                       │   ├── data/
│                                       │   ├── stats/
│                                       │   └── advancements/
│                                       ├── generated/
│                                       └── datapacks/
```

⚠️ `data/paper/` 下三个文件为 **Paper 特有**，Vanilla 无。

#### Nether

| 旧版 Paper | 新版 Paper | Vanilla |
|-----------|-----------|---------|
| `world_nether/DIM-1/region/` | `dimensions/minecraft/the_nether/region/` | ✅ 相同 |
| `world_nether/DIM-1/entities/` | `dimensions/minecraft/the_nether/entities/` | ✅ 相同 |
| `world_nether/DIM-1/poi/` | `dimensions/minecraft/the_nether/poi/` | ✅ 相同 |
| `world_nether/DIM-1/data/` | `dimensions/minecraft/the_nether/data/minecraft/` | ✅ 相同（命名空间化） |
| `data/paper/` | ❌ 无 | `dimensions/minecraft/the_nether/data/paper/` | ✅ Paper 特有 ⚠️ |

#### The End

| 旧版 Paper | 新版 Paper | Vanilla |
|-----------|-----------|---------|
| `world_the_end/DIM1/region/` | `dimensions/minecraft/the_end/region/` | ✅ 相同 |
| `world_the_end/DIM1/entities/` | `dimensions/minecraft/the_end/entities/` | ✅ 相同 |
| `world_the_end/DIM1/poi/` | `dimensions/minecraft/the_end/poi/` | ✅ 相同 |
| `world_the_end/DIM1/data/` | `dimensions/minecraft/the_end/data/minecraft/` | ✅ 相同（命名空间化） |
| `data/paper/` | ❌ 无 | `dimensions/minecraft/the_end/data/paper/` | ✅ Paper 特有 ⚠️ |

---

## 4. 每个维度的 `data/` 内部文件对比

### `dimensions/.../data/minecraft/`（新版 Paper = Vanilla）

| 文件 | Overworld | Nether | The End |
|------|-----------|--------|---------|
| `world_gen_settings.dat` | ✅ | ✅ | ✅ |
| `game_rules.dat` | ✅ | ✅ | ✅ |
| `weather.dat` | ✅ | ✅ | ✅ |
| `world_border.dat` | ✅ | ✅ | ✅ |
| `world_clocks.dat` | ✅ | ✅ | ✅ |
| `chunk_tickets.dat` | ✅ | ✅ | ✅ |
| `raids.dat` | ✅ | ✅ | ✅ |
| `scheduled_events.dat` | ✅ | ✅ | ✅ |
| `wandering_trader.dat` | ✅ | ❌ | ❌ |
| `ender_dragon_fight.dat` | ❌ | ❌ | ✅ |

Overworld 有 `wandering_trader.dat`（流浪商人刷新状态），The End 有 `ender_dragon_fight.dat`（末影龙战斗状态），这是维度特性差异，非版本差异。

### `dimensions/.../data/paper/`（Paper 特有 ⚠️）

| 文件 | Overworld | Nether | The End |
|------|-----------|--------|---------|
| `level_overrides.dat` | ✅ | ✅ | ✅ |
| `metadata.dat` | ✅ | ✅ | ✅ |
| `persistent_data_container.dat` | ✅ | ✅ | ✅ |

此目录及内部三个文件 **Vanilla 不存在**。包含 Paper 专用的世界覆盖设置、元数据和插件持久化数据容器。

---

## 5. 差异总结矩阵

| 维度 | 项目 | 旧版 Paper | 新版 Paper | Vanilla |
|------|------|-----------|-----------|---------|
| **全局** | 世界目录数 | 3 | 1 | 1 |
| **全局** | 维度目录结构 | 无（按目录平铺） | `dimensions/minecraft/{name}/` | ✅ 同新版 Paper |
| **全局** | 维度名格式 | `DIM-1` / `DIM1` | `the_nether` / `the_end` | ✅ 同新版 Paper |
| **全局** | `session.lock` | ❌ | ✅ | ✅ |
| **全局** | `uid.dat` | ✅ | ❌ | ❌ |
| **Overworld** | `region/entities/poi` | 在 `world/` 根目录 | 在 `dimensions/.../overworld/` | ✅ 同新版 Paper |
| **Overworld** | `players/` | ❌（拆为三目录） | ✅ | ✅ |
| **N/A** | `playerdata/` `stats/` `advancements/` | ✅ 根目录 | ❌（归入 `players/`） | ❌（归入 `players/`） |
| **All** | `data/minecraft/` 命名空间 | ❌（扁平） | ✅ | ✅ |
| **All** | `data/paper/` | ❌ | ✅ **Paper 特有** | ❌ |
| **All** | `paper-world.yml` | `world/` 根目录 | 每个维度各一份 | ❌ |
| **N/A** | `level*.dat` 零字节文件 | ❌（遗留垃圾） | ❌（遗留垃圾） | ❌ |

---

## 6. 一句话总结

> **新版 Paper 26.1+ 的世界目录结构与 Vanilla 完全一致**，唯一的差异是每个维度下多了 `data/paper/` 目录（包含 3 个 Paper 专用元数据文件）和 `paper-world.yml` 配置文件。旧版 Paper 的三目录平铺结构（`world/` + `world_nether/` + `world_the_end/` + `DIM-1`/`DIM1` 命名）是 Bukkit/CraftBukkit 的历史遗产，已在 Paper 26.1 中废弃。
