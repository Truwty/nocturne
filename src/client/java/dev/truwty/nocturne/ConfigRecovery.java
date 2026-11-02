package dev.truwty.nocturne;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public final class ConfigRecovery {
    public static final int SCHEMA=1;
    public record Loaded(JsonObject data,String notice,boolean writable) {}
    private ConfigRecovery() {}
    private static JsonObject parse(Path file) throws IOException {
        if(Files.size(file)>1_048_576) throw new IOException("Config exceeds 1 MiB");
        return parse(Files.readString(file));
    }
    private static JsonObject parse(String json) throws IOException {
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1_048_576)throw new IOException("Config exceeds 1 MiB");
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.has("settings") && !root.get("settings").isJsonObject()) throw new IllegalArgumentException("Invalid settings object");
        if(root.has("enabled") && (!root.get("enabled").isJsonPrimitive() || !root.getAsJsonPrimitive("enabled").isBoolean())) throw new IllegalArgumentException("Invalid enabled value");
        schema(root);
        return root;
    }
    private static int schema(JsonObject root) {
        if(!root.has("schemaVersion"))return 0;
        var value=root.get("schemaVersion");
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid config schema");
        int version=value.getAsBigDecimal().intValueExact();
        if(version<0)throw new IllegalArgumentException("Invalid config schema");
        return version;
    }
    public static Loaded load(Path file) throws IOException {
        if(!Files.exists(file)) return new Loaded(new JsonObject(),"",true);
        try {
            var root=parse(file);
            int schema=schema(root);
            if(schema>SCHEMA)return new Loaded(root,"Config is from a newer Nocturne version; saving disabled",false);
            if(schema<SCHEMA) {
                Files.copy(file,file.resolveSibling(file.getFileName()+".pre-v"+SCHEMA),StandardCopyOption.REPLACE_EXISTING);
                root.addProperty("schemaVersion",SCHEMA);
            }
            return new Loaded(root,schema<SCHEMA?"Settings migrated; previous config backed up":"",true);
        } catch(RuntimeException|IOException ex) {
            Files.copy(file,file.resolveSibling(file.getFileName()+".corrupt-"+UUID.randomUUID()));
            Path backup=file.resolveSibling(file.getFileName()+".bak");
            if(Files.exists(backup)) {
                try {
                    var restored=parse(backup);
                    boolean writable=schema(restored)<=SCHEMA;
                    return new Loaded(restored,"Recovered settings from backup; corrupt config preserved",writable);
                }
                catch(RuntimeException|IOException badBackup) {ex.addSuppressed(badBackup);}
            }
            return new Loaded(new JsonObject(),"Invalid config preserved; safe defaults loaded",true);
        }
    }
    public static void save(Path file,String json) throws IOException {
        final JsonObject candidate;
        try { candidate=parse(json); }
        catch(RuntimeException ex) { throw new IOException("Refusing invalid config",ex); }
        if(schema(candidate)>SCHEMA)throw new IOException("Cannot write a newer config schema");
        // Only retain a parseable previous config as the rolling backup.
        if(Files.exists(file)) {
            JsonObject previous=null;
            try {previous=parse(file);}
            catch(RuntimeException|IOException ex) { Files.copy(file,file.resolveSibling(file.getFileName()+".corrupt-"+UUID.randomUUID())); }
            if(previous!=null) {
                if(schema(previous)>SCHEMA)throw new IOException("Newer config is read-only; downgrade refused");
                Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            }
        }
        StudioLibrary.write(file,json);
    }
}
