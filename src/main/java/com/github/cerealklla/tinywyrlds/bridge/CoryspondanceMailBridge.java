package com.github.cerealklla.tinywyrlds.bridge;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.coryspondance.api.Coryspondance;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * TinyWyrlds-side bridge for Coryspondance's mail facility -- same isolation convention as every
 * other bridge class in this suite (see e.g. {@code bridge.TinyWyrldsTunnelBridge} in Settlemynts):
 * every call site elsewhere in this mod checks {@link #isLoaded()} first, and this class's own
 * public methods never leak a Coryspondance type out. Added 2026-11-11 for the Tag system -- notifies
 * a player when a tag they requested gets approved (see {@code wyrldprint.tag.TagApprovalRelay}).
 */
public final class CoryspondanceMailBridge {

    private CoryspondanceMailBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("coryspondance");
    }

    public static void sendTagApprovedMail(MinecraftServer server, UUID toPlayerId, String originalTag, String approvedTag) {
        String message = "Your tag request '" + originalTag + "' was accepted! The tag you'll see going forward is '"
                + approvedTag + "'.";
        Coryspondance.sendSystemMail(server, toPlayerId, "Wyrld Workbench", message, List.of());
    }
}
