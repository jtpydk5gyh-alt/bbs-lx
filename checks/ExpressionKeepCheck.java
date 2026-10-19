import mchorse.bbs_mod.camera.values.ValueExpression;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.MathBuilder;

/** An expression that the parser refuses used to be thrown away: the value
    kept nothing, so the next save wrote an empty string and the expression of
    the clip was gone for good. Now the text is kept, so the data survives. */
public class ExpressionKeepCheck
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

    static String repeat(String text, int times)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < times; i++)
        {
            builder.append(text);
        }

        return builder.toString();
    }

    public static void main(String[] args)
    {
        MathBuilder builder = new MathBuilder();

        System.out.println("== una expresion normal ==");

        ValueExpression normal = new ValueExpression("expr", builder);

        normal.setExpression("1 + 2 * 3");

        check("no esta en error", !normal.isErrored());
        check("vale 7", normal.get().doubleValue() == 7D);
        check("se escribe", normal.toData().asString().length() > 0);

        System.out.println("== una expresion demasiado larga ==");

        String longText = "1" + repeat("+1", MathBuilder.MAX_LENGTH);

        ValueExpression tooLong = new ValueExpression("expr", builder);

        tooLong.setExpression(longText);

        check("esta en error", tooLong.isErrored());
        check("pero el texto se conserva", tooLong.toString().equals(longText));
        check("y se escribe igual", tooLong.toData().asString().equals(longText));

        System.out.println("== una expresion demasiado profunda ==");

        String deepText = repeat("(", 300) + "1" + repeat(")", 300);

        ValueExpression tooDeep = new ValueExpression("expr", builder);

        tooDeep.setExpression(deepText);

        check("esta en error", tooDeep.isErrored());
        check("pero el texto se conserva", tooDeep.toString().equals(deepText));

        System.out.println("== una suma larga si se puede ==");

        String sum = repeat("1+", 200) + "1";

        ValueExpression longSum = new ValueExpression("expr", builder);

        longSum.setExpression(sum);

        check("no esta en error", !longSum.isErrored());
        check("vale 201", longSum.get().doubleValue() == 201D);

        System.out.println("== el viaje de ida y vuelta ==");

        ValueExpression loaded = new ValueExpression("expr", builder);

        BaseType data = tooLong.toData();

        check("lo que se escribe es un texto", BaseType.isString(data));

        loaded.fromData(data);

        check("al leerlo sigue en error", loaded.isErrored());
        check("y el texto sigue ahi", loaded.toString().equals(longText));

        ValueExpression loadedNormal = new ValueExpression("expr", builder);

        loadedNormal.fromData(normal.toData());

        check("una expresion buena sigue valiendo lo mismo", loadedNormal.get().doubleValue() == 7D);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
