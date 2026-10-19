import io.netty.buffer.Unpooled;
import mchorse.bbs_mod.network.PacketCrusher;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Collections;

/** The crusher used to divide the size of the payload with a float, which lost
    the last byte when the payload was exactly one buffer more. The check
    sends a payload through the real crusher and looks at what comes out the
    other side. */
public class CrusherCheck
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

    static class Crusher extends PacketCrusher
    {
        @Override
        protected void sendBuffer(net.minecraft.entity.player.PlayerEntity entity, Identifier identifier, PacketByteBuf buf)
        {}
    }

    /** The bytes of a chunk, the way the crusher writes them. */
    static PacketByteBuf chunk(int counter, int index, int total, byte[] bytes, int offset, int size)
    {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeInt(counter);
        buf.writeInt(index);
        buf.writeInt(total);
        buf.writeInt(size);
        buf.writeBytes(bytes, offset, size);

        return buf;
    }

    /** Sends a payload in chunks, the way the crusher does, and returns what
        the receiver got. */
    static byte[] roundTrip(byte[] payload)
    {
        Crusher crusher = new Crusher();
        int total = (payload.length + PacketCrusher.BUFFER_SIZE - 1) / PacketCrusher.BUFFER_SIZE;
        byte[][] received = new byte[1][];

        for (int index = 0; index < total; index++)
        {
            int offset = index * PacketCrusher.BUFFER_SIZE;
            int size = Math.min(PacketCrusher.BUFFER_SIZE, payload.length - offset);
            PacketByteBuf buf = chunk(7, index, total, payload, offset, size);

            crusher.receive(buf, (bytes, rest) -> received[0] = bytes);
        }

        return received[0];
    }

    public static void main(String[] args)
    {
        System.out.println("== una carga pequena ==");

        byte[] small = new byte[100];

        for (int i = 0; i < small.length; i++)
        {
            small[i] = (byte) i;
        }

        byte[] received = roundTrip(small);

        check("llega entera", received != null && received.length == small.length);
        check("y con los mismos bytes", received != null && java.util.Arrays.equals(received, small));

        System.out.println("== una carga de varios trozos ==");

        int limit = PacketCrusher.MAX_CHUNKS * PacketCrusher.BUFFER_SIZE;
        int[] sizes = {30_000, 30_001, 60_000, 60_001, 3_000_000, 3_000_001, 30_719_999, limit};

        for (int size : sizes)
        {
            byte[] payload = new byte[size];

            for (int i = 0; i < size; i++)
            {
                payload[i] = (byte) (i * 31);
            }

            byte[] got = roundTrip(payload);

            check(size + " bytes llegan enteros", got != null && got.length == size);
            check(size + " bytes llegan iguales", got != null && java.util.Arrays.equals(got, payload));
        }

        System.out.println("== el numero de trozos ==");

        for (int size : sizes)
        {
            int total = (size + PacketCrusher.BUFFER_SIZE - 1) / PacketCrusher.BUFFER_SIZE;

            check(size + " bytes caben en " + total + " trozos", total <= PacketCrusher.MAX_CHUNKS);
            check("y los trozos no se quedan cortos", total * PacketCrusher.BUFFER_SIZE >= size);
        }

        System.out.println("== una carga un byte mas alla del limite ==");

        byte[] tooBig = new byte[limit + 1];

        byte[] got = roundTrip(tooBig);

        check("el receptor no la acepta", got == null);

        boolean refused = false;

        try
        {
            new Crusher().send(Collections.emptyList(), new Identifier("bbs", "check"), tooBig, null);
        }
        catch (IllegalArgumentException e)
        {
            refused = true;
        }

        check("y el emisor la rechaza en vez de mandarla cortada", refused);

        System.out.println("== el emisor cuenta los trozos igual ==");

        Crusher crusher = new Crusher();
        int[] counted = new int[1];

        crusher.send(Collections.emptyList(), new Identifier("bbs", "check"), new byte[limit], (buf) ->
        {
            buf.readInt();
            buf.readInt();
            counted[0] = buf.readInt();
        });

        check("el emisor dice 1024 trozos", counted[0] == PacketCrusher.MAX_CHUNKS);
        check("que es lo que cabe", counted[0] <= PacketCrusher.MAX_CHUNKS);

        System.out.println("== una carga que no cabe se rechaza ==");

        boolean tooBigRefused = false;

        try
        {
            crusher.send(Collections.emptyList(), new Identifier("bbs", "check"), new byte[(PacketCrusher.MAX_CHUNKS + 1) * PacketCrusher.BUFFER_SIZE], null);
        }
        catch (IllegalArgumentException e)
        {
            tooBigRefused = true;
        }

        check("una carga de mas de 1024 trozos se rechaza", tooBigRefused);

        System.out.println("== un trozo con un tamano raro ==");

        Crusher other = new Crusher();
        byte[] result = new byte[1];

        PacketByteBuf bigChunk = new PacketByteBuf(Unpooled.buffer());

        bigChunk.writeInt(1);
        bigChunk.writeInt(0);
        bigChunk.writeInt(1);
        bigChunk.writeInt(PacketCrusher.BUFFER_SIZE + 1);
        bigChunk.writeBytes(new byte[8]);

        other.receive(bigChunk, (bytes, rest) -> result[0] = (byte) (bytes == null ? 0 : 1));

        check("un trozo que dice ser grande no se acepta", result[0] == 0);

        System.out.println("== un total imposible ==");

        Crusher third = new Crusher();

        result[0] = 0;

        PacketByteBuf tooMany = new PacketByteBuf(Unpooled.buffer());

        tooMany.writeInt(1);
        tooMany.writeInt(0);
        tooMany.writeInt(PacketCrusher.MAX_CHUNKS + 1);
        tooMany.writeInt(1);
        tooMany.writeBytes(new byte[1]);

        third.receive(tooMany, (bytes, rest) -> result[0] = (byte) (bytes == null ? 0 : 1));

        check("un total de mas de 1024 trozos no se acepta", result[0] == 0);

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
