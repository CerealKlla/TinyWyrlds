package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "clear the crafting zone back to void," the GUI equivalent of {@code
 *  /tinywyrlds wyrldprint clear}. */
public record ClearCraftZonePayload(BlockPos workbenchPos) implements CustomPacketPayload {

    public static final Type<ClearCraftZonePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "clear_craft_zone"));

    public static final StreamCodec<ByteBuf, ClearCraftZonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), ClearCraftZonePayload::workbenchPos,
            ClearCraftZonePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
