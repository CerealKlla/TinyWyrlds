package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import java.util.Optional;
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
    /** A copy of {@code recipeName}'s own recipe, captured whenever it's successfully loaded --
     *  this entrance's own fallback source of truth for its generation parameters, not just a name
     *  pointer into {@code WyrldRecipeStorage}. Same rationale and lifecycle as {@code
     *  TunnelDoorBlockEntity}'s own {@code recipeBackup}: rebuilds a deleted/renamed recipe from
     *  the entrance itself instead of silently failing, and stays in sync with the live recipe on
     *  every successful {@link #enter}. {@link #enterDirect} has no entity to hold this on at all
     *  (an already-documented accepted gap, see its own doc) -- only this entity-backed path
     *  benefits. */
    private WyrldRecipe recipeBackup;

    /** Not persisted -- see {@link EntranceEnterResult}'s own doc for why this is purely an
     *  in-memory hand-off, now also mirrored into {@link EntranceEnterResults} (a global,
     *  entity-independent lookup -- see that class's own doc for why this entity-local copy alone
     *  isn't enough once entry is proximity-triggered rather than interact-triggered). */
    private EntranceEnterResult lastEnterResult;

    /** Explicit user instruction, 2026-11-09, after a real live report of this entity spamming a
     *  player's chat with a fresh failure line every single tick while they stood near a broken
     *  entrance ("why the fuck are you just spamming chat over and over again?"), two corrections
     *  in a row after that -- first, that only throttling the resend rate still spams nothing
     *  useful ("what exactly do you envision happening by telling the player every 5 seconds that
     *  files are missing? do you think a player is going to magically find these files to load
     *  into the directory?"), then, after this was changed to a single one-time generic chat line
     *  instead, that the player needs no message about this at all ("The player doesn't need to be
     *  told at all") -- there is genuinely nothing a player can do about a missing recipe/
     *  WyrldPrint, so the player-facing side of this is now nothing whatsoever: {@link
     *  #handleGenerationFailure} only ever logs, on the server side, for whoever's actually
     *  operating the server to notice and fix. */

    public WyrldEntranceEntity(EntityType<? extends WyrldEntranceEntity> type, Level level) {
        super(type, level);
    }

    public static WyrldEntranceEntity create(ServerLevel level, BlockPos pos, UUID entranceId, String recipeName, UUID ownerPlayerId) {
        WyrldEntranceEntity entity = new WyrldEntranceEntity(com.github.cerealklla.tinywyrlds.registration.ModEntities.WYRLD_ENTRANCE.get(), level);
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.entranceId = entranceId;
        entity.recipeName = recipeName;
        entity.ownerPlayerId = ownerPlayerId;
        entity.recipeBackup = WyrldRecipeStorage.get().load(recipeName).orElse(null);
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

    /** @return {@code false} if the recipe no longer exists (renamed/deleted), the Wyrld Space
     *  dimension somehow isn't loaded, or generation has no WyrldPrint content to work with --
     *  silent to the player by design now (see this class's own field-level doc), logged
     *  server-side only. */
    public boolean enter(ServerPlayer player) {
        if (!(player.level().getServer().getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY) instanceof ServerLevel wyrldLevel)) {
            return false;
        }
        if (generatedWyrldId != null && WyrldSpaceData.get(player.level().getServer()).getInstance(generatedWyrldId).isPresent()) {
            recordEnterResult(new EntranceEnterResult(generatedWyrldId, java.util.List.of(), false));
            return WyrldManager.enter(player, generatedWyrldId);
        }

        Optional<OneOffGenerator.GenerateResult> generated = generateFromRecipe(player.level().getServer(), entranceId, recipeName, recipeBackup);
        if (generated.isEmpty()) {
            return false;
        }
        OneOffGenerator.GenerateResult result = generated.get();
        generatedWyrldId = result.instance().id();
        recipeBackup = WyrldRecipeStorage.get().load(recipeName).orElse(recipeBackup);
        recordEnterResult(new EntranceEnterResult(generatedWyrldId, result.spawnedEnemyIds(), true));
        if (!result.spawnPoints().isEmpty()) {
            return WyrldManager.enterAt(player, generatedWyrldId, result.spawnPoints().get(0));
        }
        return WyrldManager.enter(player, generatedWyrldId);
    }

    /**
     * Entity-free variant of {@link #enter} -- generates (or reuses) a Wyrld from {@code
     * recipeName} and teleports {@code player} straight in, with nothing ever placed in the world
     * at all. Added 2026-11-11, explicit user instruction after a real incident: a permanent,
     * invisible, proximity-triggered entrance placed wherever a player happens to be standing is
     * never the right shape for a one-shot "put this player into a Wyrld right now" event (Arryval's
     * new-player onboarding, see its own {@code onboarding.FirstLoginListener}) -- "there is no
     * scenario where a permanent tutorial entrance makes sense... Manually teleport the player into
     * the tutorial." Reuse here is backed by {@link EntranceEnterResults} alone (in-memory only,
     * not persisted across a restart) since there's no entity to hold a durable field on -- an
     * accepted gap, matching Arryval's own {@code PendingOnboarding} already being entirely
     * in-memory for the same kind of state.
     */
    public static boolean enterDirect(ServerPlayer player, UUID entranceId, String recipeName) {
        var server = player.level().getServer();
        if (!(server.getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY) instanceof ServerLevel wyrldLevel)) {
            return false;
        }
        Optional<EntranceEnterResult> cached = EntranceEnterResults.get(entranceId);
        if (cached.isPresent() && WyrldSpaceData.get(server).getInstance(cached.get().wyrldId()).isPresent()) {
            return WyrldManager.enter(player, cached.get().wyrldId());
        }

        Optional<OneOffGenerator.GenerateResult> generated = generateFromRecipe(server, entranceId, recipeName, null);
        if (generated.isEmpty()) {
            return false;
        }
        OneOffGenerator.GenerateResult result = generated.get();
        UUID wyrldId = result.instance().id();
        EntranceEnterResults.record(entranceId, new EntranceEnterResult(wyrldId, result.spawnedEnemyIds(), true));
        if (!result.spawnPoints().isEmpty()) {
            return WyrldManager.enterAt(player, wyrldId, result.spawnPoints().get(0));
        }
        return WyrldManager.enter(player, wyrldId);
    }

    /** Shared by {@link #enter} and {@link #enterDirect} -- loads {@code recipeName} and runs
     *  generation, logging (server-side only, see {@link #logGenerationFailure}'s own doc) and
     *  returning empty on any failure. {@code recipeBackup} is this entrance's own last-known
     *  recipe copy (null for {@link #enterDirect}, which has no entity to hold one) -- used, and
     *  restored back into {@code WyrldRecipeStorage}, only if the live recipe is genuinely gone. */
    private static Optional<OneOffGenerator.GenerateResult> generateFromRecipe(net.minecraft.server.MinecraftServer server,
            UUID entranceId, String recipeName, WyrldRecipe recipeBackup) {
        WyrldRecipe recipe = WyrldRecipeStorage.get().load(recipeName).orElse(null);
        if (recipe == null && recipeBackup != null) {
            recipe = recipeBackup;
            WyrldRecipeStorage.get().save(recipe);
            com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info("WyrldEntranceEntity: recipe '{}' was missing for entrance {} -- restored from this entrance's own stored backup copy.", recipeName, entranceId);
        }
        if (recipe == null) {
            logGenerationFailure("Entrance " + entranceId + ": recipe '" + recipeName + "' no longer exists.");
            return Optional.empty();
        }
        OneOffGenerator.GenerateResult result = OneOffGenerator.generate(server, recipe);
        if (result == null) {
            logGenerationFailure("Entrance " + entranceId + ": recipe '" + recipeName
                    + "' has no ROOM/ENTRANCE WyrldPrints available to generate from.");
            return Optional.empty();
        }
        return Optional.of(result);
    }

    /** See this class's own field-level doc above for why this is server-log-only -- never sent to
     *  the player's chat, not even once. */
    private static void logGenerationFailure(String logDetail) {
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.warn("WyrldEntranceEntity: {}", logDetail);
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
        output.storeNullable("RecipeBackup", WyrldRecipe.CODEC, recipeBackup);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entranceId = input.read("EntranceId", UUIDUtil.CODEC).orElse(null);
        recipeName = input.getStringOr("RecipeName", "");
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        generatedWyrldId = input.read("GeneratedWyrldId", UUIDUtil.CODEC).orElse(null);
        recipeBackup = input.read("RecipeBackup", WyrldRecipe.CODEC).orElse(null);
    }
}
