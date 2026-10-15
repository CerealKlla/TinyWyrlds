package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceData;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe;
import com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipeStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.generate.OneOffGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The real, in-world "Entrance" from the original spec's own section 4 -- a quest-triggered,
 * temporary one for now (the settlement-plot-hosted recurring variant is Settlemynts/Blueprynts'
 * own follow-up work, see design-document.md). Visually {@code Blocks.CRYING_OBSIDIAN} (a real
 * vanilla block with its own built-in particle/glow, so this needed zero new texture/renderer work
 * beyond the generic {@code Display.BlockDisplay} renderer every such entity in this suite reuses).
 *
 * <p><b>Per-player visibility</b> (explicit spec: "would only be visible to players with that
 * quest's UUID accepted") -- {@link #broadcastToPlayer}, same mechanism Myssions' own {@code
 * QuestNpcEntity} and Blueprynts' {@code GhostConstructionWallEntity} already use: returning {@code
 * false} means the server never sends that player this entity's spawn packet at all. {@link
 * #ownerPlayerId} is a single player for now -- "allowing a future quest sharing mechanic to work
 * for all party members" is explicitly deferred (no party system exists anywhere in this suite yet,
 * same honest-gap framing Myssions' own escort/road-quest work already uses for party sharing).
 *
 * <p><b>Lazy, remembered generation</b> ("entering generates a Wyrld... with that UUID as the
 * name" -- read here as "the Entrance's own id deterministically identifies which Wyrld it leads
 * to," not "regenerate every time"): {@link #enter} generates a fresh Wyrld from {@link
 * #recipeName} only the first time, then remembers {@link #generatedWyrldId} and simply re-enters
 * the same instance on every later interaction, until the periodic idle sweep ({@code
 * WyrldManager#tick}) eventually wipes it -- at which point the next {@link #enter} call generates
 * a fresh one again, same as it would have the first time.
 *
 * <p>Interaction itself is handled externally by the always-registered {@link
 * WyrldEntranceInteractListener} (a plain {@code PlayerInteractEvent.EntityInteract} listener, same
 * externalized-interaction precedent as Myssions' own {@code quest.QuestNpcTalkListener}), not a
 * {@code mobInteract}/{@code interact} override here -- this keeps the actual entry logic callable
 * directly (e.g. by a future debug command) without needing a fake interaction to trigger it.
 */
public class WyrldEntranceEntity extends Display.BlockDisplay {

    private UUID entranceId;
    private String recipeName = "";
    private UUID ownerPlayerId;
    private UUID generatedWyrldId;

    /** Not persisted -- see {@link EntranceEnterResult}'s own doc for why this is purely an
     *  in-memory hand-off to whichever external listener reads it right after {@link #enter}. */
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
        BlockDisplayHelper.setBlockState(entity, Blocks.CRYING_OBSIDIAN.defaultBlockState());
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
            lastEnterResult = new EntranceEnterResult(generatedWyrldId, List.of(), false);
            grantReturnItem(player);
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
        lastEnterResult = new EntranceEnterResult(generatedWyrldId, result.spawnedEnemyIds(), true);
        grantReturnItem(player);
        if (!result.spawnPoints().isEmpty()) {
            return WyrldManager.enterAt(player, generatedWyrldId, result.spawnPoints().get(0));
        }
        return WyrldManager.enter(player, generatedWyrldId);
    }

    /** "The player then has to escape or something like that" -- the concrete v1 realization: a
     *  real, non-consumable {@code WyrldReturnItem} that teleports them back out, auto-granted the
     *  moment they're sent in so there's always a real way out even for a player who's never heard
     *  of {@code /tinywyrlds leave} (a debug-only command, not a real player-facing mechanic). */
    private void grantReturnItem(ServerPlayer player) {
        if (!player.getInventory().contains(new net.minecraft.world.item.ItemStack(
                com.github.cerealklla.tinywyrlds.registration.ModItems.WYRLD_RETURN_ITEM.get()))) {
            player.getInventory().add(new net.minecraft.world.item.ItemStack(
                    com.github.cerealklla.tinywyrlds.registration.ModItems.WYRLD_RETURN_ITEM.get()));
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return ownerPlayerId == null || ownerPlayerId.equals(player.getUUID());
    }

    @Override
    public boolean isPickable() {
        // Vanilla Display entities default to non-pickable (purely decorative) -- explicitly
        // overridden true here since this one specifically needs to be right-click-interactable.
        return true;
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
