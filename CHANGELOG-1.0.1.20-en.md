# Tinker's Newlife 1.0.1.20

## Fixed

### The slingshot's upper limb was not drawn

When the slingshot's parts were changed to one tool handle + one bow limb + one bowstring, the item
model was only told about three of the bow's four geometry pieces. A geometry piece that is not
listed in a Tinkers tool model's `parts` list **is not rendered at all**, so the upper limb was
simply missing from the item.

* The model now lists all four pieces: `limb_bottom` and `limb_top` both map to the bow limb part
  (there is only one limb part, so both limbs share its material and keep their own textures),
  `grip` maps to the tool handle and `bowstring` maps to the bowstring.
* On top of that, the broken model still carried the old indices: its upper limb pointed at the tool
  handle and it had no `grip` entry at all, so a broken slingshot was drawn without its handle. Both
  models now use the same mapping.

## Notes

Nothing else was changed. If a slingshot still looks wrong after updating, please say which piece is
missing - the mapping table in the guide is now: limb_bottom / limb_top -> bow limb, grip -> tool
handle, bowstring -> bowstring.
