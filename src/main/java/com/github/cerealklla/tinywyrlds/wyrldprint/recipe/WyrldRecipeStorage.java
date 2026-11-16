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
 *  {@code BlueprintStorage}. Layout: {@code tinywyrlds/recipes/<Name>.json}.
 *
 *  <p><b>Tunnel recipes ({@link WyrldRecipe#tunnel()}) are redirected to {@code
 *  tinywyrlds/generateddungeons/} instead, 2026-11-15</b> -- explicit user correction: "they should
 *  always have been placed into generateddungeons since that's what they are." A tunnel recipe is
 *  auto-created per unique location pair by {@code api.TinyWyrlds#ensureTunnelRecipeExists}
 *  (called from Settlemynts' {@code zone.NaturalSettlementTunnelPlacer}), accumulating forever as
 *  more natural villages/Tunnel Stakes get placed across the server's life -- the exact same "grows
 *  forever from live gameplay, not real authored content" shape {@link
 *  com.github.cerealklla.tinywyrlds.wyrldprint.generate.TunnelGenerationStorage}'s own tunnel-group
 *  records already get this treatment for. Previously lived in {@code recipes/} alongside genuinely
 *  authored recipes (e.g. {@code WelcomeTutorial}), where both of this suite's modpack bundlers
 *  (AutoModpack's live sync, {@code ClientPack\build-mrpack.ps1}) wrongly kept re-shipping every
 *  tunnel ever generated as if it were static content. {@link #save}/{@link #load} dispatch
 *  transparently by {@link WyrldRecipe#tunnel()}/filename prefix, so every existing caller (which
 *  only ever calls the generic {@code save}/{@code load}, with no notion of where a recipe
 *  physically lives) needed zero changes. {@link #load} also self-heals an already-existing tunnel
 *  recipe still sitting in the old {@code recipes/} location the first time anything loads it by
 *  name again -- same "no re-creation needed, flips on next load" idiom {@code
 *  api.TinyWyrlds#ensureRecipeExists}'s own doc already establishes for this class. {@link
 *  #listNames} deliberately stays scoped to {@code recipes/} only -- tunnel recipes are synthetic
 *  plumbing, never meant to show up in an author-facing recipe browser. */
public final class WyrldRecipeStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";
    private static final String TUNNEL_PREFIX = "tunnelrecipe_";

    private static final WyrldRecipeStorage INSTANCE = new WyrldRecipeStorage();

    private final Path root;
    private final Path tunnelRoot;

    private WyrldRecipeStorage() {
        Path base = FMLPaths.GAMEDIR.get().resolve("tinywyrlds");
        this.root = base.resolve("recipes");
        this.tunnelRoot = base.resolve("generateddungeons");
    }

    public static WyrldRecipeStorage get() {
        return INSTANCE;
    }

    public void save(WyrldRecipe recipe) {
        Path dir = recipe.tunnel() ? tunnelRoot : root;
        Path file = dir.resolve(fileNameFor(recipe.name(), recipe.tunnel()));
        try {
            Files.createDirectories(dir);
            JsonElement json = WyrldRecipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe)
                    .getOrThrow(msg -> new IOException("Failed to encode WyrldRecipe '" + recipe.name() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save WyrldRecipe '" + recipe.name() + "'", e);
        }
        // Self-heal: a tunnel recipe that still has a stale copy sitting in the old recipes/
        // location (saved before this redirect existed) gets that copy removed the moment it's
        // next saved from here, so it doesn't linger and get bundled by a static-content scan.
        if (recipe.tunnel()) {
            try {
                Files.deleteIfExists(root.resolve(fileNameFor(recipe.name(), false)));
            } catch (IOException ignored) {
                // Best-effort cleanup only -- the new copy above is already the authoritative one.
            }
        }
    }

    public Optional<WyrldRecipe> load(String name) {
        Optional<WyrldRecipe> tunnelCopy = loadFrom(tunnelRoot.resolve(fileNameFor(name, true)));
        if (tunnelCopy.isPresent()) {
            return tunnelCopy;
        }
        Optional<WyrldRecipe> legacyCopy = loadFrom(root.resolve(fileNameFor(name, false)));
        if (legacyCopy.isPresent() && legacyCopy.get().tunnel()) {
            // Found a tunnel recipe still sitting in the old location -- migrate it now rather
            // than waiting for something to call save() on it again.
            save(legacyCopy.get());
        }
        return legacyCopy;
    }

    private static Optional<WyrldRecipe> loadFrom(Path file) {
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

    private static String fileNameFor(String name, boolean tunnel) {
        return (tunnel ? TUNNEL_PREFIX : "") + sanitize(name) + EXTENSION;
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

    /**
     * Proactively migrates every tunnel recipe still sitting in the legacy {@code recipes/}
     * location into {@code generateddungeons/}, scanning the whole directory once up front rather
     * than waiting for each one to be lazily touched by {@link #load} -- real live bug, 2026-11-16,
     * the exact same "Captured source changed" AutoModpack symptom the {@code generateddungeons/}
     * redirect itself was built to fix, just triggered a different way: {@code
     * zone.NaturalSettlementTunnelPlacer}'s shared {@code NaturalSettlementShortcut} recipe still
     * sitting in {@code recipes/} on a fresh restart got captured there by AutoModpack's own
     * pack-generation step (which runs moments after server start, before any gameplay has
     * happened), and then the FIRST natural-village Tunnel placed anywhere on the map lazily
     * triggered this same recipe's self-heal migration -- moving/deleting the exact file
     * AutoModpack was mid-transaction syncing. Called from {@code TinyWyrldsMod#commonSetup}
     * (mod-init time, not a server-lifecycle event) specifically so it can never race AutoModpack's
     * own pack generation, whichever server-starting-stage event that turns out to be hooked to --
     * this runs first, unconditionally, by construction.
     */
    public void migrateLegacyTunnelRecipes() {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            stream.filter(p -> p.toString().endsWith(EXTENSION))
                    .forEach(p -> load(stripExtension(p.getFileName().toString()))); // load() self-heals a tunnel recipe found here -- see its own doc.
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan WyrldRecipe store for legacy tunnel recipes", e);
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.substring(0, fileName.length() - EXTENSION.length());
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
