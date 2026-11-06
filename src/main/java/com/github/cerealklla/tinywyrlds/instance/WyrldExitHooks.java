package com.github.cerealklla.tinywyrlds.instance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.level.ServerPlayer;

/** Backing registry for {@link WyrldExitHandler} -- see that interface's own doc. Public so {@code api.TinyWyrlds#registerExitHandler} (a different package) can reach {@link #register}, same precedent as {@code wyrldprint.entrance.EntranceEnterResults}. */
public final class WyrldExitHooks {

    private static final List<WyrldExitHandler> HANDLERS = new CopyOnWriteArrayList<>();

    private WyrldExitHooks() {
    }

    public static void register(WyrldExitHandler handler) {
        HANDLERS.add(handler);
    }

    /** @return true if some handler claimed the exit (the caller should cancel the real teleport). */
    static boolean tryHandle(ServerPlayer player) {
        for (WyrldExitHandler handler : HANDLERS) {
            if (handler.tryHandle(player)) {
                return true;
            }
        }
        return false;
    }
}
