package com.github.cerealklla.tinywyrlds.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintArea;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintCapture;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintPlacement;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintValidator;
import com.github.cerealklla.tinywyrlds.wyrldprint.generate.OneOffGenerator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * DEBUG ONLY -- deliberately no permission gate, same precedent as every other sibling mod's own
 * {@code debug.DebugCommands}. No real player-facing content (room blueprints, WyrldRecipes,
 * quest/settlement-plot Entrances) exists yet, so these commands are the only way to exercise the
 * space-allocation/lifecycle slice at all right now.
 */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tinywyrlds")
                .requires(source -> true)
                .then(Commands.literal("create")
                        .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                        .executes(ctx -> createAndEnter(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "width"),
                                                IntegerArgumentType.getInteger(ctx, "length"))))))
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("craftzone")
                        .executes(ctx -> enterCraftZone(ctx.getSource())))
                .then(Commands.literal("wyrldprint")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(ctx -> saveWyrldPrint(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "type"))))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> loadWyrldPrint(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listWyrldPrints(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearCraftZone(ctx.getSource())))
                        .then(Commands.literal("validateall")
                                .executes(ctx -> validateAllWyrldPrints(ctx.getSource()))))
                .then(Commands.literal("generate")
                        .then(Commands.argument("minRooms", IntegerArgumentType.integer(1))
                                .then(Commands.argument("maxRooms", IntegerArgumentType.integer(1))
                                        .executes(ctx -> generateOneOff(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "minRooms"),
                                                IntegerArgumentType.getInteger(ctx, "maxRooms"))))))
                .then(Commands.literal("selectiontest")
                        .then(Commands.argument("trials", IntegerArgumentType.integer(1))
                                .then(Commands.argument("minRooms", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("maxRooms", IntegerArgumentType.integer(1))
                                                .executes(ctx -> selectionTest(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "trials"),
                                                        IntegerArgumentType.getInteger(ctx, "minRooms"),
                                                        IntegerArgumentType.getInteger(ctx, "maxRooms"))))))));
    }

    private static int saveWyrldPrint(CommandSourceStack source, String name, String typeArg) {
        ServerLevel level = source.getServer().getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY);
        if (level == null) {
            source.sendFailure(Component.literal("Wyrld Space isn't loaded."));
            return 0;
        }
        WyrldPrintType type;
        try {
            type = WyrldPrintType.valueOf(typeArg.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Type must be 'room', 'connector', or 'entrance'."));
            return 0;
        }
        String author = source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "Generator";
        WyrldPrintCapture.CaptureResult result = WyrldPrintCapture.capture(level, name, author, type);
        if (result.error().isPresent()) {
            source.sendFailure(Component.literal(result.error().get()));
            return 0;
        }
        WyrldPrintStorage.get().save(result.record().get());
        source.sendSuccess(() -> Component.literal("Saved " + type.name() + " WyrldPrint '" + name + "' ("
                + result.record().get().cells().size() + " cells, " + result.record().get().points().size() + " connector points)."), false);
        return 1;
    }

    private static int loadWyrldPrint(CommandSourceStack source, String name) {
        ServerLevel level = source.getServer().getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY);
        if (level == null) {
            source.sendFailure(Component.literal("Wyrld Space isn't loaded."));
            return 0;
        }
        var found = WyrldPrintStorage.get().load(name);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No WyrldPrint named '" + name + "'."));
            return 0;
        }
        WyrldPrintPlacement.loadIntoCraftingZone(level, found.get());
        source.sendSuccess(() -> Component.literal("Loaded '" + name + "' into the crafting zone."), false);
        return 1;
    }

    private static int clearCraftZone(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension.KEY);
        if (level == null) {
            source.sendFailure(Component.literal("Wyrld Space isn't loaded."));
            return 0;
        }
        WyrldPrintPlacement.clearCraftingZone(level);
        source.sendSuccess(() -> Component.literal("Cleared the crafting zone."), false);
        return 1;
    }

    private static int validateAllWyrldPrints(CommandSourceStack source) {
        int broken = 0;
        for (WyrldPrintRecord record : WyrldPrintStorage.get().listAll()) {
            WyrldPrintValidator.Result result = WyrldPrintValidator.checkConnectivity(record);
            if (!result.connected()) {
                broken++;
            }
            String line = (result.connected() ? "OK   " : "FAIL ") + record.name() + " (" + record.type() + "): " + result.message();
            com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info(line);
        }
        int finalBroken = broken;
        source.sendSuccess(() -> Component.literal("Validated all WyrldPrints -- " + finalBroken + " broken (see server log for the full per-piece report)."), false);
        return broken;
    }

    private static int listWyrldPrints(CommandSourceStack source) {
        var names = WyrldPrintStorage.get().listNames();
        source.sendSuccess(() -> Component.literal(names.isEmpty() ? "No WyrldPrints saved yet." : String.join(", ", names)), false);
        return names.size();
    }

    private static int generateOneOff(CommandSourceStack source, int minRooms, int maxRooms) {
        OneOffGenerator.GenerateResult result = OneOffGenerator.generate(source.getServer(), minRooms, maxRooms);
        if (result == null) {
            source.sendFailure(Component.literal("Not enough saved WyrldPrints to generate a dungeon -- need at least 1 room or entrance."));
            return 0;
        }
        WyrldInstanceRecord instance = result.instance();
        if (source.getEntity() instanceof ServerPlayer player) {
            // Multiple spawn points are meant for a whole party arriving together (explicit user
            // spec, 2026-10-14) -- this single-player debug command just uses the first one; a
            // real party-aware entry point (assigning each player their own marker) is future work.
            if (!result.spawnPoints().isEmpty()) {
                WyrldManager.enterAt(player, instance.id(), result.spawnPoints().get(0));
            } else {
                WyrldManager.enter(player, instance.id());
            }
            source.sendSuccess(() -> Component.literal("Generated and entered a one-off Wyrld " + instance.id()), false);
        } else {
            source.sendSuccess(() -> Component.literal("Generated a one-off Wyrld " + instance.id()
                    + " (no player to enter -- run from RCON/console)."), false);
        }
        return 1;
    }

    private static int enterCraftZone(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        boolean entered = WyrldPrintArea.teleportPlayerIn(player);
        source.sendSuccess(() -> Component.literal(entered
                ? "Sent to the WyrldPrint crafting area."
                : "Could not enter the WyrldPrint crafting area."), false);
        return entered ? 1 : 0;
    }

    private static int createAndEnter(CommandSourceStack source, int width, int length) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        WyrldInstanceRecord instance = WyrldManager.createWyrld(source.getServer(), width, length);
        WyrldManager.enter(player, instance.id());
        source.sendSuccess(() -> Component.literal("Created and entered Wyrld " + instance.id()), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        boolean left = WyrldManager.leave(player);
        source.sendSuccess(() -> Component.literal(left ? "Left the Wyrld." : "You're not inside a Wyrld."), false);
        return 1;
    }

    /** Debug-only: runs {@code OneOffGenerator}'s planning logic {@code trials} times with no real
     *  Wyrld creation (see {@code OneOffGenerator.planOnceForDebug}'s own doc) and tallies how many
     *  times each saved piece actually got placed -- added 2026-10-14 after a real report ("my room
     *  hasn't been selected once") to get hard counts instead of guessing from a handful of manual
     *  {@code generate} calls. Full per-piece counts go to the server log (can be long); chat gets
     *  a short summary. */
    private static int selectionTest(CommandSourceStack source, int trials, int minRooms, int maxRooms) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        int emptyPlans = 0;
        for (int i = 0; i < trials; i++) {
            var names = OneOffGenerator.planOnceForDebug(minRooms, maxRooms);
            if (names.isEmpty()) {
                emptyPlans++;
                continue;
            }
            for (String name : names) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info("selectiontest: {} trial(s), {} empty plan(s) (no ROOM WyrldPrints saved)", trials, emptyPlans);
        counts.forEach((name, count) -> com.github.cerealklla.tinywyrlds.TinyWyrldsMod.LOGGER.info("selectiontest:   {} -> placed in {} trial(s)", name, count));
        int finalEmptyPlans = emptyPlans;
        source.sendSuccess(() -> Component.literal("Ran " + trials + " trial(s) (" + finalEmptyPlans
                + " empty) across " + counts.size() + " distinct piece(s) -- see server log for full per-piece counts."), false);
        return trials;
    }
}
