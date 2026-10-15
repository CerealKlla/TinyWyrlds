package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.UUID;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceData;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipeStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.generate.OneOffGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The real, in-world "Entrance" from the original spec's own section 4 -- a quest-triggered,
 * temporary one for now (the settlement-plot-hosted recurring variant is Settlemynts/Blueprynts'
 * own follow-up work, see design-document.md). Visually renders the same gold-tinted
 * nether-portal-look block state as {@link ExitPortalBlock} (not actually placed as a real block
 * here -- a {@code Display.BlockDisplay} can render any {@code BlockState} purely cosmetically),
 * per explicit user request 2026-10-15: "I want the entrance to be similar [visually, and to walk
 * into] to actually enter the Wyrld" -- a symmetric portal experience both ways.
 *
 * <p><b>Walk-in trigger, not right-click</b> (same 2026-10-15 request) -- {@link
 * WyrldEntranceProximityTicker} checks every online owning player's distance to this entity each
 * tick and calls {@link #enter} the moment they're close enough, same spirit as {@link
 * ExitPortalBlock}'s own real vanilla {@code Portal} walk-through trigger. This entity can't use
 * that exact vanilla mechanism itself ({@code Entity#setAsInsidePortal} is a block-collision hook,
 * not available to entities), which is the real reason it's a tick-based proximity check instead --
 * see that ticker's own doc for the full account, including why re-triggering on the same visit
 * isn't a concern (the teleport itself moves the player out of range before the next tick).
 *
 * <p><b>Per-player visibility</b> (explicit spec: "would only be visible to players with that
 * quest's UUID accepted") -- {@link #broadcastToPlayer}, same mechanism Myssions' own {@code
 * QuestNpcEntity} and Blueprynts' {@code GhostConstructionWallEntity} already use: returning {@code
 * false} means the server never sends that player this entity's spawn packet at all. {@link
 * #ownerPlayerId} is a single player for now -- "allowing a future quest sharing mechanic to work
 * for all party members" is explicitly deferred (no party system exists anywhere in this suite yet,
 * same honest-gap framing Myssions' own escort/road-quest work already uses for party sharing).
 * This is also exactly why this stays an entity instead of becoming a real placed {@code
 * ExitPortalBlock} itself -- a real block's state syncs to every client with the chunk loaded, with
 * no way to hide it from non-owning players the way an entity's spawn packet can be withheld.
 *
 * <p><b>Lazy, remembered generation</b> ("entering generates a Wyrld... with that UUID as the
 * name" -- read here as "the Entrance's own id deterministically identifies which Wyrld it leads
 * to," not "regenerate every time"): {@link #enter} generates a fresh Wyrld from {@link
 * #recipeName} only the first time, then remembers {@link #generatedWyrldId} and simply re-enters
 * the same instance on every later visit, until the periodic idle sweep ({@code
 * WyrldManager#tick}) eventually wipes it -- at which point the next {@link #enter} call generates
 * a fresh one again, same as it would have the first time.
 */
public class WyrldEntranceEntity extends Display.BlockDisplay {

    private UUID entranceId;
    private String recipeName = "";
    private UUID ownerPlayerId;
    private UUID generatedWyrldId;

    /** Not persisted -- see {@link EntranceEnterResult}'s own doc for why this is purely an
     *  in-memory hand-off, now also mirrored into {@link EntranceEnterResults} (a global,
     *  entity-independent lookup -- see that class's own doc for why this entity-local copy alone
     *  isn't enough once entry is proximity-triggered rather than interact-triggered). */
    private EntranceEnterResult lastEnterResult;

    public WyrldEntranceEntity(EntityType<? extends WyrldEntranceEntity> type, Level level) {
        super(type, level);
    }

    public static WyrldEntranceEntity create(ServerLevel level, BlockPos pos, UUID entranceId, String recipeName, UUID ownerPlayerId) {
        WyrldEntranceEntity entity = new WyrldEntranceEntity(com.github.cerealklla.tinywyrlds.registration.ModEntities.WYRLD_ENTRANCE.get(), level);
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.entranceId = entranceId;
        entity.recipeName = recipeName;
        entity.ownerPlayerId = ownerPlayerId;
        BlockDisplayHelper.setBlockState(entity, ModBlocks.EXIT_PORTAL.get().defaultBlockState().setValue(ExitPortalBlock.AXIS, Direction.Axis.Z));
        level.addFreshEntity(entity);
        return entity;
    }

    public UUID entranceId() {
        return entranceId;
    }

    /** Not persisted -- see {@link #lastEnterResult}'s own doc. */
    public EntranceEnterResult lastEnterResult() {
        return lastEnterResult;
    }

    /** @return {@code false} if the recipe no longer exists (renamed/deleted) or the Wyrld Space
     *  dimension somehow isn't loaded -- a real, reportable failure, not a silent no-op. */
    public boolean enter(ServerPlayer player) {
        if (!(player.level().getServer().getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY) instanceof ServerLevel wyrldLevel)) {
            return false;
        }
        if (generatedWyrldId != null && WyrldSpaceData.get(player.level().getServer()).getInstance(generatedWyrldId).isPresent()) {
            recordEnterResult(new EntranceEnterResult(generatedWyrldId, java.util.List.of(), false));
            return WyrldManager.enter(player, generatedWyrldId);
        }

        WyrldRecipe recipe = WyrldRecipeStorage.get().load(recipeName).orElse(null);
        if (recipe == null) {
            player.sendSystemMessage(Component.literal("This Entrance's own recipe ('" + recipeName + "') no longer exists."));
            return false;
        }
        OneOffGenerator.GenerateResult result = OneOffGenerator.generate(player.level().getServer(), recipe);
        if (result == null) {
            player.sendSystemMessage(Component.literal("Not enough saved WyrldPrints to generate a dungeon -- need at least 1 room or entrance."));
            return false;
        }
        generatedWyrldId = result.instance().id();
        recordEnterResult(new EntranceEnterResult(generatedWyrldId, result.spawnedEnemyIds(), true));
        if (!result.spawnPoints().isEmpty()) {
            return WyrldManager.enterAt(player, generatedWyrldId, result.spawnPoints().get(0));
        }
        return WyrldManager.enter(player, generatedWyrldId);
    }

    private void recordEnterResult(EntranceEnterResult result) {
        lastEnterResult = result;
        if (entranceId != null) {
            EntranceEnterResults.record(entranceId, result);
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return ownerPlayerId == null || ownerPlayerId.equals(player.getUUID());
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Wyrld Entrance");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("EntranceId", UUIDUtil.CODEC, entranceId);
        output.putString("RecipeName", recipeName);
        output.storeNullable("OwnerPlayerId", UUIDUtil.CODEC, ownerPlayerId);
        output.storeNullable("GeneratedWyrldId", UUIDUtil.CODEC, generatedWyrldId);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entranceId = input.read("EntranceId", UUIDUtil.CODEC).orElse(null);
        recipeName = input.getStringOr("RecipeName", "");
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        generatedWyrldId = input.read("GeneratedWyrldId", UUIDUtil.CODEC).orElse(null);
    }
}
