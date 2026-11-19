# Tinker's Newlife 1.0.1.26

Minecraft 1.20.1 · Forge

## Sleepwake Church

- Added a rare structure that generates near villages in the Overworld and in the Great White Space. It appears in newly generated chunks.
- The church contains hidden loot chests and a resident Mo Mo merchant who stays at the altar and restocks normally.
- Find it with `/locate structure tinkersnewlife:sleepwake_church` in the Overworld or `/locate structure tinkersnewlife:sleepwake_church_white_space` in the Great White Space.

## Momo Ktulu

- Added a three-phase boss encounter with new attack patterns, weapon changes, and aerial charges. Combat tuning is still provisional.
- R'lyeh's Shadows now use distinct melee, ranged, and spellcasting behavior. Momo also updates her nearest-player target more frequently.
- Momo's death now leaves a lingering pollution field before boss loot drops. Fixed a case where the delayed loot could be skipped.
- Fixed head rotation accumulating across animation frames and a Starfall Arrow crash when its shooter lacks the Luck attribute.
- Improved synchronization and decay of Aeonian pollution and fixed cases where the Unnameable visuals fell out of sync.

## Heart and configuration

- Reworked Heart alignment rules by difficulty. Easy tracks animal kills, village defenders, and hostile factions. Normal adds village block, chest, and profession rules. Hard adds loot chest, follower, bed, and villager happiness rules.
- Restored `conscience.enabled` in the common configuration alongside the per-world `/gamerule doConscienceHeart`. Both switches must be enabled for the Heart system to run.
- Added `curse_affinity.blacklist` to exclude items from automatic Curse Affinity rolls. It accepts mod IDs, namespaces, full item IDs, and wildcards. Existing affinity is preserved.
- Translated the common configuration comments into Chinese, including generated range labels.
