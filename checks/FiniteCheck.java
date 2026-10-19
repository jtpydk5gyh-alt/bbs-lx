import mchorse.bbs_mod.camera.clips.modifiers.MathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Vector3d;

/** NaN and infinity used to reach the position, the rotation and the scale of a
    bone, and the camera, with nothing checking them. The checks drive the
    real animator and the real camera clip, not only the parser. */
public class FiniteCheck
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

    /** A value that is not a number. */
    static class NotANumber extends MolangExpression
    {
        NotANumber()
        {
            super(null);
        }

        @Override
        public double get()
        {
            return Double.NaN;
        }
    }

    /** An infinity. */
    static class Infinite extends MolangExpression
    {
        Infinite()
        {
            super(null);
        }

        @Override
        public double get()
        {
            return Double.POSITIVE_INFINITY;
        }
    }

    static class TestContext extends ClipContext<Clip, Position>
    {
        @Override
        public boolean apply(Clip clip, Position position)
        {
            return false;
        }
    }

    public static void main(String[] args)
    {
        MathBuilder builder = new MathBuilder();

        System.out.println("== las expresiones que producen NaN o infinito ==");

        check("1 % 0 da NaN", isNaN(builder, "1 % 0"));
        check("mod(1, 0) da NaN", isNaN(builder, "mod(1, 0)"));
        check("sqrt(-1) da NaN", isNaN(builder, "sqrt(-1)"));
        check("ln(-1) da NaN", isNaN(builder, "ln(-1)"));
        check("asin(2) da NaN", isNaN(builder, "asin(2)"));
        check("pow(0, -1) da infinito", isInfinite(builder, "pow(0, -1)"));

        System.out.println("== una funcion que no existe no se confunde con un NaN ==");

        check("una funcion inexistente da error de parseo, no NaN", parseFails(builder, "matematicas.sqrt(16)"));

        System.out.println("== la division si estaba protegida ==");

        check("1 / 0 no da NaN", !isNaN(builder, "1 / 0"));
        check("1 / 0 da un numero", isFinite(builder, "1 / 0"));

        System.out.println("== una expresion normal sigue dando lo que daba ==");

        check("1 + 2 * 3 = 7", value(builder, "1 + 2 * 3") == 7D);
        check("lerp(0, 10, 0.5) = 5", value(builder, "lerp(0, 10, 0.5)") == 5D);
        check("sqrt(16) = 4", value(builder, "sqrt(16)") == 4D);
        check("pow(2, 10) = 1024", value(builder, "pow(2, 10)") == 1024D);

        System.out.println("== un hueso con un valor que no es un numero ==");

        KeyframeChannel<MolangExpression> channel = new KeyframeChannel<>("x", null);

        channel.insert(0F, new NotANumber());
        channel.insert(20F, new NotANumber());

        Vector3d bone = CubicModelAnimator.interpolateList(new Vector3d(), channel, channel, channel, 10F, 42D);

        check("el hueso no se mueve a NaN", !Double.isNaN(bone.x));
        check("el hueso se queda en el valor por defecto", bone.x == 42D);

        KeyframeChannel<MolangExpression> infinite = new KeyframeChannel<>("x", null);

        infinite.insert(0F, new Infinite());
        infinite.insert(20F, new Infinite());

        Vector3d other = CubicModelAnimator.interpolateList(new Vector3d(), infinite, infinite, infinite, 10F, 42D);

        check("el hueso tampoco se mueve a infinito", !Double.isInfinite(other.x));
        check("y se queda en el valor por defecto", other.x == 42D);

        System.out.println("== la camara con una expresion que no es un numero ==");

        MathClip clip = new MathClip();

        clip.active.set(0b11111111);
        clip.duration.set(20);
        clip.expression.setExpression("sqrt(-1)");

        Position position = new Position(1F, 2F, 3F, 10F, 20F, 30F, 70F);

        clip.apply(new TestContext().setup(0, 0, 0F), position);

        check("la posicion no se vuelve NaN", !Double.isNaN(position.point.x));
        check("la posicion se queda donde estaba", position.point.x == 1F);
        check("la rotacion no se vuelve NaN", !Double.isNaN(position.angle.yaw));
        check("la rotacion se queda donde estaba", position.angle.yaw == 10F);

        System.out.println("== una camara normal si se mueve ==");

        MathClip moving = new MathClip();

        moving.active.set(0b11111111);
        moving.duration.set(20);
        moving.expression.setExpression("x + 5");

        Position other2 = new Position(1F, 2F, 3F, 10F, 20F, 30F, 70F);

        moving.apply(new TestContext().setup(0, 0, 0F), other2);

        check("la camara se mueve", other2.point.x == 6F);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    static double value(MathBuilder builder, String expression)
    {
        try
        {
            return builder.parse(expression).get().doubleValue();
        }
        catch (Exception e)
        {
            return Double.NaN;
        }
    }

    static boolean isNaN(MathBuilder builder, String expression)
    {
        return Double.isNaN(value(builder, expression));
    }

    static boolean isInfinite(MathBuilder builder, String expression)
    {
        return Double.isInfinite(value(builder, expression));
    }

    static boolean isFinite(MathBuilder builder, String expression)
    {
        return Double.isFinite(value(builder, expression));
    }

    static boolean parseFails(MathBuilder builder, String expression)
    {
        try
        {
            builder.parse(expression);

            return false;
        }
        catch (Exception e)
        {
            return true;
        }
    }
}
