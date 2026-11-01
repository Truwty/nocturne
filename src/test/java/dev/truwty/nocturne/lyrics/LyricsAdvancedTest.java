package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonObject;
import dev.truwty.nocturne.StudioLibrary;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LyricsAdvancedTest {
    @TempDir Path directory;
    private static final String ENHANCED="[00:01.000]<00:01.000>Hello <00:01.500>world<00:02.500>\n[00:03.000]\n[00:08.000]<00:08.000>Again<00:09.000>\n[00:10.000]";
    private static final String YAML="""
        version: "1.0"
        metadata:
          title: Fixture
          artist: Nocturne
        lines:
          - text: "Hello world"
            start_ms: 1000
            end_ms: 3000
            words:
              - {text: "Hello ", start_ms: 1000, end_ms: 1400}
              - {text: "world", start_ms: 1500, end_ms: 2500}
        """;
    @Test void lyricsfilePreservesIndependentWordEndsAndWhitespace(){
        var lyrics=LyricsFormats.lyricsfile(YAML);var words=lyrics.lines.getFirst().words();
        assertEquals("Hello ",words.getFirst().text());assertEquals(1400,words.getFirst().end());assertEquals(2500,words.getLast().end());assertTrue(words.getFirst().sourceTimed());
        assertEquals(.5f,words.getFirst().progress(1200),.001);assertTrue(lyrics.confidence().contains("Word synchronized"));
    }
    @Test void lyricsfileFallsBackToLegacyWhenMalformed(){
        var record=new JsonObject();record.addProperty("lyricsfile","version: broken");record.addProperty("syncedLyrics",ENHANCED);
        assertEquals(1000,LyricsFormats.fromRecord(record).lines.getFirst().start());
    }
    @Test void yamlRejectsTagsDuplicateKeysAliasesAndInvalidBounds(){
        assertThrows(RuntimeException.class,()->LyricsFormats.lyricsfile("!!java.net.URL [https://example.com]"));
        assertThrows(RuntimeException.class,()->LyricsFormats.lyricsfile(YAML+"version: '1.0'"));
        assertThrows(RuntimeException.class,()->LyricsFormats.lyricsfile(YAML.replace("end_ms: 1400","end_ms: 999")));
        assertThrows(RuntimeException.class,()->LyricsFormats.lyricsfile("version: '1.0'\nlines: &a [*a]"));
    }
    @Test void lyricsfileSegmentsMustReproduceText(){assertThrows(IllegalArgumentException.class,()->LyricsFormats.lyricsfile(YAML.replace("Hello world","Wrong text")));}
    @Test void enhancedRoundTripRetainsWordsAndCountdownGaps(){
        var lyrics=Lyrics.parse(ENHANCED);var round=Lyrics.parse(lyrics.enhancedLrc());assertEquals(lyrics.lines,round.lines);
        assertEquals(1000,lyrics.nextVocal(0));assertEquals(-1,lyrics.nextVocal(1500));assertEquals(8000,lyrics.nextVocal(5000));assertEquals(-1,lyrics.nextVocal(15000));
        assertEquals("Hello world",lyrics.chunked(1).lines.getFirst().text()+lyrics.chunked(1).lines.get(1).text());
    }
    @Test void repeatedEnhancedLinesShiftAbsoluteWordMarks(){var lyrics=Lyrics.parse("[00:01][00:10]<00:01>One <00:02>two<00:03>");assertEquals(10000,lyrics.lines.get(1).words().getFirst().start());}
    @Test void offsetsMoveLinesAndWordsTogether(){var lyrics=Lyrics.parse("[offset:-500]\n"+ENHANCED);assertEquals(500,lyrics.lines.getFirst().start());assertEquals(1000,lyrics.lines.getFirst().words().getLast().start());}
    @Test void invalidTimestampsAreRejected(){assertThrows(IllegalArgumentException.class,()->Lyrics.parse("[00:99]Bad"));assertThrows(IllegalArgumentException.class,()->Lyrics.parse("[00:02]<00:01>Bad"));}
    @Test void estimatesRemainEstimatesAcrossEnhancedExport(){var lyrics=Lyrics.fromPlain("One two\nThree four",30000);var round=Lyrics.parse(lyrics.enhancedLrc());assertTrue(round.estimated);assertTrue(round.lines.stream().flatMap(l->l.words().stream()).noneMatch(Lyrics.Word::sourceTimed));}
    @Test void translationAndRomanizationMatchOnlyNearbyLines(){
        var lyrics=Lyrics.parse(ENHANCED).secondary(Lyrics.parse("[00:01.200]مرحبا بالعالم\n[00:30]Unrelated"),Lyrics.parse("[00:01]marhaban"));
        assertEquals("مرحبا بالعالم",lyrics.lines.getFirst().translation());assertEquals("marhaban",lyrics.lines.getFirst().romanization());assertEquals("",lyrics.lines.getLast().translation());
        assertTrue(lyrics.secondaryLrc(false).contains("مرحبا"));
    }
    @Test void providerWordTimingIncludesLanguageLayers(){
        var root=new JsonObject();for(var pair:List.of(new String[]{"yrc","[1000,3000](1000,400,0)Hello (1500,1000,0)world"},new String[]{"tlyric","[00:01]こんにちは"},new String[]{"romalrc","[00:01]konnichiwa"})){var row=new JsonObject();row.addProperty("lyric",pair[1]);root.add(pair[0],row);}
        var lyrics=LyricsFormats.netease(root);assertEquals(1400,lyrics.lines.getFirst().words().getFirst().end());assertEquals("こんにちは",lyrics.lines.getFirst().translation());assertEquals("konnichiwa",lyrics.lines.getFirst().romanization());
    }
    @Test void editingWordBoundsIsUndoableAndPreservesLanguages(){
        var source=Lyrics.parse(ENHANCED).secondary(Lyrics.parse("[00:01]Translation"),null);var doc=new TimingDocument(source);
        doc.word(0,0,1100,1450);assertTrue(doc.lyrics().manual);assertEquals("Translation",doc.lyrics().lines.getFirst().translation());assertTrue(doc.undo());assertEquals(source.lines,doc.lyrics().lines);assertTrue(doc.redo());assertEquals(1100,doc.lyrics().lines.getFirst().words().getFirst().start());
    }
    @Test void lineEditsShiftAllWordsWithoutChangingDurations(){var doc=new TimingDocument(Lyrics.parse(ENHANCED));doc.line(0,1200,3200);assertEquals(1700,doc.lyrics().lines.getFirst().words().getLast().start());assertThrows(IllegalArgumentException.class,()->doc.line(0,9000,12000));}
    @Test void rejectsInvalidWordBoundsWithoutChangingDocument(){var doc=new TimingDocument(Lyrics.parse(ENHANCED));var before=doc.lyrics();assertThrows(IllegalArgumentException.class,()->doc.word(0,0,1600,1700));assertSame(before,doc.lyrics());assertThrows(IllegalArgumentException.class,()->doc.word(0,1,1400,4000));}
    @Test void editingOneWordDoesNotClaimEstimatedLinesAreSynchronized(){var doc=new TimingDocument(Lyrics.fromPlain("One two\nThree four",30000));var first=doc.lyrics().lines.getFirst().words().getFirst();doc.word(0,0,first.start(),first.end());assertTrue(doc.lyrics().estimated);assertTrue(doc.lyrics().manual);}
    @Test void dragMakesOneUndoStep(){var source=Lyrics.parse(ENHANCED);var doc=new TimingDocument(source);doc.previewWord(0,0,1050,1400);doc.previewWord(0,0,1100,1400);doc.commitPreview(source);assertTrue(doc.undo());assertEquals(source.lines,doc.lyrics().lines);assertFalse(doc.undo());}
    @Test void losslessDocumentRetainsEndsLanguagesAndProvenance(){var lyrics=LyricsFormats.lyricsfile(YAML).secondary(Lyrics.parse("[00:01]שלום"),null).manuallyCorrected();var copy=LyricDocumentCodec.decode(LyricDocumentCodec.encode(lyrics));assertEquals(lyrics.lines,copy.lines);assertTrue(copy.manual);}
    @Test void localEditsSurviveRestartAndAutomaticChoiceReplacesThem()throws Exception{
        var library=new StudioLibrary(directory);var lyrics=LyricsFormats.lyricsfile(YAML).manuallyCorrected();library.saveDocument("song",lyrics);assertEquals(lyrics.lines,new StudioLibrary(directory).local("song").lines);
        library.chooseLyrics("song","[00:02]Replacement");assertEquals("Replacement",library.local("song").lines.getFirst().text());assertFalse(Files.exists(library.documentPath("song")));
    }
    @Test void sidecarsPersistWithoutChangingSourceTiming()throws Exception{
        var library=new StudioLibrary(directory);var sidecar=directory.resolve("translation.lrc");Files.writeString(sidecar,"[00:01]翻訳");library.importSecondary("song",sidecar,false);
        var lyrics=library.secondary("song",Lyrics.parse(ENHANCED));assertEquals("翻訳",lyrics.lines.getFirst().translation());assertFalse(lyrics.manual);
    }
    @Test void songProfilesAreIsolatedAndRemovable()throws Exception{
        var library=new StudioLibrary(directory);var settings=new JsonObject();settings.addProperty("Size",125);String id=StudioLibrary.trackId("song");library.preset("songAppearance",id,settings);
        assertEquals(125,new StudioLibrary(directory).preset("songAppearance",id).get("Size").getAsInt());assertNull(library.preset("songAppearance",StudioLibrary.trackId("other")));library.removePreset("songAppearance",id);assertNull(library.preset("songAppearance",id));
    }
    @Test void rtlVisualOrderKeepsLogicalTimingIndexes(){
        var words=List.of(new Lyrics.Word("שלום",1000,1200),new Lyrics.Word("עולם",1200,1400));assertEquals(List.of(1,0),LyricText.order(words,List.of(0,1)));
    }
}
