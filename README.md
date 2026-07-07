# Praseodymium

Praseodymium is a Fabric 26.2 client-side performance mod for Minecraft Java Edition. Instead of replacing the renderer like Sodium, this version acts like an adaptive performance governor: it watches frame pacing and progressively lowers expensive settings when your FPS drops below your configured target.

It also includes two build-quality helpers:

- entity occlusion culling for things hidden behind walls
- cloud/smoke-style particle occlusion so ambient puffs stop showing through solid blocks
- connected full glass block textures for vanilla glass, stained glass, and tinted glass
- an `Alt` hotbar swapper overlay with 9 saved hotbars, inspired by Axiom
- an `H` enclosed-space checker that scans the air pocket you are pointing at
- a creative-only scan-block highlighter triggered by holding `C` for 3 seconds with a block in hand

The default target is now `50 FPS`. When performance dips below that, Praseodymium can progressively:

- reduce particles to `MINIMAL`
- disable clouds, ambient occlusion, and entity shadows
- reduce biome blend and mipmap levels
- lower entity distance scaling
- lower simulation distance
- lower render distance
- skip rendering entities that are out of sight behind solid walls using cached occlusion checks
- slowly restore your settings after FPS recovers
- open a 9-hotbar swapper overlay by holding `Alt` in creative mode and use `1-9` or the scroll wheel to choose a saved hotbar
- use `Z`, `X`, `V`, and `N` inside the `Alt` overlay to toggle occlusion culling, cycle brightness, cycle flight speed, and save the current hotbar row
- press `H` to toggle enclosed-space checking; enclosing wall blocks are outlined blue, gap cells are outlined red, and a status message appears above the hotbar
- in creative mode, hold a block in your hand and hold `C` for 3 seconds to scan the loaded area inside `min(simulation distance, render distance)` for that block type and outline matches in blue through walls

This is intentionally honest software: it can improve stability and reduce frame drops, but it cannot guarantee `50 FPS` or `1000 FPS` on every machine. If your GPU or CPU is already overloaded, Praseodymium can only trade visual fidelity and world work for better performance.

The hotbar swapper currently uses Minecraft's creative saved-hotbar system, so this first version is safest in creative mode.

## Build

Use Java 25 for Minecraft 26.2, then run:

```powershell
.\gradlew.bat build
```

## Config

After the game starts once, edit:

`config/praseodymium.properties`

Available settings:

- `target_min_fps=50`
- `recovery_fps=60`
- `minimum_render_distance=4`
- `maximum_render_distance=16`
- `minimum_simulation_distance=4`
- `maximum_simulation_distance=12`
- `adjustment_cooldown_seconds=3`
- `recovery_hold_seconds=12`
- `minimum_entity_distance_scaling=0.5`
- `occlusion_culling_enabled=true`
- `occlusion_min_distance=6`
- `occlusion_max_distance=96`
- `visible_entity_recheck_ticks=5`
- `hidden_entity_recheck_ticks=15`
