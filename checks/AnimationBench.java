import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Vector3d;

/** How long it takes to animate a model for one frame. It is a benchmark and
    it does not assert anything: it prints the numbers so they can be compared
    before and after a change.

    The value of the expression has to change on every frame, or the virtual
    machine throws the loop away, and the channels have to change too, or it
    moves the whole loop out of the frame. */
public class AnimationBench
{
    static KeyframeChannel<MolangExpression> channel(MolangParser parser, int keyframes, boolean expression) throws Exception
    {
        KeyframeChannel<MolangExpression> channel = new KeyframeChannel<>("x", null);

        for (int i = 0; i < keyframes; i++)
        {
            String text = expression ? "math.sin(query.life_time * 360) * " + i : String.valueOf(i);

            channel.insert(i * 5F, parser.parseExpression(text));
        }

        return channel;
    }

    public static void main(String[] args) throws Exception
    {
        MolangParser parser = new MolangParser();

        System.out.println("== un hueso: nueve busquedas de segmento por fotograma ==");

        for (boolean expression : new boolean[] {false, true})
        {
            for (int keyframes : new int[] {1, 5, 20, 100})
            {
                KeyframeChannel<MolangExpression>[] xs = new KeyframeChannel[16];
                KeyframeChannel<MolangExpression>[] ys = new KeyframeChannel[16];
                KeyframeChannel<MolangExpression>[] zs = new KeyframeChannel[16];

                for (int k = 0; k < 16; k++)
                {
                    xs[k] = channel(parser, keyframes, expression);
                    ys[k] = channel(parser, keyframes, expression);
                    zs[k] = channel(parser, keyframes, expression);
                }

                Vector3d out = new Vector3d();
                int frames = 50000;
                double sum = 0D;

                for (int i = 0; i < 2000; i++)
                {
                    parser.setValue("query.life_time", (double) i);
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                }

                long start = System.nanoTime();

                for (int i = 0; i < frames; i++)
                {
                    parser.setValue("query.life_time", (double) i);
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                }

                long time = (System.nanoTime() - start) / 1000L;

                System.out.println((expression ? "expresion" : "constante") + ", " + keyframes + " keyframes -> "
                    + (time * 1000L / frames) + " ns por hueso y fotograma");
            }
        }

        System.out.println("== un modelo de 30 huesos ==");

        for (int keyframes : new int[] {5, 20, 100})
        {
            KeyframeChannel<MolangExpression>[] xs = new KeyframeChannel[16];
            KeyframeChannel<MolangExpression>[] ys = new KeyframeChannel[16];
            KeyframeChannel<MolangExpression>[] zs = new KeyframeChannel[16];

            for (int k = 0; k < 16; k++)
            {
                xs[k] = channel(parser, keyframes, true);
                ys[k] = channel(parser, keyframes, true);
                zs[k] = channel(parser, keyframes, true);
            }

            Vector3d out = new Vector3d();
            int frames = 20000;
            double sum = 0D;

            for (int i = 0; i < 2000; i++)
            {
                parser.setValue("query.life_time", (double) i);

                for (int bone = 0; bone < 30; bone++)
                {
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                }
            }

            long start = System.nanoTime();

            for (int i = 0; i < frames; i++)
            {
                parser.setValue("query.life_time", (double) i);

                for (int bone = 0; bone < 30; bone++)
                {
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                    sum += CubicModelAnimator.interpolateList(out, xs[i & 15], ys[i & 15], zs[i & 15], i, 0D).x;
                }
            }

            long time = (System.nanoTime() - start) / 1000L;

            System.out.println(keyframes + " keyframes -> " + (time * 1000L / frames) + " ns por modelo y fotograma ("
                + (time * 1000D / frames / 1000D) + " us)");
        }
    }
}
