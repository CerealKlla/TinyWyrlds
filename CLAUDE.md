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
  data model, a global file store mirroring Blueprynts' own `BlueprintStorage`). Advancements
  disabled in Wyrld Space.
- **"Generate One-off" is real connector-aware generation** (`generate.OneOffGenerator`, second
  pass) — matches connector points by kind + opposite facing, tries all 4 rotations via vanilla's
  own `BlockState#rotate`, rejects colliding placements. A real collision-detection bug (the
  expected 1-cell connector seam being flagged as a clash) was found live and fixed; re-tested
  afterward hitting the requested room count consistently across several runs. Still not
  size-matched and still no WyrldRecipe-driven selection — flagged, not forgotten.
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
- **`ENTRANCE` WyrldPrint type + Spawn Location marker block added, 2026-10-14, later same
  day** (see [context/classes/Entrance.md](context/classes/Entrance.md)) — a third WyrldPrint type,
  treated like a `ROOM` for authoring/connectivity but always placed exactly once (the generated
  layout's guaranteed first/anchor piece, never elsewhere), with a new gold-glowing
  `SpawnLocationBlock` marking exactly where `/tinywyrlds generate` should teleport the player
  (captured the same build-time-aid way Connector Points' own glass fill is, never a real block in
  the stamped dungeon). `./gradlew build`/`test` green; **not yet live-tested** — no real ENTRANCE
  WyrldPrint with a Spawn Location marker has been authored and run through `/tinywyrlds generate`
  yet.
- **Placement math now makes joined pieces touch, not overlap, same day** (see
  [context/classes/WyrldPrintMechanic.md](context/classes/WyrldPrintMechanic.md)'s own 5th
  collision-detection iteration) — a wall-type join used to force the new piece's door into the
  exact same cell as the existing door, dragging the whole shared wall plane into forced
  coincidence; fixed to align 1 cell further out along the existing door's own outward facing
  instead, landing the two doors in genuinely adjacent cells. Also: connectors can no longer chain
  directly to another connector (always sit between two rooms), and a new `selectiontest` debug
  command reports real per-piece selection-frequency counts instead of guessing from a handful of
  manual `generate` calls.
- **Seven real rounds of live bug-fixing on generation/dead-end handling, same day, all
  confirmed live** (see [context/classes/WyrldPrintMechanic.md](context/classes/WyrldPrintMechanic.md)
  for the blow-by-blow): connector size matching enforced; a real regression where a wall-type join
  re-opened its own just-consumed door (inflating room counts with duplicates); a guarantee that no
  frontier is ever left a real unused dead end (a "closing pass" using dedicated 1-door endcap
  rooms, tried as a last resort during growth too, not only once growth stops); the `ENTRANCE`
  corrected to not count toward `minRooms`/`maxRooms` and to always get a connector attached rather
  than a direct room join; and — after six iterations chasing it — a confirmed, understood root
  cause for open "dead" doorways (a cell already holding the connector's own real door/trapdoor
  block was wrongly treated as "already real content, leave alone," so it never got sealed).
  **Confirmed live**: "I went through two very large dungeons and found no more empty doors."
- **Generated dungeons now save their structural layout, same day** (see
  [context/classes/GeneratedDungeon.md](context/classes/GeneratedDungeon.md)), explicit user spec:
  "save the layout into a GeneratedDungeon folder... so we can regenerate the same layout." Only the
  room arrangement/doors/door-sealing result is saved (by a fresh UUID); nothing about mob/loot
  content, since none of that exists yet — the record is shaped so a future mob/loot system has
  nowhere to put per-generation random state, keeping that randomized on every regenerate by
  construction. New `/tinywyrlds regenerate <id>` command. `./gradlew build`/`test` green; **not
  yet live-tested**.

- **The in-world "right-click to bring up a menu" fixture built, 2026-10-15** — the long-deferred
  spec item picked up as the first item in the project's own standing pending-work list. A single
  permanent `workbench.WyrldWorkbenchBlock` (visually a crafting-table lookalike, same v1 precedent
  Blueprynts' `ConstructionSiteBlock` set) is now placed by `WyrldPrintArea#ensureBuilt` on the
  staging strip itself, right-clickable into `WyrldWorkbenchScreen` — Save/Load/Clear/Validate
  All/Generate One-off/Regenerate, a real GUI front end over the exact same lower-level APIs
  `debug.DebugCommands` already called (so the two never diverge in behavior), purely
  payload-driven (no container menu), same convention as Blueprynts' `ConstructionSiteScreen`.
  Carries no block-entity state of its own — every saved-WyrldPrint list is read fresh from
  `WyrldPrintStorage` on each open. A real bug caught during this build: the `SaveWyrldPrintPayload`
  record's `type` component clashed with `CustomPacketPayload#type()`'s own accessor (a record
  component can't share a name with an interface method of a different return type) — renamed to
  `printType`. `./gradlew build`/`test` green; a real `runServer` boot-smoke-test from Cartographyr
  confirmed the block/payload registration and `WyrldPrintArea#ensureBuilt`'s new placement step
  both run with no exception (the crafting-zone floor/obsidian/workbench all built cleanly before an
  unrelated, pre-existing issue — Production's own already-running server holding the shared
  voicechat UDP port — tore the disposable dev-boot session down; confirmed via
  `Get-NetUDPEndpoint`/`Get-CimInstance Win32_Process` that this is Production's own process, not
  anything this change introduced). **Live client confirmation (actually right-clicking it and
  using each button) still pending** — same standing gap as the ENTRANCE/Spawn Location marker and
  `/tinywyrlds regenerate` above.

See [context/classes/](context/classes/) for per-class reference.
