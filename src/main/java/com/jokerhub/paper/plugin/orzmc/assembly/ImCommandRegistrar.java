package com.jokerhub.paper.plugin.orzmc.assembly;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.features.bot.ImAdminService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * IM 管理子命令（挂在 /config im 下，方案 §4.3 D10-D12）：setup / status / bind / test。
 *
 * <p>权限由 /config 管理根的 admin 拦截链统一兜底（D10：bind 仅控制台/游戏内 op）；
 * 服务层 {@link ImAdminService} 另有权限守卫防串用（可单测权限拒绝）。</p>
 */
final class ImCommandRegistrar {

    private ImCommandRegistrar() {}

    /** 构建 {@code im} 子树节点，由 /config 注册器挂载。 */
    static LiteralCommandNode<CommandSourceStack> build(ImAdminService svc) {
        LiteralArgumentBuilder<CommandSourceStack> im = literal("im");
        im.then(literal("setup").executes(ctx -> {
            svc.setup(sender(ctx));
            return 1;
        }));
        im.then(literal("status").executes(ctx -> {
            svc.status(sender(ctx));
            return 1;
        }));
        im.then(bindSubtree(svc));
        im.then(testSubtree(svc));
        im.executes(ctx -> {
            svc.setup(sender(ctx));
            ctx.getSource()
                    .getSender()
                    .sendMessage(Component.text(
                            "用法: /config im setup|status | bind <平台> <group|user> <会话id> <admin_group|player_group|admin_dm>"
                                    + " | test <平台> <group|user> <会话id> <文本>"));
            return 1;
        });
        return im.build();
    }

    /** bind 子树：platform chat_type chat_id role（剩余整段 greedyString 收下，服务端 split 归一空白，免多空格解析失败）。 */
    private static ArgumentBuilder<CommandSourceStack, ?> bindSubtree(ImAdminService svc) {
        RequiredArgumentBuilder<CommandSourceStack, String> args =
                argument("arguments", StringArgumentType.greedyString());
        args.executes(ctx -> {
            CommandSender s = sender(ctx);
            String[] t = splitTokens(ctx.getArgument("arguments", String.class));
            if (t.length != 4) {
                s.sendMessage(Component.text("用法: /config im bind <平台> <group|user> <会话id> "
                        + "<admin_group|player_group|admin_dm>（参数用单个或多个空格分隔均可）"));
                return 1;
            }
            svc.bind(s, t[0], t[1], t[2], t[3]);
            return 1;
        });
        return literal("bind").then(args);
    }

    /** test 子树：platform chat_type chat_id text（text 可含空格并保留；前面 3 参数多空格亦免疫）。 */
    private static ArgumentBuilder<CommandSourceStack, ?> testSubtree(ImAdminService svc) {
        RequiredArgumentBuilder<CommandSourceStack, String> args =
                argument("arguments", StringArgumentType.greedyString());
        args.executes(ctx -> {
            CommandSender s = sender(ctx);
            // 前 3 个结构化 token 按空白切，第 4 段起为自由文本（保留内部空格）
            String[] t = ctx.getArgument("arguments", String.class) == null
                    ? new String[0]
                    : ctx.getArgument("arguments", String.class).trim().split("\\s+", 4);
            if (t.length < 3) {
                s.sendMessage(Component.text("用法: /config im test <平台> <group|user> <会话id> <文本>"));
                return 1;
            }
            svc.test(s, t[0], t[1], t[2], t.length >= 4 ? t[3] : "");
            return 1;
        });
        return literal("test").then(args);
    }

    /** 按任意连续空白切分并去首尾空白（多空格/前导/尾随统一归一；chat_id 等不含空格）。 */
    private static String[] splitTokens(String raw) {
        if (raw == null) {
            return new String[0];
        }
        return raw.trim().split("\\s+");
    }

    private static CommandSender sender(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }
}
