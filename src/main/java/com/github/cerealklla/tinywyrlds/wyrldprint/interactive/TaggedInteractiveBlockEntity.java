package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.tinywyrlds.registration.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared block entity for {@link TaggedLeverBlock}/{@link TaggedButtonBlock} -- the one real place
 * a "which quest-facing switch is this" identity lives, per explicit user design (2026-11-06): no
 * separate position-keyed lookup table anywhere, the block itself is the single source of truth.
 *
 * <p>{@code tag} is the plain string an author/admin types into {@link TagPromptScreen} -- set once
 * while building a room in the WyrldPrint crafting zone (captured into the saved WyrldPrint's own
 * {@code WyrldPrintCell#tag} from there, see {@code wyrldprint.WyrldPrintCapture}'s own doc), or set/
 * edited live on an already-generated instance's block the same way. {@code dungeonInstanceId} is
 * {@code null} until {@code generate.OneOffGenerator#stamp} fills it in the moment this specific
 * block is actually placed into a real generated instance -- a tag typed in the crafting zone has no
 * instance to belong to yet.
 *
 * <p>Self-registers into {@link TaggedInteractiveRegistry} (a genuine O(1)/Θ(1) map, not a scan)
 * whenever both fields are non-blank -- via {@link #setLevel} (covers a chunk reloading from disk,
 * and a piston moving this block: vanilla destroys the old block entity and builds a new one at the
 * destination from the same NBT, and that new instance's own {@code setLevel} re-announces it under
 * the same key automatically, no external bookkeeping needed) and via {@link #setTag}/{@link
 * #setTagAndInstance} (covers the instant a caller actually sets a tag for the first time). {@link
 * #setRemoved} always deregisters.
 */
public class TaggedInteractiveBlockEntity extends BlockEntity {

    private String tag = "";
    private UUID dungeonInstanceId;

    public TaggedInteractiveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TAGGED_INTERACTIVE.get(), pos, state);
    }

    public String tag() {
        return tag;
    }

    public Optional<UUID> dungeonInstanceId() {
        return Optional.ofNullable(dungeonInstanceId);
    }

    /** The crafting-zone-authoring path, and the "edit an already-generated instance's block live" path -- never touches {@code dungeonInstanceId}, which is either not yet assigned (crafting zone) or already correct (a live instance). */
    public void setTag(String tag) {
        deregisterCurrent();
        this.tag = tag;
        setChanged();
        tryRegister();
    }

    /** {@code generate.OneOffGenerator#stamp}'s own path -- the one moment a captured tag gets a real instance id for the first time. */
    public void setTagAndInstance(String tag, UUID dungeonInstanceId) {
        deregisterCurrent();
        this.tag = tag;
        this.dungeonInstanceId = dungeonInstanceId;
        setChanged();
        tryRegister();
    }

    /**
     * A genuine toggle -- on becomes off, off becomes on, unconditionally, every call. Real
     * redstone propagation via the same flag-3 (notify neighbors + clients) convention {@code
     * generate.OneOffGenerator#stamp} already places every other block with, so any wiring the
     * WyrldPrint's own author built (e.g. dust leading to a door) reacts exactly like a player had
     * pulled/pressed it by hand.
     *
     * <p><b>Correction, 2026-11-06</b>: the first version of this only ever turned the lever ON,
     * silently no-op-ing if it was already on -- a real, explicit user correction: "this method is
     * supposed to 'toggle' the lever... there should never be a way for a 'toggle' to fail to
     * function." That guard was never asked for and caused a real live bug: a lever captured already
     * in the 'on' position (left that way from authoring/testing) meant every generated instance
     * started pre-unlocked, so a caller's later toggle call did nothing and nothing ever visibly
     * changed. A real toggle has no such failure mode -- whatever state it's actually in, this flips
     * it, every time. A no-op only if this is somehow no longer a real lever/button at all (shouldn't
     * happen -- nothing in this mod ever swaps the block out from under this block entity).
     */
    public void toggle() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = getBlockState();
        BlockState newState;
        if (state.getBlock() instanceof LeverBlock) {
            newState = state.setValue(LeverBlock.POWERED, !state.getValue(LeverBlock.POWERED));
        } else if (state.getBlock() instanceof ButtonBlock) {
            newState = state.setValue(ButtonBlock.POWERED, !state.getValue(ButtonBlock.POWERED));
        } else {
            return;
        }
        serverLevel.setBlock(worldPosition, newState, 3);
        serverLevel.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.6F);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        tryRegister();
    }

    @Override
    public void setRemoved() {
        deregisterCurrent();
        super.setRemoved();
    }

    private void tryRegister() {
        if (level instanceof ServerLevel && dungeonInstanceId != null && !tag.isBlank()) {
            TaggedInteractiveRegistry.register(dungeonInstanceId, tag, this);
        }
    }

    private void deregisterCurrent() {
        if (dungeonInstanceId != null && !tag.isBlank()) {
            TaggedInteractiveRegistry.unregister(dungeonInstanceId, tag, this);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tag = input.getStringOr("Tag", "");
        dungeonInstanceId = input.read("DungeonInstanceId", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Tag", tag);
        output.storeNullable("DungeonInstanceId", UUIDUtil.CODEC, dungeonInstanceId);
    }
}
