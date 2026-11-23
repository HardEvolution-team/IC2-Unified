# IC2 Unified

Compat for running **IC2 Classic 1.5.15** and **IC2 Experimental 2.8.222** together on 1.12.2 (Cleanroom / Forge).

## Why a relocated jar
Both mods use the mod id `ic2` and the java packages `ic2.*`, so they can not be loaded at the same time.
The `relocateClassic` task rewrites IC2 Classic:

| original | relocated |
|---|---|
| packages `ic2.*` (incl. its own copy of the IC2 API) | `ic2classic.*` |
| mod id / resource domain `ic2` | `ic2classic` |
| `assets/ic2/**` | `assets/ic2classic/**` |
| fluids `ic2coolant`, `ic2steam`, ... | `ic2classic_coolant`, `ic2classic_steam`, ... |
| FML APIs `ic2api`, `ic2audioapi` | `ic2classicapi`, `ic2classicaudioapi` |

Lang keys that Experimental already defines are dropped from Classic's lang files (so Classic does not overwrite
Experimental's texts); an `ic2classic.*` copy of every `ic2.*` key is kept.

## What the mod does
* **Shared EU** – every energy tile of one IC2 is mirrored (dynamic proxy) into the energy net of the other one.
  Classic cables power Experimental machines, Experimental generators charge Classic MFSUs, cables of both mods connect.
  Mirrors only forward calls to the real tile, so energy is never duplicated. Tiers are the same (1 = 32 EU/t ...).
* **Crafting conflicts** – recipes of both mods that accept the same grid are detected; by default the Classic one is
  disabled (`PREFER_EXPERIMENTAL`) and a 1:1 shapeless conversion *Experimental item -> Classic item* is added.
* **Materials** – for every ore-dictionary material present in both mods (ingots, dusts, plates, ores, ...) a
  conversion recipe is added both ways: 2 items stacked vertically -> 2 items of the other mod.
* **World gen** – Classic world generators are removed by default (Experimental already generates the same ores).

Everything is configurable in `config/ic2unified.cfg`.

## IC2 Classic addons
Addons made for IC2 Classic reference `ic2.*` and the mod id `ic2`, which now belong to Experimental.
Put such addons into `<game dir>/ic2classic-addons/` (**not** `mods/`). On startup the IC2 Unified coremod rewrites them
(`ic2.*` -> `ic2classic.*`, `"ic2"` / `required-after:ic2` -> `ic2classic`, `ic2:` resources, Classic fluid and
API names, reflective class names), stores the result in `ic2unified/addon-cache/` and adds it to the class path, where
Forge loads it as a normal mod. Only new or changed jars are processed (cached by hash).
The folder can be changed with `-Dic2unified.addonsDir=<path>`.

## Installing
1. `mods/`: **IC2 Experimental** (`industrialcraft-2-2.8.222-ex112.jar`) and `ic2unified-<version>.jar`.
2. `ic2classic-addons/` (next to `mods/`, created on first start): the **original, unmodified** `IC2Classic-1.12.2-1.5.15.jar`
   downloaded from its CurseForge page. **Not** into `mods/`.
3. Start the game. The IC2 Unified coremod relocates Classic on your machine into `ic2unified/classic-cache/` (once,
   cached by hash) and loads it. Nothing from IC2 Classic or IC2 Experimental is shipped with this mod.

## Building
1. Put `IC2Classic-1.12.2-*.jar` and `industrialcraft-2-*.jar` into `libs/` (they are git-ignored).
2. `gradlew build` (compiles against a build-time relocated Classic, `build/relocated/`).
Dev run: `gradlew runServer -Pic2unified.devmods=true` (prebuilt relocated jar) or add `-Pic2unified.runtimeRelocate=true`
and put the original Classic jar into `run/server/ic2classic-addons/` to test the runtime relocation.

## Known limitations
* Worlds that already contain IC2 Classic blocks lose them (registry names changed `ic2:` -> `ic2classic:`).
* Classic fluids are separate from Experimental fluids (different names).
* Coremod addons of IC2 Classic are not supported by the addon loader.

## License
HardEvolution Restricted Source-Available License (HE-RSAL) 1.1 — see `LICENSE.en.md` / `LICENSE.ru.md`.
Covers only this project's own code. IC2 Experimental and IC2 Classic belong to their authors.
