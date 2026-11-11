package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Approve" on {@code WyrldWorkbenchTagReviewScreen} -- {@code
 *  approvedTag} may equal {@code originalTag} (approved as-is) or differ (the admin corrected it
 *  before approving). {@code workbenchPos} is just so the handler can resend a fresh
 *  {@code OpenWyrldWorkbenchScreenPayload} for the real workbench this screen was opened from, same
 *  as every other action in this screen flow. The server re-checks gamemaster permission itself;
 *  this button is only ever shown to an admin client-side, which is not a real permission boundary
 *  on its own. */
public record ApproveTagPayload(BlockPos workbenchPos, String originalTag, String approvedTag) implements CustomPacketPayload {

    public static final Type<ApproveTagPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "approve_tag"));

    public static final StreamCodec<ByteBuf, ApproveTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), ApproveTagPayload::workbenchPos,
            ByteBufCodecs.STRING_UTF8, ApproveTagPayload::originalTag,
            ByteBufCodecs.STRING_UTF8, ApproveTagPayload::approvedTag,
            ApproveTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
