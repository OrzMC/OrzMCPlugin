package com.jokerhub.paper.plugin.orzmc.core.bot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageEnvelopeTest {

    @Test
    void publicMessage_createsPublicEnvelope() {
        MessageEnvelope env = MessageEnvelope.publicMessage("hello");
        assertEquals(MessageEnvelope.TargetType.PUBLIC, env.targetType());
        assertEquals("hello", env.message());
        assertEquals(MessageEnvelope.Format.DEFAULT, env.format());
    }

    @Test
    void privateMessage_createsPrivateEnvelope() {
        MessageEnvelope env = MessageEnvelope.privateMessage("secret");
        assertEquals(MessageEnvelope.TargetType.PRIVATE, env.targetType());
        assertEquals("secret", env.message());
    }

    @Test
    void withFormat_returnsNewEnvelopeWithUpdatedFormat() {
        MessageEnvelope original = MessageEnvelope.publicMessage("test");
        MessageEnvelope updated = original.withFormat(MessageEnvelope.Format.PLAIN);
        assertNotSame(original, updated);
        assertEquals(MessageEnvelope.Format.PLAIN, updated.format());
        assertEquals(MessageEnvelope.TargetType.PUBLIC, updated.targetType());
        assertEquals("test", updated.message());
    }

    @Test
    void withTargetType_returnsNewEnvelopeWithUpdatedTarget() {
        MessageEnvelope original = MessageEnvelope.publicMessage("test");
        MessageEnvelope updated = original.withTargetType(MessageEnvelope.TargetType.PRIVATE);
        assertNotSame(original, updated);
        assertEquals(MessageEnvelope.TargetType.PRIVATE, updated.targetType());
    }

    @Test
    void immutability_withMethodsCreateNewInstances() {
        MessageEnvelope original = MessageEnvelope.publicMessage("original");
        MessageEnvelope changedTarget = original.withTargetType(MessageEnvelope.TargetType.PRIVATE);
        MessageEnvelope changedFormat = original.withFormat(MessageEnvelope.Format.CODE_BLOCK);

        // Original remains unchanged
        assertEquals(MessageEnvelope.TargetType.PUBLIC, original.targetType());
        assertEquals(MessageEnvelope.Format.DEFAULT, original.format());

        // All modified instances are distinct
        assertNotEquals(original, changedTarget);
        assertNotEquals(original, changedFormat);
    }
}
