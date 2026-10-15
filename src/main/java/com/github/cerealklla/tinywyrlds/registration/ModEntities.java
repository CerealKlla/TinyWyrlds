package com.github.cerealklla.tinywyrlds.registration;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, TinyWyrldsMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WyrldEntranceEntity>> WYRLD_ENTRANCE =
            ENTITY_TYPES.register("wyrld_entrance", () -> EntityType.Builder.<WyrldEntranceEntity>of(
                    WyrldEntranceEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "wyrld_entrance"))));
}
