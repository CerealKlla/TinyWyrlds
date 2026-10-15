package com.github.cerealklla.tinywyrlds.wyrldprint.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Global, file-based store of every saved {@link GeneratedDungeonRecord} -- one JSON file per
 * generated dungeon, keyed by its own {@code id} (filename {@code <uuid>.json}), living outside
 * any world save. Mirrors {@code WyrldPrintStorage}'s own pattern closely (same Gson+JsonOps codec
 * round-trip), but keyed by UUID instead of a human-chosen name, since a generated dungeon has no
 * author-given name -- it's the direct, structural result of one {@code /tinywyrlds generate} call.
 */
public final class GeneratedDungeonStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private static final GeneratedDungeonStorage INSTANCE = new GeneratedDungeonStorage();

    private final Path root;

    private GeneratedDungeonStorage() {
        this.root = FMLPaths.GAMEDIR.get().resolve("tinywyrlds").resolve("generateddungeons");
    }

    public static GeneratedDungeonStorage get() {
        return INSTANCE;
    }

    public void save(GeneratedDungeonRecord record) {
        try {
            Files.createDirectories(root);
            Path file = root.resolve(record.id() + EXTENSION);
            JsonElement json = GeneratedDungeonRecord.CODEC.encodeStart(JsonOps.INSTANCE, record)
                    .getOrThrow(msg -> new IOException("Failed to encode generated dungeon '" + record.id() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save generated dungeon '" + record.id() + "'", e);
        }
    }

    public Optional<GeneratedDungeonRecord> load(UUID id) {
        Path file = root.resolve(id + EXTENSION);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            GeneratedDungeonRecord record = GeneratedDungeonRecord.CODEC.parse(JsonOps.INSTANCE, element)
                    .getOrThrow(msg -> new IOException("Failed to decode generated dungeon " + file + ": " + msg));
            return Optional.of(record);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read generated dungeon file " + file, e);
        }
    }
}
