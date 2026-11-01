package dev.truwty.nocturne.lyrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LyricsRecoveryTest {
 @Test void offersDifferentFixesForLookupTimingAndPlacement() {
  assertEquals(LyricsRecovery.Action.RETRY,LyricsRecovery.classify(true,true,false,"No lyrics found",false,false,0,true).action());
  assertEquals(LyricsRecovery.Action.RESYNC,LyricsRecovery.classify(true,true,true,"Synced",true,false,5000,true).action());
  assertEquals(LyricsRecovery.Action.SHOW,LyricsRecovery.classify(true,true,true,"Synced",true,true,0,true).action());
 }
 @Test void introsAdvertisementsAndLoadingDoNotTriggerFalsePlacementRepairs() {
  assertEquals(LyricsRecovery.Action.NONE,LyricsRecovery.classify(true,true,true,"Synced",false,true,0,true).action());
  assertEquals(LyricsRecovery.Action.NONE,LyricsRecovery.classify(true,true,false,"Advertisement: waiting",false,false,0,true).action());
  assertEquals(LyricsRecovery.Action.NONE,LyricsRecovery.classify(true,true,false,"Finding lyrics...",false,false,0,true).action());
 }
}
