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
        var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(root.has("settings") && !root.get("settings").isJsonObject()) throw new IllegalArgumentException("Invalid settings object");
        if(root.has("enabled") && (!root.get("enabled").isJsonPrimitive() || !root.getAsJsonPrimitive("enabled").isBoolean())) throw new IllegalArgumentException("Invalid enabled value");
        return root;
    }
    public static Loaded load(Path file) throws IOException {
        if(!Files.exists(file)) return new Loaded(new JsonObject(),"",true);
        try {
            var root=parse(file);
            int schema=root.has("schemaVersion")?root.get("schemaVersion").getAsInt():0;
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
                    boolean writable=!restored.has("schemaVersion") || restored.get("schemaVersion").getAsInt()<=SCHEMA;
                    return new Loaded(restored,"Recovered settings from backup; corrupt config preserved",writable);
                }
                catch(RuntimeException|IOException badBackup) {ex.addSuppressed(badBackup);}
            }
            return new Loaded(new JsonObject(),"Invalid config preserved; safe defaults loaded",true);
        }
    }
    public static void save(Path file,String json) throws IOException {
        // Only retain a parseable previous config as the rolling backup.
        if(Files.exists(file)) {
            try {parse(file);Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);}
            catch(RuntimeException|IOException ex) { Files.copy(file,file.resolveSibling(file.getFileName()+".corrupt-"+UUID.randomUUID())); }
        }
        StudioLibrary.write(file,json);
    }
}
