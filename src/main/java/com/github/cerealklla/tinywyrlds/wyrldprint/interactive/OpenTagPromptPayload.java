package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client: "open the tag prompt for the lever/button at {@code pos}, currently tagged {@code currentTag} (blank if untagged)" -- sent by {@code TagInteractListener} on a qualifying shift-right-click. */
public record OpenTagPromptPayload(BlockPos pos, String currentTag) implements CustomPacketPayload {

    public static final Type<OpenTagPromptPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "open_tag_prompt"));

    public static final StreamCodec<ByteBuf, OpenTagPromptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenTagPromptPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenTagPromptPayload::currentTag,
            OpenTagPromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
