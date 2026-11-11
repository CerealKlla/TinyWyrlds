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

/** Client-to-server: "capture the crafting zone's current content and save it" -- {@code
 *  printType} is {@code WyrldPrintType#name()} lowercase, same word the debug command already
 *  accepts. Named {@code printType}, not {@code type}, to avoid clashing with {@link
 *  CustomPacketPayload#type()}'s own accessor -- a record component can't share a name with an
 *  interface method of a different return type.
 *
 * <p>{@code tags} added 2026-11-11 (the Tag system) -- the final merged tag list (every selected
 *  approved tag, union the parsed custom-box tokens, already deduplicated client-side) the Save
 *  screen sends; the server doesn't need to know which of those strings are "new" vs
 *  "already approved" -- it just checks each one. */
public record SaveWyrldPrintPayload(BlockPos workbenchPos, String name, String printType, List<String> tags) implements CustomPacketPayload {

    public static final Type<SaveWyrldPrintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TinyWyrldsMod.MODID, "save_wyrld_print"));

    public static final StreamCodec<ByteBuf, SaveWyrldPrintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SaveWyrldPrintPayload::workbenchPos,
            ByteBufCodecs.STRING_UTF8, SaveWyrldPrintPayload::name,
            ByteBufCodecs.STRING_UTF8, SaveWyrldPrintPayload::printType,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SaveWyrldPrintPayload::tags,
            SaveWyrldPrintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
