package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.NocturneClient;

import java.io.*;
import java.util.concurrent.*;

/**
 * Desktop Discord IPC only: no token, account lookup, server address, song title or web request.
 */
public final class DiscordPresence {
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "Nocturne Discord timeout");
                        thread.setDaemon(true);
                        return thread;
                    });
    private static volatile RandomAccessFile pipe;
    private static volatile Thread worker;
    private static volatile String desired = "", status = "Off";

    private DiscordPresence() {}

    public static String status() {
        return status;
    }

    public static synchronized void update(boolean enabled, String id) {
        String next = enabled ? id : "";
        if (!next.isEmpty() && !System.getProperty("os.name", "").startsWith("Windows")) {
            status = "Desktop Discord IPC is available on Windows";
            return;
        }
        if (desired.equals(next)) {
            if (enabled && id.isEmpty()) status = "Set your Discord application ID";
            return;
        }
        desired = next;
        closePipe();
        if (worker != null) worker.interrupt();
        if (next.isEmpty()) {
            status = enabled ? "Set your Discord application ID" : "Off";
            return;
        }
        worker = Thread.ofVirtual().name("Nocturne Discord IPC").start(() -> run(next));
    }

    private static void run(String id) {
        Thread owner = Thread.currentThread();
        while (desired.equals(id) && !owner.isInterrupted()) {
            RandomAccessFile connection = null;
            try {
                status = "Looking for desktop Discord";
                for (int i = 0; i < 10; i++) {
                    try {
                        connection = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                        break;
                    } catch (FileNotFoundException missing) {
                        NocturneClient.LOG.trace("Discord IPC slot {} is not available", i);
                    }
                }
                if (connection == null) throw new IOException("Desktop Discord not detected");
                if (!desired.equals(id) || owner.isInterrupted()) {
                    connection.close();
                    return;
                }
                pipe = connection;
                final RandomAccessFile opened = connection;
                var timeout = WATCHDOG.schedule(() -> close(opened), 5, TimeUnit.SECONDS);
                try {
                    DiscordRpc.write(connection, DiscordRpc.json(0, DiscordRpc.handshake(id)));
                    var ready = DiscordRpc.read(connection);
                    if (ready.opcode() != 1
                            || !ready.json().has("evt")
                            || !"READY".equals(ready.json().get("evt").getAsString()))
                        throw new IOException("Discord did not accept the application ID");
                } finally {
                    timeout.cancel(false);
                }
                DiscordRpc.write(
                        connection,
                        DiscordRpc.json(1, DiscordRpc.activity(ProcessHandle.current().pid())));
                status = "Connected / generic Minecraft presence";
                while (desired.equals(id) && !owner.isInterrupted()) {
                    var frame = DiscordRpc.read(connection);
                    if (frame.opcode() == 2) throw new IOException("Discord closed the connection");
                    if (frame.opcode() == 3)
                        DiscordRpc.write(connection, new DiscordRpc.Frame(4, frame.payload()));
                    if (frame.opcode() == 1) {
                        var response = frame.json();
                        if (response.has("evt")
                                && "ERROR".equals(response.get("evt").getAsString()))
                            throw new IOException(
                                    "Discord rejected the presence; check the application ID");
                    }
                }
            } catch (IOException | RuntimeException error) {
                if (desired.equals(id))
                    status =
                            error.getMessage() == null
                                    ? "Discord IPC unavailable"
                                    : error.getMessage();
                NocturneClient.LOG.debug("Discord presence connection ended", error);
            } finally {
                if (connection != null) close(connection);
                if (pipe == connection) pipe = null;
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException interrupted) {
                owner.interrupt();
            }
        }
    }

    private static void close(RandomAccessFile connection) {
        try {
            connection.close();
        } catch (IOException error) {
            NocturneClient.LOG.debug("Could not close Discord IPC", error);
        }
    }

    private static void closePipe() {
        var active = pipe;
        pipe = null;
        if (active != null) close(active);
    }

    public static synchronized void close() {
        desired = "";
        closePipe();
        if (worker != null) worker.interrupt();
        WATCHDOG.shutdownNow();
    }
}
