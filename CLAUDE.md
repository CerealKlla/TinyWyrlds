# TinyWyrlds

Instanced-world mod for the Kyngdoms suite. A "Wyrld" is a purpose-built, UUID-named world map,
entered/exited via teleport, frequently reset/wiped and eventually fully deleted, with no voxel
creation or destruction permitted inside (except non-solid blocks like plants/webs). Rooms are
authored with an admin-facing blueprint editor and assembled per-visit from a WyrldRecipe; Entrances
into a Wyrld are generated either temporarily by a quest or standing on a settlement plot with a
weekly-rotating blueprint.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/tinywyrlds-context), not
part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's
cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a
fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/tinywyrlds-context.git context
```

Before searching source for architecture, ownership boundaries, API shape, or "why does this work
this way," check `context/` first:

- `context/design-document.md` — the authoritative design spec, including the user's own original
  request verbatim, broken into subsystems each annotated with real implementation status, plus open
  design questions that need resolving before building subsystem 1 (Wyrld lifecycle). Start here for
  anything about intended shape or scope.
- `context/decisions.md` — dated log of decisions + pending/cross-mod-interface facts.
- `context/classes/` — one short markdown file per implemented class or tightly-related group.

**Keep this system current as you work** — same discipline as every other mod in this suite: update
design-document.md/decisions.md when a decision is made, update classes/ when a class's public
surface changes, commit+push context/ separately from this repo's own commits.

## Status

Scaffolded 2026-10-13 — standalone mod, mirroring Myssions'/Kyt's own project shape:
- Loader: **NeoForge**, Minecraft **26.1.2**, Java **25**
- Group ID: `com.github.cerealklla.tinywyrlds` / Mod ID: `tinywyrlds`
- No required sibling-mod dependencies. Optional soft dependencies on Myssions (quest UUID
  grant/accept gating an Entrance), Settlemynts (a plot type hosting a standing Wyrld Entrance), and
  Blueprynts (shares its Construction-Site *concept* for the room blueprint editor, not a real code
  dependency).
**First slice built 2026-10-13 — Wyrld lifecycle** (see [context/design-document.md](context/design-document.md)'s
"Wyrld lifecycle architecture" section for the full design back-and-forth, and
[context/classes/](context/classes/) for per-class reference):
- One permanent, custom `tinywyrlds:wyrld_space` dimension (void-flat, `min_y=0`/`height=2032`) —
  isolated per-UUID dimensions and a fixed max-cell-size grid were both proposed and explicitly
  rejected (see the design doc) in favor of a 2D free-rectangle packer (`space.WyrldSpaceAllocator`)
  that reserves exactly the footprint each Wyrld's own generated layout needs, no fixed size
  anywhere. Vertical Y-band "stacking" was also proposed and correctly rejected — a Minecraft chunk
  column loads as a whole regardless of Y, so every instance just shares one baseline Y instead.
- `instance.WyrldManager` — create a Wyrld, teleport a player in/out (remembering where they came
  from), and a periodic idle sweep (ACTIVE → STALE after 5 min unoccupied → wiped+freed after 15
  more). `voxel.VoxelLockListener` enforces "no solid block create/destroy" (non-solid = no
  collision shape) scoped to the Wyrld Space dimension.
- No room/recipe/Entrance content exists yet — only reachable via `/tinywyrlds create <w> <l>` /
  `/tinywyrlds leave` debug commands. `./gradlew build`/`test` green (4 allocator unit tests),
  boot-smoke-tested clean via `runServer` (the dimension registers/loads with no errors, whole
  8-mod suite unaffected). **Never actually entered/left from a real connected client yet.**

See [context/classes/](context/classes/) for per-class reference.
