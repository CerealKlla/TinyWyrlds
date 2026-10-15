package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "generate a one-off dungeon and enter it," the GUI equivalent of {@code
 *  /tinywyrlds generate <minRooms> <maxRooms>}. */
public record GenerateOneOffPayload(BlockPos workbenchPos, int minRooms, int maxRooms) implements CustomPacketPayload {

    public static final Type<GenerateOneOffPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "generate_one_off"));

    public static final StreamCodec<ByteBuf, GenerateOneOffPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), GenerateOneOffPayload::workbenchPos,
            ByteBufCodecs.VAR_INT, GenerateOneOffPayload::minRooms,
            ByteBufCodecs.VAR_INT, GenerateOneOffPayload::maxRooms,
            GenerateOneOffPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
