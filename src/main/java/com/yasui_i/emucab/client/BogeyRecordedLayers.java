package com.yasui_i.emucab.client;

import com.yasui_i.emucab.EMUCab;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Background-only decoding of existing dependency textures. No dependency OGG
 * is copied into this mod. Returned arrays are shared read-only PCM at 48 kHz.
 */
@EventBusSubscriber(modid = EMUCab.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BogeyRecordedLayers {
    public record Layers(float[] mechanical, float[] wind) {}
    public static final Layers EMPTY = new Layers(new float[0], new float[0]);
    private static final ResourceLocation MECHANICAL = ResourceLocation.parse("create:sounds/train.ogg");
    private static final ResourceLocation WIND = ResourceLocation.parse("electroenergetics:sounds/train/background/wind_static.ogg");
    private static final int RATE = 48000;
    private static final int MAX_DECODED_FLOATS = 16 * 1024 * 1024;
    private static final long MAX_ENCODED_BYTES = 64L * 1024 * 1024;
    private static CompletableFuture<Layers> cached;
    private static long generation;

    /** Starts work once per resource generation; never waits on the caller. */
    public static synchronized CompletableFuture<Layers> load() {
        if (cached == null) cached = load(Minecraft.getInstance().getResourceManager());
        return cached;
    }

    /** Independent provider overload also permits resource/fallback integration checks. */
    public static CompletableFuture<Layers> load(ResourceProvider resources) {
        return CompletableFuture.supplyAsync(() -> decode(resources), Util.nonCriticalIoPool());
    }

    public static synchronized long generation() { return generation; }

    /** Running sources should pick up the next load result when generation changes. */
    public static synchronized void reset() {
        cached = null;
        generation++;
    }

    @SubscribeEvent public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Layers>() {
            @Override protected Layers prepare(ResourceManager resources, ProfilerFiller profiler) {
                return decode(resources);
            }

            @Override protected void apply(Layers layers, ResourceManager resources, ProfilerFiller profiler) {
                synchronized (BogeyRecordedLayers.class) {
                    cached = CompletableFuture.completedFuture(layers);
                    generation++;
                }
            }
        });
    }

    private static Layers decode(ResourceProvider resources) {
        return new Layers(read(resources, MECHANICAL, 90, 3800, true),
                read(resources, WIND, 120, 4500, false));
    }

    private static float[] read(ResourceProvider resources, ResourceLocation id, double low, double high, boolean required) {
        var found = resources.getResource(id);
        if (found.isEmpty()) {
            if (required) EMUCab.LOGGER.warn("VVVF mechanical texture {} missing; using synthesized fallback", id);
            return new float[0]; // CEE is optional; absence is an ordinary fallback.
        }
        try (InputStream input = new BoundedInput(found.get().open());
             JOrbisAudioStream stream = new JOrbisAudioStream(input)) {
            var format = stream.getFormat();
            int channels = format.getChannels();
            double sampleRate = format.getSampleRate();
            if (channels < 1 || channels > 8 || sampleRate < 8000 || sampleRate > 192000)
                throw new IOException("Unsupported texture format: " + format);
            var values = new FloatArrayList();
            while (stream.readChunk(sample -> {
                if (!Float.isFinite(sample)) throw new IllegalArgumentException("Non-finite decoded texture");
                if (values.size() >= MAX_DECODED_FLOATS) throw new IllegalArgumentException("Decoded texture exceeds 64 MiB");
                values.add(sample);
            })) { /* JOrbis writes Java floats directly; no native PCM buffer is retained. */ }
            int frames = values.size() / channels;
            if (frames < 32) throw new IOException("Texture too short");
            int outputLength = (int) Math.round((double) frames * RATE / sampleRate);
            if (outputLength < 32 || outputLength > MAX_DECODED_FLOATS)
                throw new IOException("Resampled texture exceeds bound");
            float[] mono = new float[frames];
            for (int i = 0; i < frames; i++) {
                double sum = 0;
                for (int ch = 0; ch < channels; ch++) sum += values.getFloat(i * channels + ch);
                mono[i] = (float) (sum / channels);
            }
            // Low-pass before downsampling; linear interpolation is sufficient
            // for the band-limited noise layers, not used for motor tonal pitch.
            if (sampleRate > RATE) filter(mono, sampleRate, 0, Math.min(high, RATE * .4));
            float[] output = new float[outputLength];
            for (int i = 0; i < outputLength; i++) {
                double position = i * sampleRate / RATE;
                int first = Math.min((int) position, frames - 1);
                int second = Math.min(first + 1, frames - 1);
                output[i] = (float) (mono[first] + (mono[second] - mono[first]) * (position - first));
            }
            removeDc(output);
            filter(output, RATE, low, high);
            // Suppress the startup response rather than repeating it each loop.
            int trim = Math.min(RATE / 20, output.length / 16);
            float[] stable = java.util.Arrays.copyOfRange(output, trim, output.length);
            removeDc(stable);
            float[] loop = loop(stable);
            removeDc(loop);
            if (id.equals(MECHANICAL)) {
                suppressMechanicalImpacts(loop);
                removeDc(loop);
            }
            double energy = 0, peak = 0;
            for (float sample : loop) { energy += sample * (double) sample; peak = Math.max(peak, Math.abs(sample)); }
            double rms = Math.sqrt(energy / loop.length);
            if (rms < 1e-7) throw new IOException("Silent texture");
            double gain = Math.min(.1 / rms, .92 / Math.max(peak, 1e-9));
            for (int i = 0; i < loop.length; i++) loop[i] *= (float) gain;
            EMUCab.LOGGER.debug("VVVF texture {} decoded to {} loop samples", id, loop.length);
            return loop;
        } catch (Exception error) {
            EMUCab.LOGGER.warn("Could not load VVVF texture {}; using synthesized fallback: {}", id, error.toString());
            return new float[0];
        }
    }

    /** A short equal-power-shaped amplitude crossfade at the cyclic boundary. */
    private static float[] loop(float[] input) {
        int overlap = Math.min((int) (.12 * RATE), input.length / 4);
        int length = input.length - overlap;
        float[] output = java.util.Arrays.copyOfRange(input, overlap, input.length);
        for (int i = 0; i < overlap; i++) {
            double mix = Math.sin(i * Math.PI / (2 * Math.max(1, overlap - 1)));
            mix *= mix;
            output[length - overlap + i] = (float) (input[input.length - overlap + i] * (1 - mix) + input[i] * mix);
        }
        return output;
    }

    private static void removeDc(float[] data) {
        double mean = 0;
        for (float sample : data) mean += sample;
        mean /= data.length;
        for (int i = 0; i < data.length; i++) data[i] -= (float) mean;
    }

    /**
     * The train texture contains rail-joint thumps. Keep its continuous spectrum
     * but compress those bursts before the loop is shared by moving bogeys.
     * Circular lookahead and gain smoothing make this safe across the loop seam;
     * this preparation runs once on the resource worker, never per audio sample.
     */
    private static void suppressMechanicalImpacts(float[] data) {
        int window = Math.min(RATE / 50, data.length);
        float[] envelope = circularMean(data, window, true);
        for (int i = 0; i < envelope.length; i++) envelope[i] = (float) Math.sqrt(Math.max(0, envelope[i]));
        float[] ordered = envelope.clone();
        java.util.Arrays.sort(ordered);
        double threshold = ordered[ordered.length / 2];
        if (threshold < 1e-8) return;
        // No upward expansion of quiet sections: only reduce impact energy.
        for (int i = 0; i < envelope.length; i++)
            envelope[i] = (float) Math.min(1, threshold / Math.max(envelope[i], 1e-8));
        float[] gain = circularMean(envelope, Math.min(RATE / 200, data.length), false);
        for (int i = 0; i < data.length; i++) data[i] *= gain[i];
    }

    /** Centered cyclic box window, with double accumulation for decoded PCM. */
    private static float[] circularMean(float[] data, int window, boolean squared) {
        float[] result = new float[data.length];
        int left = window / 2, right = window - left;
        double sum = 0;
        for (int i = -left; i < right; i++) {
            double value = data[(i + data.length) % data.length];
            sum += squared ? value * value : value;
        }
        for (int i = 0; i < data.length; i++) {
            result[i] = (float) (sum / window);
            double remove = data[(i - left + data.length) % data.length];
            double add = data[(i + right) % data.length];
            sum += squared ? add * add - remove * remove : add - remove;
        }
        return result;
    }

    /** Cascaded third-order high/low pass, close to the offline texture bands. */
    private static void filter(float[] data, double rate, double low, double high) {
        if (low > 0) {
            biquad(data, rate, low, true);
            firstOrder(data, rate, low, true);
        }
        biquad(data, rate, high, false);
        firstOrder(data, rate, high, false);
    }

    private static void biquad(float[] data, double rate, double hz, boolean high) {
        double w = 2 * Math.PI * hz / rate, c = Math.cos(w), alpha = Math.sin(w) / 2;
        double a0 = 1 + alpha, a1 = -2 * c / a0, a2 = (1 - alpha) / a0;
        double b0 = (1 + (high ? c : -c)) / (2 * a0);
        double b1 = (high ? -2 : 2) * b0, b2 = b0, z1 = 0, z2 = 0;
        for (int i = 0; i < data.length; i++) {
            double value = b0 * data[i] + z1;
            z1 = b1 * data[i] - a1 * value + z2;
            z2 = b2 * data[i] - a2 * value;
            data[i] = (float) value;
        }
    }

    private static void firstOrder(float[] data, double rate, double hz, boolean high) {
        double k = Math.tan(Math.PI * hz / rate), a1 = (k - 1) / (k + 1);
        double b0 = (high ? 1 : k) / (1 + k), b1 = high ? -b0 : b0;
        double previous = 0, output = 0;
        for (int i = 0; i < data.length; i++) {
            double next = b0 * data[i] + b1 * previous - a1 * output;
            previous = data[i]; output = next; data[i] = (float) next;
        }
    }

    private static final class BoundedInput extends FilterInputStream {
        private long read;
        private BoundedInput(InputStream input) { super(input); }
        private void count(int n) throws IOException {
            if (n > 0 && (read += n) > MAX_ENCODED_BYTES) throw new IOException("Encoded texture exceeds 64 MiB");
        }
        @Override public int read() throws IOException { int value = in.read(); count(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int n = in.read(bytes, offset, length); count(n); return n;
        }
    }

    private BogeyRecordedLayers() {}
}
