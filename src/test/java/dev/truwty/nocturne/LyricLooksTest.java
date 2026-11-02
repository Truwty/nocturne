package dev.truwty.nocturne;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LyricLooksTest {
 @Test void ships57DistinctNamesAndLayoutsRatherThanOnlyRecolours() {
  assertEquals(57,LyricLooks.all().size());
  var names=new HashSet<String>();var layouts=new HashSet<com.google.gson.JsonObject>();
  for(var look:LyricLooks.all()) {
   assertTrue(names.add(look.name()),look.name());var values=look.values();values.remove("Colour");values.remove("Second colour");
   assertTrue(layouts.add(values),"Duplicate non-colour look: "+look.name());
   assertEquals(LyricLooks.named("Perfection").values().keySet(),look.values().keySet());
  }
 }
 @Test void perfectionLocksPlacementAndRemovesDisruptiveEffects() {
  var p=LyricLooks.named("Perfection").values();assertTrue(p.get("Stable lyric placement").getAsBoolean());
  assertEquals("Follow view",p.get("Placement").getAsString());assertEquals("Karaoke",p.get("Animation").getAsString());
  for(String name:List.of("Animated lyric text","Sparkles","Beat zoom","Shake effects","Flash effects","Battle words when fighting","Orbit you in F5 front view"))assertFalse(p.get(name).getAsBoolean(),name);
  assertEquals(0,p.get("Lines stay for").getAsDouble());
 }
 @Test void catalogPreservesTimingConnectionsAndVisibilityPreferences() {
  for(String name:List.of("Timing offset","Hide lyrics in chat","Automatic context profiles","Photosensitivity-friendly mode","Reduced motion","Automatic update checks"))assertFalse(LyricLooks.controls(name),name);
 }
 @Test void exposedValuesCannotMutateBuiltInPresets() {
  var first=LyricLooks.named("Perfection").values();first.addProperty("Size",200);
  assertEquals(100,LyricLooks.named("Perfection").values().get("Size").getAsInt());
  assertThrows(UnsupportedOperationException.class,()->LyricLooks.all().clear());
 }
 @Test void searchFindsFamiliesNamesAndDescriptionsWithoutLosingOtherCategories() {
  assertEquals("Perfection",LyricLooks.search("perfection","All").getFirst().name());
  assertEquals(7,LyricLooks.search("","Cloud").size());
  assertTrue(LyricLooks.search("does-not-exist","All").isEmpty());
 }
}
