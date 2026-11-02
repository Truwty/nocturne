package dev.truwty.nocturne;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigRecoveryTest {
 @TempDir Path dir;
 @Test void migratesLegacySettingsWithoutLosingValues() throws Exception {
  Path file=dir.resolve("nocturne.json");Files.writeString(file,"{\"enabled\":false,\"settings\":{\"Size\":150}}");
  var loaded=ConfigRecovery.load(file);assertFalse(loaded.data().get("enabled").getAsBoolean());assertEquals(150,loaded.data().getAsJsonObject("settings").get("Size").getAsInt());assertTrue(Files.exists(dir.resolve("nocturne.json.pre-v1")));
 }
 @Test void recoversLastGoodBackupAndPreservesBadOriginal() throws Exception {
  Path file=dir.resolve("nocturne.json");Files.writeString(file,"{\"schemaVersion\":1,\"enabled\":false}");ConfigRecovery.save(file,"{\"schemaVersion\":1,\"enabled\":true}");Files.writeString(file,"broken");
  var loaded=ConfigRecovery.load(file);assertFalse(loaded.data().get("enabled").getAsBoolean());try(var paths=Files.list(dir)){assertTrue(paths.anyMatch(p->p.toString().contains(".corrupt-")));}
 }
 @Test void futureSchemaIsReadOnly() throws Exception {
  Path file=dir.resolve("nocturne.json");Files.writeString(file,"{\"schemaVersion\":99,\"settings\":{}}");assertFalse(ConfigRecovery.load(file).writable());
 }
 @Test void refusesMalformedSaveWithoutTouchingConfigOrBackup() throws Exception {
  Path file=dir.resolve("nocturne.json"),backup=dir.resolve("nocturne.json.bak");
  String original="{\"schemaVersion\":1,\"enabled\":false}";
  Files.writeString(file,original);Files.writeString(backup,"previous backup");
  for(String candidate:new String[]{"broken","{\"settings\":[]}","{\"schemaVersion\":-1}","{\"schemaVersion\":1.5}","{\"schemaVersion\":\"1\"}","{\"schemaVersion\":999999999999}"}) {
   assertThrows(java.io.IOException.class,()->ConfigRecovery.save(file,candidate));
   assertEquals(original,Files.readString(file));assertEquals("previous backup",Files.readString(backup));
  }
 }
 @Test void saveCannotOverwriteFutureConfigEvenIfCallerIgnoresReadOnlyFlag() throws Exception {
  Path file=dir.resolve("nocturne.json");String future="{\"schemaVersion\":99,\"settings\":{\"future\":true}}";Files.writeString(file,future);
  assertThrows(java.io.IOException.class,()->ConfigRecovery.save(file,"{\"schemaVersion\":1}"));
  assertEquals(future,Files.readString(file));assertFalse(Files.exists(dir.resolve("nocturne.json.bak")));
 }
 @Test void invalidSchemaRecoversBackupInsteadOfTruncatingFractionalVersion() throws Exception {
  Path file=dir.resolve("nocturne.json");Files.writeString(file,"{\"schemaVersion\":1.5}");Files.writeString(dir.resolve("nocturne.json.bak"),"{\"schemaVersion\":1,\"enabled\":false}");
  assertFalse(ConfigRecovery.load(file).data().get("enabled").getAsBoolean());
 }
}
