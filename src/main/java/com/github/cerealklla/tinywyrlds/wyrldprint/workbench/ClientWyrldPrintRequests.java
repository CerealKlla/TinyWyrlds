package com.github.cerealklla.tinywyrlds.wyrldprint.workbench;

import java.util.Optional;

/**
 * Client-side bridge for "the WyrldPrint Workbench screen should open/refresh" -- deliberately
 * zero client-only imports, same reasoning as Blueprynts' {@code
 * construction.ClientConstructionRequests}: written from the common {@code
 * TinyWyrldsMod#registerPayloads} handler (which must stay harmless to class-load on a dedicated
 * server), and read/cleared from a genuinely client-only tick listener in {@code
 * TinyWyrldsModClient} that's the only place allowed to touch {@code Minecraft}/{@code Screen}.
 */
public final class ClientWyrldPrintRequests {

    private static volatile OpenWyrldWorkbenchScreenPayload pendingWorkbenchScreen;

    private ClientWyrldPrintRequests() {
    }

    public static void requestWorkbenchScreen(OpenWyrldWorkbenchScreenPayload payload) {
        pendingWorkbenchScreen = payload;
    }

    public static Optional<OpenWyrldWorkbenchScreenPayload> takePendingWorkbenchScreen() {
        OpenWyrldWorkbenchScreenPayload request = pendingWorkbenchScreen;
        pendingWorkbenchScreen = null;
        return Optional.ofNullable(request);
    }
}
