# Tinker's Newlife 1.0.1.17

## Added

### The whip

A new Tinkers weapon built from a tough handle, a large plate and a bowstring. It is not a club: the
whip is a simulated rope (57 points) whose root is pinned to a hand that really swings, and the damage
comes from the rope sections sweeping through a target fast enough.

* **Left click lashes.** The swing itself deals **no melee damage at all** - what hurts is the lash.
  A lash takes about 7 ticks to script the hand through its arc and then lets the rope fly free for
  another 32 ticks; that flying section is what throws the whip and it is where the damage window sits.
  Damage scales with the whip's attack damage, and every target already hit by the same lash takes half
  of what the previous one took.
* **The reach is about 10 blocks.** The rope's rest lengths are scaled up from the reference model
  (0.19 -> 0.44 per segment, total ~10.2 blocks) and the hand arc is correspondingly bigger.
* **Attack speed decides how often, not how hard.** The drive is fixed at the reference's 3 tick windup
  plus 4 tick stroke, so a fast whip and a slow whip throw the rope exactly the same way; attack speed
  only sets the cooldown between lashes. (Stretching the drive with attack speed - an earlier idea - made
  slow whips unable to reach at all, so it is gone.)
* **The tip cracks** because the mass taper makes it 1/15 of the root, and the per-particle speed cap
  rises from 82 to 190 blocks per second along the length.

### The guard (right click)

Right click **retracts the whip** if a lash is still out (about eight ticks back into your hand) and then
**raises a guard** for as long as you hold the button. The guard shows the **shield** pose in third person
and the whip is **raised toward your view in first person** as well.

* **Perfect block** - a hit landing within **0.5 seconds** after you raise the guard, or pressing right
  click within 0.5 seconds *after* being hit, cancels the damage entirely, snaps the whip out and throws
  the **whole** damage back at the attacker. The guard then drops immediately, you must **release right
  click before you can raise it again**, and it goes on a **2 second** cooldown.
* **Normal block** - any other blocked hit is only reduced by **40 percent**, and releasing the button
  puts the guard on a **1 second** cooldown.
* **Blocking costs durability**, using the shield formula (`1 + floor(damage)`), on a perfect block and a
  normal one alike. Swinging the whip costs 1 durability per lash.

### Whip marks

Every time the whip connects with a creature it gains a stack of **Whip Weaken**: -10 percent movement
speed and -10 percent attack damage per stack, up to **8 stacks (-80 percent)**. It lasts 10 seconds and
every new lash refreshes it, so a target you keep lashing gets slower and hits softer while one you leave
alone recovers. It is a real status effect, so it works on AI mobs, players and summons alike and shows
up in the HUD and through `/attribute`.

### Supervisor, a whip-only modifier

The whip can be given **Supervisor** (Dragon's Breath + Golden Apple + Nicholas' Blessing). Only the whip
can take it.

With it the lash deals no damage at all and does managing instead:

* Lashing anything of yours - any mob whose owner is you (pets, mounts and the like), plus Goety servants,
  your ring-of-one-mind partner, and their pets as well - stacks **Strength and Speed** on it, one level
  per lash up to **level V**, lasting 20 seconds and refreshed by every lash.
* Lashing a **villager** has a 5 percent chance to restock it on the spot, for **half** the usual amount,
  at most **3 times per villager per day** (counted on the villager itself, independent of the vanilla
  two-restocks-a-day rule).

## Changed

### The whip cannot mine

Its mining speed is 0 - both as a base value and as a 0 multiplier, so the contribution the large plate
would otherwise make from its material is multiplied away too. It was never in a harvest tag in the first
place; it carries only the weapon tags.

### Slingshot

* The recipe is now **one tool handle + one bow limb + one bowstring**, with the matching three-slot
  station layout.
* Fired stones **shatter where they land** - 24 block-break particles of whatever was loaded, then the
  projectile is gone. The stone was consumed on firing either way, so nothing is lost; you just no longer
  have to pick the shot back up. Shots that hit a creature still fall like vanilla arrows and shatter when
  that fall ends.

### Tang Hengdao and Dreadsteel sword qi

The arc is a flat fan-shaped mesh again, and it is oriented by the caster instead of by preset angles:

* The **circle's centre sits on the caster**, so the arc is concave towards you and convex away - the
  blade light carries the caster's position so every client draws it the same way.
* The arc's plane is **tilted about 20 degrees** off the straight-at-the-target direction. A plane that
  contains both you and the arc is edge-on when you look straight at it, which is what used to degenerate
  into a thin line; the tilt keeps it readable without aiming it away.
* Each stage now **rotates the whole arc plane** about the player-to-target axis, so the four-hit combo
  fans out around the target instead of drawing four arcs on top of each other. Mirroring, band width and
  the golden-angle offsets still tell the stages apart.

## Fixed

### Broken-state textures for the whip

New `handle_broken` / `plate_broken` / `bowstring_broken` part textures, derived from the hand-drawn part
art (60 percent darker, two cracked pixels mid-span, a small chip off each end, and only pixels that
already exist are touched so the silhouette never gains stray pixels). The broken model points at them
again, so a whip at zero durability shows a chipped whip rather than missing textures.

### Dangling tag entries that were breaking whole Tinkers tags

The removed weapon (the SlashBlade-style katana) had been taken out of the mod, but references to its
items were still sitting in this mod's tag files. In 1.20.1 a tag fails to load **as a whole** if any of
its entries points at an item that does not exist, and that failure cascades along tag dependencies - in
practice `tconstruct:modifiable/durability` and, through it, `aoe`, `bonus_slots`, `harvest`,
`melee/sword`, `melee/primary`, `interactable/right`, `multipart` and `weapon` failed to load, so this
mod's tools silently lost their durability, weapon and melee tags. All of it is cleaned up now: the
leftover entries, the removed cast items and the references to deleted tags are gone, all 184 tag files
parse, and a cross-check of every `tinkersnewlife:` reference against the ids this mod registers comes
back empty.

### The slingshot's upper limb was not drawn

When the slingshot's parts changed to one tool handle + one bow limb + one bowstring, the item model was
only told about three of the bow's four geometry pieces - and a piece that is not listed in a Tinkers
tool model's `parts` list is not rendered at all, so the upper limb was missing. Both the normal and the
broken model now list all four: `limb_bottom` and `limb_top` both map to the bow limb, `grip` to the tool
handle and `bowstring` to the bowstring.

## Removed

* Every remaining trace of the removed weapon - its part textures and item models, its part-texture
  generator entries, its language keys (item names, its five exclusive modifiers, recipe messages,
  creative tab text and station slot labels), its mixin config comments and 173 leftover comment markers
  in eight shared source files. The four mentions that are about the SlashBlade **mod** rather than that
  weapon were deliberately kept.

## Notes

* The whip is a port of **Better Whips** (MIT, my2167592261-cell): the rope, the anchors, the drives, the
  constraint family and the damage formula are ports; the entity, the item, the renderer and the contact
  loop are this mod's own. See `LICENSES/BetterWhips-MIT.txt`, which also ships inside the jar.
* Known simplifications on the whip: block collision is per-point depenetration rather than swept
  capsules, the idle coil flourish is not implemented, and the arm is not posed by a renderer mixin.
* Tags are rebuilt when a world loads, so restart the game after updating.

## Added (2026-10-06, sixteenth pass - version unchanged)

### Severing recipes for every linked mod's mobs

Tinkers' Severing does not duplicate a mob's normal drops - it only drops what a severing recipe names
explicitly. Since the crossover mobs had no such recipes at all, they now have them: **454 recipes**
covering **goety (175), twilightforest (69), aquaculture (60), iceandfire (55), vampirism (47),
irons_spellbooks (30) and lavafishing (18)**, so a severing tool now also yields what those mobs normally
drop. Each recipe is gated on `forge:mod_loaded`, so packs without that mod simply skip it, and the whole
set is generated by `tools/gen-severing-recipes.ps1` (reads each mod's entity loot tables, validates the
entity id against the mod's own language file, and can be re-run with more mod ids added).