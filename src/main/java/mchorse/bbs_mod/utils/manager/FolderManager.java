package mchorse.bbs_mod.utils.manager;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Folder based manager
 */
public abstract class FolderManager <T extends ValueGroup> implements IManager<T>
{
    protected Supplier<File> folder;

    public FolderManager(Supplier<File> folder)
    {
        this.folder = folder;
    }

    public File getFolder()
    {
        File file = this.folder.get();

        if (!file.exists())
        {
            file.mkdirs();
        }

        return file;
    }

    @Override
    public boolean exists(String name)
    {
        File file = this.getFile(name);

        return file != null && file.exists();
    }

    @Override
    public boolean rename(String from, String to)
    {
        File file = this.getFile(from);
        File target = this.getFile(to);

        if (file != null && target != null && file.exists())
        {
            if (file.renameTo(target))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean delete(String name)
    {
        File file = this.getFile(name);

        return file != null && file.delete();
    }

    /**
     * Add a folder.
     */
    public boolean addFolder(String path)
    {
        File folder = this.getFolder(this.normalizePath(path));

        if (folder == null || folder.exists())
        {
            return false;
        }

        return folder.mkdirs();
    }

    /**
     * Rename given folder to another name. From and to arguments expect trailing slashes!
     */
    public boolean renameFolder(String from, String to)
    {
        from = this.normalizePath(from);
        to = this.normalizePath(to);

        File folder = this.getFolder(from);
        File target = this.getFolder(to);

        if (folder != null && target != null && folder.isDirectory())
        {
            if (folder.renameTo(target))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Delete given folder. It only works if the folder is empty.
     */
    public boolean deleteFolder(String path)
    {
        File folder = this.getFolder(this.normalizePath(path));

        if (folder != null && folder.isDirectory())
        {
            if (folder.delete())
            {
                return true;
            }
        }

        return false;
    }

    private String normalizePath(String path)
    {
        return path.endsWith("/") ? path : path + "/";
    }

    @Override
    public Collection<String> getKeys()
    {
        Set<String> set = new HashSet<>();

        if (this.folder == null)
        {
            return set;
        }

        this.recursiveFind(set, this.getFolder(), "");

        return set;
    }

    private void recursiveFind(Set<String> set, File folder, String prefix)
    {
        for (File file : folder.listFiles())
        {
            String name = file.getName();

            if (file.isFile() && this.isData(file))
            {
                set.add(prefix + name.substring(0, name.lastIndexOf(".")));
            }
            else if (file.isDirectory() && !file.getName().startsWith("_"))
            {
                File[] files = file.listFiles();

                if (files == null || files.length == 0)
                {
                    set.add(prefix + name + "/");
                }
                else
                {
                    this.recursiveFind(set, file, prefix + name + "/");
                }
            }
        }
    }

    protected boolean isData(File file)
    {
        return file.getName().endsWith(this.getExtension());
    }

    public List<File> getBackups(String id)
    {
        File file = this.getFile(id);

        if (file == null)
        {
            return Collections.emptyList();
        }

        File folder = this.getBackupsFolder(id);
        File[] files = folder.listFiles();

        if (files == null)
        {
            return Collections.emptyList();
        }

        List<File> backups = new ArrayList<>();

        for (File f : files)
        {
            if (f.isFile() && f.getName().endsWith(this.getExtension()))
            {
                backups.add(f);
            }
        }

        backups.sort((a, b) -> b.getName().compareTo(a.getName()));

        return backups;
    }

    protected File getBackupsFolder(String id)
    {
        File file = this.getFile(id);

        return new File(file.getParentFile(), "_" + StringUtils.fileName(id));
    }

    public File getFile(String name)
    {
        if (this.folder == null)
        {
            return null;
        }

        File root = this.getFolder();
        File file = new File(root, name + this.getExtension());

        return IOUtils.inside(root, file) ? file : null;
    }

    public File getFolder(String path)
    {
        if (this.folder == null)
        {
            return null;
        }

        File root = this.getFolder();
        File file = new File(root, path);

        return IOUtils.inside(root, file) ? file : null;
    }

    protected String getExtension()
    {
        return ".json";
    }
}