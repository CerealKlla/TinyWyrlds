package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "set the tag at {@code pos} to {@code tag}" (blank clears it) -- the real write, re-validated server-side against whatever's actually there rather than trusted blindly. */
public record SetTagPayload(BlockPos pos, String tag) implements CustomPacketPayload {

    public static final Type<SetTagPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "set_tag"));

    public static final StreamCodec<ByteBuf, SetTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SetTagPayload::pos,
            ByteBufCodecs.STRING_UTF8, SetTagPayload::tag,
            SetTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
