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

## Building / installing
1. Put `IC2Classic-1.12.2-*.jar` and `industrialcraft-2-*.jar` into `libs/` (they are git-ignored).
2. `gradlew build` – also runs `relocateClassic`.
3. Mods folder: `industrialcraft-2-2.8.222-ex112.jar`, `build/relocated/IC2Classic-relocated.jar`
   (**not** the original Classic jar) and `build/libs/ic2unified-1.0.0.jar`.

Dev run with both mods: `gradlew runServer -Pic2unified.devmods=true` (or `runClient`).

## Known limitations
* Worlds that already contain IC2 Classic blocks lose them (registry names changed `ic2:` -> `ic2classic:`).
* Classic fluids are separate from Experimental fluids (different names).
* IC2 Classic addons compiled against `ic2.core`/`ic2.api.classic` of Classic will not work with the relocated jar.
