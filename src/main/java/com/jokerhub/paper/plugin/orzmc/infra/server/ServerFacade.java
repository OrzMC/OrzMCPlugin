package com.jokerhub.paper.plugin.orzmc.infra.server;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFacade implements ServerAccess, ServerLogger, ServerScheduler {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private final JavaPlugin plugin;

    public ServerFacade(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Server server() {
        return plugin.getServer();
    }

    public Logger logger() {
        return plugin.getLogger();
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    public void runSync(Runnable task) {
        server().getScheduler().runTask(plugin, task);
    }

    public void runAsync(Runnable task) {
        server().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runLater(Runnable task, long delayTicks) {
        server().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void executeConsoleCommands(Runnable after, String... consoleCmds) {
        ConsoleCommandSender console = server().getConsoleSender();
        runSync(() -> {
            for (String consoleCmd : consoleCmds) {
                server().dispatchCommand(console, consoleCmd);
            }
            if (after != null) {
                after.run();
            }
        });
    }

    public ConsoleCommandResult executeConsoleCommand(String rawConsoleCmd) {
        String consoleCmd = normalizeConsoleCommand(rawConsoleCmd);
        ConsoleCommandSender console = server().getConsoleSender();
        ArrayList<String> outputLines = new ArrayList<>();
        ConsoleCommandSender capturingConsole = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("equals".equals(methodName)) {
                        return proxy == (args == null ? null : args[0]);
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(methodName)) {
                        return "CapturingConsoleCommandSender[" + console + "]";
                    }
                    if ("sendMessage".equals(methodName)
                            || "sendPlainMessage".equals(methodName)
                            || "sendRichMessage".equals(methodName)) {
                        captureMessages(outputLines, args);
                    }
                    try {
                        return method.invoke(console, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        boolean dispatched = server().dispatchCommand(capturingConsole, consoleCmd);
        return new ConsoleCommandResult(consoleCmd, dispatched, outputLines);
    }

    private static String normalizeConsoleCommand(String rawConsoleCmd) {
        if (rawConsoleCmd == null) {
            return "";
        }
        String consoleCmd = rawConsoleCmd.trim();
        if (consoleCmd.startsWith("/")) {
            consoleCmd = consoleCmd.substring(1).trim();
        }
        return consoleCmd;
    }

    private static void captureMessages(List<String> outputLines, Object[] args) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            captureMessage(outputLines, arg);
        }
    }

    private static void captureMessage(List<String> outputLines, Object arg) {
        if (arg == null) {
            return;
        }
        if (arg instanceof String message) {
            addCapturedText(outputLines, message);
            return;
        }
        if (arg instanceof String[] messages) {
            for (String message : messages) {
                addCapturedText(outputLines, message);
            }
            return;
        }
        if (arg instanceof Component component) {
            addCapturedText(outputLines, PLAIN_TEXT.serialize(component));
            return;
        }
        if (arg instanceof ComponentLike componentLike) {
            addCapturedText(outputLines, PLAIN_TEXT.serialize(componentLike.asComponent()));
            return;
        }
        if (arg instanceof Iterable<?> items) {
            for (Object item : items) {
                captureMessage(outputLines, item);
            }
        }
    }

    private static void addCapturedText(List<String> outputLines, String rawText) {
        if (rawText == null) {
            return;
        }
        String normalizedText = rawText.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalizedText.split("\n")) {
            if (!line.isBlank()) {
                outputLines.add(line);
            }
        }
    }

    public record ConsoleCommandResult(String command, boolean dispatched, List<String> outputLines) {
        public ConsoleCommandResult {
            outputLines = List.copyOf(outputLines);
        }

        public String message() {
            if (!outputLines.isEmpty()) {
                return String.join("\n", outputLines);
            }
            if (dispatched) {
                return "命令已执行: " + command;
            }
            return "命令不存在或执行失败: " + command;
        }
    }
}
