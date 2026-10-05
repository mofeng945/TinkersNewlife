# Tinker's Newlife 1.0.1.17

## Changed

### The slingshot is now built from one tool handle, one bow limb and one bowstring

The three parts are all standard Tinkers parts, so **nothing new has to be crafted or patterned**:
the part builder patterns and casts you already have are enough. (Tinkers' own javelin is made from
a tool handle plus a bow limb, and its fishing rod from a bow limb plus a bowstring - the slingshot
now sits in the same family.)

The Tinker Station layout was re-arranged into a diagonal, matching the requested order:

```
  a          a = bowstring   (22, 32)
     b       b = bow limb    (34, 44)
        c    c = tool handle (46, 56)
```

Slot labels were renamed accordingly (`bow limb`, `tool handle`, `bowstring`) in both English and
Chinese, and the guide book entry plus the group description now describe the new recipe.

## Does it still work as a ranged weapon?

Yes. Firing is implemented in code, not in the parts: the item is a modifiable bow that draws while
you hold right-click and spawns its own stone projectile entity on release, using the tool's
`velocity` / `projectile damage` stats - and those stats come from the **bow limb** material, which
the recipe still contains. Replacing the second bow limb with a tool handle therefore keeps the
slingshot a fully working ranged weapon; the handle contributes durability and attack-speed style
stats instead.

Two side effects worth knowing:

* With **one** bow limb instead of two, the bow stats contributed by materials are roughly halved,
  so a slingshot fires somewhat slower/weaker than the previous two-limb version at equal materials.
* Slingshots built **before** this update stored their materials against the old part order, so an
  old tool is re-interpreted with the new parts. Rebuild it (or give yourself a new one) if its
  stats look odd.