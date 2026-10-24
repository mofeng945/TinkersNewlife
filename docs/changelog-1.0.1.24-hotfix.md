# Tinker's Newlife 1.0.1.24-hotfix

This is a hotfix on top of 1.0.1.24 and contains exactly one change: **log spam**.

---

## Fix

### The log was flooded with `Tool stack constructed using non-modifiable tool` (Render thread, ~120 lines/second)
- **Symptom**: while the "Construct / blueprint" item-picker screen was open, the log spammed
  that warning at roughly 120 lines per second; crafting Tinker bullets or using Miner's Vision
  also produced occasional entries.
- **Cause**: the warning itself is printed by **TConstruct** (`ToolStack` warns when it is handed
  a Tinker tool item **without NBT**). This mod was the caller: `ConstructSelectScreen` created
  `new ItemStack(tinker tool)` **every frame** to draw the icon, and two Tinker tools were visible
  in the grid — so two warnings per frame, ~120/s.
- **Fixed**:
  - Construct screen: display stacks for Tinker items are now **initialised once and cached**
    (`ToolStack.createTool(..., MaterialNBT.EMPTY)` + `updateStack`), reused for rendering and tooltips.
  - Tinker bullets: the ammo item is now initialised via `createTool` instead of calling
    `ToolStack.from` on an empty stack.
  - Materialisation (`materializeTCon` / `materialsOf`) and Miner's Vision: check
    `ToolStack.isInitialized` first and treat empty stacks as "no materials / no modifier"
    (behaviour unchanged, the warning just no longer fires).
- **For players**: installing this build is enough — **no** TConstruct config change required.
  If you still want absolute silence, set `logInvalidToolStack` to `IGNORE` in
  `config/tconstruct-common.toml` (default is `WARNING`).

---

## Notes
- Version: `1.0.1.24-hotfix` (same baseline as `1.0.1.24`, only this fix differs).
- Please **replace** the old `tinkersnewlife-1.0.1.24.jar`; do not install both.
- Not yet verified in-game: opening the construct screen should no longer spam the log, and
  crafting Tinker bullets should no longer produce the warning.
