package emucabmotortest;

import com.yasui_i.emucab.client.BogeyRecordedLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Checks real dependency OGG decode and missing/corrupt optional-asset fallback. */
public final class RecordedLayerClientCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void samples(float[] audio, String name, int minimum) {
        check(audio.length >= minimum, name + " recording was not decoded");
        double sum = 0, energy = 0, peak = 0;
        for (float value : audio) {
            check(Float.isFinite(value), name + " non-finite PCM");
            sum += value; energy += value * (double) value; peak = Math.max(peak, Math.abs(value));
        }
        check(Math.abs(sum / audio.length) < .00001, name + " retained DC");
        check(Math.sqrt(energy / audio.length) > .03, name + " inaudible texture");
        check(peak <= .921, name + " clipping after normalization");
        check(Math.abs(audio[0] - audio[audio.length - 1]) < .06, name + " hard cyclic jump");
    }

    /** Measure every centered cyclic20ms window, including the loop seam. */
    private static double mechanicalEnvelopeCrest(float[] audio) {
        int window = 960, left = window / 2;
        double[] envelope = new double[audio.length];
        double sum = 0;
        for (int i = -left; i < left; i++) {
            double value = audio[(i + audio.length) % audio.length];
            sum += value * value;
        }
        for (int i = 0; i < audio.length; i++) {
            envelope[i] = Math.sqrt(Math.max(0, sum / window));
            double remove = audio[(i - left + audio.length) % audio.length];
            double add = audio[(i + left) % audio.length];
            sum += add * add - remove * remove;
        }
        java.util.Arrays.sort(envelope);
        double crest = envelope[envelope.length - 1] / envelope[envelope.length / 2];
        check(crest <= 1.35, "Mechanical texture retained strong repeated rail impacts:20ms crest=" + crest);
        return crest;
    }

    public static String run() throws Exception {
        var real = BogeyRecordedLayers.load().get(20, TimeUnit.SECONDS);
        samples(real.mechanical(), "Create train", 48000);
        double mechanicalCrest = mechanicalEnvelopeCrest(real.mechanical());
        if (Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(
                "electroenergetics:sounds/train/background/wind_static.ogg")).isPresent())
            samples(real.wind(), "CEE wind", 48000 * 10);
        check(BogeyRecordedLayers.load().getNow(BogeyRecordedLayers.EMPTY) == real,
                "Layer cache did not retain shared PCM");
        var absent = BogeyRecordedLayers.load(ResourceProvider.EMPTY).get(20, TimeUnit.SECONDS);
        check(absent.mechanical().length == 0 && absent.wind().length == 0, "Missing resources did not fallback");
        Resource broken = new Resource(null, () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        var corrupt = BogeyRecordedLayers.load(ResourceProvider.fromMap(Map.of(
                ResourceLocation.parse("create:sounds/train.ogg"), broken))).get(20, TimeUnit.SECONDS);
        check(corrupt.mechanical().length == 0 && corrupt.wind().length == 0,
                "Invalid OGG did not fallback");
        long generation = BogeyRecordedLayers.generation();
        BogeyRecordedLayers.reset();
        check(BogeyRecordedLayers.generation() == generation + 1, "Reset did not invalidate generation");
        var rebuilt = BogeyRecordedLayers.load().get(20, TimeUnit.SECONDS);
        samples(rebuilt.mechanical(), "Reloaded Create train", 48000);
        mechanicalEnvelopeCrest(rebuilt.mechanical());
        check(rebuilt != real && rebuilt.mechanical() != real.mechanical(), "Reset retained decoded array");
        return "real Create/CEE OGG float decode, shared bounded48k mono loops, DC/headroom/cyclic edge, mechanical cyclic20ms crest="
                + mechanicalCrest + ", missing/corrupt fallback, cache invalidation";
    }

    private RecordedLayerClientCheck() {}
}
