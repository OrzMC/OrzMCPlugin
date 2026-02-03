# 开发说明

本插件仅支持 Gradle 构建方式。

推荐开发环境：

- **[IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download)** +
  **[Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)**

> 以下假设你在 MacOS 上进行插件开发

## 使用 Gradle 构建

使用 Gradle Wrapper 进行命令行构建，执行以下命令进行打包：

```bash
$ ./gradlew clean build
```

命令行本地运行调试服务器（自动下载服务端并启动，需要同意 EULA 协议）：

```bash
$ ./gradlew runServer  # 已默认添加 --nojline --nogui，避免终端特性告警
```

使用 IntelliJ IDEA CE（社区免费版）构建和运行插件，可以打断点调试，参考文档：

- https://github.com/jpenilla/run-task#basic-usage
- https://github.com/jpenilla/run-task/wiki

![gradle build](../images/gradle_build_guide.png)

## 相关链接

- [PaperAPI文档](https://papermc.io/javadocs)
- [SpigotAPI文档](https://hub.spigotmc.org/javadocs/spigot/)
- [Bukkit Wiki](https://bukkit.fandom.com/wiki/Main_Page)
- [TextComponent](https://docs.adventure.kyori.net/text.html#creating-components)
