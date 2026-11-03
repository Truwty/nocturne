package dev.truwty.nocturne.experience;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DiscordRpc {
    public static final int MAX_PAYLOAD = 65536;

    public record Frame(int opcode, byte[] payload) {
        public Frame {
            if (opcode < 0 || opcode > 4 || payload.length > MAX_PAYLOAD)
                throw new IllegalArgumentException("Invalid Discord IPC frame");
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        public JsonObject json() {
            return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private DiscordRpc() {}

    public static Frame json(int opcode, JsonObject value) {
        return new Frame(opcode, value.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void write(DataOutput output, Frame frame) throws IOException {
        output.writeInt(Integer.reverseBytes(frame.opcode));
        output.writeInt(Integer.reverseBytes(frame.payload.length));
        output.write(frame.payload);
    }

    public static Frame read(DataInput input) throws IOException {
        int opcode = Integer.reverseBytes(input.readInt()),
                length = Integer.reverseBytes(input.readInt());
        if (opcode < 0 || opcode > 4 || length < 0 || length > MAX_PAYLOAD)
            throw new IOException("Invalid Discord IPC header");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new Frame(opcode, bytes);
    }

    public static JsonObject handshake(String id) {
        if (!id.matches("[0-9]{17,20}"))
            throw new IllegalArgumentException("Use a Discord application ID, not a token");
        var value = new JsonObject();
        value.addProperty("v", 1);
        value.addProperty("client_id", id);
        return value;
    }

    public static JsonObject activity(long pid) {
        var activity = new JsonObject();
        activity.addProperty("type", 0);
        activity.addProperty("details", "Playing Minecraft");
        activity.addProperty("state", "Nocturne / your world, your soundtrack");
        var args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", activity);
        var command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        return command;
    }
}
