# Tinker's Newlife 1.0.1.28 — Momo combat and guidebook update

Minecraft 1.20.1 · Forge · Changes since 1.0.1.27

## Momo's boss fight

- Momo takes on her merchant colors in phase two. In phase three, a prismatic glow spreads across her silhouette before two layered, counter-rotating hexagrams unfold behind her.
- Walking and running now have distinct poses. Bow draw, aim, and release can play over sideways steps or a retreat. Added animations for unarmed and armed strikes, Star Dream, phase transitions, aerial charges, shadow summoning, and death. Fixed the torso separating from the legs during deep leans.
- Momo now faces her target while strafing instead of aiming along her movement path. Aerial charges track the target during windup, then face the committed charge direction.
- Removed the extra 90-degree roll over Momo's death animation. A violet barrier and a hexagram on the ground mark the 16-block death field until the explosion 10 seconds later. The timing of pollution, the blast, and rewards is unchanged.

## Alignment and merchant favor

- Damage from boss Momo to a player is reduced by that player's alignment and actual merchant favor. Each score is clamped to 0–50 and contributes 0.6 percentage points per point, up to 30% each and **60% combined**. Negative scores do not add damage. For example, +20 alignment and +50 favor reduce damage by 42%.
- This replaces the Heart's usual incoming alignment modifier for that hit; the two do not stack. Disabling the Heart removes only the alignment contribution. The rule covers Momo's own attacks, spells, and Starfall arrows, not shadow attacks or unattributed pollution bursts. It does not reduce crowd-control duration.

## Starwell Arena and Patchouli

- Moved arena columns off the entrance axes, relocated the stairs to the crypt into side passages, and leveled the landing at the southern approach. Existing arenas are not rebuilt automatically.
- Both guidebook languages now cover Sleepwake Church, Starwell Arena, and the Momo boss fight, including summoning, vault rewards, phase cues, and damage reduction. Updated the Heart, Curse Affinity, and R'lyeh's Call entries, replacing the outdated alignment checklist with the current difficulty-based rules.
