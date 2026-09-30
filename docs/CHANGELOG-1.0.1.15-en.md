# Tinkers' Newlife 1.0.1.15 — Changelog

**Minecraft 1.20.1 · Forge · Tinkers' Construct 3.11.2.166**

## New: Tinkers' Spear

* Added a new modifiable weapon, the **Spear** (broad blade + two tough handles), ported from the 1.21.11 vanilla spear.
* **+1.5 blocks reach**; cannot mine blocks.
* **Right-click charge:** 12-tick wind-up, then damage scales with the closing speed between you and your target — the faster you close in, the harder it hits (0.1x minimum, no damage when you are not closing in). Knockback and horseback dismounts also scale with speed.
* **Left-click jab:** the mob you click takes a normal attack, and every other target inside your reach gets stabbed as well.
* The vanilla 1.21.11 charge animation was ported for third person, so the tip no longer points backwards while charging.
* Material-tinted part and broken textures are included for every material.

## Goety: Apostle rebalance

Patch file: `config/mofengbaizhi/tinkersnewlife-apostle.toml` — **enabled by default**.

* **Teleport shield** — the Apostle takes 60% less damage for 2 seconds after each teleport (configurable).
* **Magic arrows** — arrows fired by the Apostle deal magic damage.
* **Hell Cloud** — purges every beneficial effect from players standing inside it.
* **Obsidian pillars** — the Piglin Brutes they summon now wear a full set of netherite armor and carry a netherite axe.
* **Overworld regeneration** — in its second phase the Apostle regenerates 1% of its max health per second (ignores Goety's Smite anti-regen by default; both values are configurable).
* **Pillar cap raised from 4 to 6** (applies in every dimension).
* **Arrow + spell** — in its second phase the Apostle has a 30% chance to drop a Fire Blast Trap under its target whenever it fires.
* **Dual-title Apostles** — 10% of newly spawned Apostles get a **second** title (e.g. "Scorpion's Tail" + "Name of Glory"). The second title is guaranteed to actually take effect, not just show up in the name.
* **Raid wave** — in the Overworld, dropping the Apostle to 10% health summons a wave of Goety servant raiders that all recognise the Apostle as their master.
* **Apostle damage x1.5** (configurable, `damage_multiplier`).

## Boss fights: multiplayer support

* The mod now **detects how many players took part in a boss fight** — dealing damage to the boss counts (pets, summons and projectiles count for their owner), and so does being damaged by the boss.
* **Loot rolls equal the number of participants.** Bosses are detected with the `forge:bosses` tag, so modded bosses are covered automatically. Extra loot drops on the ground.
* **Kill-the-boss advancements are granted to every participant**, not just the killer.
* **Twilight Forest bosses:** their chest settlement now receives one loot roll per participant, and anything that does not fit inside the chest is dropped next to it — nothing is silently lost. Twilight Forest's own boss advancement triggers are mirrored to all participants as well.
* New `[boss_fight]` config section (master switch, rolls, advancements, safety cap).

## Fixes

* **Spear durability** — hits with the spear now actually consume durability (the vanilla `hurtEnemy` hook is a no-op for Tinkers' tools, so previously they consumed none at all).
* **Infinity** — damage below the threshold is blocked at no curse energy cost and without a shield break, as originally designed.
