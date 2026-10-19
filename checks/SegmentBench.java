import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class SegmentBench
{
    public static void main(String[] args)
    {
        int[] sizes = {100, 1000, 10000};

        System.out.printf("%10s %14s %16s%n", "keyframes", "ns/consulta", "en 20 ticks/s");

        for (int size : sizes)
        {
            KeyframeChannel<Double> channel = new KeyframeChannel<>("bench", KeyframeFactories.DOUBLE);

            for (int i = 0; i < size; i++)
            {
                channel.insert(i * 0.5F, (double) i);
            }

            float length = (float) channel.getLength();
            double sink = 0;

            for (int i = 0; i < 20_000; i++)
            {
                sink += channel.interpolate((i % 1000) / 1000F * length);
            }

            long best = Long.MAX_VALUE;
            int queries = 200_000;

            for (int trial = 0; trial < 3; trial++)
            {
                long t0 = System.nanoTime();

                for (int i = 0; i < queries; i++)
                {
                    sink += channel.interpolate((i % 1000) / 1000F * length);
                }

                long t1 = System.nanoTime();

                best = Math.min(best, t1 - t0);
            }

            double perQuery = (double) best / queries;
            double perSecond = perQuery * 20;

            System.out.printf("%10d %14.1f %13.3f ms   (sink=%.0f)%n", size, perQuery, perSecond / 1_000_000, sink);
        }
    }
}