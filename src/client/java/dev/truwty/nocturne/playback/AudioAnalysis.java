package dev.truwty.nocturne.playback;

import java.util.*;

/** Hann-windowed 2048 point FFT of actual, isolated application PCM. Called by the media reader. */
public final class AudioAnalysis {
  public static final int SAMPLES = 2048, BANDS = 32;

  public record Frame(
      float peak,
      float rms,
      float bass,
      float mid,
      float treble,
      float[] spectrum,
      float[] waveform,
      double bpm,
      double confidence,
      boolean onset,
      long received) {
    public Frame {
      spectrum = spectrum.clone();
      waveform = waveform.clone();
    }

    @Override
    public float[] spectrum() {
      return spectrum.clone();
    }

    @Override
    public float[] waveform() {
      return waveform.clone();
    }

    public boolean fresh(long now) {
      return now >= received && now - received < 500_000_000L;
    }
  }

  private final double[] real = new double[SAMPLES],
      imaginary = new double[SAMPLES],
      previous = new double[SAMPLES / 2];
  private final ArrayDeque<Double> intervals = new ArrayDeque<>();
  private long last, lastOnset;
  private double fluxMean, previousRms;

  public synchronized void reset() {
    last = lastOnset = 0;
    fluxMean = previousRms = 0;
    intervals.clear();
    Arrays.fill(previous, 0);
  }

  public synchronized Frame analyze(byte[] pcm, int rate, long now) {
    if (pcm.length != SAMPLES * 2 || rate < 8000 || rate > 192000 || now < 0)
      throw new IllegalArgumentException("Invalid PCM frame");
    if (last != 0 && (now <= last || now - last >= 500_000_000L)) reset();
    double peak = 0, sum = 0;
    var wave = new float[64];
    for (int i = 0; i < SAMPLES; i++) {
      double sample = (short) ((pcm[i * 2] & 255) | (pcm[i * 2 + 1] << 8)) / 32768.0;
      peak = Math.max(peak, Math.abs(sample));
      sum += sample * sample;
      real[i] = sample * (.5 - .5 * Math.cos(2 * Math.PI * i / (SAMPLES - 1)));
      imaginary[i] = 0;
      if (i % 32 == 0) wave[i / 32] = (float) sample;
    }
    fft();
    double rms = Math.sqrt(sum / SAMPLES), flux = 0, bass = 0, mid = 0, treble = 0;
    var spectrum = new float[BANDS];
    double[] energy = new double[SAMPLES / 2];
    for (int i = 1; i < energy.length; i++) {
      double magnitude = Math.hypot(real[i], imaginary[i]) * 4 / SAMPLES;
      energy[i] = magnitude * magnitude;
      flux += Math.max(0, magnitude - previous[i]);
      previous[i] = magnitude;
      double hz = (double) i * rate / SAMPLES;
      if (hz >= 20 && hz < 250) bass += energy[i];
      else if (hz < 2000) mid += energy[i];
      else if (hz < 16000) treble += energy[i];
    }
    for (int band = 0; band < BANDS; band++) {
      double low = 20 * Math.pow(800, band / (double) BANDS),
          high = 20 * Math.pow(800, (band + 1.0) / BANDS);
      int first = Math.max(1, (int) Math.ceil(low * SAMPLES / rate));
      int end =
          Math.min(energy.length, Math.max(first + 1, (int) Math.ceil(high * SAMPLES / rate)));
      double total = 0;
      for (int i = first; i < end; i++) total += energy[i];
      spectrum[band] = (float) Math.min(1, Math.sqrt(total));
    }
    boolean onset =
        last != 0
            && rms > .012
            && (rms > previousRms * 1.6 || flux > Math.max(.08, fluxMean * 2.0))
            && (lastOnset == 0 || now - lastOnset >= 250_000_000L);
    if (onset) {
      if (lastOnset != 0) {
        double interval = (now - lastOnset) / 1e9;
        if (interval >= .25 && interval <= 1.5) {
          intervals.addLast(interval);
          while (intervals.size() > 8) intervals.removeFirst();
        } else intervals.clear();
      }
      lastOnset = now;
    }
    if (lastOnset != 0 && now - lastOnset > 2_000_000_000L) intervals.clear();
    fluxMean = fluxMean * .9 + flux * .1;
    previousRms = rms;
    last = now;
    double bpm = 0, confidence = 0;
    if (intervals.size() >= 4) {
      double mean = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
      double variance =
          intervals.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(1);
      confidence = Math.clamp(1 - Math.sqrt(variance) / mean * 4, 0, 1);
      if (confidence >= .6) bpm = 60 / mean;
    }
    return new Frame(
        (float) peak,
        (float) rms,
        level(bass),
        level(mid),
        level(treble),
        spectrum,
        wave,
        bpm,
        confidence,
        onset,
        now);
  }

  private static float level(double squared) {
    return (float) Math.min(1, Math.sqrt(squared));
  }

  private void fft() {
    for (int i = 1, j = 0; i < SAMPLES; i++) {
      int bit = SAMPLES >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double value = real[i];
        real[i] = real[j];
        real[j] = value;
      }
    }
    for (int length = 2; length <= SAMPLES; length <<= 1) {
      double angle = -2 * Math.PI / length,
          stepReal = Math.cos(angle),
          stepImaginary = Math.sin(angle);
      for (int offset = 0; offset < SAMPLES; offset += length) {
        double wr = 1, wi = 0;
        for (int j = 0; j < length / 2; j++) {
          int a = offset + j, b = a + length / 2;
          double r = real[b] * wr - imaginary[b] * wi, im = real[b] * wi + imaginary[b] * wr;
          real[b] = real[a] - r;
          imaginary[b] = imaginary[a] - im;
          real[a] += r;
          imaginary[a] += im;
          double next = wr * stepReal - wi * stepImaginary;
          wi = wr * stepImaginary + wi * stepReal;
          wr = next;
        }
      }
    }
  }
}
