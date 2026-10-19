import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The language files are read by the game when it starts. One of them had a
    brace too many and was not valid JSON at all, so that language did not
    load and nobody noticed. This reads them all and compares their keys. */
public class LangCheck
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

    static JsonObject read(File file)
    {
        try
        {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            return JsonParser.parseString(text).getAsJsonObject();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static void main(String[] args) throws IOException
    {
        File folder = new File(args.length > 0 ? args[0] : "src/main/resources/assets/bbs/lang");

        System.out.println("== los ficheros de idioma son JSON valido ==");

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        check("hay ficheros de idioma", files != null && files.length > 0);

        List<File> broken = new ArrayList<>();

        for (File file : files)
        {
            if (read(file) == null)
            {
                broken.add(file);
            }
        }

        check("ninguno esta roto", broken.isEmpty());

        for (File file : broken)
        {
            System.out.println("       " + file.getName() + " no es un JSON valido");
        }

        System.out.println("== las claves que faltan van al ingles, pero se cuentan ==");

        File english = new File(folder, "en_us.json");
        JsonObject reference = read(english);

        check("el ingles se lee", reference != null);

        Map<String, String> referenceKeys = new TreeMap<>();

        for (Map.Entry<String, JsonElement> entry : reference.entrySet())
        {
            referenceKeys.put(entry.getKey(), entry.getValue().getAsString());
        }

        for (File file : files)
        {
            if (file.equals(english))
            {
                continue;
            }

            JsonObject other = read(file);
            List<String> missing = new ArrayList<>();

            for (String key : referenceKeys.keySet())
            {
                if (!other.has(key))
                {
                    missing.add(key);
                }
            }

            if (!missing.isEmpty())
            {
                System.out.println("       " + file.getName() + " no tiene " + missing.size() + " claves, el juego pondra el ingles");
            }
        }

        System.out.println("== el nombre del mod es el mismo en todos ==");

        String name = null;

        for (File file : files)
        {
            JsonObject other = read(file);

            if (other == null || !other.has("itemGroup.bbs.main"))
            {
                continue;
            }

            String value = other.get("itemGroup.bbs.main").getAsString();

            if (name == null)
            {
                name = value;
            }

            check(file.getName() + " llama al mod igual que los demas", name.equals(value));
        }

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
