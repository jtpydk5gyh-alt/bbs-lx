package mchorse.bbs_mod.data.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DataGzipStorage extends DataStorage
{
    private DataStorage storage;

    public DataGzipStorage(DataStorage storage)
    {
        this.storage = storage;
    }

    @Override
    protected InputStream getInputStream() throws IOException
    {
        return new GZIPInputStream(this.storage.getInputStream());
    }

    @Override
    protected OutputStream getOutputStream() throws IOException
    {
        return new GZIPOutputStream(this.storage.getOutputStream());
    }

    @Override
    protected File getTarget()
    {
        return this.storage.getTarget();
    }

    @Override
    protected OutputStream openTarget(File file) throws IOException
    {
        return new GZIPOutputStream(new FileOutputStream(file));
    }
}