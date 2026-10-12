# Tinker's Newlife 1.0.1.16-hotfix7

## Removed

### Every remaining trace of the removed weapon is gone

The weapon itself was taken out of this pack earlier, but a few traces were still sitting in the
source tree. All of them are now gone:

* its three part textures (blade / handle / sheath) and the three item models that pointed at them,
* five entries in the part-texture generator: its three part textures plus the two entries for its
  removed blade and sheath parts,
* its language keys in both languages - the three item names, the five modifiers that only existed
  for it (with their descriptions and flavour text), the three recipe messages, the creative tab
  title and description and the three Tinker Station slot labels,
* the two "these mixins are disabled" comment members in the mixin config,
* and the 173 line-by-line comment markers that had been left behind in eight shared source files.

## Notes

Four mentions of SlashBlade were deliberately kept, because they are about the SlashBlade **mod**
rather than about this weapon:

* the inventory rendering optimisation mixin plus its two config entries (that optimisation is what
  you asked for back then - it makes SlashBlade items render cheaply in GUIs),
* the `@slashblade` entry in a mod-id list in the config,
* one comment in the soul lantern item that explains a bypass needed when a player holds a
  reforged_slashblade in the off hand,
* and one sentence in the Tang Hengdao's guide entry that describes its grey slash as
  "SlashBlade-like" - that is only a style reference; say the word and it goes too.

Restoring the weapon is still possible: the branch `feature/katana-slashblade` holds the complete
implementation from before it was removed.
