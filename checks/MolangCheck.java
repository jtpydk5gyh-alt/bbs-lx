import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;

/** The Molang parser had no limit on the size of an expression, and it could
    not read a variable of a single letter, because it looked at the second
    character of the name without checking that there was one. */
public class MolangCheck
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
        MolangParser parser = new MolangParser();

        System.out.println("== una expresion normal sigue dando lo que daba ==");

        check("1 + 2 * 3 = 7", value(parser, "1 + 2 * 3") == 7D);
        check("math.sin(0) = 0", value(parser, "math.sin(0)") == 0D);
        check("math.abs(-3) = 3", value(parser, "math.abs(-3)") == 3D);
        check("math.lerp(0, 10, 0.5) = 5", value(parser, "math.lerp(0, 10, 0.5)") == 5D);
        check("un ternario funciona", value(parser, "1 > 0 ? 2 : 3") == 2D);
        check("una variable con punto funciona", parses(parser, "v.a"));
        check("una consulta funciona", parses(parser, "q.life_time"));
        check("una consulta dentro de una funcion funciona", parses(parser, "math.sin(q.life_time)"));

        System.out.println("== una variable de una sola letra se puede leer ==");

        check("'a' se parsea", parses(parser, "a"));
        check("'a' vale 0", value(parser, "a") == 0D);
        check("'a + 1' se parsea", parses(parser, "a + 1"));
        check("'a + 1' vale 1", value(parser, "a + 1") == 1D);
        check("'ab' sigue siendo una variable distinta", parses(parser, "ab"));

        System.out.println("== y se puede escribir ==");

        check("'a=1' se parsea", parses(parser, "a=1"));
        check("'a=1;a=2' se parsea", parses(parser, "a=1;a=2"));
        check("'ab=1;ab=2' se parsea", parses(parser, "ab=1;ab=2"));
        check("'v.a=1;v.b=2' se parsea", parses(parser, "v.a=1;v.b=2"));

        boolean assigned = true;

        try
        {
            parser.setValue("a", 5);
        }
        catch (Throwable e)
        {
            assigned = false;
        }

        check("'a' se puede asignar", assigned);
        check("'a' vale 5 despues de asignarlo", assigned && value(parser, "a") == 5D);
        check("'a + 1' vale 6", assigned && value(parser, "a + 1") == 6D);

        System.out.println("== el tamano de la expresion esta limitado ==");

        int limit = MathBuilder.MAX_LENGTH;

        check("una expresion de " + limit + " caracteres entra", parses(parser, "\"" + "a".repeat(limit - 2) + "\""));
        check("una expresion de " + (limit + 1) + " caracteres no entra", !parses(parser, "\"" + "a".repeat(limit - 1) + "\""));
        check("una expresion de un millon de caracteres no entra", !parses(parser, "1".repeat(1000000)));

        System.out.println("== y con las sentencias tambien ==");

        check("512 sentencias caben", parses(parser, "a=1;".repeat(512)));
        check("100000 sentencias no caben", !parses(parser, "a=1;".repeat(100000)));

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean parses(MolangParser parser, String expression)
    {
        try
        {
            parser.parseExpression(expression);

            return true;
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    static double value(MolangParser parser, String expression)
    {
        try
        {
            MolangExpression parsed = parser.parseExpression(expression);

            return parsed == null ? Double.NaN : parsed.get();
        }
        catch (Throwable e)
        {
            return Double.NaN;
        }
    }
}
