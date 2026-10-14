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
  `/tinywyrlds leave` debug commands. **Confirmed live, 2026-10-14** — create/leave and the voxel
  lock all behaved as expected in a real connected dev client.

**WyrldPrint system built and content-populated, 2026-10-14** (renamed from "room blueprint
system" — explicit user instruction, to avoid confusion with Blueprynts' own "Blueprint") — see
[context/design-document.md](context/design-document.md)'s subsystem 2,
[context/classes/WyrldPrintArea.md](context/classes/WyrldPrintArea.md),
[context/classes/ConnectorPoint.md](context/classes/ConnectorPoint.md), and
[context/classes/WyrldPrintMechanic.md](context/classes/WyrldPrintMechanic.md):
- The fixed, permanent 150x100 crafting zone (void crafting area + single-block obsidian boundary
  + cobblestone staging), with Creative gamemode + voxel-lock exemption inside it — **confirmed
  live** after two real fixes (always-night lighting; the obsidian boundary was a 12-tall wall
  instead of 1 block).
- Six real Connector Point blocks (genuine `DoorBlock`/`TrapDoorBlock` subclasses, oak door/
  trapdoor look, purple particle glow, auto-fill size-tiered glass marking the attachment area).
- A real save/load/capture mechanic (`WyrldPrintType` ROOM/CONNECTOR, forward/lateral-normalized
  data model, a global file store mirroring Blueprynts' own `BlueprintStorage`) and a first-pass
  "Generate One-off" (`generate.OneOffGenerator` — a flagged placeholder: random chain placement,
  no real connector-point alignment or rotation yet). Advancements disabled in Wyrld Space.
- **45 real sample WyrldPrints generated** (25 rooms across 5 themes — tent/wizard tower/cave/
  mine/basement — + 20 connectors spanning horizontal/vertical/mixed, up to a redstone hidden
  piston door), built via a throwaway script driving the mod's own real capture pipeline over
  RCON, same precedent as Blueprynts' own "Ancestors" set. Copied into the shared Dev Server's
  own WyrldPrint store.
- **Explicitly not built this pass**: the in-world "right-click to bring up a menu" block
  (create/edit/save/Generate-One-off GUI) — deliberately deprioritized in favor of real content
  generation within a time-boxed session; the debug commands are a full functional equivalent
  today. Loot/mob/friendly/interactive marker points, real rotation, and WyrldRecipe-driven
  (rather than random-chain) generation are also still unbuilt — see decisions.md.
- `./gradlew build` green throughout; generation itself was 45/45 successful captures.

See [context/classes/](context/classes/) for per-class reference.
