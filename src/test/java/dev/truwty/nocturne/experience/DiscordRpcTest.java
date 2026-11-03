package dev.truwty.nocturne.experience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.*;

class DiscordRpcTest {
    @Test
    void framesUseLittleEndianAndRoundTripUnicode() throws Exception {
        var json = new com.google.gson.JsonObject();
        json.addProperty("value", "Nocturne 星");
        var bytes = new ByteArrayOutputStream();
        DiscordRpc.write(new DataOutputStream(bytes), DiscordRpc.json(1, json));
        byte[] payload = bytes.toByteArray();
        assertEquals(1, payload[0]);
        assertEquals(0, payload[1]);
        var decoded = DiscordRpc.read(new DataInputStream(new ByteArrayInputStream(payload)));
        assertEquals(json, decoded.json());
    }

    @Test
    void oversizedFramesAreRejectedBeforeAllocation() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(Integer.reverseBytes(1));
        out.writeInt(Integer.reverseBytes(Integer.MAX_VALUE));
        assertThrows(
                IOException.class,
                () ->
                        DiscordRpc.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void activityContainsNoUserServerOrTrackMetadata() {
        var activity = DiscordRpc.activity(123);
        assertEquals(123, activity.getAsJsonObject("args").get("pid").getAsLong());
        assertEquals(
                java.util.Set.of("type", "details", "state"),
                activity.getAsJsonObject("args").getAsJsonObject("activity").keySet());
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordRpc.handshake("not-an-application-id"));
    }
}
