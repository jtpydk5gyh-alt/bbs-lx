import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.IFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** The arguments of the encoder were split by the spaces, so a path with a
    space in it was two arguments and the export was broken. And the clips that
    did not fit were thrown away, so a long recording lost its actions. */
public class ExportCheck
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

    public static void main(String[] args)
    {
        System.out.println("== los argumentos del codificador ==");

        List<String> plain = FFMpegUtils.splitArgs("-i in.mp4 -c:v libx264 out.mp4");

        check("una cadena normal se parte bien", plain.size() == 5);
        check("y las partes son las que son", plain.get(0).equals("-i") && plain.get(4).equals("out.mp4"));

        List<String> quoted = FFMpegUtils.splitArgs("-i \"C:\\Users\\John Doe\\movie.mp4\" -c copy out.mp4");

        check("una ruta con espacios es un argumento", quoted.size() == 5);
        check("y es la ruta entera", quoted.get(1).equals("C:\\Users\\John Doe\\movie.mp4"));
        check("las demas no se mueven", quoted.get(2).equals("-c") && quoted.get(3).equals("copy"));

        List<String> many = FFMpegUtils.splitArgs("  -a   -b  ");

        check("los espacios de sobra no crean argumentos", many.size() == 2);
        check("y no hay argumentos vacios", !many.contains(""));

        System.out.println("== los clips que no caben ==");

        ListType data = new ListType();

        for (int i = 0; i < Clips.MAX_CLIPS + 10; i++)
        {
            MapType clip = new MapType();

            clip.putString("type", "test:clip");
            clip.putInt("tick", i);

            data.add(clip);
        }

        Clips clips = new Clips("clips", new ClipFactory());

        clips.fromData(data);

        check("se leen los que caben", clips.get().size() == Clips.MAX_CLIPS);

        BaseType written = clips.toData();

        check("y al guardarlos estan todos", written.asList().size() == Clips.MAX_CLIPS + 10);
        check("los que no se leyeron se guardan igual", written.asList().get(Clips.MAX_CLIPS).asMap().getInt("tick") == Clips.MAX_CLIPS);

        System.out.println("== una grabacion normal no se toca ==");

        ListType small = new ListType();

        for (int i = 0; i < 10; i++)
        {
            MapType clip = new MapType();

            clip.putString("type", "test:clip");
            clip.putInt("tick", i);

            small.add(clip);
        }

        Clips normal = new Clips("clips", new ClipFactory());

        normal.fromData(small);

        check("se leen todos", normal.get().size() == 10);
        check("y se guardan todos", normal.toData().asList().size() == 10);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
