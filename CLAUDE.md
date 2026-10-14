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
- Empty mod -- `TinyWyrldsMod` registers nothing yet, only logs "TinyWyrlds common setup" on boot.
  No content exists. See [context/design-document.md](context/design-document.md) for the full
  spec (verbatim user request + subsystem breakdown + open design questions) and
  [context/decisions.md](context/decisions.md) for pending work. **Nothing has been built yet** —
  the open design questions around Wyrld lifecycle (real per-UUID `Level` vs. a bounded shared-space
  region, reset/wipe triggers, voxel-lock enforcement) need resolving before any real slice starts.

See [context/classes/](context/classes/) for per-class reference (empty so far).
