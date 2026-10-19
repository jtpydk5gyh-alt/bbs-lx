import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.List;

/**
 * The segment receives the index the binary search already knows, instead of
 * looking for it with a linear scan. The check looks at the neighbours of the
 * keyframe the search returned, which is what the segment is supposed to hold:
 * the keyframe before it and the keyframe after it.
 *
 * The neighbours are looked up by position in the list and by reference, not
 * by value, because two keyframes of a file can be equal: a file with two
 * keyframes in the same tick is read as two keyframes, and the scan used to
 * find the first one of the two, which is the wrong neighbour.
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

    /** Where a keyframe is in the list, by reference and not by value. */
    static int positionOf(KeyframeChannel<Double> channel, Keyframe<Double> keyframe)
    {
        List<Keyframe<Double>> keyframes = channel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++)
        {
            if (keyframes.get(i) == keyframe)
            {
                return i;
            }
        }

        return -1;
    }

    static void checkSegment(String label, KeyframeChannel<Double> channel, float ticks)
    {
        KeyframeSegment<Double> segment = channel.findSegment(ticks);

        if (segment == null)
        {
            check(label + ": hay segmento", false);

            return;
        }

        List<Keyframe<Double>> keyframes = channel.getKeyframes();
        int index = positionOf(channel, segment.a);

        check(label + ": el keyframe esta en la lista", index >= 0);

        Keyframe<Double> expectedPreA = index > 0 ? channel.get(index - 1) : segment.a;
        Keyframe<Double> expectedPostB = index >= 0 && index + 2 < keyframes.size() ? channel.get(index + 2) : segment.b;

        check(label + ": la tangente anterior es el keyframe de antes", expectedPreA == segment.preA);
        check(label + ": la tangente posterior es el keyframe de despues", expectedPostB == segment.postB);
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

                checkSegment(size + " keyframes en el tick " + ticks, channel, ticks);
            }
        }

        System.out.println("== dos keyframes en el mismo tick ==");

        KeyframeChannel<Double> twin = new KeyframeChannel<>("check", KeyframeFactories.DOUBLE);

        twin.add(new Keyframe<>("", KeyframeFactories.DOUBLE, 0F, 1D));
        twin.add(new Keyframe<>("", KeyframeFactories.DOUBLE, 0F, 1D));
        twin.add(new Keyframe<>("", KeyframeFactories.DOUBLE, 10F, 2D));
        twin.add(new Keyframe<>("", KeyframeFactories.DOUBLE, 20F, 3D));
        twin.sort();

        check("los dos keyframes del principio son iguales", twin.get(0).equals(twin.get(1)));
        check("pero no son el mismo", twin.get(0) != twin.get(1));

        for (float ticks = -5F; ticks <= 25F; ticks += 0.5F)
        {
            checkSegment("keyframes gemelos en el tick " + ticks, twin, ticks);
        }

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
