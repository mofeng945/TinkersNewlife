"""Audit FluidRegistrar buckets and the actual TCon/Mantle fluid display units.
Usage: python tools/tests/fluid_units.py [TConstruct.jar Mantle.jar]
No writes; the optional installed jars verify the unit values rather than trusting a wiki.
"""
import json
import re
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'common/src/main/resources'
DATA = RES / 'data'
ASSETS = RES / 'assets/tinkersnewlife'

def load(path):
    return json.loads(path.read_bytes())

def values(tag):
    result = {}
    for value in load(tag)['values']:
        name, required = (value, True) if isinstance(value, str) else (value['id'], value.get('required', True))
        assert name not in result, ('duplicate', tag, name)
        result[name] = required
    return result

for texture_path in ASSETS.glob('mantle/fluid_texture/*.json'):
    texture = load(texture_path)
    if 'camera' in texture:
        opacity = texture.get('camera_opacity')
        assert type(opacity) in (int, float) and 0 <= opacity <= 1, ('invalid camera_opacity', texture_path)

names = set()
for source in ROOT.glob('*/src/main/java/**/*Fluids.java'):
    names.update(re.findall(r'\bentry\(\s*"([a-z0-9_]+)"', source.read_text('utf8')))
assert {'molten_sapphire', 'crystal_essence', 'molten_pixie_silver'} <= names
bucket_tag = values(DATA / 'forge/tags/items/buckets.json')
for name in names:
    bucket = name + '_bucket'
    assert 'tinkersnewlife:' + bucket in bucket_tag, ('bucket not tagged', bucket)
    model = load(ASSETS / f'models/item/{bucket}.json')
    assert model['loader'] == 'tconstruct:fluid_container' and model['parent'] == 'forge:item/bucket_drip', bucket
    # Omitting fluid is valid: the baked item override uses FluidUtil.getFluidContained().
    assert model.get('fluid', 'tinkersnewlife:' + name + '_still') == 'tinkersnewlife:' + name + '_still', bucket

unit_files = {'metal': ('metals', [810, 90, 10]), 'gem_large': ('large_gems', [900, 100, 25]),
              'gem_small': ('small_gems', [400, 100, 25]), 'bottle': ('bottle', [1000, 250, 50])}
classes = {}
for category in unit_files:
    entries = values(DATA / f'tconstruct/tags/fluids/tooltips/{category}.json')
    for fluid, required in entries.items():
        assert not required, ('conditional fluid not optional', fluid)
        family, variant = fluid.removeprefix('tinkersnewlife:').rsplit('_', 1)
        assert family in names and variant in {'still', 'flowing'}, fluid
        assert fluid not in classes, ('ambiguous unit list', fluid)
        classes[fluid] = category
        assert 'tinkersnewlife:' + family + ('_still' if variant == 'flowing' else '_flowing') in entries

def category(name):
    return classes.get('tinkersnewlife:' + name + '_still', 'bucket/mB')
assert category('molten_pixie_silver') == category('molten_dark_metal') == 'metal'
assert category('molten_sapphire') == category('crystal_essence') == 'gem_large'
assert category('fire_blood') == category('liquid_lightning') == 'bottle'
assert category('curse_residue') == category('unholy_blood') == category('gheloth_blood') == 'bucket/mB'
assert values(DATA/'forge/tags/fluids/molten_sapphire.json') == {
    'tinkersnewlife:molten_sapphire_still': False, 'tinkersnewlife:molten_sapphire_flowing': False,
    '#tinkersinnovation:molten_sapphire': False}
if len(sys.argv) > 1:
    with ZipFile(sys.argv[1]) as tc, ZipFile(sys.argv[2]) as mantle:
        for tag, (unit_list, amounts) in unit_files.items():
            template = json.loads(tc.read(f'assets/tconstruct/mantle/fluid_tooltips/{unit_list}.json'))
            assert template['tag'] == 'tconstruct:tooltips/' + tag
            assert [u['needed'] for u in template['units']] == amounts
        assert json.loads(mantle.read('assets/mantle/mantle/fluid_tooltips/fallback.json'))['redirect'] == 'mantle:buckets'
for c in [*unit_files, 'bucket/mB']:
    print(f'{c}:', ', '.join(n for n in sorted(names) if category(n) == c))
print(f'OK: {len(names)} registered fluids, all buckets tagged and modeled, {len(classes)} unambiguous specialized fluid variants')
