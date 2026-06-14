package com.jokerhub.paper.plugin.orzmc.core.bot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageEnvelopeTest {

    @Test
    void publicMessage_createsPublicEnvelope() {
        MessageEnvelope env = MessageEnvelope.publicMessage("hello");
        assertEquals(MessageEnvelope.TargetType.PUBLIC, env.targetType());
        assertEquals("hello", env.message());
        assertNull(env.channelKey());
        assertEquals(MessageEnvelope.Format.DEFAULT, env.format());
    }

    @Test
    void privateMessage_createsPrivateEnvelope() {
        MessageEnvelope env = MessageEnvelope.privateMessage("secret");
        assertEquals(MessageEnvelope.TargetType.PRIVATE, env.targetType());
        assertEquals("secret", env.message());
    }

    @Test
    void channelMessage_createsChannelEnvelope() {
        MessageEnvelope env = MessageEnvelope.channelMessage("admin-channel", "broadcast");
        assertEquals(MessageEnvelope.TargetType.CHANNEL, env.targetType());
        assertEquals("admin-channel", env.channelKey());
        assertEquals("broadcast", env.message());
    }

    @Test
    void channelMessage_withFormat_createsFormattedEnvelope() {
        MessageEnvelope env =
                MessageEnvelope.channelMessage("logs", "error msg", MessageEnvelope.Format.CODE_BLOCK);
        assertEquals(MessageEnvelope.TargetType.CHANNEL, env.targetType());
        assertEquals("logs", env.channelKey());
        assertEquals(MessageEnvelope.Format.CODE_BLOCK, env.format());
    }

    @Test
    void withFormat_returnsNewEnvelopeWithUpdatedFormat() {
        MessageEnvelope original = MessageEnvelope.publicMessage("test");
        MessageEnvelope updated = original.withFormat(MessageEnvelope.Format.PLAIN);
        assertNotSame(original, updated);
        assertEquals(MessageEnvelope.Format.PLAIN, updated.format());
        assertEquals(MessageEnvelope.TargetType.PUBLIC, updated.targetType());
        assertEquals("test", updated.message());
        assertNull(updated.channelKey());
    }

    @Test
    void withTargetType_returnsNewEnvelopeWithUpdatedTarget() {
        MessageEnvelope original = MessageEnvelope.publicMessage("test");
        MessageEnvelope updated = original.withTargetType(MessageEnvelope.TargetType.PRIVATE);
        assertNotSame(original, updated);
        assertEquals(MessageEnvelope.TargetType.PRIVATE, updated.targetType());
    }

    @Test
    void withChannelKey_returnsNewEnvelopeWithUpdatedKey() {
        MessageEnvelope original = MessageEnvelope.publicMessage("test");
        MessageEnvelope updated = original.withChannelKey("mod-channel");
        assertNotSame(original, updated);
        assertEquals("mod-channel", updated.channelKey());
    }

    @Test
    void immutability_withMethodsCreateNewInstances() {
        MessageEnvelope original = MessageEnvelope.publicMessage("original");
        MessageEnvelope changedTarget = original.withTargetType(MessageEnvelope.TargetType.CHANNEL);
        MessageEnvelope changedFormat = original.withFormat(MessageEnvelope.Format.CODE_BLOCK);
        MessageEnvelope changedChannel = original.withChannelKey("key");

        // Original remains unchanged
        assertEquals(MessageEnvelope.TargetType.PUBLIC, original.targetType());
        assertEquals(MessageEnvelope.Format.DEFAULT, original.format());
        assertNull(original.channelKey());

        // All modified instances are distinct
        assertNotEquals(original, changedTarget);
        assertNotEquals(original, changedFormat);
        assertNotEquals(original, changedChannel);
    }
}
