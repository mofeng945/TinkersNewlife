# Tinker's Newlife 1.0.1.16-hotfix2

A hotfix release on top of **1.0.1.16-hotfix**. Everything listed below is what has changed since
that build, so this one file covers the whole update.

## Fixed

### Critical - the Tinkers tags stopped loading

In 1.20.1 a tag fails to load **as a whole** if any of its entries points at an item that does not
exist, and that failure then cascades through every tag that references it. Leftover entries from a
removed weapon did exactly that, with two consequences:

* `tconstruct:modifiable/durability` - together with `aoe`, `bonus_slots`, `harvest`,
  `melee/sword`, `melee/primary`, `interactable/right`, `multipart` and `weapon` - failed to load, so
  tools from this mod (Tang Hengdao, rapier, spear, slingshot, yoyo and the rest) silently lost their
  durability / weapon / melee tags.
* Those failures cascaded up into `tconstruct:modifiable` itself, which dragged unrelated tags down
  with it (`twilightforest:banned_uncraftables`, `parry:excluded_shields`), and `tconstruct:casts/*`
  broke as well.

Every dangling reference has been removed.

### The slingshot - the upper limb was not drawn

A geometry piece that is not listed in a Tinkers tool model's `parts` list is not rendered at all. The
slingshot's model was only listing three of the bow's four pieces, so the upper limb was missing, and
the broken-state model still carried the old part indices (its upper limb pointed at the tool handle
and it had no grip entry, so a broken slingshot was drawn without a handle). Both models now list all
four pieces: both limbs map to the bow limb part (they share its material and keep their own
textures), the grip maps to the tool handle and the bowstring maps to the bowstring.

### The slingshot - stone shots shatter when they land

Fired stones no longer stick in the ground waiting to be picked up. The moment a shot lands it bursts
into block-break particles - the particles of the stone that was actually loaded, so a cobblestone
shot gives cobblestone chips - and then the projectile disappears. The stone was consumed when the
shot was fired either way, so nothing is lost: you just no longer have to walk over and pick it up.

## Changed

### The slingshot is now built from one tool handle, one bow limb and one bowstring

All three are standard Tinkers parts, so **nothing new has to be crafted or patterned** - the part
builder patterns and casts you already own are enough. (Tinkers' own javelin is a tool handle plus a
bow limb, and its fishing rod a bow limb plus a bowstring; the slingshot now sits in the same family.)

The Tinker Station layout was re-arranged into a diagonal, matching the requested order:

```
  a          a = bowstring   (22, 32)
     b       b = bow limb    (34, 44)
        c    c = tool handle (46, 56)
```

Slot labels were renamed accordingly (`bow limb`, `tool handle`, `bowstring`) in both languages, and
the guide book entry plus the group description describe the new recipe.

Please note two side effects: with **one** bow limb instead of two the bow stats contributed by
materials are roughly halved, so a slingshot fires a little slower and weaker than the two-limb
version at equal materials; and slingshots built before this update stored their materials against
the old part order, so an old tool should be rebuilt if its stats look odd.

## Notes

Tags are built when a world loads, so **restart the game after updating** for them to be rebuilt.
