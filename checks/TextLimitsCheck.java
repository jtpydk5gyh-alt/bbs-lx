import mchorse.bbs_mod.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** A text file was read whole into memory, so a file of a few gigabytes was
    read into the game before anything looked at what it held. */
public class TextLimitsCheck
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

    static byte[] lines(int count) throws Exception
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        for (int i = 0; i < count; i++)
        {
            stream.write("v 0 0 0\n".getBytes("UTF-8"));
        }

        return stream.toByteArray();
    }

    public static void main(String[] args) throws Exception
    {
        System.out.println("== un texto normal se lee entero ==");

        byte[] normal = lines(100);

        check("se lee", IOUtils.readLines(new ByteArrayInputStream(normal)).size() == 100);

        System.out.println("== un texto con demasiadas lineas ==");

        boolean refused = false;

        try
        {
            IOUtils.readLines(new ByteArrayInputStream(lines(IOUtils.MAX_LINES + 10)));
        }
        catch (Exception e)
        {
            refused = true;
        }

        check("no se lee", refused);

        System.out.println("== un texto con demasiados caracteres ==");

        refused = false;

        try
        {
            ByteArrayOutputStream big = new ByteArrayOutputStream();
            String line = "v " + "0".repeat(1000) + "\n";

            for (int i = 0; i < 100000; i++)
            {
                big.write(line.getBytes("UTF-8"));
            }

            IOUtils.readLines(new ByteArrayInputStream(big.toByteArray()));
        }
        catch (Exception e)
        {
            refused = true;
        }

        check("no se lee", refused);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
