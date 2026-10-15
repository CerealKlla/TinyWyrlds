package com.github.cerealklla.tinywyrlds.wyrldprint.marker;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The four "possible content" marker categories from the original spec ("specified possible loot
 * box spawn points (various types like common, rare, epic, etc), specified possible enemy mob
 * spawn points, specified possible friendly spawn points, and possible locations for interactive
 * components like buttons, switches, etc."). Each gets its own {@link MarkerBlock} instance (same
 * one-block-per-variant precedent as the six Connector Point blocks) with a distinct particle for
 * quick visual identification while authoring, since there are now several marker kinds on top of
 * Connector Points and Spawn Location. Deliberately a plain content marker, same as {@code
 * SpawnLocationBlock} -- no special capture/placement handling needed: it's captured, saved, and
 * stamped the same generic way any other decorative block is, and a future WyrldRecipe-driven
 * population step (not built yet) can simply scan a generated Wyrld for these block types and
 * replace them with real loot/mobs/interactions, rather than this mod pre-deciding that now.
 */
public enum MarkerKind {
    LOOT_COMMON("loot_spawn_common", "Loot Spawn (Common)", ParticleTypes.ENCHANT),
    LOOT_RARE("loot_spawn_rare", "Loot Spawn (Rare)", ParticleTypes.SOUL_FIRE_FLAME),
    LOOT_EPIC("loot_spawn_epic", "Loot Spawn (Epic)", ParticleTypes.WITCH),
    ENEMY("enemy_spawn", "Enemy Spawn", ParticleTypes.SMOKE),
    FRIENDLY("friendly_spawn", "Friendly Spawn", ParticleTypes.HAPPY_VILLAGER),
    INTERACTIVE("interactive_component", "Interactive Component", ParticleTypes.ELECTRIC_SPARK);

    private final String id;
    private final String label;
    private final SimpleParticleType particle;

    MarkerKind(String id, String label, SimpleParticleType particle) {
        this.id = id;
        this.label = label;
        this.particle = particle;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public SimpleParticleType particle() {
        return particle;
    }
}
