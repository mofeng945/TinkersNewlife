# Tinker's Newlife 1.0.1.13

**Minecraft 1.20.1 · Forge 47.4.22+ · Tinkers' Construct 3.11.2+** (soft-requires Goety)

A visual-and-audio release for the **Unnameable** effect and for **Domains**: the old "simulated blindness" is gone, replaced by a full-screen signal-failure look — blown-up field of view, a sharpened/high-contrast/high-saturation image with **inverted colours**, an intermittent screen-tearing glitch overlay, and whispered words *and a whispered voice* while it lasts. Domains also got their own sound design.

---

## Highlights

### Unnameable No Longer Blinds You — It Corrupts Your Screen
- **Black fog and crushed view distance are gone.** The effect used to fake blindness (fog planes pulled to 1% and the fog colour painted black). It now does the opposite kind of violence to your perception:
- **Field of view is pushed up** (×1.4 by default) — the world stretches out around you.
- **Post-processing shader**: the image is **sharpened** (unsharp mask), then pushed to **higher contrast** and **higher saturation**, and finally **colour-inverted**. This is real per-pixel processing (a post-processing chain loaded through the vanilla `GameRenderer#loadEffect` path — the same mechanism vanilla uses for the spider/creeper spectator effects), not a translucent overlay.
- **It does not stay on**: the colour effect pulses — roughly **2–5 seconds on, then 0.5–1.5 seconds off**, randomly — so it reads as a signal that keeps dropping out instead of a filter glued to your screen. Set `post_effect_pulse = false` to hold it continuously.
- **It is always removed the moment the effect ends** — and on death/respawn, leaving the world, disconnecting or changing dimension the shader is force-removed, so inverted colours can never get stuck.

### Unnameable Now Tears the Screen Apart
- A full-screen **signal-interference overlay** while affected: horizontal **tearing bands** with a red/cyan chromatic split, **static noise**, a **rolling interference bar** sweeping downward, and irregular **white/dark flashes**, on top of the pre-existing camera sway.
- The pattern is deliberately **re-drawn only a few times per second** (instead of every frame) so it looks like a failing display rather than smooth snow, and the intensity scales with the effect's level.

### Unnameable Whispers To You
- Words flicker in and out at random spots on the screen while the effect is on — pale violet, sickly cyan and a washed-out blood pink. Each line is drawn **bold** and **stays put for about half a second** (0.5–0.9 s) before jumping somewhere else, while its brightness keeps flickering frame by frame (and occasionally drops out for a frame) — so it is actually legible, yet still reads as flickering whispers rather than as a HUD label.
- The whisper lines are **translatable** (keys `whisper.tinkersnewlife.*`), so resource packs and translations can replace every one of them — or add more lines.
- A **whispered voice track** (`tinkersnewlife:effect.whispers`, 30 s, looped) plays for as long as the effect lasts and **stops the instant it ends** — it is driven by a self-ticking sound instance, so expiry, `/effect clear`, death or a milk bucket all silence it within a tick.
- Both the whispers and the glitch now **fade in and out smoothly** (the intensity is interpolated every frame), so the effect creeps in and drains away instead of snapping on and off.
- Higher effect levels bring **more lines at once and brighter** ones; `whispers = false` turns the text off.

### Domains Now Have a Voice
- **Expanding a domain** plays two brand-new tracks **layered on top of each other** at the domain's centre — `tinkersnewlife:domain.base` (a low rumble) and `tinkersnewlife:domain.open` (the opening blast). Both are registered with a **64-block range** so everyone standing anywhere in a large domain (radius can exceed 40 blocks) actually hears it.
- **Closing a domain** (pressing the key, running out of curse power, getting sealed) plays the vanilla **fire-extinguishing** hiss, pitched down and boosted so it carries across the whole sphere — the technique burning itself out.
- **Having a domain broken** (smashing the barrier with a cursed tool, or losing the curse core that sustained it) plays the vanilla **glass-shattering** crash at the domain's centre.
- Domain sounds are positional (played at the sphere's centre on the `BLOCKS` / `PLAYERS` channels), so they fade with distance like any world sound — and the two expansion tracks are also listed as subtitles (「领域：展开」/「Domain: expands」).

### The Prison Realm Can Now Be Crafted
- A new **Curse Crafting Ritual** recipe for the **Prison Realm** (`tinkersnewlife:gourd_jail`):
  - **Core slot**: 1 × a **Modifier Crystal carrying Idle Transfiguration** (`tinkersnewlife:wu_wei`)
  - **Material slots (8)**: Obsidian · Gheloth Remains · Boundary Fragment · Soul Lantern · Curse Bottle · Seared Soul Glass · Crying Obsidian · Eye of Ender
  - **Curse cost**: **10000** (the ritual drains 10 per tick — about 50 seconds)
- The core is matched by **NBT**: only the crystal that actually carries Idle Transfiguration works (a blank crystal or a different technique will not match), so the ritual asks for a real technique crystal rather than "any crystal".
- The in-game guide entry for the Prison Realm now documents the recipe on its own page.

### Two More Curse Crafting Recipes: Playful Cloud & Inverted Spear of Heaven
- **Playful Cloud** (`tinkersnewlife:you_yun`) — core: a **Modifier Crystal carrying Cursed Energy Release**; materials: 2 × Netherite Ingot, 3 × Blessing of Nicholas, 2 × Chain; curse cost **8500** (~43 s).
- **Inverted Spear of Heaven** (`tinkersnewlife:tian_ni_huo`) — core: a **Modifier Crystal carrying Jacob's Ladder**; materials: Iron Ingot, Netherite Ingot, Stick, Soul Torch, String, Yellow King's Lingering Wind, Broken Durandal Blade; curse cost **11500** (~58 s).
- Both cores are matched **by NBT**, so only the crystal carrying that exact technique works. The in-game guide entries for both weapons now document their recipes.

### Boundary Fragments Are Ten Times Rarer
- Destroying a domain used to drop a **Boundary Fragment** from roughly **1 %** of its boundary blocks; that is now **1/1000 (0.1 %)** per block. Boundary blocks scale with the square of the radius (a radius-10 domain is about 1250 blocks, radius 20 about 5000), so a destroyed domain now yields roughly **1–5 fragments** instead of a dozen or more.
- The rate is configurable: `[domains] fragment_drop_denominator` (default `1000`; set `1` to make every boundary block drop one). The in-game guide and item tooltips were updated to match.

## Configuration
```toml
[unnameable]
  fov_multiplier = 1.4      # field of view multiplier (1.0 - 2.5)
  post_effect = true        # sharpen + contrast + saturation + INVERTED COLOURS
  post_effect_pulse = true  # keep the colour effect intermittent (recommended)
  glitch = true             # screen tearing / static / rolling bar / flashes
  glitch_intensity = 1.0    # 0 - 3
  whispers = true           # whispered text flickering across the screen
  whisper_sound = true      # looped whispered voice audio (AMBIENT sound channel)
  whisper_sound_volume = 1.0  # 0 - 2
```
Everything here is client-side and can be switched off individually; with all of them off the effect keeps only its gameplay penalties (hunger drain, slowness, weakness) and the camera sway.

## Notes
- The old blindness hooks (`RenderFog` / `ComputeFogColor`) were removed entirely — the fog is no longer touched.
- If you use another shader mod and see conflicts, turn `post_effect` off; the glitch overlay is independent of it.
