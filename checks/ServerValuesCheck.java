import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.IFactory;
import mchorse.bbs_mod.utils.keyframes.factories.DoubleKeyframeFactory;

import java.util.Collection;
import java.util.Collections;

/** The values of a film are user data. A value that is not a number used to
    reach the position of an entity, and a list of user data could have as many
    elements as it wanted. */
public class ServerValuesCheck
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

    static class TestClip extends Clip
    {
        @Override
        protected Clip create()
        {
            return new TestClip();
        }
    }

    static class ClipFactory implements IFactory<Clip, ClipFactoryData>
    {
        @Override
        public Link getType(Clip object)
        {
            return new Link("test", "clip");
        }

        @Override
        public Clip create(Link type)
        {
            return new TestClip();
        }

        @Override
        public ClipFactoryData getData(Clip object)
        {
            return null;
        }

        @Override
        public ClipFactoryData getData(Link type)
        {
            return null;
        }

        @Override
        public Collection<Link> getKeys()
        {
            return Collections.emptyList();
        }
    }

    static class Numbers extends ValueList<BaseValue>
    {
        Numbers()
        {
            super("numbers");
        }

        @Override
        protected BaseValue create(String id)
        {
            return new ValueFloat(id, 0F);
        }
    }

    public static void main(String[] args)
    {
        System.out.println("== un valor normal se lee ==");

        DoubleKeyframeFactory factory = new DoubleKeyframeFactory();

        check("un numero se lee", factory.fromData(new DoubleType(12.5D)) == 12.5D);
        check("y un numero entero tambien", factory.fromData(new DoubleType(3D)) == 3D);

        System.out.println("== un valor que no es un numero no se lee ==");

        check("un NaN no pasa", Double.isFinite(factory.fromData(new DoubleType(Double.NaN))));
        check("un infinito no pasa", Double.isFinite(factory.fromData(new DoubleType(Double.POSITIVE_INFINITY))));
        check("un infinito negativo tampoco", Double.isFinite(factory.fromData(new DoubleType(Double.NEGATIVE_INFINITY))));
        check("y el valor es cero", factory.fromData(new DoubleType(Double.NaN)) == 0D);

        System.out.println("== una lista no puede tener los elementos que quiera ==");

        ListType data = new ListType();

        for (int i = 0; i < 100000; i++)
        {
            data.add(new MapType());
        }

        Numbers numbers = new Numbers();

        numbers.fromData(data);

        check("se leen hasta el limite", numbers.getList().size() == ValueList.MAX_ELEMENTS);
        check("y no mas", numbers.getList().size() < data.size());

        System.out.println("== una lista normal se lee entera ==");

        ListType small = new ListType();

        for (int i = 0; i < 10; i++)
        {
            small.add(new MapType());
        }

        Numbers ten = new Numbers();

        ten.fromData(small);

        check("los diez elementos estan", ten.getList().size() == 10);

        System.out.println("== los clips tampoco ==");

        ListType clips = new ListType();

        for (int i = 0; i < Clips.MAX_CLIPS + 10; i++)
        {
            MapType clip = new MapType();

            clip.putString("type", "test:clip");

            clips.add(clip);
        }

        Clips group = new Clips("clips", new ClipFactory());

        group.fromData(clips);

        check("se leen hasta el limite", group.get().size() == Clips.MAX_CLIPS);
        check("y no mas", group.get().size() < clips.size());

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
