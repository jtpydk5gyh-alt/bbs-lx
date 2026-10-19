import mchorse.bbs_mod.data.storage.DataStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The data format had no limit on how deep the containers could be nested, so
    a payload with a thousand levels of maps made the reader recurse until the
    stack gave out. The error was a StackOverflowError, which is not an
    Exception, so nothing caught it. The writer had the same problem. */
public class DataDepthCheck
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

    static BaseType nested(int depth)
    {
        MapType root = new MapType();
        MapType current = root;

        for (int i = 0; i < depth; i++)
        {
            MapType child = new MapType();

            current.put("hijo", child);
            current = child;
        }

        current.put("fin", new StringType("hola"));

        return root;
    }

    static BaseType nestedList(int depth)
    {
        ListType root = new ListType();
        ListType current = root;

        for (int i = 0; i < depth; i++)
        {
            ListType child = new ListType();

            current.add(child);
            current = child;
        }

        current.add(new StringType("hola"));

        return root;
    }

    /** A payload with as many levels of maps as asked for, written by hand
        because the writer refuses to make one. */
    static byte[] deepBytes(int depth, boolean list) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);

        out.writeByte('B');
        out.writeByte('B');
        out.writeByte('S');
        out.writeByte('1');

        if (list)
        {
            out.writeByte(0);
            out.writeByte(0);
        }
        else
        {
            out.writeByte(0);
            out.writeByte(1);
            out.writeByte(0);
            out.writeUTF("hijo");
        }

        for (int i = 0; i < depth; i++)
        {
            if (list)
            {
                out.writeByte(BaseType.TYPE_LIST);
                out.writeInt(1);
            }
            else
            {
                out.writeByte(BaseType.TYPE_MAP);
                out.writeInt(1);
                out.writeByte(0);
            }
        }

        out.writeByte(BaseType.TYPE_STRING);
        out.writeUTF("hola");
        out.flush();

        return stream.toByteArray();
    }

    static String read(byte[] bytes)
    {
        try
        {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

            return String.valueOf(DataStorage.readFromStream(stream));
        }
        catch (IOException e)
        {
            return "IO: " + e.getMessage();
        }
        catch (Throwable e)
        {
            return "ERROR: " + e.getClass().getSimpleName();
        }
    }

    static String write(BaseType data)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            DataStorage.writeToStream(stream, data);

            return "OK:" + stream.size();
        }
        catch (IOException e)
        {
            return "IO: " + e.getMessage();
        }
        catch (Throwable e)
        {
            return "ERROR: " + e.getClass().getSimpleName();
        }
    }

    static byte[] writeBytes(BaseType data) throws Exception
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        DataStorage.writeToStream(stream, data);

        return stream.toByteArray();
    }

    public static void main(String[] args) throws Exception
    {
        System.out.println("== una estructura normal se lee ==");

        String normal = read(writeBytes(nested(10)));

        check("diez niveles se leen", normal.startsWith("{") && normal.contains("hola"));

        System.out.println("== una lista anidada tambien ==");

        String list = read(writeBytes(nestedList(10)));

        check("diez niveles de listas se leen", list.contains("hola"));

        System.out.println("== pero no se puede anidar sin fin ==");

        String deep = read(deepBytes(5000, false));

        check("leer cinco mil niveles se rechaza", deep.startsWith("IO:"));
        check("y no es un fallo de pila", !deep.contains("ERROR:"));

        String deepList = read(deepBytes(5000, true));

        check("leer cinco mil niveles de listas se rechaza", deepList.startsWith("IO:"));
        check("y tampoco es un fallo de pila", !deepList.contains("ERROR:"));

        System.out.println("== y el escritor tampoco se deja desbordar ==");

        String written = write(nested(5000));

        check("escribir cinco mil niveles se rechaza", written.startsWith("IO:"));
        check("y no es un fallo de pila", !written.contains("ERROR:"));

        String writtenList = write(nestedList(5000));

        check("escribir cinco mil niveles de listas se rechaza", writtenList.startsWith("IO:"));
        check("y tampoco es un fallo de pila", !writtenList.contains("ERROR:"));

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
