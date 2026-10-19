import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/** The animation of a model was never written to the file: the serializer
    returned empty lists, so a model that was exported came out without its
    animations. The reader was there all along, so the fix is to write what
    the reader expects. */
public class AnimationRoundTripCheck
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

    static ListType keyframe(double time, String interpolation, BaseType x, BaseType y, BaseType z)
    {
        ListType list = new ListType();

        list.addDouble(time);
        list.addString(interpolation);
        list.add(x);
        list.add(y);
        list.add(z);

        return list;
    }

    static BaseType number(double value)
    {
        return new DoubleType(value);
    }

    static Keyframe<?> at(KeyframeChannel<?> channel, int index)
    {
        return index < channel.getKeyframes().size() ? channel.getKeyframes().get(index) : null;
    }

    static double value(Keyframe<?> keyframe)
    {
        return keyframe == null ? Double.NaN : ((mchorse.bbs_mod.math.molang.expressions.MolangExpression) keyframe.getValue()).get();
    }

    public static void main(String[] args)
    {
        MolangParser parser = new MolangParser();

        ListType translate = new ListType();

        translate.add(keyframe(0D, "linear", number(1D), number(2D), number(3D)));
        translate.add(keyframe(1D, "catmullrom", number(4D), number(5D), number(6D)));

        ListType scale = new ListType();

        scale.add(keyframe(0D, "linear", number(1D), number(1D), number(1D)));

        ListType rotate = new ListType();

        rotate.add(keyframe(0.5D, "linear", new StringType("math.sin(q.life_time)"), number(0D), number(0D)));

        MapType data = new MapType();

        data.put("translate", translate);
        data.put("scale", scale);
        data.put("rotate", rotate);

        System.out.println("== lo que se lee del fichero ==");

        AnimationPart part = new AnimationPart(parser);

        part.fromData(data);

        check("la traslacion tiene dos keyframes", part.x.getKeyframes().size() == 2);
        check("la escala tiene un keyframe", part.sx.getKeyframes().size() == 1);
        check("la rotacion tiene un keyframe", part.rx.getKeyframes().size() == 1);
        check("el primer keyframe de la traslacion esta en el tick 0", at(part.x, 0) != null && at(part.x, 0).getTick() == 0F);
        check("el segundo esta en el tick 20", at(part.x, 1) != null && at(part.x, 1).getTick() == 20F);
        check("el keyframe de la rotacion esta en el tick 10", at(part.rx, 0) != null && at(part.rx, 0).getTick() == 10F);
        check("la interpolacion es la de catmullrom", at(part.x, 1) != null && at(part.x, 1).getInterpolation().getInterp() == Interpolations.HERMITE);

        System.out.println("== lo que se escribe ==");

        MapType out = new MapType();

        part.toData(out);

        check("la traslacion se escribe", out.getList("translate").size() == 2);
        check("la escala se escribe", out.getList("scale").size() == 1);
        check("la rotacion se escribe", out.getList("rotate").size() == 1);

        System.out.println("== lo que se lee de vuelta ==");

        AnimationPart again = new AnimationPart(parser);

        again.fromData(out);

        check("la traslacion conserva los keyframes", again.x.getKeyframes().size() == 2);
        check("la escala conserva el keyframe", again.sx.getKeyframes().size() == 1);
        check("la rotacion conserva el keyframe", again.rx.getKeyframes().size() == 1);

        check("el primer tick se conserva", at(again.x, 0) != null && at(again.x, 0).getTick() == 0F);
        check("el segundo tick se conserva", at(again.x, 1) != null && at(again.x, 1).getTick() == 20F);
        check("el tick de la rotacion se conserva", at(again.rx, 0) != null && at(again.rx, 0).getTick() == 10F);

        check("el valor de X se conserva", value(at(again.x, 0)) == 1D);
        check("el valor de Y se conserva", value(at(again.y, 0)) == 2D);
        check("el valor de Z se conserva", value(at(again.z, 0)) == 3D);
        check("el valor del segundo keyframe se conserva", value(at(again.x, 1)) == 4D);

        check("la interpolacion se conserva", at(again.x, 1) != null && at(again.x, 1).getInterpolation().getInterp() == Interpolations.HERMITE);

        check("una expresion se conserva", at(again.rx, 0) != null && at(again.rx, 0).getValue().toString().contains("math.sin(query.life_time)"));

        System.out.println("== escribir dos veces da lo mismo ==");

        MapType outAgain = new MapType();

        again.toData(outAgain);

        check("el segundo viaje escribe lo mismo", out.equals(outAgain));

        System.out.println("== una animacion vacia se sigue escribiendo vacia ==");

        AnimationPart empty = new AnimationPart(parser);

        MapType emptyData = new MapType();

        empty.toData(emptyData);

        check("sin keyframes no hay nada que escribir", emptyData.getList("translate").isEmpty());
        check("tampoco en la escala", emptyData.getList("scale").isEmpty());
        check("ni en la rotacion", emptyData.getList("rotate").isEmpty());

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
