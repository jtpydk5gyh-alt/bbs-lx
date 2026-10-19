import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.molang.MolangParser;

/** NaN and infinity used to reach the position, the rotation and the scale of a
    bone, and the camera, with nothing checking them. */
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