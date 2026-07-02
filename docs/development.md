# 开发说明

本插件仅支持 Gradle 构建方式。

推荐开发环境：

- **[IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download)** +
  **[Minecraft Development 插件](https://plugins.jetbrains.com/plugin/8327-minecraft-development)**

> 以下假设你在 MacOS 上进行插件开发

## 环境要求

- **Java 25**（CI 与 integrationTest 固定使用 Java 25；如果默认 JDK 版本较高，通过 `JAVA_HOME=/path/to/jdk25 ./gradlew ...` 指定）

## 使用 Gradle 构建

使用 Gradle Wrapper 进行命令行构建，执行以下命令进行打包：

```bash
$ ./gradlew clean build
```

命令行本地运行调试服务器（自动下载服务端并启动，需要同意 EULA 协议）：

```bash
$ ./gradlew runServer  # 已默认添加 --nojline --nogui --online-mode=false
```

使用 IntelliJ IDEA CE（社区免费版）构建和运行插件，可以打断点调试，参考文档：

- https://github.com/jpenilla/run-task#basic-usage
- https://github.com/jpenilla/run-task/wiki

![gradle build](../images/gradle_build_guide.png)

## 自动化测试

运行全部测试：

```bash
$ ./gradlew test
```

配置与模板的冒烟测试会直接读取 resources 下的默认配置，确保类型化映射与模板解析可用。

代码格式检查（Palantir 风格，使用 spotless 8.7.0）：

```bash
$ ./gradlew spotlessCheck
```

自动修复格式问题：

```bash
$ ./gradlew spotlessApply
```

对齐 CI 的一键检查（spotless + test + integrationTest + shadowJar）：

```bash
$ ./gradlew check
```

运行集成测试（MockBukkit 模拟 Paper 服务器）：

```bash
$ ./gradlew integrationTest
```

集成测试会在 MockBukkit 环境中执行命令与事件链路，同时对默认配置进行健康检查与模板变量校验。

## 相关链接

- [PaperAPI文档](https://papermc.io/javadocs)
- [SpigotAPI文档](https://hub.spigotmc.org/javadocs/spigot/)
- [Bukkit Wiki](https://bukkit.fandom.com/wiki/Main_Page)
- [TextComponent](https://docs.adventure.kyori.net/text.html#creating-components)
