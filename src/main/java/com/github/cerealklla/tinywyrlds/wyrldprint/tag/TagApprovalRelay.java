package com.github.cerealklla.tinywyrlds.wyrldprint.tag;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.nuclyus.api.Nuclyus;
import com.github.cerealklla.nuclyus.tag.TagApprovalListener;
import com.github.cerealklla.tinywyrlds.TinyWyrldsMod;
import com.github.cerealklla.tinywyrlds.bridge.CoryspondanceMailBridge;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * TinyWyrlds' reaction to {@link Nuclyus#approveTag} -- registered once (a stateless singleton,
 * from {@code TinyWyrldsMod}'s own constructor via {@code Nuclyus.registerTagApprovalListener(new
 * TagApprovalRelay())}) rather than per-server-start, so a repeated world reload within one JVM
 * (e.g. a `runClient` dev session) can never accumulate duplicate listeners. Resolves the live
 * server fresh on each call via {@link ServerLifecycleHooks#getCurrentServer()} instead of capturing
 * one at construction time, since construction happens before any server exists at all.
 *
 * <p>Two things happen on every real approval: every already-saved {@link WyrldPrintRecord} using
 * the old tag text gets rewritten to the final approved text (the whole reason the vocabulary lives
 * in Nuclyus but the retroactive rewrite has to live here -- Nuclyus knows nothing about {@code
 * WyrldPrintStorage}), and, if the requester can be resolved, they get a Coryspondance letter naming
 * both texts (explicit user request, 2026-11-11: "I'd like the player who requested it to get a
 * Coryspondance letter indicating their tag was accepted and the value they'll use going forward is
 * &lt;Selected Tag&gt;.").
 */
public final class TagApprovalRelay implements TagApprovalListener {

    @Override
    public void onTagApproved(String originalTag, String approvedTag, UUID requestedBy) {
        int updated = rewriteTaggedPrints(originalTag, approvedTag);
        TinyWyrldsMod.LOGGER.info("TagApprovalRelay: '{}' -> '{}' approved, {} WyrldPrint(s) updated.",
                originalTag, approvedTag, updated);
        if (requestedBy == null || !CoryspondanceMailBridge.isLoaded()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            CoryspondanceMailBridge.sendTagApprovedMail(server, requestedBy, originalTag, approvedTag);
        }
    }

    private int rewriteTaggedPrints(String originalTag, String approvedTag) {
        int updated = 0;
        for (WyrldPrintRecord record : WyrldPrintStorage.get().listAll()) {
            if (!record.tags().contains(originalTag)) {
                continue;
            }
            List<String> newTags = record.tags().stream()
                    .map(tag -> tag.equals(originalTag) ? approvedTag : tag)
                    .distinct()
                    .toList();
            WyrldPrintStorage.get().save(record.withTags(newTags));
            updated++;
        }
        return updated;
    }
}
