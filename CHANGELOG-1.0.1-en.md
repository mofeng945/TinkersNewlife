# Tinker's Newlife 1.0.1

**Minecraft 1.20.1 · Forge 47.4.22+ · Tinkers' Construct 3.11.2+**

The 1.0.1 release centers on the **Cursed Power Core (咒力核心) system** — the mod's largest update since its first public builds. The Curse Core has been rebuilt from a plain modular accessory into the heart of a full Jujutsu-style power system: curse power, techniques, domains, New Shadow skills, rituals and shikigami, all layered on top of the Tinkers' Construct tool pipeline.

---

## Highlights

### Cursed Power & the Curse Core
- **Reworked Curse Core** — now a single-part modular item crafted through a **multiblock ritual** (basin with molten metal + structure + experience, ~6 ingots of any compatible metal), instead of a station-made accessory. The consumed metal decides the core's material, stats and traits.
- **Curse Power (咒力)** — players now carry a real curse-power pool with its own HUD and sync packet. Capacity/regeneration scale with the core's *Curse Output* / *Curse Total* / *Curse Affinity*; power regenerates over time while a core is equipped.
- **Curse Affinity** — a player-wide modifier that drops randomly (0–50) on any wearable curio found in loot; it feeds every technique/domain formula.
- **Soul fallback** — when curse power runs out, the core can draw on Goety soul energy (1 curse = 3 souls) via the reflection bridge, so casting never hard-stops.
- **Curse Core traits** — *Curse Output*, *Curse Total* and material traits now actually participate in combat hooks (melee damage/hit) for techniques and domains.

### Techniques (术式)
- New **technique slot** on the Curse Core; cycle with one key and cast with another (HUD shows the selected technique), all logic centralized in `BaseTechnique` (cooldown/burnout, cost, targeting, cast).
- Techniques include: **Yu Chu Zi** (merged Cleave/Dismantle/Furnace arts), **Blood Manipulation** (composite Piercing Blood / Hundred Converging / Supernova), **Ratio Technique**, **Limitless** (Infinity / Blue / Red / Purple), **Reverse Cursed Technique**, **Ten Shadows** (+10 tamed shikigami with their own AI/rendering/riding), **Wu Wei (Idle Transfiguration)**, **Cursed Speech**, **Curse Spirit Manipulation**, **Sky / Lightning / Plant / Flame Manipulation**, **Puppet**, **Conjuration**, **Projection**, **Black Bird**, **Anti-Gravity Mechanism**, **Jacob's Ladder**, **Cursed Energy Release** and more.
- Techniques now come from **Ancient Curse Scrolls** (loot) and are **unlocked permanently**; everything is recorded in the Patchouli guide as you learn it.

### Domains (领域)
- **12 expansion domains** with a shared engine (`BaseDomain`): black sphere rendering, entry/exit blocking (invisible walls), domain clashes, per-second curse cost and open/close handling.
- Includes **Unlimited Void**, **Zuo Sha Bo Tu**, **Taizang Bianye**, **Zhen Yan Xiang Ai** (borrow every technique you have unlocked), **Qian He Ying Yi Ting**, **Tie Guan Gai Wei Shan**, **Zi Bi Yuan Dun Guo**, **Dang Yun Ping Xian**, **Shi Bao Yue Gong Dian**, **San Chong Ji Ku**, **Execution by Verdict** (with the Executioner's Sword) and the **Fu Mo Yu Chu Zi** domain.
- Domains can be resisted by the **New Shadow skills**: **Mikoshi Kago**, **Falling Blossom** and **New Shadow Style: Simple Domain**, all passive counters that activate when you are trapped in an enemy domain.

### Rituals & Heavenly Restrictions
- **Heavenly Restriction - Tyrant**: trade curse power for a pure physical body (speed/jump/attack multiplied, cannot wear a Curse Core, Black Flash locked to 0).
- **Heavenly Restriction - Curse**: trade away body stats for +200 Curse Affinity and boosted output/total while wearing a core.
- The two restrictions are mutually exclusive and persist through death; an in-game command clears them.
- **Curse Core ritual** rework with clearer three-stage titles and no chat spam.

### New Content Built on Tinkers' Construct
- **New materials**: **Cursed Metal** (Goety) and **Dark Metal** (Goety) — full melting/casting chains, fluids, part textures and palette-based material render info.
- **New modifiers**: **Corruption**, **Soul Mending** (repair with soul energy), **Soul Eater** (upgrade slot, boosts soul gain), **Spell Breaker** and **Magic Resist** (Dark Metal armor/tools).
- **Momo, the weapon merchant** (墨默) — a neutral full-moon merchant with trading GUI, hireable combat AI, shikigami/curse-speech integrations and Apollyon-tier boss support.
- **Cursed tools**: Inverted Spear of Heaven (pierces boss damage caps / obsidian pillars), Prison Realm (seals bosses), Executioner's Sword, Playful Cloud and the Durandal set.

### Quality of Life & Integration
- **Tool melting rework** — melt an entire Tinkers' tool in the **Foundry** to recover *all* of its material fluids at once (byproduct injection via `handleByproducts`), replacing the earlier bespoke melting block.
- **Corrected metal volumes** — everything now matches TConstruct's real units (1 ingot = 90 mb).
- **New config file** `config/mofengbaizhi/tinkersnewlife-common.toml`: toggles for each Elder God event, a master switch for Curse Core crafting/usage, and **per-technique / per-domain** damage/cost/radius scaling coefficients.
- **Patchouli guidebook** — the guide now ships in **both Chinese (zh_cn) and English (en_us)** with unified terminology; entries auto-hide when their source mod (Goety / Ice and Fire) is absent.
- **Cross-mod content is fully conditional** — recipes carry `forge:mod_loaded` conditions, creative-tab items are gated, and the guide uses `mod:` flags. The mod runs and plays fine with or without Goety, Ice and Fire or Iron's Spells.

### Fixes & Balance
- Countless combat/AI fixes across shikigami, riding, momo, curse speech, domains and the Iron's Spells / Goety bridges (no crashes when optional mods are missing).
- Burning/paralysis/stun immunity handling, boss damage-cap piercing, and server-authoritative persistence for all player progress (affinity, restrictions, techniques, tamed shikigami).

---

**Dependencies:** Tinkers' Construct (required), Mantle (required), Curios (recommended).
**Optional integration:** Goety, Ice and Fire, Iron's Spells 'n Spellbooks, Patchouli (guidebook).
