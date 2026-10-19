package mchorse.bbs_mod.actions;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionPlayer
{
    public static final int MAX_DURATION = 20 * 60 * 60;

    private static final Logger LOGGER = LogUtils.getLogger();

    public Film film;
    public int tick;
    public boolean playing = true;
    public int countdown;
    public int exception;
    public PlayerType type;

    public boolean syncing;
    public boolean stopDamage = true;

    private ServerPlayerEntity serverPlayer;
    private ServerWorld world;
    private int duration;

    private Map<String, LivingEntity> actors = new HashMap<>();

    private List<ItemStack> cachedInventory = new ArrayList<>();
    private Form cachedForm;

    private float cacheHp;
    private float cacheHunger;
    private int cacheXpLevel;
    private float cacheXpProgress;

    public ActionPlayer(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.countdown = countdown;
        this.exception = exception;
        this.type = type;

        this.serverPlayer = serverPlayer;
        this.duration = Math.min(film.camera.calculateDuration(), MAX_DURATION);

        this.updateReplayEntities();

        Replay fpReplay = film.getFirstPersonReplay();

        if (this.type == PlayerType.NORMAL && this.serverPlayer != null && fpReplay != null)
        {
            for (int i = 0; i < this.serverPlayer.getInventory().size(); i++)
            {
                this.cachedInventory.add(serverPlayer.getInventory().getStack(i).copy());
                this.serverPlayer.getInventory().setStack(i, CollectionUtils.getSafe(this.film.inventory.getStacks(), i, ItemStack.EMPTY));
            }

            Morph morph = Morph.getMorph(this.serverPlayer);

            if (morph != null)
            {
                this.cachedForm = FormUtils.copy(morph.getForm());
            }

            ServerNetwork.sendMorphToTracked(this.serverPlayer, fpReplay.form.get());

            this.cacheHp = this.serverPlayer.getHealth();
            this.cacheHunger = this.serverPlayer.getHungerManager().getSaturationLevel();
            this.cacheXpLevel = this.serverPlayer.experienceLevel;
            this.cacheXpProgress = this.serverPlayer.experienceProgress;

            this.serverPlayer.setHealth(this.film.hp.get());
            this.serverPlayer.getHungerManager().setSaturationLevel(this.film.hunger.get());
            this.serverPlayer.experienceProgress = this.film.xpProgress.get();
            this.serverPlayer.setExperienceLevel(this.film.xpLevel.get());
        }
    }

    public void updateReplayEntities()
    {
        for (LivingEntity entity : this.actors.values())
        {
            if (!entity.isPlayer())
            {
                entity.discard();
            }
        }

        this.actors.clear();

        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);
            boolean isActor = replay.actor.get() || replay.fp.get();

            if (i == this.exception || !isActor || !replay.enabled.get())
            {
                continue;
            }

            if (replay.fp.get() && this.serverPlayer != null)
            {
                if (this.type == PlayerType.NORMAL)
                {
                    this.actors.put(replay.getId(), this.serverPlayer);
                }
            }
            else
            {
                ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, this.world);

                actor.setForm(FormUtils.copy(replay.form.get()));

                this.apply(actor, replay, this.tick, false);
                this.actors.put(replay.getId(), actor);
                this.world.spawnEntity(actor);
            }
        }

        for (ServerPlayerEntity player : this.world.getPlayers())
        {
            ServerNetwork.sendActors(player, this.film.getId(), this.actors);
        }
    }

    public ServerWorld getWorld()
    {
        return this.world;
    }

    public ServerPlayerEntity getServerPlayer()
    {
        return this.serverPlayer;
    }

    private int getServerPlayerPermissionLevel()
    {
        if (this.serverPlayer == null)
        {
            return 0;
        }

        return this.serverPlayer.getServer().getPermissionLevel(this.serverPlayer.getGameProfile());
    }

    public void apply(LivingEntity actor, Replay replay, float tick, boolean ticking)
    {
        double x = MathUtils.finite(replay.keyframes.x.interpolate(tick), actor.getX());
        double y = MathUtils.finite(replay.keyframes.y.interpolate(tick), actor.getY());
        double z = MathUtils.finite(replay.keyframes.z.interpolate(tick), actor.getZ());
        float yawHead = (float) MathUtils.finite(replay.keyframes.headYaw.interpolate(tick), actor.getYaw());
        float yawBody = (float) MathUtils.finite(replay.keyframes.bodyYaw.interpolate(tick), actor.getBodyYaw());
        float pitch = (float) MathUtils.finite(replay.keyframes.pitch.interpolate(tick), actor.getPitch());

        Vec3d pos = actor.getPos();

        if (ticking)
        {
            actor.move(MovementType.SELF, new Vec3d(x - pos.x, y - pos.y, z - pos.z));
        }

        actor.setPosition(x, y, z);
        actor.setYaw(yawHead);
        actor.setHeadYaw(yawHead);
        actor.setPitch(pitch);
        actor.setBodyYaw(yawBody);
        actor.setSneaking(replay.keyframes.sneaking.interpolate(tick) > 0);
        actor.setOnGround(replay.keyframes.grounded.interpolate(tick) > 0);
        actor.equipStack(EquipmentSlot.OFFHAND, replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY));
        actor.equipStack(EquipmentSlot.HEAD, replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY));
        actor.equipStack(EquipmentSlot.CHEST, replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY));
        actor.equipStack(EquipmentSlot.LEGS, replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY));
        actor.equipStack(EquipmentSlot.FEET, replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY));

        if (actor instanceof ServerPlayerEntity player)
        {
            int selectedSlot = player.getInventory().selectedSlot;
            int slot = MathUtils.clamp(replay.keyframes.selectedSlot.interpolate(this.tick), 0, 8);

            if (selectedSlot != slot)
            {
                ServerNetwork.sendSelectedSlot(player, slot);
            }

            actor.equipStack(EquipmentSlot.MAINHAND, replay.keyframes.mainHand.interpolate(tick, ItemStack.EMPTY));
        }
        else
        {
            actor.equipStack(EquipmentSlot.MAINHAND, replay.keyframes.mainHand.interpolate(tick, ItemStack.EMPTY));
        }

        double vx = MathUtils.finite(x - replay.keyframes.x.interpolate(tick - 1), 0D);
        double vy = MathUtils.finite(y - replay.keyframes.y.interpolate(tick - 1), 0D);
        double vz = MathUtils.finite(z - replay.keyframes.z.interpolate(tick - 1), 0D);

        if (vy == 0D)
        {
            vy = -0.0784;
        }

        actor.setVelocity(vx, vy, vz);

        actor.fallDistance = (float) MathUtils.finite(replay.keyframes.fall.interpolate(tick), 0D);
    }

    public boolean tick()
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return false;
        }

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                this.apply(entry.getValue(), replay, this.tick, true);
            }
        }

        if (!this.playing)
        {
            return false;
        }

        if (this.tick >= 0)
        {
            this.applyAction();
        }

        this.tick += 1;

        return !this.syncing && this.tick >= this.duration;
    }

    private void applyAction()
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
        List<Replay> list = this.film.replays.getList();

        fakePlayer.setPermissionLevel(this.getServerPlayerPermissionLevel());

        for (int i = 0; i < list.size(); i++)
        {
            if (i == this.exception)
            {
                continue;
            }

            Replay replay = list.get(i);

            if (!replay.enabled.get())
            {
                continue;
            }

            LivingEntity actor = this.actors.get(replay.getId());

            replay.applyActions(actor, fakePlayer, this.film, this.tick);
        }
    }

    public void syncData(DataPath key, BaseType data)
    {
        BaseValue baseValue;

        try
        {
            baseValue = this.film.getRecursively(key);
        }
        catch (Exception e)
        {
            LOGGER.warn("The path '" + key + "' of the film '" + this.film.getId() + "' was not found, so nothing was changed.");

            return;
        }

        if (baseValue != null)
        {
            baseValue.fromData(data);

            if (baseValue.getId().equals("actor") || baseValue.getId().equals("enabled") || baseValue.getId().equals("replays"))
            {
                this.updateReplayEntities();
            }
        }
    }

    public void goTo(int tick)
    {
        this.goTo(this.tick, tick);
    }

    public void goTo(int from, int tick)
    {
        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                this.apply(entry.getValue(), replay, this.tick, false);
            }
        }

        if (from != tick)
        {
            this.tick = from;
            int to = Math.min(tick, this.duration);

            if (to < 0)
            {
                to = 0;
            }

            while (this.tick != to)
            {
                this.tick += this.tick > to ? -1 : 1;

                this.applyAction();
            }
        }
    }

    public void stop()
    {
        for (LivingEntity value : this.actors.values())
        {
            if (!value.isPlayer())
            {
                value.discard();
            }
        }

        if (this.type == PlayerType.NORMAL && this.serverPlayer != null && this.film.getFirstPersonReplay() != null)
        {
            for (int i = 0; i < this.serverPlayer.getInventory().size(); i++)
            {
                this.serverPlayer.getInventory().setStack(i, this.cachedInventory.get(i));
            }

            ServerNetwork.sendMorphToTracked(this.serverPlayer, this.cachedForm);

            this.serverPlayer.setHealth(this.cacheHp);
            this.serverPlayer.getHungerManager().setSaturationLevel(this.cacheHunger);
            this.serverPlayer.experienceProgress = this.cacheXpProgress;
            this.serverPlayer.setExperienceLevel(this.cacheXpLevel);
        }
    }

    public void toggle()
    {
        this.playing = !this.playing;
    }
}