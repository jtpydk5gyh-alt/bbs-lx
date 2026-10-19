import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** The numbers of a form were not bounded: a form with a life of NaN, or a
    hitbox of a billion, or 65536 parts of a body, was read as it was. */
public class FormLimitsCheck
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
        BBSSettings.recordingOverlays = new ValueBoolean("overlays", true);
        BBSSettings.recordingPoseTransformOverlays = new ValueInt("pose_transform_overlays", 0, 0, 42);

        ModelForm form = new ModelForm();

        System.out.println("== un numero que no es un numero ==");

        form.hp.fromData(new FloatType(Float.NaN));

        check("la vida no es NaN", Float.isFinite(form.hp.get()));

        form.hp.fromData(new FloatType(Float.POSITIVE_INFINITY));

        check("la vida no es infinita", Float.isFinite(form.hp.get()));

        System.out.println("== un numero que no cabe ==");

        form.hp.fromData(new FloatType(1e30F));

        check("la vida esta acotada", form.hp.get() <= 1024F);
        check("y no es negativa", form.hp.get() >= 0F);

        form.hitboxWidth.fromData(new FloatType(1e30F));

        check("el ancho de la caja esta acotado", form.hitboxWidth.get() <= 16F);

        form.hitboxHeight.fromData(new FloatType(-5F));

        check("el alto de la caja no es negativo", form.hitboxHeight.get() >= 0F);

        form.lighting.fromData(new FloatType(50F));

        check("la luz esta acotada", form.lighting.get() <= 1F);

        form.uiScale.fromData(new FloatType(0F));

        check("la escala de la interfaz no es cero", form.uiScale.get() > 0F);

        System.out.println("== un numero normal se queda ==");

        form.hp.fromData(new FloatType(40F));

        check("la vida es la que se puso", form.hp.get() == 40F);

        form.hp.fromData(new FloatType(0F));

        check("y una vida de cero tambien vale", form.hp.get() == 0F);

        System.out.println("== las partes del cuerpo ==");

        check("el limite de las partes es uno que no se pasa", mchorse.bbs_mod.forms.forms.BodyPartManager.MAX_BODY_PARTS <= 256);
        check("y el de una lista es mas alto", mchorse.bbs_mod.settings.values.core.ValueList.MAX_ELEMENTS > 256);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
