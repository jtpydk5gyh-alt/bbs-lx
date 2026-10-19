import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.InterpContext;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** The animator used to evaluate the tangent keyframes a second time, so a
    stateful expression (math.random) gave a tangent that did not match the
    value it was supposed to be the tangent of. A spy interpolation records the
    handles the animator really hands over to the interpolation. */
public class EvalCountCheck
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

    /** A Molang expression that counts how many times it is evaluated. */
    static class Counting extends MolangExpression
    {
        int evaluations;
        double value;
        boolean stateful;

        Counting(double value)
        {
            super(null);

            this.value = value;
        }

        Counting stateful()
        {
            this.stateful = true;

            return this;
        }

        @Override
        public double get()
        {
            this.evaluations++;

            if (this.stateful)
            {
                this.value += 1D;

                return this.value;
            }

            return this.value;
        }
    }

    /** An interpolation that remembers the handles it was given. */
    static class Spy implements IInterp
    {
        int calls;
        double a0;
        double a;
        double b;
        double b0;
        double x;

        @Override
        public double interpolate(InterpContext context)
        {
            this.calls++;
            this.a0 = context.a0;
            this.a = context.a;
            this.b = context.b;
            this.b0 = context.b0;
            this.x = context.x;

            return context.a;
        }

        @Override
        public String getKey()
        {
            return "spy";
        }

        @Override
        public int getKeyCode()
        {
            return 0;
        }
    }

    /** One axis of an animation: a channel, the expressions on it and a spy
        interpolation to see what the animator hands over. */
    static class Axis
    {
        final KeyframeChannel<MolangExpression> channel = new KeyframeChannel<>("x", null);
        final List<Counting> expressions = new ArrayList<>();
        final Spy spy = new Spy();

        void add(float tick, Counting expression)
        {
            int index = this.channel.insert(tick, expression);

            this.expressions.add(expression);

            this.channel.get(index).getInterpolation().setInterp(this.spy);
        }

        int evaluations()
        {
            int total = 0;

            for (Counting expression : this.expressions)
            {
                total += expression.evaluations;
            }

            return total;
        }
    }

    static Axis axis(Counting... expressions)
    {
        Axis axis = new Axis();

        for (int i = 0; i < expressions.length; i++)
        {
            axis.add(i * 10F, expressions[i]);
        }

        return axis;
    }

    static Axis[] axes(double... values)
    {
        Axis[] axes = new Axis[3];

        for (int i = 0; i < axes.length; i++)
        {
            Counting[] expressions = new Counting[values.length];

            for (int v = 0; v < values.length; v++)
            {
                expressions[v] = new Counting(values[v]);
            }

            axes[i] = axis(expressions);
        }

        return axes;
    }

    static Vector3d animate(Axis[] axes, float frame)
    {
        return CubicModelAnimator.interpolateList(new Vector3d(), axes[0].channel, axes[1].channel, axes[2].channel, frame, 0D);
    }

    public static void main(String[] args)
    {
        System.out.println("== con dos keyframes, cada expresion se evalua una vez ==");

        Axis[] two = axes(0D, 10D);

        animate(two, 5F);

        check("la expresion del primer keyframe se evalua una vez", two[0].expressions.get(0).evaluations == 1);
        check("y la del segundo tambien", two[0].expressions.get(1).evaluations == 1);
        check("la interpolacion recibe los handles una sola vez", two[0].spy.calls == 1);

        System.out.println("== con dos keyframes, la tangente es el propio keyframe ==");

        check("la tangente anterior es el valor inicial", two[0].spy.a0 == 0D);
        check("y coincide con el valor inicial", two[0].spy.a0 == two[0].spy.a);
        check("la tangente posterior es el valor final", two[0].spy.b0 == 10D);
        check("y coincide con el valor final", two[0].spy.b0 == two[0].spy.b);

        System.out.println("== con cuatro keyframes, la tangente es la del vecino ==");

        Axis[] four = axes(100D, 0D, 10D, 1000D);

        animate(four, 15F);

        check("la tangente anterior es el keyframe previo", four[0].spy.a0 == 100D);
        check("el valor inicial es el keyframe actual", four[0].spy.a == 0D);
        check("el valor final es el keyframe siguiente", four[0].spy.b == 10D);
        check("la tangente posterior es el keyframe que sigue", four[0].spy.b0 == 1000D);

        check("las cuatro expresiones se evaluan una vez cada una", four[0].evaluations() == 4);

        System.out.println("== una expresion con estado da la misma tangente que su valor ==");

        Axis random = axis(new Counting(0D).stateful(), new Counting(0D).stateful());
        Axis random2 = axis(new Counting(0D).stateful(), new Counting(0D).stateful());
        Axis random3 = axis(new Counting(0D).stateful(), new Counting(0D).stateful());

        animate(new Axis[] {random, random2, random3}, 5F);

        check("la tangente anterior es la misma evaluacion que el valor inicial", random.spy.a0 == random.spy.a);
        check("la tangente posterior es la misma evaluacion que el valor final", random.spy.b0 == random.spy.b);
        check("cada expresion se evalua una sola vez", random.evaluations() == 2);

        System.out.println("== el valor interpolado no ha cambiado ==");

        Axis[] same = axes(0D, 10D, 20D, 30D);

        for (Axis axis : same)
        {
            for (int i = 1; i < 4; i++)
            {
                axis.channel.get(i).getInterpolation().setInterp(Interpolations.HERMITE);
            }
        }

        boolean equal = true;

        for (float frame = 0F; frame <= 30F && equal; frame += 0.5F)
        {
            Vector3d got = animate(same, frame);
            KeyframeSegment<MolangExpression> segment = same[0].channel.findSegment(frame);

            double expected = Interpolations.HERMITE.interpolate(IInterp.context.set(
                segment.preA.getValue().get(),
                segment.a.getValue().get(),
                segment.b.getValue().get(),
                segment.postB.getValue().get(),
                segment.x
            ));

            if (got.x != expected)
            {
                equal = false;
                System.out.println("  FAIL el fotograma " + frame + " da " + got.x + " en vez de " + expected);
            }
        }

        check("todos los fotogramas dan el valor de siempre", equal);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
