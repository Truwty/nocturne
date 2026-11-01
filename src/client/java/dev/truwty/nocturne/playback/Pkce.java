package dev.truwty.nocturne.playback;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public record Pkce(String verifier, String state, long deadline) {
    public static Pkce create(long now) {
        return new Pkce(random(64), random(32), now + 300000);
    }

    private static String random(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String challenge() {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Map<String, String> query(String query) {
        if (query == null || query.length() > 8192)
            throw new IllegalArgumentException("Invalid OAuth response");
        var result = new HashMap<String, String>();
        for (String item : query.split("&")) {
            String[] p = item.split("=", 2);
            String key = URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                    value = p.length == 2 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "";
            if (result.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Duplicate OAuth parameter");
        }
        return result;
    }

    public boolean accepts(String value, long now) {
        return value != null
                && now <= deadline
                && MessageDigest.isEqual(
                        state.getBytes(StandardCharsets.US_ASCII),
                        value.getBytes(StandardCharsets.US_ASCII));
    }
}
