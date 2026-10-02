# 故障排查（常见报错判定与处置）

> 状态：现行 ｜ 最后更新：2026-10-02
> 用途：收录**看起来像 OrzMC 问题、实际不是**或需要特定处置的报错。新增条目请写明「现象 / 根因 / 处置 / 上游」。

---

## 1. 控制台在启动完成前执行命令 → `CommandSourceStack.getLevel() is null` NPE

### 现象

在服务端启动过程中（日志出现 `Done (…)` **之前**）往控制台敲命令，启动完成后日志出现：

```
[ERROR]: Command exception: /update check
java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.ServerLevel.getGameRules()"
  because the return value of "net.minecraft.commands.CommandSourceStack.getLevel()" is null
    at net.minecraft.commands.Commands.executeCommandInContext(Commands.java:463)
    at net.minecraft.commands.Commands.performCommand(Commands.java:378)
    at net.minecraft.commands.Commands.performCommand(Commands.java:366)
    at net.minecraft.commands.Commands.performPrefixedCommand(Commands.java:357)
    at net.minecraft.server.dedicated.DedicatedServer.handleConsoleInputs(DedicatedServer.java:574)
    ...
[INFO]: An unexpected error occurred while trying to execute that command
```

### 判定：**不是 OrzMC 的问题**（Paper 上游缺陷）

- 异常发生在 Paper 的命令派发器前导阶段，**OrzMC（及任何插件）的代码都不在栈里**。
- 与命令、与是否装插件**无关**：实测「**零插件** + 原版 `/list`」同样复现；同一条命令在 `Done (…)` **之后**执行则完全正常。
- 触发条件是**在启动完成前敲命令**；控制台输入越早，越容易命中。

### 根因

1. 启动期控制台输入被包装为 `ConsoleInput`，其 `CommandSourceStack` 由 `MinecraftServer#createCommandSourceStack()` 构建：
   level 取自 `findRespawnDimension()` → 世界尚未加载完成时 `overworld()` 为 `null` ⇒ **`level == null`**；
2. 之后 tick 里 `DedicatedServer#handleConsoleInputs()` 处理排队输入 → `Commands#performCommand` →
   `Commands#executeCommandInContext()` **无 null 检查**地读取 `source.getLevel().getGameRules(...)`（已用 `javap`
   反汇编 `net.minecraft.commands.Commands` 确认）→ NPE。

### 处置（服主侧）

- **等日志出现 `Done (…)` 后再在控制台执行命令**；或用面板 / RCON 在启动完成后发送。
- 这是一次性报错，服务端不会崩（Paper 捕获后打印 `An unexpected error occurred…`）；无需改任何配置或插件。
- ⚠️ 不要把它误判为 `/update`（或其它命令）的插件 bug——换成任意原版命令、去掉全部插件都会复现。

### 上游

- PaperMC/Paper **#13580**「Unable to execute commands in console before worlds are loaded」
  — `open` / `status: accepted` / `upstream: vanilla`。
  https://github.com/PaperMC/Paper/issues/13580
- 复现版本：Paper `26.2-126`、`26.2-129`（最初报告为 `1.21.11-90`）。
