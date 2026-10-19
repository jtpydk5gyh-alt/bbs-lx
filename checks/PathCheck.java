import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.DataPath;

import java.util.Arrays;
import java.util.List;

/** A path of a value was resolved by taking the first segment and stopping
    there, so the undo and the redo of the editor changed nothing: the value
    they found was not the one of the path, and the undo gave up. */
public class PathCheck
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

    static ValueGroup group(String id, BaseValue... values)
    {
        ValueGroup group = new ValueGroup(id);

        for (BaseValue value : values)
        {
            group.add(value);
        }

        return group;
    }

    public static void main(String[] args)
    {
        System.out.println("== un camino de un valor dentro de otros grupos ==");

        ValueFloat deep = new ValueFloat("tick", 5F);
        ValueGroup inner = group("inner", deep);
        ValueGroup middle = group("middle", inner);
        ValueGroup root = group("", middle, new ValueFloat("name", 1F));

        DataPath path = deep.getPath();

        check("el camino es middle/inner/tick", path.toString().equals("middle/inner/tick"));

        BaseValue found = root.getRecursively(path);

        check("el camino encuentra el valor", found == deep);
        check("y su camino es el que se pidio", found.getPath().equals(path));

        System.out.println("== un camino de un solo segmento ==");

        BaseValue name = root.getRecursively(new DataPath("name"));

        check("encuentra el valor", name != null && name.getId().equals("name"));

        System.out.println("== un camino que no existe ==");

        boolean thrown = false;

        try
        {
            root.getRecursively(new DataPath("middle/nada"));
        }
        catch (IllegalStateException e)
        {
            thrown = true;
        }

        check("falla con una excepcion", thrown);

        thrown = false;

        try
        {
            root.getRecursively(new DataPath("middle/inner/nada"));
        }
        catch (IllegalStateException e)
        {
            thrown = true;
        }

        check("y tambien cuando falla la ultima parte", thrown);

        System.out.println("== un camino que acaba en un grupo ==");

        BaseValue group2 = root.getRecursively(new DataPath("middle/inner"));

        check("encuentra el grupo", group2 == inner);

        System.out.println("== el camino se puede escribir de las dos maneras ==");

        BaseValue bySlash = root.getRecursively(new DataPath("middle/inner/tick"));
        BaseValue byList = root.getRecursively(new DataPath(Arrays.asList("middle", "inner", "tick")));

        check("con barras", bySlash == deep);
        check("con una lista", byList == deep);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
