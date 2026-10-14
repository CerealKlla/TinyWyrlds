package com.github.cerealklla.tinywyrlds.registration;

import java.util.function.Supplier;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.instance.PlayerWyrldData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TinyWyrldsMod.MODID);

    // Server bookkeeping only -- nothing client-side reads this yet, so no .sync(). copyOnDeath()
    // so dying inside a Wyrld doesn't lose the return-home path.
    public static final Supplier<AttachmentType<PlayerWyrldData>> WYRLD_DATA = ATTACHMENT_TYPES.register(
            "wyrld_data",
            () -> AttachmentType.builder(holder -> PlayerWyrldData.EMPTY)
                    .serialize(PlayerWyrldData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build()
    );
}
