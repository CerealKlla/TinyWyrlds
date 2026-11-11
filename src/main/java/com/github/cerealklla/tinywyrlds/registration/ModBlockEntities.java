package com.github.cerealklla.tinywyrlds.registration;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.TunnelDoorBlockEntity;
import com.github.cerealklla.tinywyrlds.wyrldprint.interactive.TaggedInteractiveBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** This mod's first-ever {@code BlockEntity} -- see {@code wyrldprint.interactive.TaggedInteractiveBlockEntity}'s own doc for why it's needed at all. */
public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyWyrldsMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TaggedInteractiveBlockEntity>> TAGGED_INTERACTIVE =
            BLOCK_ENTITIES.register("tagged_interactive", () -> new BlockEntityType<>(
                    TaggedInteractiveBlockEntity::new, ModBlocks.TAGGED_LEVER.get(), ModBlocks.TAGGED_BUTTON.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TunnelDoorBlockEntity>> TUNNEL_DOOR =
            BLOCK_ENTITIES.register("tunnel_door", () -> new BlockEntityType<>(
                    TunnelDoorBlockEntity::new, ModBlocks.TUNNEL_DOOR.get()));
}
