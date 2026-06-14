package com.jokerhub.paper.plugin.orzmc.features.command;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CommandFeedbackServiceTest extends ServiceTestBase {

    private final CommandFeedbackService service = new CommandFeedbackService();

    @Test
    void cooldownTip() {
        String text = PlainTextComponentSerializer.plainText().serialize(service.cooldownTip());
        assertTrue(text.contains("冷却"));
    }

    @Test
    void adminRequiredTip() {
        String text = PlainTextComponentSerializer.plainText().serialize(service.adminRequiredTip());
        assertTrue(text.contains("管理员权限"));
    }

    @Test
    void playerRequiredTip() {
        String text = PlainTextComponentSerializer.plainText().serialize(service.playerRequiredTip());
        assertTrue(text.contains("玩家"));
    }

    @Test
    void usageTip() {
        String text = PlainTextComponentSerializer.plainText().serialize(service.usageTip("/cmd <arg>"));
        assertEquals("/cmd <arg>", text);
    }

    @Test
    void portNumberRequiredTip() {
        String text = PlainTextComponentSerializer.plainText().serialize(service.portNumberRequiredTip());
        assertTrue(text.contains("数字"));
    }
}
