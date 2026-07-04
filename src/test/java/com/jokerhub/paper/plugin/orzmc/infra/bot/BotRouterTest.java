package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotRouterTest {

    @Mock
    private ThrottledLogger throttledLogger;

    @Mock
    private BotAdapter adapter1;

    @Mock
    private BotAdapter adapter2;

    private BotRouter router;

    @BeforeEach
    void setUp() {
        router = new BotRouter(throttledLogger);
    }

    @Test
    void route_beforeInitialization_queuesMessage() {
        var env = MessageEnvelope.publicMessage("hello");
        router.route(env);

        verify(throttledLogger).info(eq("bots-init"), anyString());
    }

    @Test
    void route_afterSetup_sendsToAllAdapters() {
        router.setAdapters(List.of(adapter1, adapter2));
        router.setup();

        var env = MessageEnvelope.publicMessage("hello");
        router.route(env);

        verify(adapter1).send(env);
        verify(adapter2).send(env);
    }

    @Test
    void route_pendingQueue_flushedAfterSetup() {
        var env = MessageEnvelope.publicMessage("queued");
        router.route(env);
        verify(throttledLogger).info(eq("bots-init"), anyString());

        router.setAdapters(List.of(adapter1));
        router.setup();

        verify(adapter1).send(env);
    }

    @Test
    void route_adapterException_doesNotBlockOtherAdapters() {
        router.setAdapters(List.of(adapter1, adapter2));
        router.setup();

        doThrow(new RuntimeException("send failed")).when(adapter1).send(any());

        var env = MessageEnvelope.publicMessage("hello");
        assertDoesNotThrow(() -> router.route(env));

        verify(adapter2).send(env);
        verify(throttledLogger).warning(eq("bot-send"), anyString());
    }

    @Test
    void teardown_clearsAdaptersAndQueue() {
        router.setAdapters(List.of(adapter1));
        router.setup();
        router.teardown();

        var env = MessageEnvelope.publicMessage("after");
        router.route(env);
        verify(adapter1, never()).send(env);
    }

    @Test
    void setAdapters_null_treatedAsEmpty() {
        router.setAdapters(null);
        router.setup();
        var env = MessageEnvelope.publicMessage("ok");
        assertDoesNotThrow(() -> router.route(env));
    }
}
