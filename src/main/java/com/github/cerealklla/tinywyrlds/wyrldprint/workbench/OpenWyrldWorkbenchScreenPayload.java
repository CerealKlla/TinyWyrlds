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
 *
 * <p>{@code loadedName} is the crafting zone's current {@code WyrldPrintPlacement#loadedName()},
 * empty string standing in for "none" (no nullable-string codec needed) -- the Save screen uses it
 * to default its name field, per the 2026-10-15 request described there. {@code loadedType} is the
 * same idea for {@code WyrldPrintPlacement#loadedType()} (lowercase {@code WyrldPrintType#name()},
 * empty for "none") -- added 2026-11-11, real bug: the Save screen's Type toggle always started at
 * ROOM regardless of what was actually loaded, so re-saving a loaded CONNECTOR/ENTRANCE/TUTORIAL
 * print without remembering to cycle it back silently overwrote its real type.
 *
 * <p>{@code approvedTags}/{@code pendingTags}/{@code canManageTags} added 2026-11-11 (the Tag
 * system) -- {@code approvedTags} feeds the Save screen's tag picker, {@code pendingTags} feeds the
 * "Manage Tags... (N pending)" button's count and the review screen's own row list,
 * {@code canManageTags} is resolved server-side from the opening player's real permission level
 * ({@code Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())},
 * the same predicate every gated command's own {@code .requires(...)} already uses) -- never
 * trust a client-only gate for
 * anything that mutates shared data; this just controls whether the button even renders.
 * {@code loadedTags} is {@code WyrldPrintPlacement#loadedTags()} -- same bug class as {@code
 * loadedType}, found right after it: the Save screen's tag picker had no concept of "which tags
 * does the currently-loaded print already have," so re-saving one without re-selecting every tag by
 * hand silently wiped them.
 */
public record OpenWyrldWorkbenchScreenPayload(
        BlockPos workbenchPos,
        List<String> savedNames,
        List<String> savedTypes,
        String loadedName,
        String loadedType,
        List<String> loadedTags,
        List<String> approvedTags,
        List<String> pendingTags,
        boolean canManageTags)
        implements CustomPacketPayload {

    public static final Type<OpenWyrldWorkbenchScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "open_wyrld_workbench_screen"));

    public static final StreamCodec<ByteBuf, OpenWyrldWorkbenchScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenWyrldWorkbenchScreenPayload::workbenchPos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::savedNames,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::savedTypes,
            ByteBufCodecs.STRING_UTF8, OpenWyrldWorkbenchScreenPayload::loadedName,
            ByteBufCodecs.STRING_UTF8, OpenWyrldWorkbenchScreenPayload::loadedType,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::loadedTags,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::approvedTags,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenWyrldWorkbenchScreenPayload::pendingTags,
            ByteBufCodecs.BOOL, OpenWyrldWorkbenchScreenPayload::canManageTags,
            OpenWyrldWorkbenchScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
