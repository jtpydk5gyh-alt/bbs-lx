import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.factories.AnchorKeyframeFactory;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.factories.ShapeKeysKeyframeFactory;

/** A value of the wrong type used to throw while it was read, and the form
    was lost, or the body part was emptied and the next save wrote it empty. */
public class FormDataCheck
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
        System.out.println("== un ancla que no es un mapa ==");

        AnchorKeyframeFactory anchors = new AnchorKeyframeFactory();

        boolean anchorOk = reads(anchors, new IntType(5));

        check("no lanza", anchorOk);
        check("y el ancla esta vacia", anchorOk && anchors.fromData(new IntType(5)).x == 0F);

        System.out.println("== unas shape keys que no son un mapa ==");

        ShapeKeysKeyframeFactory shapes = new ShapeKeysKeyframeFactory();

        boolean shapesOk = reads(shapes, new IntType(5));

        check("no lanza", shapesOk);
        check("y las shape keys estan vacias", shapesOk && shapes.fromData(new IntType(5)).shapeKeys.isEmpty());

        System.out.println("== una particula con un identificador que no existe ==");

        MapType data = new MapType();

        data.putString("particle", "esto no es un identificador");

        ParticleSettings particles = new ParticleSettings();

        boolean particleOk = reads(particles, data);

        check("no lanza", particleOk);
        check("y se queda la particula de antes", particleOk && particles.particle.toString().equals("minecraft:flame"));

        System.out.println("== una particula con un identificador bueno ==");

        data.putString("particle", "minecraft:explosion");

        ParticleSettings good = new ParticleSettings();

        good.fromData(data);

        check("se lee", good.particle.toString().equals("minecraft:explosion"));

        System.out.println("== un camino de una propiedad que no existe ==");

        /* The settings are filled when the mod starts, which does not happen
            here, and a form asks for one of them to know how many overlays it
            has to make. */
        BBSSettings.recordingOverlays = new ValueBoolean("overlays", true);
        BBSSettings.recordingPoseTransformOverlays = new ValueInt("pose_transform_overlays", 0, 0, 42);

        ModelForm form = new ModelForm();

        check("un camino que no es un numero no da la propiedad de otro lado",
            mchorse.bbs_mod.forms.FormUtils.getProperty(form, "no_existe/lighting") == null);
        check("un camino con un numero que no existe tampoco",
            mchorse.bbs_mod.forms.FormUtils.getProperty(form, "0/lighting") == null);
        check("un camino de una sola parte sigue funcionando",
            mchorse.bbs_mod.forms.FormUtils.getProperty(form, "lighting") != null);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean reads(AnchorKeyframeFactory factory, BaseType data)
    {
        try
        {
            factory.fromData(data);

            return true;
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    static boolean reads(ShapeKeysKeyframeFactory factory, BaseType data)
    {
        try
        {
            factory.fromData(data);

            return true;
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    static boolean reads(ParticleSettings settings, MapType data)
    {
        try
        {
            settings.fromData(data);

            return true;
        }
        catch (Throwable e)
        {
            return false;
        }
    }
}
