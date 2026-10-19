package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;

public class AnimationPart implements IMapSerializable
{
    public final KeyframeChannel<MolangExpression> x = new KeyframeChannel<>("x", null);
    public final KeyframeChannel<MolangExpression> y = new KeyframeChannel<>("y", null);
    public final KeyframeChannel<MolangExpression> z = new KeyframeChannel<>("z", null);

    public final KeyframeChannel<MolangExpression> sx = new KeyframeChannel<>("sx", null);
    public final KeyframeChannel<MolangExpression> sy = new KeyframeChannel<>("sy", null);
    public final KeyframeChannel<MolangExpression> sz = new KeyframeChannel<>("sz", null);

    public final KeyframeChannel<MolangExpression> rx = new KeyframeChannel<>("rx", null);
    public final KeyframeChannel<MolangExpression> ry = new KeyframeChannel<>("ry", null);
    public final KeyframeChannel<MolangExpression> rz = new KeyframeChannel<>("rz", null);

    public final List<KeyframeChannel<MolangExpression>> channels = List.of(this.x, this.y, this.z, this.sx, this.sy, this.sz, this.rx, this.ry, this.rz);

    private MolangParser parser;

    public AnimationPart(MolangParser parser)
    {
        this.parser = parser;
    }

    @Override
    public void fromData(MapType data)
    {
        if (data.has("translate")) parseChannel(this.x, this.y, this.z, data.get("translate"), MolangParser.ZERO);
        if (data.has("scale")) parseChannel(this.sx, this.sy, this.sz, data.get("scale"), MolangParser.ONE);
        if (data.has("rotate")) parseChannel(this.rx, this.ry, this.rz, data.get("rotate"), MolangParser.ZERO);
    }

    private void parseChannel(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, BaseType data, MolangExpression defaultValue)
    {
        if (BaseType.isList(data))
        {
            for (BaseType keyframe : (ListType) data)
            {
                this.parseAnimationVector(x, y, z, keyframe, defaultValue);
            }
        }

        x.sort();
        y.sort();
        z.sort();
    }

    private void parseAnimationVector(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, BaseType data, MolangExpression defaultValue)
    {
        if (!BaseType.isList(data))
        {
            return;
        }

        ListType values = data.asList();

        if (values.size() >= 5)
        {
            double time = values.getDouble(0) * 20F;
            IInterp interp = AnimationInterpolation.byName(values.getString(1));

            int xIndex = x.insert((float) time, this.parseValue(this.parser, values.get(2), defaultValue));
            int yIndex = y.insert((float) time, this.parseValue(this.parser, values.get(3), defaultValue));
            int zIndex = z.insert((float) time, this.parseValue(this.parser, values.get(4), defaultValue));

            x.get(xIndex).getInterpolation().setInterp(interp);
            y.get(yIndex).getInterpolation().setInterp(interp);
            z.get(zIndex).getInterpolation().setInterp(interp);
        }
    }

    private MolangExpression parseValue(MolangParser parser, BaseType element, MolangExpression defaultValue)
    {
        if (element.isNumeric())
        {
            return new MolangValue(parser, new Constant(element.asNumeric().doubleValue()));
        }

        try
        {
            return parser.parseExpression(((StringType) element).value);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return defaultValue;
    }

    @Override
    public void toData(MapType data)
    {
        data.put("translate", this.serializeChannel(this.x, this.y, this.z, MolangParser.ZERO));
        data.put("scale", this.serializeChannel(this.sx, this.sy, this.sz, MolangParser.ONE));
        data.put("rotate", this.serializeChannel(this.rx, this.ry, this.rz, MolangParser.ZERO));
    }

    private ListType serializeChannel(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, MolangExpression defaultValue)
    {
        ListType list = new ListType();
        List<Keyframe<MolangExpression>> kx = x.getKeyframes();
        List<Keyframe<MolangExpression>> ky = y.getKeyframes();
        List<Keyframe<MolangExpression>> kz = z.getKeyframes();
        int size = Math.max(kx.size(), Math.max(ky.size(), kz.size()));

        for (int i = 0; i < size; i++)
        {
            Keyframe<MolangExpression> fx = CollectionUtils.getSafe(kx, i, null);
            Keyframe<MolangExpression> fy = CollectionUtils.getSafe(ky, i, null);
            Keyframe<MolangExpression> fz = CollectionUtils.getSafe(kz, i, null);
            Keyframe<MolangExpression> reference = fx != null ? fx : (fy != null ? fy : fz);
            ListType keyframe = new ListType();

            keyframe.addDouble(reference.getTick() / 20D);
            keyframe.addString(this.serializeInterpolation(reference));
            keyframe.add(this.serializeValue(fx, defaultValue));
            keyframe.add(this.serializeValue(fy, defaultValue));
            keyframe.add(this.serializeValue(fz, defaultValue));

            list.add(keyframe);
        }

        return list;
    }

    private String serializeInterpolation(Keyframe<MolangExpression> keyframe)
    {
        String name = AnimationInterpolation.toName(keyframe.getInterpolation().getInterp());

        return name == null ? AnimationInterpolation.toName(Interpolations.LINEAR) : name;
    }

    private BaseType serializeValue(Keyframe<MolangExpression> keyframe, MolangExpression defaultValue)
    {
        return keyframe == null ? defaultValue.toData() : keyframe.getValue().toData();
    }
}
