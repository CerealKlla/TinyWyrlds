package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "run the connectivity validator over every saved WyrldPrint," the GUI
 *  equivalent of {@code /tinywyrlds wyrldprint validateall}. Result is reported back in chat. */
public record ValidateAllWyrldPrintsPayload(BlockPos workbenchPos) implements CustomPacketPayload {

    public static final Type<ValidateAllWyrldPrintsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "validate_all_wyrld_prints"));

    public static final StreamCodec<ByteBuf, ValidateAllWyrldPrintsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), ValidateAllWyrldPrintsPayload::workbenchPos,
            ValidateAllWyrldPrintsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
