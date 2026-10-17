package mchorse.bbs_mod.network;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class PacketCrusher
{
    public static final int BUFFER_SIZE = 30_000;
    public static final int MAX_CHUNKS = 1024;
    public static final int MAX_PENDING = 16;
    public static final int MAX_PENDING_BYTES = 1 << 25;

    private Map<Integer, ByteArrayOutputStream> chunks = new HashMap<>();
    private int pendingBytes;
    private int counter;

    public void reset()
    {
        this.chunks.clear();
        this.pendingBytes = 0;
        this.counter = 0;
    }

    public void receive(PacketByteBuf buf, IBufferReceiver receiver)
    {
        int id = buf.readInt();
        int index = buf.readInt();
        int total = buf.readInt();
        int size = buf.readInt();

        if (size < 0 || size > BUFFER_SIZE || size > buf.readableBytes())
        {
            return;
        }

        if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total)
        {
            return;
        }

        if (!this.chunks.containsKey(id) && this.chunks.size() >= MAX_PENDING)
        {
            return;
        }

        if (this.pendingBytes + size > MAX_PENDING_BYTES)
        {
            return;
        }

        byte[] bytes = new byte[size];

        buf.readBytes(bytes);

        ByteArrayOutputStream map = this.chunks.computeIfAbsent(id, (k) -> new ByteArrayOutputStream());

        if (map.size() + size > total * BUFFER_SIZE)
        {
            this.chunks.remove(id);
            this.pendingBytes -= map.size();

            return;
        }

        this.pendingBytes += size;
        map.writeBytes(bytes);

        if (index == total - 1)
        {
            byte[] finalBytes = map.toByteArray();

            if (finalBytes.length == 1 && finalBytes[0] == 69)
            {
                finalBytes = null;
            }

            this.chunks.remove(id);
            this.pendingBytes -= finalBytes == null ? 1 : finalBytes.length;

            receiver.receiveBuffer(finalBytes, buf);
        }
    }

    public void send(PlayerEntity entity, Identifier identifier, BaseType baseType, Consumer<PacketByteBuf> consumer)
    {
        this.send(Collections.singleton(entity), identifier, baseType, consumer);
    }

    public void send(PlayerEntity entity, Identifier identifier, byte[] bytes, Consumer<PacketByteBuf> consumer)
    {
        this.send(Collections.singleton(entity), identifier, bytes, consumer);
    }

    public void send(Collection<PlayerEntity> entities, Identifier identifier, BaseType baseType, Consumer<PacketByteBuf> consumer)
    {
        this.send(entities, identifier, DataStorageUtils.writeToBytes(baseType), consumer);
    }

    public void send(Collection<PlayerEntity> entities, Identifier identifier, byte[] bytes, Consumer<PacketByteBuf> consumer)
    {
        if (bytes.length == 0)
        {
            bytes = new byte[]{69};
        }

        int total = Math.max((int) Math.ceil(bytes.length / (float) BUFFER_SIZE), 1);
        int counter = this.counter;

        if (total > MAX_CHUNKS)
        {
            throw new IllegalArgumentException("Payload of " + bytes.length + " bytes needs " + total + " chunks, the limit is " + MAX_CHUNKS);
        }

        for (int index = 0; index < total; index++)
        {
            int offset = index * BUFFER_SIZE;

            PacketByteBuf buf = PacketByteBufs.create();
            int size = Math.min(BUFFER_SIZE, bytes.length - offset);

            buf.writeInt(counter);
            buf.writeInt(index);
            buf.writeInt(total);
            buf.writeInt(size);
            buf.writeBytes(bytes, offset, size);

            if (consumer != null && index == total - 1)
            {
                consumer.accept(buf);
            }

            for (PlayerEntity playerEntity : entities)
            {
                this.sendBuffer(playerEntity, identifier, buf);
            }
        }

        this.counter += 1;
    }

    protected abstract void sendBuffer(PlayerEntity entity, Identifier identifier, PacketByteBuf buf);
}