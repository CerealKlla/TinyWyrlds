package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldLifecycle;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModBlockEntities;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceData;
import com.github.cerealklla.tinywyrlds.wyrldprint.generate.OneOffGenerator;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipeStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holds one {@link TunnelDoorBlock}'s own Tunnel-pairing identity -- which pair it belongs to,
 * whether it's the "A" or "B" end, where its partner door physically is, which {@code WyrldRecipe}
 * generates the connecting content, and which {@link WyrldLifecycle} governs it. Both doors of a
 * pair are configured together, once, by {@code api.TinyWyrlds#createTunnelDoorPair} -- each one
 * is handed the other's real {@link GlobalPos} directly at creation time, so neither ever needs a
 * registry/index lookup to find its partner.
 *
 * <p>{@link #enter} is the whole mechanic: resolve (or lazily generate, on the very first use --
 * explicit user instruction not to bother generating any earlier, "whichever is easier/less
 * performance heavy on the server") the one Wyrld instance this pairing leads to, keyed in {@code
 * WyrldSpaceData}'s tunnel-assignment map by the pair id alone for {@link WyrldLifecycle#PUBLIC}
 * (one shared instance for every entrant) or by pair id + the entering player's own UUID for
 * {@link WyrldLifecycle#INSTANCED} (a private generation per player, first-come-first-generated) --
 * then teleport the player to whichever of the instance's two baked-in spawn points matches this
 * door's own role.
 */
public class TunnelDoorBlockEntity extends BlockEntity {

    private UUID pairId;
    private boolean roleA;
    private GlobalPos partnerPos;
    private String recipeName = "";
    private WyrldLifecycle lifecycle = WyrldLifecycle.INSTANCED;

    public TunnelDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNNEL_DOOR.get(), pos, state);
    }

    /** Called once, right after placement, by {@code api.TinyWyrlds#createTunnelDoorPair} -- never
     *  re-configured afterward. */
    public void configure(UUID pairId, boolean roleA, GlobalPos partnerPos, String recipeName, WyrldLifecycle lifecycle) {
        this.pairId = pairId;
        this.roleA = roleA;
        this.partnerPos = partnerPos;
        this.recipeName = recipeName;
        this.lifecycle = lifecycle;
        setChanged();
    }

    public void enter(ServerPlayer player) {
        if (pairId == null || partnerPos == null || level == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity at {}: never configured -- not a real Tunnel door.", worldPosition);
            return;
        }
        MinecraftServer server = player.level().getServer();
        String assignmentKey = lifecycle == WyrldLifecycle.PUBLIC ? pairId.toString() : pairId + ":" + player.getUUID();

        Optional<UUID> existingWyrldId = WyrldSpaceData.get(server).getTunnelAssignment(assignmentKey);
        if (existingWyrldId.isPresent()) {
            Optional<WyrldInstanceRecord> existingInstance = WyrldSpaceData.get(server).getInstance(existingWyrldId.get());
            if (existingInstance.isPresent()) {
                Optional<BlockPos> spawn = roleA ? existingInstance.get().tunnelSpawnA() : existingInstance.get().tunnelSpawnB();
                if (spawn.isPresent()) {
                    WyrldManager.enterAt(player, existingWyrldId.get(), spawn.get());
                    return;
                }
            }
            // The remembered instance was wiped (shouldn't normally happen for PUBLIC, which is
            // swept-exempt -- but an INSTANCED assignment has no such protection) or never got a
            // real spawn point for some reason -- fall through and generate fresh rather than
            // leaving the player stuck with a dead assignment forever.
        }

        WyrldRecipe recipe = WyrldRecipeStorage.get().load(recipeName).orElse(null);
        if (recipe == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity: recipe '{}' no longer exists for Tunnel pair {}.", recipeName, pairId);
            return;
        }
        GlobalPos myPos = GlobalPos.of(level.dimension(), worldPosition);
        GlobalPos destA = roleA ? myPos : partnerPos;
        GlobalPos destB = roleA ? partnerPos : myPos;
        OneOffGenerator.TunnelGenerateResult result = OneOffGenerator.generateTunnel(server, recipe, destA, destB, lifecycle);
        if (result == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity: recipe '{}' has no valid ENTRANCE/CONNECTOR/ROOM content to build Tunnel pair {} from.", recipeName, pairId);
            return;
        }
        WyrldSpaceData.get(server).putTunnelAssignment(assignmentKey, result.instance().id());
        WyrldManager.enterAt(player, result.instance().id(), roleA ? result.spawnA() : result.spawnB());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pairId = input.read("PairId", UUIDUtil.CODEC).orElse(null);
        roleA = input.getBooleanOr("RoleA", true);
        partnerPos = input.read("PartnerPos", GlobalPos.CODEC).orElse(null);
        recipeName = input.getStringOr("RecipeName", "");
        lifecycle = input.read("Lifecycle", WyrldLifecycle.CODEC).orElse(WyrldLifecycle.INSTANCED);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("PairId", UUIDUtil.CODEC, pairId);
        output.putBoolean("RoleA", roleA);
        output.storeNullable("PartnerPos", GlobalPos.CODEC, partnerPos);
        output.putString("RecipeName", recipeName);
        output.store("Lifecycle", WyrldLifecycle.CODEC, lifecycle);
    }
}
