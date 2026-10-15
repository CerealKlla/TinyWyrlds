package com.github.cerealklla.tinywyrlds.wyrldprint.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.neoforged.fml.loading.FMLPaths;

/** Global, file-based store of every saved {@link WyrldRecipe} -- one JSON file per recipe, living
 *  outside any world save, same precedent as {@code WyrldPrintStorage}/Blueprynts' own
 *  {@code BlueprintStorage}. Layout: {@code tinywyrlds/recipes/<Name>.json}. */
public final class WyrldRecipeStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private static final WyrldRecipeStorage INSTANCE = new WyrldRecipeStorage();

    private final Path root;

    private WyrldRecipeStorage() {
        this.root = FMLPaths.GAMEDIR.get().resolve("tinywyrlds").resolve("recipes");
    }

    public static WyrldRecipeStorage get() {
        return INSTANCE;
    }

    public void save(WyrldRecipe recipe) {
        try {
            Files.createDirectories(root);
            Path file = root.resolve(sanitize(recipe.name()) + EXTENSION);
            JsonElement json = WyrldRecipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe)
                    .getOrThrow(msg -> new IOException("Failed to encode WyrldRecipe '" + recipe.name() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save WyrldRecipe '" + recipe.name() + "'", e);
        }
    }

    public Optional<WyrldRecipe> load(String name) {
        Path file = root.resolve(sanitize(name) + EXTENSION);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            return Optional.of(WyrldRecipe.CODEC.parse(JsonOps.INSTANCE, element)
                    .getOrThrow(msg -> new IOException("Failed to decode " + file + ": " + msg)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read WyrldRecipe file " + file, e);
        }
    }

    public List<String> listNames() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return names;
        }
        try (var stream = Files.list(root)) {
            stream.filter(p -> p.toString().endsWith(EXTENSION))
                    .forEach(p -> load(stripExtension(p.getFileName().toString())).ifPresent(r -> names.add(r.name())));
        } catch (IOException e) {
            throw new RuntimeException("Failed to list WyrldRecipe store", e);
        }
        return names;
    }

    private static String stripExtension(String fileName) {
        return fileName.substring(0, fileName.length() - EXTENSION.length());
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
