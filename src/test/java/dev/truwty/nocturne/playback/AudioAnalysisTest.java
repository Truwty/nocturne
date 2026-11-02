package dev.truwty.nocturne.playback;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AudioAnalysisTest {
  private byte[] tone(double frequency, double amplitude) {
    byte[] pcm = new byte[AudioAnalysis.SAMPLES * 2];
    for (int i = 0; i < AudioAnalysis.SAMPLES; i++) {
      short sample = (short) (Math.sin(2 * Math.PI * frequency * i / 44100) * amplitude * 32767);
      pcm[i * 2] = (byte) sample;
      pcm[i * 2 + 1] = (byte) (sample >> 8);
    }
    return pcm;
  }

  @Test
  void separatesActualFrequencyBands() {
    var analyzer = new AudioAnalysis();
    var bass = analyzer.analyze(tone(100, .5), 44100, 1_000_000_000L);
    var mid = analyzer.analyze(tone(1000, .5), 44100, 1_050_000_000L);
    var treble = analyzer.analyze(tone(8000, .5), 44100, 1_100_000_000L);
    assertTrue(bass.bass() > bass.mid() * 100);
    assertTrue(mid.mid() > mid.bass() * 100);
    assertTrue(treble.treble() > treble.mid() * 100);
    assertEquals(.5 / Math.sqrt(2), mid.rms(), .01);
  }

  @Test
  void silenceHasNoInventedTempoOrSpectrum() {
    var analyzer = new AudioAnalysis();
    for (int i = 1; i < 200; i++) {
      var frame = analyzer.analyze(tone(0, 0), 44100, i * 50_000_000L);
      assertEquals(0, frame.bpm());
      assertFalse(frame.onset());
      assertEquals(0, frame.peak());
      for (float level : frame.spectrum()) assertEquals(0, level);
    }
  }

  @Test
  void estimatesRegularOnsetsAndResetsAfterCaptureGap() {
    var analyzer = new AudioAnalysis();
    AudioAnalysis.Frame frame = null;
    for (int i = 1; i <= 100; i++)
      frame = analyzer.analyze(tone(100, i % 10 == 0 ? .8 : 0), 44100, i * 50_000_000L);
    assertNotNull(frame);
    assertEquals(120, frame.bpm(), .1);
    assertTrue(frame.confidence() > .9);
    assertEquals(0, analyzer.analyze(tone(100, .8), 44100, 7_000_000_000L).bpm());
  }

  @Test
  void rejectsInvalidFramesAndProtectsPublishedArrays() {
    var analyzer = new AudioAnalysis();
    assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(new byte[10], 44100, 1));
    var frame = analyzer.analyze(tone(100, .5), 44100, 100);
    var bands = frame.spectrum();
    bands[0] = 100;
    assertTrue(frame.spectrum()[0] <= 1);
    assertFalse(frame.fresh(600_000_100L));
  }

  @Test
  void analysisBudgetCatchesQuadraticTransforms() {
    var analyzer = new AudioAnalysis();
    var pcm = tone(440, .5);
    for (int i = 1; i <= 100; i++) analyzer.analyze(pcm, 44100, i * 50_000_000L);
    assertTimeout(
        java.time.Duration.ofSeconds(5),
        () -> {
          for (int i = 101; i <= 1100; i++) analyzer.analyze(pcm, 44100, i * 50_000_000L);
        });
  }
}
