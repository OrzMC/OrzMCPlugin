# 配置 Schema 升级治理规范

> 适用范围：`config.yml` / `templates.yml` / `easybot.yml` 三个 **schema 文件**。
> 运行时数据文件（`portals.yml` / `access_rules.yml` / `permission.yml` / `guide_book.yml`）由插件
> 运行时读写，**不存在「升级补默认」语义，永不纳入自动迁移**——改动它们必须走代码内专门迁移，禁止加入
> `ConfigSchema.SCHEMA_FILES`。

机制代码入口：`ConfigSchema`（版本常量/文件清单）、`ConfigUpgrader`（门控与流水线）、
`DefaultsMerger`（do-no-harm 深合并）、`LegacyDefaultFlips`（旧默认翻转表）、
`ConfigService.upgradeSchemaFiles()`（启动挂载点）。

---

## 1. 版本命名空间与判定表

schema 文件顶层统一携带 `config-version: N`，三个文件共享同一个版本号（`ConfigSchema.LATEST_VERSION`），
同步发版。

| 磁盘 `config-version` | 判定 | 动作 |
|---|---|---|
| 缺失 / 非数字 / `2`（#238 前的旧值） | **legacy**（不可信） | 备份 → 旧默认翻转 → 深合并 → 回写最新版本 |
| `1`–`MIN_TRUSTED_VERSION-1` | **legacy**（不可信） | 同上（与上两行等价对账） |
| `>= MIN_TRUSTED_VERSION` 且 `< LATEST` | 可信但落后 | 备份 → 深合并（**不跑** legacy 翻转）→ 回写最新版本 |
| `== LATEST_VERSION` | 最新 | 零动作（不写文件、不告警、不备份） |
| `> LATEST_VERSION` | 插件降级 | 跳过，告警提示可能降级，**不做逆向迁移** |

当前 `MIN_TRUSTED_VERSION == LATEST_VERSION == 10`：首个可信版本即最新版本，不存在「可信但落后」区间，
也不存在版本链上的受控翻转。见 §3.3。

## 2. 升级流水线（顺序有讲究，勿打乱）

对每个 schema 文件，`ConfigUpgrader.upgrade` 严格按序执行：

1. **损坏判定**：文件存在、非空、但解析结果无任何键 → 疑似 YAML 损坏 → 跳过、`severe` 告警、不备份不覆盖。
2. **版本读取与降级判定**：磁盘版本高于内置 → 跳过。
3. **备份**：把磁盘原文件复制为 `<文件>.bak`（`REPLACE_EXISTING`，只保留最近一次）。备份失败 → 中止该文件。
4. **旧默认翻转**（仅 legacy）：先于深合并执行——若先 merge 会把缺键补成新默认，翻转逻辑会误报
   「已自定义保留」。
5. **深合并**：只补缺失键；已存在的值（含显式空列表/空串/null）一律不覆盖；默认是段、磁盘同名位置是
   标量/列表 → 记录冲突并保留磁盘值。
6. **回写版本标记** `config-version = LATEST_VERSION`。
7. **调用方落盘**（`ConfigService.upgradeSchemaFiles` 对 MIGRATED 结果执行 `saveConfig`）并打印升级报告
   （新增键按顶层段分组汇总、翻转明细、保留自定义明细）。

**重要**：升级只发生在 **`ConfigService.setup()`（插件启动）** 时。`/orzmc config reload` 重载
**不会**重新触发 schema 迁移；如需对回滚的旧文件重新对账，请重启。

## 3. 开发侧契约（每次改动配置必须遵守）

### 3.1 新增配置键 / 新段 / 新模板事件 key

1. 在 jar 内置默认资源（`src/main/resources/` 对应文件）补上新键与默认值——资源是**唯一权威默认源**。
2. 提升 `ConfigSchema.LATEST_VERSION`（+1），并把三个 schema 资源顶层 `config-version` 同步为新值。
   - `ConfigSchemaResourceTest` 钉死「资源版本 == 代码最新版」，漏同步会直接挂测试。
3. 旧安装启动自动补键，无需任何人工迁移。
4. 若新增的是**模板事件文案**：请把常量加进 `TemplateKeys`（保持「值 == `templates:` 下真实键名」），并
   **加入 `TemplateKeys.ALL`**——`ConfigHealthCheck` 对 `ALL` 全量校验，且 `TemplateKeysTest` 钉死
   「`ALL` 每个 key 必须在内置 `templates.yml` 有默认文本」。缺失会同时产生健康检查告警与测试失败。

### 3.2 故意翻转某默认值（行为变更）

不能只改资源里的默认值——已存在的旧值会被 `DefaultsMerger` 视为「已自定义」而**永不覆盖**。必须：

1. 在 `LegacyDefaultFlips.SPECS` 登记一条：`new FlipSpec(path, 旧默认值)`。**只登记旧默认**，
   新默认运行时从内置默认资源取（避免新旧默认在代码里双份漂移）。
2. 提升 `LATEST_VERSION` 并同步资源版本标记。
3. 效果：仅当磁盘值 == 旧默认（可推断管理员未自定义）才翻到新默认；已自定义的值保留并在升级报告列出
   「保留自定义」。
4. 语义保证：数值按 `longValue()` 比较（`6` 与 `3000L` 等价）；列表按整体 `equals`。

### 3.3 legacy 翻转表的边界（诚实声明，勿误用）

`LegacyDefaultFlips` 只在「磁盘版本 < `MIN_TRUSTED_VERSION`」时执行，语义是**一次性收编不可信旧装**
（无标记 / 旧 `2` → v10）。它**不是**版本链迁移表：

- 当前不存在可信中间版本（`MIN_TRUSTED == LATEST == 10`），所以表内条目 = v10 发布时的旧默认收编。
- 将来 v10 → v11 若需要**再次翻转默认值**，本机制不会对 v10 安装生效（v10 已 trusted，不跑 legacy 翻转）。
  届时请扩展为**按源版本门控的翻转表**（例：给 FlipSpec 增加 `minFromVersion` 语义），不要在
  `LegacyDefaultFlips` 里堆叠新条目——那只会影响「无标记/旧 2」的安装，达不到 v10 老装目的。
- 若只是**新增键或修改默认且愿意接受老装保留旧值**，走 §3.1 + §3.2 中「只抬版本」即可，无需翻转表。

### 3.4 结构性变更（禁止静默）

深合并**只加键、永不删键/移键/改路径**。以下场景**没有**自动迁移，必须显式处理：

- **键重命名 / 段搬迁**（如把 `chat.max_messages_per_minute` 挪到 `chat.rate_limit.per_minute`）：
  旧路径的自定义值会成为孤儿（文件还在、不再被读取），新路径取默认。
  处理：开发侧写一次性迁移脚本/步骤，或至少在发布说明 + 健康检查里给出明确人工指引；
  **不要**尝试用 merge 猜语义。
- **值语义/格式变化**（类型、枚举取值、单位换算）：merge 保留旧值，与新格式冲突时由健康检查显式告警。
- **整文件级并入/拆出**（如历史上 #238 把按功能拆分 YAML 并入 `config.yml`）：不属于 schema 自动迁移
  能力范围，需专项迁移说明。

### 3.5 不要在 ConfigUpgrader 里做业务判断

`ConfigUpgrader`/`DefaultsMerger`/`LegacyDefaultFlips` 是纯结构升级设施，不感知任何业务键含义。
业务侧校验（类型、取值域、占位符、颜色合法性）一律放 `ConfigHealthCheck`，保持「迁移只负责形态，
校验只负责语义」。

## 4. 运维侧规则

1. **升级动作 = 替换 JAR + 重启**。启动即自动备份 → 补缺 → 翻转 → 回写，无需手动删配置重生成。
2. **不要手动改/删 `config-version`**：删除会触发多余的备份+合并；改成更高值会让插件误判降级而跳过。
3. **`.bak` 是升级前最近一次原始文件**，审计或回滚用；确认无误后再清理。
4. **首次迁移会把 schema 文件规范化重写**：Bukkit `FileConfiguration.save` 不保留注释，因此该文件原有的
   手写注释会丢失一次（之后不再重写，除非再次迁移）。审计变更请比对 `.bak`，别依赖文件内注释。
5. 怀疑 YAML 损坏（文件非空但解析为空）时插件会跳过并 `severe` 告警，请人工修复后重启，不会被覆盖。
6. 插件降级（磁盘版本高于内置）时不做逆向迁移，属预期安全行为。

## 5. 单测护栏（改了别忘跑）

- `ConfigSchemaResourceTest`：三份 schema 资源都携带 `config-version: LATEST`；SCHEMA_FILES 映射精确；
  运行时数据文件不在清单里。
- `ConfigUpgraderTest`：legacy 无标记/旧 `2` 迁移、最新零动作、降级跳过、无默认源跳过、损坏跳过、
  merge 保留显式空列表/自定义值、翻转仅命中旧默认、`entity_teleport_whitelist` 仅旧 4 项默认时扩展、
  templates 缺键回填。
- `DefaultsMergerTest`：嵌套补键、保留空值、段/标量冲突、空 disk 段补缺。
- `TemplateKeysTest`：`ALL` 无重复、每个 key 在内置 `templates.yml` 有默认文本。
- `ConfigHealthCheckTest`：健康夹具遍历 `TemplateKeys.ALL` 填充（勿退化为静态清单）。
- `ConfigServiceTest`（服务接缝级，最接近生产的整包验证）：
  - `setup_legacyInstall_upgradesAllSchemaFilesToDisk`：三份 schema 文件全为无版本标记旧档 → 一次启动后
    `.bak` 原始内容、翻转与自定义保留、缺键补齐**全部落盘可从磁盘重读验证**，且健康检查不再报模板 key 缺失；
  - `setup_secondRun_isNoop_doesNotRewriteSchemaFiles`：已最新版二次启动（setup 再运行）对三个 schema 文件
    **零写入**（字节与 mtime 不变）——UP_TO_DATE 路径的落盘级保证。
