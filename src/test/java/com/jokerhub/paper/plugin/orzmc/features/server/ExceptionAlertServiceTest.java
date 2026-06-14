package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExceptionAlertServiceTest {

    @Mock private TypedConfigProvider configs;
    @Mock private Notifier notifier;

    private ExceptionAlertService service;

    @BeforeEach
    void setUp() {
        service = new ExceptionAlertService(configs, notifier);
    }

    @Test
    void notify_sendsExceptionAlert() {
        when(configs.renderEvent(eq("exception_alert"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("error detail"));

        service.notify(new ServerException("test exception", new RuntimeException("cause")));

        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }
}
