package com.jokerhub.paper.plugin.orzmc.features.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class BotStatusServiceTest extends ServiceTestBase {

    @Test
    void buildMinimalMessage_normal_showsThreeWordsWithoutClick() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(true, true, true, true, true, null, 0, 0, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildMinimalMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertEquals("enabled httpOk wsOk", plain);
        // 全部正常：三个词均不可点击
        long clickables =
                msg.children().stream().filter(c -> c.clickEvent() != null).count();
        assertEquals(0, clickables);
    }

    @Test
    void buildMinimalMessage_httpAbnormal_httpClickableOnly() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(true, false, true, true, true, "HTTP 403", 0, 0, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildMinimalMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertEquals("enabled httpNotOk wsOk", plain);
        List<Component> clickables =
                msg.children().stream().filter(c -> c.clickEvent() != null).toList();
        assertEquals(1, clickables.size());
        assertEquals(ClickEvent.runCommand("/bot http"), clickables.get(0).clickEvent());
        assertNotNull(clickables.get(0).hoverEvent());
    }

    @Test
    void buildMinimalMessage_deliveryFailureHttpShowsAbnormalAndClickable() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        // httpOk 为 true（网关正常），但批量投递有目标失败 → http 状态应显示异常且可点击
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(
                        true, true, true, true, true, null, 1, 2, List.of("telegram:player-chat"), 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildMinimalMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertEquals("enabled httpNotOk wsOk", plain);
        List<Component> clickables =
                msg.children().stream().filter(c -> c.clickEvent() != null).toList();
        assertEquals(1, clickables.size());
        assertEquals(ClickEvent.runCommand("/bot http"), clickables.get(0).clickEvent());
    }

    @Test
    void buildMinimalMessage_wsDown_wsClickableOnly() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(true, true, true, false, true, null, 0, 0, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildMinimalMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertEquals("enabled httpOk wsNotOk", plain);
        List<Component> clickables =
                msg.children().stream().filter(c -> c.clickEvent() != null).toList();
        assertEquals(1, clickables.size());
        assertEquals(ClickEvent.runCommand("/bot ws"), clickables.get(0).clickEvent());
    }

    @Test
    void buildMinimalMessage_disabled_httpAndWsClickable() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot")).thenReturn(new HealthStatus.Entry(false, false, false, false, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildMinimalMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertEquals("disabled httpUnknown wsNotOk", plain);
        // 禁用时 http 未检查、ws 断开，两者均可点击
        long clickables =
                msg.children().stream().filter(c -> c.clickEvent() != null).count();
        assertEquals(2, clickables);
    }

    @Test
    void buildHttpDetail_includesStateDeliveryAndError() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(
                        true, true, true, true, true, "boom", 1, 2, List.of("telegram:player-chat"), 0));

        BotStatusService service = new BotStatusService(styles, health);
        String plain = PlainTextComponentSerializer.plainText().serialize(service.buildHttpDetail());

        assertTrue(plain.contains("HTTP: 异常"), plain);
        assertTrue(plain.contains("失败平台 (1/2):"), plain);
        assertTrue(plain.contains("telegram:player-chat"), plain);
        assertTrue(plain.contains("错误: boom"), plain);
    }

    @Test
    void buildHttpDetail_unknownTotalRendersWithoutRatio() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        // total 未知（deliveryTotal=0）：不判定为全部失败，头部不带比例
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(
                        true, true, true, true, true, null, 1, 0, List.of("telegram:player-chat"), 0));

        BotStatusService service = new BotStatusService(styles, health);
        String plain = PlainTextComponentSerializer.plainText().serialize(service.buildHttpDetail());

        assertTrue(plain.contains("失败平台:"), plain);
        assertTrue(plain.contains("telegram:player-chat"), plain);
        assertFalse(plain.contains("失败平台 (1/"), plain);
    }

    @Test
    void buildHttpDetail_listsEachFailedPlatformOnItsOwnLine() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(
                        true, true, true, true, true, null, 2, 3, List.of("telegram:a", "discord:b"), 0));

        BotStatusService service = new BotStatusService(styles, health);
        String plain = PlainTextComponentSerializer.plainText().serialize(service.buildHttpDetail());

        assertTrue(plain.contains("失败平台 (2/3):\ntelegram:a\ndiscord:b"), plain);
        assertFalse(plain.contains("telegram:a, discord:b"), plain);
    }

    @Test
    void buildWsDetail_showsStateAndError() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        stubStyles(styles);
        HealthStatus health = mock(HealthStatus.class);
        when(health.get("easybot"))
                .thenReturn(new HealthStatus.Entry(true, true, true, false, true, "ws down", 0, 0, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        String plain = PlainTextComponentSerializer.plainText().serialize(service.buildWsDetail());

        assertTrue(plain.contains("WS: 已断开"), plain);
        assertTrue(plain.contains("错误: ws down"), plain);
    }

    private static void stubStyles(OrzTextStyles styles) {
        when(styles.warn(anyString())).then(i -> Component.text((String) i.getArgument(0)));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));
        when(styles.success(anyString())).then(i -> Component.text((String) i.getArgument(0)));
        when(styles.error(anyString())).then(i -> Component.text((String) i.getArgument(0)));
    }
}
