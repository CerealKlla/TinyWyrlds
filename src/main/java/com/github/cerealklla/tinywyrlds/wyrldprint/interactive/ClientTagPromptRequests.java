package com.github.cerealklla.tinywyrlds.wyrldprint.interactive;

import java.util.Optional;

/** Client-side bridge for "the tag prompt should open" -- zero client-only imports, same precedent as {@code wyrldprint.workbench.ClientWyrldPrintRequests}: written from the common payload handler (which must stay harmless on a dedicated server), read/cleared from a genuinely client-only tick listener in {@code TinyWyrldsModClient}. */
public final class ClientTagPromptRequests {

    private static volatile OpenTagPromptPayload pendingPrompt;

    private ClientTagPromptRequests() {
    }

    public static void requestPrompt(OpenTagPromptPayload payload) {
        pendingPrompt = payload;
    }

    public static Optional<OpenTagPromptPayload> takePendingPrompt() {
        OpenTagPromptPayload request = pendingPrompt;
        pendingPrompt = null;
        return Optional.ofNullable(request);
    }
}
