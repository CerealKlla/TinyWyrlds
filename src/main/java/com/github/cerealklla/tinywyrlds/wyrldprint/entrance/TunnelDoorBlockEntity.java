package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.ArrayList;
import java.util.List;
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
 * Holds one {@link TunnelDoorBlock}'s own Tunnel-group identity -- which group it belongs to, its
 * own index within that group, where every door in the group physically is (itself included, at
 * {@code groupPositions.get(roleIndex)}), which {@code WyrldRecipe} generates the connecting
 * content, and which {@link WyrldLifecycle} governs it. Every door of a group is configured
 * together, once, by {@code api.TinyWyrlds#createTunnelDoorGroup} -- each one is handed the whole
 * group's real {@link GlobalPos} list directly at creation time, so none ever needs a
 * registry/index lookup to find the others.
 *
 * <p>Widened from a fixed two-door {@code roleA}/{@code partnerPos} pair to an arbitrary-length
 * group, 2026-11-12, per explicit user spec: "certain settlements could have multiple entrances to
 * the same underground sewer system... I could also see having multiple entrances to, say, a
 * mountain mineshaft themed tunnel." A door's own {@code groupId} (NBT key kept as {@code "PairId"}
 * for backward compatibility with already-placed doors) is still just a UUID shared by the whole
 * group, whatever its size.
 *
 * <p>{@link #enter} is the whole mechanic: resolve (or lazily generate, on the very first use --
 * explicit user instruction not to bother generating any earlier, "whichever is easier/less
 * performance heavy on the server") the one Wyrld instance this group leads to, keyed in {@code
 * WyrldSpaceData}'s tunnel-assignment map by the group id alone for {@link WyrldLifecycle#PUBLIC}
 * (one shared instance for every entrant) or by group id + the entering player's own UUID for
 * {@link WyrldLifecycle#INSTANCED} (a private generation per player, first-come-first-generated) --
 * then teleport the player to whichever of the instance's baked-in spawn points matches this door's
 * own {@code roleIndex}.
 */
public class TunnelDoorBlockEntity extends BlockEntity {

    private UUID groupId;
    private int roleIndex;
    /** Every door's position in this group, including this door's own (at {@code groupPositions
     *  .get(roleIndex)}), in the same order every door in the group was configured in. */
    private List<GlobalPos> groupPositions = List.of();
    private String recipeName = "";
    private WyrldLifecycle lifecycle = WyrldLifecycle.INSTANCED;
    /** A copy of {@link #recipeName}'s own recipe, captured whenever it's successfully loaded --
     *  this door's own fallback source of truth for its generation parameters, not just a name
     *  pointer into {@code WyrldRecipeStorage}. Explicit user spec: an entrance should carry its
     *  own generation parameters so a deleted/renamed recipe can be rebuilt from the entrance
     *  itself rather than silently failing. Kept in sync on every successful {@link #enter} (see
     *  its own doc), so an admin's later edit (e.g. adding a tag) is still picked up normally --
     *  this backup copy only ever gets used when the live recipe is actually missing. */
    private WyrldRecipe recipeBackup;
    /** A legacy single {@code PartnerPos} read back by {@link #loadAdditional}, held here until
     *  {@link #onLoad} can actually resolve this door's own dimension to rebuild the equivalent
     *  2-element {@link #groupPositions} list -- {@code level} is still null inside {@code
     *  loadAdditional} itself (it's only set afterward, by {@code LevelChunk
     *  #addAndRegisterBlockEntity}), so the migration can't run there. */
    private GlobalPos pendingLegacyPartnerPos;

    public TunnelDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNNEL_DOOR.get(), pos, state);
    }

    /** Called once, right after placement, by {@code api.TinyWyrlds#createTunnelDoorGroup} -- never
     *  re-configured afterward. The recipe is loaded once here too, as this door's first {@link
     *  #recipeBackup} -- {@code ensureTunnelRecipeExists} has always already run by this point,
     *  so it's real. */
    public void configure(UUID groupId, int roleIndex, List<GlobalPos> groupPositions, String recipeName, WyrldLifecycle lifecycle) {
        this.groupId = groupId;
        this.roleIndex = roleIndex;
        this.groupPositions = List.copyOf(groupPositions);
        this.recipeName = recipeName;
        this.lifecycle = lifecycle;
        this.recipeBackup = WyrldRecipeStorage.get().load(recipeName).orElse(null);
        setChanged();
    }

    public void enter(ServerPlayer player) {
        if (groupId == null || groupPositions.isEmpty() || level == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity at {}: never configured -- not a real Tunnel door.", worldPosition);
            return;
        }
        MinecraftServer server = player.level().getServer();
        String assignmentKey = lifecycle == WyrldLifecycle.PUBLIC ? groupId.toString() : groupId + ":" + player.getUUID();

        Optional<UUID> existingWyrldId = WyrldSpaceData.get(server).getTunnelAssignment(assignmentKey);
        if (existingWyrldId.isPresent()) {
            Optional<WyrldInstanceRecord> existingInstance = WyrldSpaceData.get(server).getInstance(existingWyrldId.get());
            if (existingInstance.isPresent()) {
                Optional<BlockPos> spawn = existingInstance.get().tunnelSpawn(roleIndex);
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
        if (recipe == null && recipeBackup != null) {
            recipe = recipeBackup;
            WyrldRecipeStorage.get().save(recipe);
            TinyWyrldsMod.LOGGER.info("TunnelDoorBlockEntity: recipe '{}' was missing for Tunnel group {} -- restored from this door's own stored backup copy.", recipeName, groupId);
        }
        if (recipe == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity: recipe '{}' no longer exists for Tunnel group {}, and no backup copy is stored to restore it from.", recipeName, groupId);
            return;
        }
        if (!recipe.equals(recipeBackup)) {
            recipeBackup = recipe;
            setChanged();
        }
        OneOffGenerator.TunnelGenerateResult result = OneOffGenerator.generateTunnel(server, recipe, groupPositions, lifecycle);
        if (result == null) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity: recipe '{}' has no valid ENTRANCE/CONNECTOR/ROOM content to build Tunnel group {} from.", recipeName, groupId);
            return;
        }
        if (roleIndex < 0 || roleIndex >= result.spawns().size()) {
            TinyWyrldsMod.LOGGER.warn("TunnelDoorBlockEntity: freshly-generated Tunnel group {} has {} spawn point(s), but this door's roleIndex is {} -- state is out of sync.", groupId, result.spawns().size(), roleIndex);
            return;
        }
        WyrldSpaceData.get(server).putTunnelAssignment(assignmentKey, result.instance().id());
        WyrldManager.enterAt(player, result.instance().id(), result.spawns().get(roleIndex));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (pendingLegacyPartnerPos != null && level != null) {
            GlobalPos myPos = GlobalPos.of(level.dimension(), worldPosition);
            List<GlobalPos> rebuilt = new ArrayList<>();
            if (roleIndex == 0) {
                rebuilt.add(myPos);
                rebuilt.add(pendingLegacyPartnerPos);
            } else {
                rebuilt.add(pendingLegacyPartnerPos);
                rebuilt.add(myPos);
            }
            groupPositions = rebuilt;
            pendingLegacyPartnerPos = null;
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        groupId = input.read("PairId", UUIDUtil.CODEC).orElse(null);
        roleIndex = input.getIntOr("RoleIndex", input.getBooleanOr("RoleA", true) ? 0 : 1);
        List<GlobalPos> loaded = input.read("GroupPositions", GlobalPos.CODEC.listOf()).orElse(null);
        if (loaded == null) {
            // Pre-generalization save (single PartnerPos, implicit 2-door group) -- `level` isn't
            // set yet here, so stash the raw value and let onLoad() (called once this entity
            // actually has a level) do the real reconstruction.
            pendingLegacyPartnerPos = input.read("PartnerPos", GlobalPos.CODEC).orElse(null);
            loaded = List.of();
        } else {
            pendingLegacyPartnerPos = null;
        }
        groupPositions = loaded;
        recipeName = input.getStringOr("RecipeName", "");
        lifecycle = input.read("Lifecycle", WyrldLifecycle.CODEC).orElse(WyrldLifecycle.INSTANCED);
        recipeBackup = input.read("RecipeBackup", WyrldRecipe.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("PairId", UUIDUtil.CODEC, groupId);
        output.putInt("RoleIndex", roleIndex);
        output.store("GroupPositions", GlobalPos.CODEC.listOf(), groupPositions);
        output.putString("RecipeName", recipeName);
        output.store("Lifecycle", WyrldLifecycle.CODEC, lifecycle);
        output.storeNullable("RecipeBackup", WyrldRecipe.CODEC, recipeBackup);
    }
}
