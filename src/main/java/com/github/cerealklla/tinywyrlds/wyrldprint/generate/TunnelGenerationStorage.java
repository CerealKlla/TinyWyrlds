package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.neoforged.fml.loading.FMLPaths;

/**
 * File-based store of every {@link TunnelGenerationRecord} -- one JSON file per Tunnel group, in
 * the SAME {@code tinywyrlds/generateddungeons/} directory {@link GeneratedDungeonStorage} already
 * uses (explicit user spec named that exact folder), filename {@code tunnel_<groupId>.json} --
 * prefixed so it's never ambiguous at a glance against an ordinary dungeon's own bare-UUID file,
 * even though a real collision is effectively impossible (both are real UUIDs). {@link #load}
 * returning empty IS the self-heal signal: deleting the file on disk is deleting the record, with
 * no cache anywhere pretending otherwise.
 */
public final class TunnelGenerationStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PREFIX = "tunnel_";
    private static final String EXTENSION = ".json";

    private static final TunnelGenerationStorage INSTANCE = new TunnelGenerationStorage();

    private final Path root;

    private TunnelGenerationStorage() {
        this.root = FMLPaths.GAMEDIR.get().resolve("tinywyrlds").resolve("generateddungeons");
    }

    public static TunnelGenerationStorage get() {
        return INSTANCE;
    }

    public void save(TunnelGenerationRecord record) {
        try {
            Files.createDirectories(root);
            Path file = fileFor(record.groupId(), record.playerId());
            JsonElement json = TunnelGenerationRecord.CODEC.encodeStart(JsonOps.INSTANCE, record)
                    .getOrThrow(msg -> new IOException("Failed to encode Tunnel generation record '" + record.groupId() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Tunnel generation record '" + record.groupId() + "'", e);
        }
    }

    /** @param playerId must match whatever {@link WyrldLifecycle} the group was configured with --
     *  empty for {@code PUBLIC}, the entering player's own id for {@code INSTANCED} -- same two-
     *  shape key {@code TunnelDoorBlockEntity#enter} always used, just realized as a filename now
     *  instead of a map key. */
    public Optional<TunnelGenerationRecord> load(UUID groupId, Optional<UUID> playerId) {
        Path file = fileFor(groupId, playerId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            TunnelGenerationRecord record = TunnelGenerationRecord.CODEC.parse(JsonOps.INSTANCE, element)
                    .getOrThrow(msg -> new IOException("Failed to decode Tunnel generation record " + file + ": " + msg));
            return Optional.of(record);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Tunnel generation record file " + file, e);
        }
    }

    public void delete(UUID groupId, Optional<UUID> playerId) {
        try {
            Files.deleteIfExists(fileFor(groupId, playerId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete Tunnel generation record '" + groupId + "'", e);
        }
    }

    /** Every currently-saved record, for the one real caller that needs to enumerate them all --
     *  {@code /tinywyrlds tunnel clear}, which must free each one's real Wyrld space, not just
     *  forget it, so it stays meaningful now that fresh generations never touch {@code
     *  WyrldSpaceData#tunnelAssignments} at all. */
    public List<TunnelGenerationRecord> listAll() {
        List<TunnelGenerationRecord> all = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return all;
        }
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().startsWith(PREFIX)).toList()) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    JsonElement element = GSON.fromJson(json, JsonElement.class);
                    TunnelGenerationRecord record = TunnelGenerationRecord.CODEC.parse(JsonOps.INSTANCE, element)
                            .getOrThrow(msg -> new IOException("Failed to decode Tunnel generation record " + file + ": " + msg));
                    all.add(record);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read Tunnel generation record file " + file, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list Tunnel generation records in " + root, e);
        }
        return all;
    }

    private Path fileFor(UUID groupId, Optional<UUID> playerId) {
        String suffix = playerId.map(id -> groupId + "_" + id).orElse(groupId.toString());
        return root.resolve(PREFIX + suffix + EXTENSION);
    }
}
