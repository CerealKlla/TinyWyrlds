package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens/refreshes {@code WyrldWorkbenchScreen} with the full list of saved
 * WyrldPrints (name + type, parallel lists), so the client makes no further server queries to
 * render its Load list -- same shape/precedent as Blueprynts' own {@code
 * OpenConstructionSiteScreenPayload}. Resent after a Save adds a new entry so an already-open
 * screen's Load list stays current.
 */
public record OpenWyrldWorkbenchScreenPayload(
        BlockPos workbenchPos,
        List<String> savedNames,
        List<String> savedTypes)
        implements CustomPacketPayload {

    public static final Type<OpenWyrldWorkbenchScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "open_wyrld_workbench_screen"));

    public static final StreamCodec<ByteBuf, OpenWyrldWorkbenchScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenWyrldWorkbenchScreenPayload::workbenchPos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::savedNames,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::savedTypes,
            OpenWyrldWorkbenchScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
