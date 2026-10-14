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
                                .executes(ctx -> listWyrldPrints(ctx.getSource()))))
                .then(Commands.literal("generate")
                        .then(Commands.argument("minRooms", IntegerArgumentType.integer(1))
                                .then(Commands.argument("maxRooms", IntegerArgumentType.integer(1))
                                        .executes(ctx -> generateOneOff(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "minRooms"),
                                                IntegerArgumentType.getInteger(ctx, "maxRooms")))))));
    }

    private static int saveWyrldPrint(CommandSourceStack source, String name, String typeArg) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        WyrldPrintType type;
        try {
            type = WyrldPrintType.valueOf(typeArg.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Type must be 'room' or 'connector'."));
            return 0;
        }
        WyrldPrintCapture.CaptureResult result = WyrldPrintCapture.capture(level, name, player.getName().getString(), type);
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
        if (!(source.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Only a player can run this."));
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

    private static int listWyrldPrints(CommandSourceStack source) {
        var names = WyrldPrintStorage.get().listNames();
        source.sendSuccess(() -> Component.literal(names.isEmpty() ? "No WyrldPrints saved yet." : String.join(", ", names)), false);
        return names.size();
    }

    private static int generateOneOff(CommandSourceStack source, int minRooms, int maxRooms) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        WyrldInstanceRecord instance = OneOffGenerator.generate(source.getServer(), minRooms, maxRooms);
        if (instance == null) {
            source.sendFailure(Component.literal("Not enough saved WyrldPrints to generate a dungeon -- need at least 1 room."));
            return 0;
        }
        WyrldManager.enter(player, instance.id());
        source.sendSuccess(() -> Component.literal("Generated and entered a one-off Wyrld " + instance.id()), false);
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
}
