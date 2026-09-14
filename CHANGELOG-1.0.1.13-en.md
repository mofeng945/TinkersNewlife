# Tinker's Newlife 1.0.1.13

**Minecraft 1.20.1 · Forge 47.4.22+ · Tinkers' Construct 3.11.2+** (soft-requires Goety)

A visual-overhaul release for the **Unnameable** effect: the old "simulated blindness" is gone, replaced by a full-screen signal-failure look — blown-up field of view, a sharpened/high-contrast/high-saturation image with **inverted colours**, and an intermittent screen-tearing glitch overlay.

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

## Configuration
```toml
[unnameable]
  fov_multiplier = 1.4      # field of view multiplier (1.0 - 2.5)
  post_effect = true        # sharpen + contrast + saturation + INVERTED COLOURS
  post_effect_pulse = true  # keep the colour effect intermittent (recommended)
  glitch = true             # screen tearing / static / rolling bar / flashes
  glitch_intensity = 1.0    # 0 - 3
```
Everything here is client-side and can be switched off individually; with all of them off the effect keeps only its gameplay penalties (hunger drain, slowness, weakness) and the camera sway.

## Notes
- The old blindness hooks (`RenderFog` / `ComputeFogColor`) were removed entirely — the fog is no longer touched.
- If you use another shader mod and see conflicts, turn `post_effect` off; the glitch overlay is independent of it.
