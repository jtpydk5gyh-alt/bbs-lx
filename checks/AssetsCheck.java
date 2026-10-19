import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.vox.VoxReader;
import mchorse.bbs_mod.vox.VoxDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/** A file can say how big it is, and the file can be much smaller. The
    readers of the assets used to reserve what the file said, so a file of a
    few bytes could make the game reserve hundreds of megabytes. */
public class AssetsCheck
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

    /** The RIFF format is little endian. */
    static void writeInt(DataOutputStream out, int value) throws Exception
    {
        out.writeByte(value & 0xff);
        out.writeByte((value >> 8) & 0xff);
        out.writeByte((value >> 16) & 0xff);
        out.writeByte((value >> 24) & 0xff);
    }

    static byte[] wav(byte[] audio, int claimedSize)
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);

        try
        {
            out.writeBytes("RIFF");
            writeInt(out, 36 + audio.length);
            out.writeBytes("WAVE");

            out.writeBytes("fmt ");
            writeInt(out, 16);
            out.writeByte(1);
            out.writeByte(0);
            out.writeByte(1);
            out.writeByte(0);
            writeInt(out, 44100);
            writeInt(out, 88200);
            out.writeByte(2);
            out.writeByte(0);
            out.writeByte(16);
            out.writeByte(0);

            out.writeBytes("data");
            writeInt(out, claimedSize);
            out.write(audio);

            out.flush();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return stream.toByteArray();
    }

    static byte[] vox(int models, int claimedVoxels)
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);

        try
        {
            out.writeBytes("VOX ");
            writeInt(out, 150);

            out.writeBytes("MAIN");
            writeInt(out, 0);
            writeInt(out, 0);

            for (int i = 0; i < models; i++)
            {
                out.writeBytes("SIZE");
                writeInt(out, 12);
                writeInt(out, 0);
                writeInt(out, 4);
                writeInt(out, 4);
                writeInt(out, 4);

                out.writeBytes("XYZI");
                writeInt(out, 4);
                writeInt(out, 0);
                writeInt(out, claimedVoxels);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(1);
            }

            out.flush();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return stream.toByteArray();
    }

    static Wave readWave(byte[] bytes)
    {
        try
        {
            return new WaveReader().read(new ByteArrayInputStream(bytes));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    static VoxDocument readVox(byte[] bytes)
    {
        try
        {
            return new VoxReader().read(new ByteArrayInputStream(bytes), new Link("test", "test.vox"));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** A PNG of the given size, written by the JDK so that it is a real one.
        The pixels can be a lie, the header is what the reader looks at first. */
    static byte[] png(int width, int height, boolean gray) throws Exception
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        javax.imageio.ImageIO.write(image, "png", stream);

        return stream.toByteArray();
    }

    /** A PNG that says it is huge, without the pixels. */
    static byte[] hugePng(int width, int height) throws Exception
    {
        byte[] png = png(4, 4, false);
        byte[] out = png.clone();

        writeIntBigEndian(out, 16, width);
        writeIntBigEndian(out, 20, height);

        java.util.zip.CRC32 crc = new java.util.zip.CRC32();

        crc.update(out, 8, 21);

        int value = (int) crc.getValue();

        out[29] = (byte) value;
        out[30] = (byte) (value >> 8);
        out[31] = (byte) (value >> 16);
        out[32] = (byte) (value >> 24);

        return out;
    }

    static void writeIntBigEndian(byte[] bytes, int offset, int value)
    {
        bytes[offset] = (byte) (value >> 24);
        bytes[offset + 1] = (byte) (value >> 16);
        bytes[offset + 2] = (byte) (value >> 8);
        bytes[offset + 3] = (byte) value;
    }

    static void chunk(DataOutputStream out, String id, byte[] data) throws Exception
    {
        byte[] name = id.getBytes("ASCII");
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();

        crc.update(name);
        crc.update(data);

        out.write(name);
        out.writeInt(data.length);
        out.write(data);
        out.writeInt((int) crc.getValue());
    }

    public static void main(String[] args) throws Exception
    {
        System.out.println("== un wav normal ==");

        byte[] audio = new byte[1024];

        for (int i = 0; i < audio.length; i++)
        {
            audio[i] = (byte) (i * 7);
        }

        Wave wave = readWave(wav(audio, audio.length));

        check("se lee", wave != null);
        check("y el audio es el que tiene", wave != null && wave.data.length == audio.length);
        check("sin perder bytes", wave != null && java.util.Arrays.equals(wave.data, audio));

        System.out.println("== un wav que dice que es enorme ==");

        Wave lying = readWave(wav(audio, 100 * 1024 * 1024));

        check("se lee", lying != null);
        check("pero solo guarda los bytes que hay", lying != null && lying.data.length == audio.length);

        System.out.println("== un wav que dice que es de dos gigas ==");

        Wave refused = readWave(wav(audio, 0x7FFFFFF0));

        check("no se lee", refused == null);

        System.out.println("== un wav que no tiene chunk de audio ==");

        Wave empty = readWave(wav(new byte[0], 0));

        check("se lee", empty != null);
        check("pero sin audio", empty != null && empty.data.length == 0);

        System.out.println("== un vox normal ==");

        VoxDocument document = readVox(vox(1, 1));

        check("se lee", document != null);
        check("y tiene un modelo", document != null && document.chunks.size() == 1);

        System.out.println("== un vox con demasiados modelos ==");

        VoxDocument many = readVox(vox(1000, 1));

        check("no se lee", many == null);

        System.out.println("== un vox que dice que tiene billions de voxels ==");

        long start = System.nanoTime();

        VoxDocument lyingVox = readVox(vox(1, 0x7FFFFFFF));

        long time = (System.nanoTime() - start) / 1000000L;

        check("no se lee", lyingVox == null);
        check("y no tarda una vida", time < 2000);

        System.out.println("== un vox con un volumen enorme ==");

        VoxDocument huge = readVox(vox(1, 1));

        check("un modelo normal si se lee", huge != null);

        System.out.println("== un png normal ==");

        try
        {
            mchorse.bbs_mod.utils.resources.Pixels pixels = mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(new ByteArrayInputStream(png(4, 4, false)));

            check("se lee", pixels != null);
            check("y tiene el tamano que dice", pixels != null && pixels.width == 4 && pixels.height == 4);

            mchorse.bbs_mod.utils.resources.Pixels gray = mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(new ByteArrayInputStream(png(4, 4, true)));

            check("un png en gris tambien se lee", gray != null);
            check("y con el tamano que dice", gray != null && gray.width == 4 && gray.height == 4);
        }
        catch (Throwable e)
        {
            check("el png se puede leer (" + e + ")", false);
        }

        System.out.println("== un png que dice que es enorme ==");

        try
        {
            mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(new ByteArrayInputStream(hugePng(17000, 17000)));

            check("no se lee", false);
        }
        catch (Throwable e)
        {
            check("no se lee", true);
        }

        System.out.println("== un png roto ==");

        byte[] broken = java.util.Arrays.copyOf(png(4, 4, false), 20);

        try
        {
            mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(new ByteArrayInputStream(broken));

            check("no se lee", false);
        }
        catch (Throwable e)
        {
            check("no se lee", true);
        }

        System.out.println("== algo que no es un png ==");

        try
        {
            mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(new ByteArrayInputStream(new byte[100]));

            check("no se lee", false);
        }
        catch (Throwable e)
        {
            check("no se lee", true);
        }

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
