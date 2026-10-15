package com.github.cerealklla.tinywyrlds.wyrldprint.entrance;

import com.github.cerealklla.tinywyrlds.instance.WyrldManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The real, concrete realization of the original spec's vague "the player then has to escape or
 *  something like that" -- a plain, non-consumable item (same reusable-tool precedent as
 *  Blueprynts' Locator items) auto-granted by {@code WyrldEntranceEntity#enter}, so a real player
 *  always has a way out without needing to know about the {@code /tinywyrlds leave} debug command.
 *  Right-click anywhere calls {@code WyrldManager#leave} directly -- a no-op with a clear message
 *  if not currently inside a Wyrld at all. */
public class WyrldReturnItem extends Item {

    public WyrldReturnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        boolean left = WyrldManager.leave(serverPlayer);
        if (!left) {
            serverPlayer.sendSystemMessage(Component.literal("You're not inside a Wyrld right now."));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
