package com.jokerhub.paper.plugin.orzmc.core.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BotInboundHandlerTest {

    @Test
    void handleMessage_invokesCallback() {
        AtomicReference<String> captured = new AtomicReference<>();

        BotInboundHandler handler = (message, isAdmin, callback) -> {
            callback.accept(MessageEnvelope.publicMessage("reply: " + message));
        };

        handler.handleMessage("hello", true, env -> captured.set(env.message()));

        assertEquals("reply: hello", captured.get());
    }

    @Test
    void handleMessage_adminFlagPassed() {
        AtomicReference<Boolean> adminFlag = new AtomicReference<>();

        BotInboundHandler handler = (message, isAdmin, callback) -> {
            adminFlag.set(isAdmin);
        };

        handler.handleMessage("cmd", true, env -> {});
        assertTrue(adminFlag.get());

        handler.handleMessage("cmd", false, env -> {});
        assertFalse(adminFlag.get());
    }

    @Test
    void handleMessage_nullCallback_doesNotThrow() {
        BotInboundHandler handler = (message, isAdmin, callback) -> {
            // callback 可能为 null，实现应能处理
        };

        assertDoesNotThrow(() -> handler.handleMessage("test", false, null));
    }

    @Test
    void handleMessage_emptyMessage_doesNotThrow() {
        BotInboundHandler handler = (message, isAdmin, callback) -> {
            if (callback != null) {
                callback.accept(MessageEnvelope.publicMessage(""));
            }
        };

        assertDoesNotThrow(() -> handler.handleMessage("", false, env -> {}));
    }
}
