package com.github.cerealklla.tinywyrlds.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
                        .executes(ctx -> leave(ctx.getSource()))));
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
