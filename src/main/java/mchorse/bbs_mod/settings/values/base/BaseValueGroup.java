package mchorse.bbs_mod.settings.values.base;

import mchorse.bbs_mod.utils.DataPath;

import java.util.List;

public abstract class BaseValueGroup extends BaseValue
{
    public BaseValueGroup(String id)
    {
        super(id);
    }

    public abstract List<BaseValue> getAll();

    public abstract BaseValue get(String key);

    public BaseValue getRecursively(DataPath path)
    {
        if (path.size() <= 0)
        {
            throw new IllegalStateException("Property by path " + path + " can't be found!");
        }

        BaseValue value = this.get(path.strings.get(0));

        if (value == null)
        {
            value = this.searchRecursively(path);
        }
        else if (path.size() > 1)
        {
            if (value instanceof BaseValueGroup group)
            {
                value = group.getRecursively(new DataPath(path.strings.subList(1, path.size())));
            }
            else
            {
                value = null;
            }
        }

        if (value == null)
        {
            throw new IllegalStateException("Property by path " + path + " can't be found!");
        }

        return value;
    }

    private BaseValue searchRecursively(DataPath splits)
    {
        int i = 0;
        BaseValue current = this;

        while (current != null && i < splits.size())
        {
            if (current instanceof BaseValueGroup group)
            {
                current = group.get(splits.strings.get(i));
                i += 1;
            }
            else
            {
                current = null;
            }
        }

        return current;
    }

    public abstract void copy(BaseValueGroup group);
}