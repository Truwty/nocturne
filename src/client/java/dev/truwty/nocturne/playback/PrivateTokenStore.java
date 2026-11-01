package dev.truwty.nocturne.playback;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Separate from preferences, preset exports and diagnostic reports. */
public final class PrivateTokenStore {
    private final Path directory;

    public PrivateTokenStore(Path directory) {
        this.directory = directory;
    }

    private static void restrict(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            Files.setPosixFilePermissions(
                    path,
                    Files.isDirectory(path)
                            ? PosixFilePermissions.fromString("rwx------")
                            : PosixFilePermissions.fromString("rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null)
            throw new IOException("Filesystem does not support private credential permissions");
        acl.setAcl(
                List.of(
                        AclEntry.newBuilder()
                                .setType(AclEntryType.ALLOW)
                                .setPrincipal(Files.getOwner(path))
                                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                .build()));
    }

    private byte[] key() throws IOException {
        Files.createDirectories(directory);
        restrict(directory);
        Path file = directory.resolve("key.bin");
        if (!Files.exists(file)) {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            Files.createFile(file);
            restrict(file);
            Files.write(file, key);
        }
        restrict(file);
        if (Files.size(file) != 32) throw new IOException("Credential key is damaged");
        byte[] key = Files.readAllBytes(file);
        if (key.length != 32) throw new IOException("Credential key is damaged");
        return key;
    }

    public synchronized void save(String text) throws IOException {
        if (text.length() > 16384) throw new IOException("Credential data too large");
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] payload = cipher.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] file = new byte[12 + payload.length];
            System.arraycopy(iv, 0, file, 0, 12);
            System.arraycopy(payload, 0, file, 12, payload.length);
            Path temp = Files.createTempFile(directory, "credentials-", ".tmp");
            try {
                restrict(temp);
                Files.write(temp, file);
                try {
                    Files.move(
                            temp,
                            directory.resolve("spotify.bin"),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(
                            temp,
                            directory.resolve("spotify.bin"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                restrict(directory.resolve("spotify.bin"));
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new IOException("Could not protect credentials", ex);
        }
    }

    public synchronized String load() throws IOException {
        Path file = directory.resolve("spotify.bin");
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 32768) throw new IOException("Credential data too large");
        try {
            restrict(file);
            byte[] value = Files.readAllBytes(file);
            if (value.length < 28) throw new IOException("Credential data damaged");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, Arrays.copyOf(value, 12)));
            return new String(
                    cipher.doFinal(value, 12, value.length - 12),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IOException("Credentials cannot be decrypted; reconnect the account", ex);
        }
    }

    public synchronized void clear() throws IOException {
        Files.deleteIfExists(directory.resolve("spotify.bin"));
        Files.deleteIfExists(directory.resolve("key.bin"));
    }
}
