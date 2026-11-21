"""Check that Momo's derived rig stays in sync with the artist's source and that
new clips cannot move the torso away from the hip/shoulder attachment points.
Run from any directory with Python 3. No game files or external packages needed.
"""
import json
from pathlib import Path

assets = Path(__file__).resolve().parents[2] / 'cursed/src/main/resources/assets/tinkersnewlife'
def bones(name):
    model = json.loads((assets / 'geo' / name).read_bytes())['minecraft:geometry'][0]
    return {bone['name']: bone for bone in model['bones']}

source = bones('ktulu_momo.geo.json')
rig = bones('ktulu_momo_rigged.geo.json')
assert rig.pop('hip_root') == {'name': 'hip_root', 'pivot': [0, 12, 0]}
roots = {name for name, bone in source.items() if 'parent' not in bone}
assert roots == {'body', 'head', 'rightArm2', 'leftArm2', 'rightLeg2', 'leftLeg2'}
for name in roots:
    assert rig[name].pop('parent') == 'hip_root'
assert rig == source, 'mesh, UVs, joint pivots or distal hierarchy diverged'
count = 0
for file in (assets / 'animations').glob('ktulu_momo*.animation.json'):
    clips = json.loads(file.read_bytes())['animations']
    for name, clip in clips.items():
        tracks = clip['bones']
        assert set(tracks) <= set(source) | {'hip_root'}, name
        if file.name != 'ktulu_momo.animation.json':
            assert 'rotation' not in tracks.get('body', {}), name
            assert all('position' not in tracks.get(root, {}) for root in roots), name
        count += 1
print(f'{count} clips: bone references valid; shared transforms on all new clips; source mesh unchanged')
