# 权限组节点配置表（Rank & Review）

> 基于本地测试服（与线上插件一致：EssentialsX/GetMeHome/GriefPrevention/WorldEdit/WorldGuard/OrzMC 等 16 插件）设计并实测验证。
> 配置命令：`lp group <组> permission set <节点> true`（LP 继承链 admin→builder→member→default，各组只配增量）。
> **线上同步时逐条执行本表命令即可**（同步前先 `lp export` 备份）。

## L0 default（访客）— 生存基础体验

| 权限节点 | 用途 |
|:--|:--|
| `ls.bypass` | 登录豁免（未注册者限权） |
| `essentials.afk` `essentials.back` `essentials.msg` `essentials.reply` | 基础社交 |
| `essentials.balance` `essentials.baltop` `essentials.pay` | 经济基础 |
| `getmehome.user` | 家命令基础 |
| `bod.back` | 死亡回档 |
| `deathchest.command.report` | 死亡箱查询 |
| `ezshops.shop.buy` `ezshops.shop.sell` `ezshops.playershop.browse` | 商店浏览/交易 |

## L1 member（成员）— 完整玩家功能

| 权限节点 | 用途 |
|:--|:--|
| `getmehome.command.home` `getmehome.command.sethome` `getmehome.command.delhome` `getmehome.command.listhomes` `getmehome.command.setdefaulthome` | 家/传送点（GetMeHome 实现） |
| `essentials.tpa` `essentials.tpahere` `essentials.spawn` `essentials.warp` `essentials.kit` `essentials.mail` | 传送/补给 |
| `griefprevention.createclaims` `griefprevention.trapped` | 领地基础 |
| `ezshops.playershop.create` `ezshops.playershop.buy` `ezshops.playershop.sell` | 玩家商店 |

## L2 builder（建造者）— WorldEdit/WorldGuard 裁剪子集

| 权限节点 | 用途 |
|:--|:--|
| `worldedit.wand` | 木斧 |
| `worldedit.selection.*` | 选区（pos/wand/expand 等） |
| `worldedit.region.*` | 区域填充（set/replace 等） |
| `worldedit.clipboard.*` | 复制粘贴（copy/paste） |
| `worldedit.history.*` | 撤销/重做（undo/redo） |
| `worldedit.brush.*` | 笔刷 |
| `worldedit.tool.*` | 工具 |
| `worldedit.utility.*` | 实用命令 |
| `worldedit.help` | 帮助 |
| `worldguard.region.claim` `worldguard.region.claim.own` | 圈地 |
| `worldguard.region.define` `worldguard.region.remove` | 区域创建/删除 |
| `worldguard.region.addmember` `worldguard.region.removemember` `worldguard.region.setparent` | 区域成员管理 |
| `worldguard.region.flag.*` | 区域旗标 |
| `worldguard.region.list` `worldguard.region.info` `worldguard.region.teleport` | 区域查询 |

> 裁剪说明：不给 `worldedit.reload`、`worldedit.schematic.*`（上传/下载）、`worldguard.region.bypass` 等管理侧节点。

## L3 admin（管理员）— 管理命令（**无 `*`、无 luckperms.\*、无 op**）

| 权限节点 | 用途 |
|:--|:--|
| `orzmc.admin` | OrzMC 管理命令（白名单/传送门/TNT/维护） |
| `minecraft.command.kick` `minecraft.command.ban` `minecraft.command.pardon` `minecraft.command.whitelist` | 玩家管理（原生命令） |
| `minecraft.command.gamemode` `minecraft.command.effect` `minecraft.command.tp` `minecraft.command.give` `minecraft.command.save-all` | 游戏管理 |
| `bukkit.command.gamemode` `bukkit.command.kick` `bukkit.command.ban` `bukkit.command.whitelist` | Bukkit 别名 |
| `essentials.kick` `essentials.ban` `essentials.unban` `essentials.gamemode` `essentials.heal` `essentials.give` `essentials.teleport` `essentials.tp` `essentials.time` `essentials.weather` | Essentials 管理 |
| `griefprevention.admin.*` `griefprevention.restorenature` | 领地管理 |
| `worldguard.region.bypass` `worldguard.region.override` | WG 管理 |
| `vault.admin` `ezshops.shop.admin` `ezshops.playershop.admin` | 经济/商店管理 |
| `deathchest.admin` `bod.bypass` | 死亡箱管理 |

**高危节点（明确不授予任何组）**：`*`、`luckperms.*`、`minecraft.command.op`、`bukkit.command.op`、`essentials.stop`、`essentials.reload`。

## 本地测试服验证结果（2026-08-08，LP check 实测）

| 等级 | 验证账号 | 权限检查 | 结果 |
|:--|:--|:--|:--|
| L0 | default 组 | `essentials.msg` | ✅ true |
| L0 | default 组 | `essentials.sethome` | ✅ false（历史脏配置已清理） |
| L1 | joker | `getmehome.command.sethome` / `essentials.spawn` | ✅ true |
| L2 | TestNewbie | `worldedit.wand` / `worldedit.selection.pos` | ✅ true |
| L2 | TestNewbie | `minecraft.command.kick` | ✅ false（无越权） |
| L3 | TestMember | `minecraft.command.kick` / `orzmc.admin` | ✅ true |
| L3 | TestMember | `minecraft.command.op` | ✅ **false（不可自封 op）** |
| L3 | TestMember | `luckperms.user` | ✅ **false（不可改权限）** |

- admin 组配置前后：2 项（`*` + `luckperms.*`）→ **33 项精确节点**，高危全部移除
- 权限系统与 op 完全解耦：玩家组（含 admin）无 op 能力，ops.json 仅服主账号

## 注意事项

- **joker 残留脏数据**（本地测试历史）：world 上下文快照的 builder/member 组无法用普通 LP 命令移除（完整上下文匹配），已确认**不影响 global 判定**（OrzMC 统一 global 上下文操作），线上部署前用 `lp user <X> parent info` 逐个检查存量玩家，发现带上下文的组按 `lp user <X> parent remove <组> --context ...` 清理
- 配置以命令清单形式同步：`perm_commands.txt`（本地测试服执行记录）可作为线上同步脚本蓝本，**线上执行前先 `lp export` 备份**
