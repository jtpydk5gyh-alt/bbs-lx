import mchorse.bbs_mod.camera.values.ValueExpression;
import mchorse.bbs_mod.math.MathBuilder;

/** The expression parser had no limits at all, and every catch around it caught
    Exception, so a StackOverflowError or an OutOfMemoryError escaped instead of
    being reported. */
public class ParserLimitCheck
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

    static String nested(int depth)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < depth; i++)
        {
            builder.append('(');
        }

        builder.append('1');

        for (int i = 0; i < depth; i++)
        {
            builder.append(')');
        }

        return builder.toString();
    }

    static String nestedMixed(int depth)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < depth; i++)
        {
            builder.append("(1 + ");
        }

        builder.append('1');

        for (int i = 0; i < depth; i++)
        {
            builder.append(')');
        }

        return builder.toString();
    }

    static String terms(int count)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < count; i++)
        {
            if (i > 0)
            {
                builder.append(" + ");
            }

            builder.append('1');
        }

        return builder.toString();
    }

    public static void main(String[] args)
    {
        MathBuilder builder = new MathBuilder();

        System.out.println("== una expresion normal sigue funcionando ==");

        check("1 + 2 * 3 se parsea", parses(builder, "1 + 2 * 3"));
        check("lerp se parsea", parses(builder, "lerp(1, 2, 3)"));
        check("un ternario se parsea", parses(builder, "1 > 0 ? 2 : 3"));
        check("un grupo anidado normal se parsea", parses(builder, "((1 + 2) * 3)"));
        check("una cadena de 100 caracteres entra", parses(builder, terms(25)));
        check("ocho niveles de parentesis entran", parses(builder, nested(8)));

        System.out.println("== lo que antes reventaba la pila o el heap ==");

        check("1000 anidados con contenido se rechazan", !parses(builder, nestedMixed(1000)));
        check("2000 anidados con contenido se rechazan", !parses(builder, nestedMixed(2000)));
        check("5000 anidados se rechazan", !parses(builder, nested(5000)));
        check("20000 anidados se rechazan", !parses(builder, nested(20000)));
        check("5000 terminos se rechazan", !parses(builder, terms(5000)));
        check("20000 terminos se rechazan", !parses(builder, terms(20000)));
        check("una cadena de 100000 caracteres se rechaza", !parses(builder, "1".repeat(100000)));

        System.out.println("== el camino del servidor: la camara de la pelicula ==");

        ValueExpression expression = new ValueExpression("expression", new MathBuilder());

        check("una expresion normal se acepta", sets(expression, "lerp(1, 2, 3)") && expression.get() != null);
        check("1000 anidados con contenido no revientan", sets(expression, nestedMixed(1000)));
        check("y la expresion se queda vacia", expression.get() == null);
        check("5000 anidados no revientan", sets(expression, nested(5000)));
        check("y la expresion se queda vacia", expression.get() == null);
        check("5000 terminos no revientan", sets(expression, terms(5000)));
        check("y la expresion se queda vacia", expression.get() == null);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean parses(MathBuilder builder, String expression)
    {
        try
        {
            builder.parse(expression);

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
        catch (StackOverflowError e)
        {
            return false;
        }
    }

    static boolean sets(ValueExpression expression, String value)
    {
        try
        {
            expression.setExpression(value);

            return true;
        }
        catch (StackOverflowError e)
        {
            return false;
        }
        catch (OutOfMemoryError e)
        {
            return false;
        }
        catch (Throwable e)
        {
            return true;
        }
    }
}