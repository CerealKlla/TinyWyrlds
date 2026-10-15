package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "clear the crafting zone and paste this saved WyrldPrint back in for editing." */
public record LoadWyrldPrintPayload(BlockPos workbenchPos, String name) implements CustomPacketPayload {

    public static final Type<LoadWyrldPrintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "load_wyrld_print"));

    public static final StreamCodec<ByteBuf, LoadWyrldPrintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), LoadWyrldPrintPayload::workbenchPos,
            ByteBufCodecs.STRING_UTF8, LoadWyrldPrintPayload::name,
            LoadWyrldPrintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
