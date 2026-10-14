package com.github.cerealklla.tinywyrlds.wyrldprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Global, file-based store of every saved {@link WyrldPrintRecord} -- one JSON file per
 * WyrldPrint, living outside any world save, same precedent as Blueprynts' own {@code
 * BlueprintStorage} (mirrored closely here). Layout: {@code tinywyrlds/wyrldprints/<Type>/
 * <Name> - <Author>.json}. Not a cache -- every read/write goes straight to disk.
 */
public final class WyrldPrintStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private static final WyrldPrintStorage INSTANCE = new WyrldPrintStorage();

    private final Path root;

    private WyrldPrintStorage() {
        this.root = FMLPaths.GAMEDIR.get().resolve("tinywyrlds").resolve("wyrldprints");
    }

    public static WyrldPrintStorage get() {
        return INSTANCE;
    }

    public void save(WyrldPrintRecord record) {
        deleteExisting(record.name());
        Path dir = root.resolve(sanitize(record.type().name()));
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileNameFor(record));
            JsonElement json = WyrldPrintRecord.CODEC.encodeStart(JsonOps.INSTANCE, record)
                    .getOrThrow(msg -> new IOException("Failed to encode WyrldPrint '" + record.name() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save WyrldPrint '" + record.name() + "'", e);
        }
    }

    public Optional<WyrldPrintRecord> load(String name) {
        return findFile(name).map(this::readRecord);
    }

    public List<String> listNames() {
        List<String> names = new ArrayList<>();
        forEachRecord((path, record) -> names.add(record.name()));
        return names;
    }

    public List<WyrldPrintRecord> listAll() {
        List<WyrldPrintRecord> records = new ArrayList<>();
        forEachRecord((path, record) -> records.add(record));
        return records;
    }

    private String fileNameFor(WyrldPrintRecord record) {
        return sanitize(record.name()) + " - " + sanitize(record.author()) + EXTENSION;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Optional<Path> findFile(String name) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(EXTENSION))
                    .filter(p -> {
                        WyrldPrintRecord record = readRecord(p);
                        return record != null && record.name().equals(name);
                    })
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to search WyrldPrint store", e);
        }
    }

    private void deleteExisting(String name) {
        findFile(name).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to remove previous copy of WyrldPrint '" + name + "'", e);
            }
        });
    }

    private void forEachRecord(BiConsumer<Path, WyrldPrintRecord> consumer) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(EXTENSION)).forEach(path -> {
                WyrldPrintRecord record = readRecord(path);
                if (record != null) {
                    consumer.accept(path, record);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list WyrldPrint store", e);
        }
    }

    private WyrldPrintRecord readRecord(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            return WyrldPrintRecord.CODEC.parse(JsonOps.INSTANCE, element)
                    .getOrThrow(msg -> new IOException("Failed to decode " + file + ": " + msg));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read WyrldPrint file " + file, e);
        }
    }
}
