package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "re-stamp a previously generated dungeon layout," the GUI equivalent of
 *  {@code /tinywyrlds regenerate <id>}. {@code dungeonId} is a UUID's string form. */
public record RegenerateDungeonPayload(BlockPos workbenchPos, String dungeonId) implements CustomPacketPayload {

    public static final Type<RegenerateDungeonPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "regenerate_dungeon"));

    public static final StreamCodec<ByteBuf, RegenerateDungeonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RegenerateDungeonPayload::workbenchPos,
            ByteBufCodecs.STRING_UTF8, RegenerateDungeonPayload::dungeonId,
            RegenerateDungeonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
