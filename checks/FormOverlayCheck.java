import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** The overlays of a form are made according to a setting, so a form that was
    recorded with more overlays than the setting asks for came back without
    them, and the next save wrote it without them. */
public class FormOverlayCheck
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

        System.out.println("== una forma grabada con mas overlays de los que pide el ajuste ==");

        MapType data = new MapType();

        data.putString("model", "mi/modelo");
        data.put("transform_overlay0", new MapType());
        data.put("transform_overlay1", new MapType());
        data.put("transform_overlay2", new MapType());
        data.put("pose_overlay0", new MapType());
        data.put("pose_overlay1", new MapType());

        ModelForm form = new ModelForm();

        form.fromData(data);

        check("los tres overlays de transformacion estan", form.additionalTransforms.size() == 3);
        check("y los dos de pose", form.additionalOverlays.size() == 2);

        System.out.println("== los overlays se pueden leer ==");

        check("el de transformacion se lee", form.get("transform_overlay2") != null);
        check("y el de pose", form.get("pose_overlay1") != null);

        System.out.println("== una forma sin overlays ==");

        ModelForm plain = new ModelForm();

        plain.fromData(new MapType());

        check("no hay overlays de transformacion", plain.additionalTransforms.isEmpty());
        check("ni de pose", plain.additionalOverlays.isEmpty());

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
