# Tinker's Newlife 1.0.1.7

**Minecraft 1.20.1 · Forge 47.4.22+ · Tinkers' Construct 3.11.2+** (soft-requires Goety)

A servant-combat and Goety-integration release: Goety minions finally fight like real Tinkers' users, and the Soul Mender can now repair your Tinkers' gear.

---

## Highlights

### Goety Servants Fight with Tinkers' Weapons & Armor
- **Melee weapons** — right-click your own Goety servant while holding a Tinkers' melee weapon to equip it (old weapon drops, no drop chance, consumes one copy).
- **Bows & Crossbows** — ranged servants (RangedAttackMob skeletons) now wield Tinkers' bows *and* crossbows: they aim, charge and fire like vanilla, and **arrows inherit the weapon's modifiers and projectile damage** so modifier builds work at range.
- **Crossbow support fixed** — servant ranged AI previously recognized bows only; both launcher types are now handled.
- **Armor** — servants can wear Tinkers' armor (respecting Goety's wear rules); every armor trait/passive they wear actually works on them.

### Attack Modifiers Now Work on Any Weapon-Wielding Creature
- Hit-on-hit modifier effects (Dragonsteel, Dreadsteel, Cosmic, Infusion, Child of the Stars, Fus Ro Dah, Dark Metal breaker, etc.) trigger for **any living entity** holding a Tinkers' weapon, not just players — monsters and servants included. Player-only hooks (charms, corruption, Hastur, kill drops) stay player-gated.

### Dragonsteel Fire & Lightning Are No Longer Self-Destructive
- **Dragonflame (fire)** — no longer uses a raw explosion that hurts you when fighting point-blank; it now shows a visual burst and deals manual, precisely-targeted damage that **never hits the wielder**.
- **Dragonsteel Lightning** — the bolt is visual-only; the actual strike uses vanilla thunder-hit rules that **never damage the holder**.
- Both effects **no longer destroy dropped items** on the ground.

### Soul Mender Repairs Tinkers' Gear (Goety)
- The Goety **Soul Mender** now accepts Tinkers' tools *and* armor. Place a damaged tool into the mender and it **floats inside the block, spinning** (0.6× scale) while souls are drained from a **Cursed Cage directly below** (1 soul per repair, ~0.5s per point).
- Faithful to the original: **blue soul-flame particles** every second, the **fire-ambient sound**, and a **smoke burst** when the item is finished.
- Right-click = put first, take back otherwise (chat tells you why if it can't). The item is a no-collision display entity visible to **all players** and survives restarts; finished items pop out as pickable drops.

### Soul Repair Now Also Mends Servant Gear
- The **Soul Repair** modifier now scans your Goety servants' armor/weapons too, repairing their Tinkers' gear by spending **your** (the master's) souls — never repairs at full durability, never wastes souls.

## Fixes
- Fixed a server crash (ConcurrentModificationException) when a repair finished.
- Fixed "souls consumed but durability never repaired" on the Soul Mender.
- Servant armor/weapon passives now respect "works on any wearer" consistently (night vision, fire resistance, Star Child max-health, Cursed Adrenaline speed boost, etc.).
