package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Deny" on {@code WyrldWorkbenchTagReviewScreen} -- removes the tag from
 *  the pending queue only, no retroactive effect on already-tagged prints. {@code workbenchPos} is
 *  just so the handler can resend a fresh screen for the real workbench, same as {@link
 *  ApproveTagPayload}'s own field. */
public record DenyTagPayload(BlockPos workbenchPos, String originalTag) implements CustomPacketPayload {

    public static final Type<DenyTagPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "deny_tag"));

    public static final StreamCodec<ByteBuf, DenyTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), DenyTagPayload::workbenchPos,
            ByteBufCodecs.STRING_UTF8, DenyTagPayload::originalTag,
            DenyTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
