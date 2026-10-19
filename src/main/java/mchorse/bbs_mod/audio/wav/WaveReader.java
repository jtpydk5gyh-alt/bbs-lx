package mchorse.bbs_mod.audio.wav;

import mchorse.bbs_mod.audio.BinaryChunk;
import mchorse.bbs_mod.audio.BinaryReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.utils.Pair;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @link http://soundfile.sapp.org/doc/WaveFormat/
 */
public class WaveReader extends BinaryReader
{
    public static final int MAX_CHUNK = 1 << 28;
    public static final int MAX_ENTRIES = 4096;

    public Wave read(InputStream stream) throws Exception
    {
        try
        {
            BinaryChunk main = this.readChunk(stream);

            if (!main.id.equals("RIFF"))
            {
                throw new Exception("Given file is not 'RIFF'! It's '" + main.id + "' instead...");
            }

            String format = this.readFourString(stream);

            if (!format.equals("WAVE"))
            {
                throw new Exception("Given RIFF file is not a 'WAVE' file! It's '" + format + "' instead...");
            }

            int audioFormat = -1;
            int numChannels = -1;
            int sampleRate = -1;
            int byteRate = -1;
            int blockAlign = -1;
            int bitsPerSample = -1;
            byte[] data = null;
            List<WaveList> lists = new ArrayList<>();
            List<WaveCue> cues = new ArrayList<>();

            while (true)
            {
                try
                {
                    BinaryChunk chunk = this.readChunk(stream);

                    this.log("Reading chunk: " + chunk.id + " " + chunk.size + " " + stream.available());

                    if (chunk.id.equals("fmt "))
                    {
                        audioFormat = this.readShort(stream);
                        numChannels = this.readShort(stream);

                        sampleRate = this.readInt(stream);
                        byteRate = this.readInt(stream);

                        blockAlign = this.readShort(stream);
                        bitsPerSample = this.readShort(stream);

                        /* Discarding extra data */
                        if (chunk.size > 16)
                        {
                            stream.skip(chunk.size - 16);
                        }
                    }
                    else if (chunk.id.equals("data"))
                    {
                        data = this.readData(stream, chunk.size);
                    }
                    /* https://www.recordingblogs.com/wiki/list-chunk-of-a-wave-file */
                    else if (chunk.id.equals("LIST"))
                    {
                        byte[] listData = this.readData(stream, chunk.size);

                        try
                        {
                            ByteArrayInputStream bytes = new ByteArrayInputStream(listData);
                            WaveList list = new WaveList(this.readFourString(bytes));

                            while (bytes.available() > 0)
                            {
                                if (list.entries.size() >= MAX_ENTRIES)
                                {
                                    throw new EOFException("Refusing more than " + MAX_ENTRIES + " entries in a LIST chunk");
                                }

                                String id = this.readFourString(bytes);
                                int size = this.readInt(bytes);

                                if (size < 0 || size > bytes.available())
                                {
                                    throw new EOFException("Refusing a LIST entry of " + size + " bytes");
                                }

                                byte[] stringData = new byte[size];

                                bytes.read(stringData);

                                String string = new String(stringData);

                                list.entries.add(new Pair<>(id, string));
                            }

                            lists.add(list);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                    /* https://www.recordingblogs.com/wiki/cue-chunk-of-a-wave-file */
                    else if (chunk.id.equals("cue "))
                    {
                        byte[] cueData = this.readData(stream, chunk.size);

                        ByteArrayInputStream bytes = new ByteArrayInputStream(cueData);

                        int cuesCount = this.readInt(bytes);

                        if (cuesCount < 0 || cuesCount > MAX_ENTRIES)
                        {
                            throw new EOFException("Refusing " + cuesCount + " cues, the limit is " + MAX_ENTRIES);
                        }

                        while (cuesCount > 0)
                        {
                            WaveCue cue = new WaveCue();

                            cue.id = this.readInt(bytes);
                            cue.position = this.readInt(bytes);
                            cue.dataChunkID = this.readInt(bytes);
                            cue.chunkStart = this.readInt(bytes);
                            cue.blockStart = this.readInt(bytes);
                            cue.sampleStart = this.readInt(bytes);
                            cuesCount -= 1;

                            cues.add(cue);
                        }
                    }
                    else
                    {
                        this.skip(stream, chunk.size);
                    }
                }
                catch (EOFException e)
                {
                    e.printStackTrace();

                    break;
                }
            }

            if (data == null)
            {
                throw new Exception("The data chunk isn't present in this file!");
            }

            Wave wave = new Wave(audioFormat, numChannels, sampleRate, byteRate, blockAlign, bitsPerSample, data);

            wave.lists = lists;
            wave.cues = cues;

            return wave;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private void log(String s)
    {
        // System.out.println(s);
    }

    /**
     * Read the data of a chunk. A chunk says how big it is, but the file may
     * be shorter than that, and then the missing part is not read at all: it
     * is not reserved either, so a file of a few bytes that says it has a
     * hundred megabytes of audio does not make the game reserve a hundred
     * megabytes.
     */
    private byte[] readData(InputStream stream, int size) throws Exception
    {
        int available = stream.available();

        if (available > 0 && available < size)
        {
            size = available;
        }

        byte[] data = new byte[size];
        int offset = 0;

        while (offset < size)
        {
            int read = stream.read(data, offset, size - offset);

            if (read <= 0)
            {
                break;
            }

            offset += read;
        }

        if (offset == size)
        {
            return data;
        }

        byte[] shorter = new byte[offset];

        System.arraycopy(data, 0, shorter, 0, offset);

        return shorter;
    }

    public BinaryChunk readChunk(InputStream stream) throws Exception
    {
        this.log("- Starting reading chunk..." + stream.available());

        String id = this.readFourString(stream);

        this.log("- Read chunk ID... " + id + " " + stream.available());

        int size = this.readInt(stream);

        this.log("- Read chunk size... " + size + " " + stream.available());

        if (size < 0 || size > MAX_CHUNK)
        {
            throw new EOFException("Refusing a chunk of " + size + " bytes, the limit is " + MAX_CHUNK);
        }

        return new BinaryChunk(id, size);
    }
}