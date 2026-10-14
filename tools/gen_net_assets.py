"""Generates the v0.3 networking assets: network cable, wall jack.
Textures, models, blockstates, loot tables and English names.
Run from the repo root: python3 tools/gen_net_assets.py"""
import json, os, random
from PIL import Image

ROOT = 'src/main/resources/assets/ccsn'
DATA = 'src/main/resources/data/ccsn'
BT = f'{ROOT}/textures/block'; IT = f'{ROOT}/textures/item'
BM = f'{ROOT}/models/block'; IM = f'{ROOT}/models/item'; BS = f'{ROOT}/blockstates'
LOOT = f'{DATA}/loot_table/blocks'
for d in (BT, IT, BM, IM, BS, LOOT): os.makedirs(d, exist_ok=True)
random.seed(11)

COLOURS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
           'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']

def img(w=16, h=16, c=(0, 0, 0, 0)): return Image.new('RGBA', (w, h), c if len(c) == 4 else c + (255,))
def px(im, x, y, c): im.putpixel((x, y), c if len(c) == 4 else c + (255,))
def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1): px(im, x, y, c)
def grey(v): return (v, v, v)
def noise(c, a=3): return tuple(max(0, min(255, v + random.randint(-a, a))) for v in c)
def jdump(obj, path):
    with open(path, 'w') as f: json.dump(obj, f, indent=2); f.write('\n')

# ================= textures =================
# Cable: light greyscale (tinted in game) with a diagonal twist so runs read as cable, not plastic.
cable = img()
for x in range(16):
    for y in range(16):
        k = (x + y) % 4
        v = 168 if k == 0 else (248 if k == 2 else 222)
        px(cable, x, y, noise(grey(v), 3))
cable.save(f'{BT}/network_cable.png')

# Wall jack faceplate. Plate occupies uv [4,3,12,13]: cols 4..11, rows 3..12 (row 3 = top).
PLATE = (228, 228, 222); HI = (246, 246, 242); LO = (188, 188, 182)
HOLE = (24, 25, 29); PIN = (205, 172, 70); SCREW = (160, 160, 156)
jack = img(); rect(jack, 0, 0, 15, 15, PLATE)
for x in range(4, 12):
    for y in range(3, 13): px(jack, x, y, noise(PLATE, 2))
rect(jack, 4, 3, 11, 3, HI); rect(jack, 4, 3, 4, 12, HI)          # top/left bevel
rect(jack, 4, 12, 11, 12, LO); rect(jack, 11, 4, 11, 12, LO)       # bottom/right bevel
rect(jack, 6, 7, 9, 9, HOLE)                                       # RJ45 socket
for x in (6, 7, 8, 9): px(jack, x, 7, PIN if x % 2 == 0 else (150, 125, 50))  # contacts
rect(jack, 7, 10, 8, 10, HOLE)                                     # latch notch
px(jack, 7, 11, SCREW); px(jack, 8, 11, LO)                         # screw
jack.save(f'{BT}/wall_jack.png')

side = img()
for x in range(16):
    for y in range(16): px(side, x, y, noise(PLATE if y < 8 else (212, 212, 206), 2))
side.save(f'{BT}/wall_jack_side.png')

label = img()
for x in range(16):
    for y in range(16): px(label, x, y, noise(grey(236), 2))
label.save(f'{BT}/wall_jack_label.png')

# Cable item: a loose coil with a plug on the end, greyscale for tinting.
coil = img()
cx, cy = 7.5, 7.0
for x in range(16):
    for y in range(16):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if 3.6 <= d <= 6.2:
            shade = 245 if (x + y) % 4 == 2 else (200 if (x + y) % 4 == 0 else 225)
            if y > cy + 2: shade -= 30          # underside in shadow
            px(coil, x, y, grey(shade))
for y in range(3, 7):                       # inner loop to read as a coil, not a ring
    px(coil, 9, y + 3, grey(190)); px(coil, 10, y + 3, grey(215))
rect(coil, 11, 12, 13, 14, (64, 66, 72))     # plug body (untinted dark... tinted slightly, still reads dark)
rect(coil, 14, 13, 14, 13, (190, 160, 70))   # contacts
coil.save(f'{IT}/network_cable.png')

# ================= models =================
A, B = 6.5, 9.5          # 3 px strand
def faces(names, tex='#cable', tint=True):
    out = {}
    for n in names:
        f = {'texture': tex}
        if tint: f['tintindex'] = 0
        out[n] = f
    return out

jdump({'textures': {'cable': 'ccsn:block/network_cable', 'particle': 'ccsn:block/network_cable'},
       'elements': [{'from': [A, A, A], 'to': [B, B, B],
                     'faces': faces(['north', 'south', 'east', 'west', 'up', 'down'])}]},
      f'{BM}/network_cable_core.json')
jdump({'textures': {'cable': 'ccsn:block/network_cable', 'particle': 'ccsn:block/network_cable'},
       'elements': [{'from': [A, A, 0], 'to': [B, B, A],
                     'faces': faces(['north', 'east', 'west', 'up', 'down'])}]},
      f'{BM}/network_cable_arm.json')

ROT = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270},
       'up': {'x': 270}, 'down': {'x': 90}}
multipart = [{'apply': {'model': 'ccsn:block/network_cable_core'}}]
for d, r in ROT.items():
    multipart.append({'when': {d: 'true'}, 'apply': {'model': 'ccsn:block/network_cable_arm', **r}})
jdump({'multipart': multipart}, f'{BS}/network_cable.json')

for c in COLOURS:
    jdump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'ccsn:item/network_cable'}},
          f'{IM}/network_cable_{c}.json')

def jack_elements(z0):
    """Plate is 2 px deep starting at z0; the front faces north (-z)."""
    plate = {'from': [4, 3, z0], 'to': [12, 13, z0 + 2], 'faces': {
        'north': {'texture': '#front', 'uv': [4, 3, 12, 13]},
        'south': {'texture': '#side', 'uv': [4, 3, 12, 13]},
        'east': {'texture': '#side', 'uv': [0, 3, 2, 13]},
        'west': {'texture': '#side', 'uv': [0, 3, 2, 13]},
        'up': {'texture': '#side', 'uv': [4, 0, 12, 2]},
        'down': {'texture': '#side', 'uv': [4, 0, 12, 2]}}}
    # Coloured label strip above the socket: shows which cable run the jack is on.
    lab = {'from': [5, 11, z0 - 0.25], 'to': [11, 12, z0], 'faces': {
        n: {'texture': '#label', 'tintindex': 0} for n in ['north', 'east', 'west', 'up', 'down']}}
    return [plate, lab]

JACK_TEX = {'front': 'ccsn:block/wall_jack', 'side': 'ccsn:block/wall_jack_side',
            'label': 'ccsn:block/wall_jack_label', 'particle': 'ccsn:block/wall_jack_side'}
jdump({'textures': JACK_TEX, 'elements': jack_elements(14)}, f'{BM}/wall_jack.json')
jdump({'parent': 'minecraft:block/block', 'textures': JACK_TEX, 'elements': jack_elements(7)},
      f'{IM}/wall_jack.json')
jdump({'variants': {f'facing={d}': {'model': 'ccsn:block/wall_jack', **r} for d, r in ROT.items()}},
      f'{BS}/wall_jack.json')

# ================= loot =================
jdump({'type': 'minecraft:block', 'pools': [{
    'rolls': 1,
    'conditions': [{'condition': 'minecraft:survives_explosion'}],
    'entries': [{'type': 'minecraft:item', 'name': f'ccsn:network_cable_{c}', 'conditions': [{
        'condition': 'minecraft:block_state_property', 'block': 'ccsn:network_cable',
        'properties': {'color': c}}]} for c in COLOURS]}]},
    f'{LOOT}/network_cable.json')
jdump({'type': 'minecraft:block', 'pools': [{
    'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
    'entries': [{'type': 'minecraft:item', 'name': 'ccsn:wall_jack'}]}]},
    f'{LOOT}/wall_jack.json')

# ================= names =================
lang_path = f'{ROOT}/lang/en_us.json'
lang = json.load(open(lang_path))
lang['block.ccsn.network_cable'] = 'Network Cable'
lang['block.ccsn.wall_jack'] = 'Wall Jack'
for c in COLOURS:
    lang[f'item.ccsn.network_cable_{c}'] = ' '.join(w.capitalize() for w in c.split('_')) + ' Network Cable'
jdump(lang, lang_path)
print('networking assets written')
