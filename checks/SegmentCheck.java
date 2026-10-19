import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The segment now receives the index the binary search already knows, instead
 * of looking it up with a linear indexOf. This compares the old and the new
 * result for every kind of query, including the awkward ones.
 */
public class SegmentCheck
{
    static int passed = 0;
    static int failed = 0;

    static void check(String label, boolean ok)
    {
        if (ok)
        {
            passed++;
        }
        else
        {
            failed++;
            System.out.println("  FAIL " + label);
        }
    }

    /** The old way: resolve the index by scanning, like fill() used to. */
    static Keyframe<Double>[] oldNeighbours(KeyframeChannel<Double> channel, Keyframe<Double> a, Keyframe<Double> b)
    {
        int index = channel.getKeyframes().indexOf(a);

        if (index >= 0)
        {
            Keyframe<Double> preA = channel.get(index - 1);
            Keyframe<Double> postB = channel.get(index + 2);

            return new Keyframe[]{preA == null ? a : preA, postB == null ? b : postB};
        }

        return new Keyframe[]{a, b};
    }

    public static void main(String[] args)
    {
        int[] sizes = {1, 2, 3, 10, 137, 1000};

        for (int size : sizes)
        {
            KeyframeChannel<Double> channel = new KeyframeChannel<>("check", KeyframeFactories.DOUBLE);

            for (int i = 0; i < size; i++)
            {
                channel.insert(i * 3F, (double) i);
            }

            float length = (float) channel.getLength();

            for (int q = 0; q < 400; q++)
            {
                float ticks = (q / 400F) * (length + 40F) - 20F;
                KeyframeSegmentCheck pair = null;

                /* The new path */
                mchorse.bbs_mod.utils.keyframes.KeyframeSegment<Double> segment = channel.findSegment(ticks);

                if (segment == null)
                {
                    check("sin segmentos para " + size, size == 0);
                    continue;
                }

                /* The old path, resolved by scanning */
                Keyframe<Double>[] old = oldNeighbours(channel, segment.a, segment.b);

                check("preA coincide con " + size + " keyframes en el tick " + ticks,
                    old[0] == segment.preA);
                check("postB coincide con " + size + " keyframes en el tick " + ticks,
                    old[1] == segment.postB);
            }
        }

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    static class KeyframeSegmentCheck
    {
    }
}