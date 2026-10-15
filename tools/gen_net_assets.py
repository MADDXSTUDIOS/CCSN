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

# Patch lead: the lead itself uses CC's own cable texture (computercraft:block/cable_side) so it
# matches CC's grey cable exactly, resource packs included. Ours are just the RJ45 plug and boot.
if os.path.exists(f'{BT}/patch_lead.png'): os.remove(f'{BT}/patch_lead.png')
boot = img()                                   # moulded strain-relief boot, a shade darker than CC cable
for x in range(16):
    for y in range(16): px(boot, x, y, noise((104, 108, 114), 3))
for y in range(0, 16, 2):
    for x in range(16): px(boot, x, y, noise((96, 100, 106), 2))   # grip ridges
boot.save(f'{BT}/patch_lead_boot.png')
# RJ45 plug: clear plastic body; gold contacts along the front of the top face (auto UV of the
# top face lands on cols 5..10, rows 12..13), coloured wires showing through behind them.
plug = img()
for x in range(16):
    for y in range(16): px(plug, x, y, noise((214, 224, 230), 3))
WIRES = [(230, 150, 60), (200, 120, 40), (90, 170, 90), (60, 110, 200)]
for i, x in enumerate(range(5, 11)):
    px(plug, x, 13, (222, 186, 74) if i % 2 == 0 else (176, 142, 52))   # gold contacts
    px(plug, x, 12, WIRES[i % 4])                                          # wires under the clear top
    px(plug, x, 11, tuple(min(255, c + 30) for c in WIRES[i % 4]))
for x in range(16): px(plug, x, 6, (190, 200, 206))                       # moulding line on the sides
plug.save(f'{BT}/rj45_plug.png')

# ---- Covers ----
STEEL = (158, 164, 172); STEEL_D = (126, 132, 140); STEEL_L = (186, 192, 200)
tray = img()                                          # galvanised steel rail / riser sheet
for x in range(16):
    for y in range(16):
        c = noise(STEEL, 4)
        if random.random() < 0.06: c = noise(STEEL_L, 3)   # zinc spangle
        px(tray, x, y, c)
for x in range(16): px(tray, x, 0, STEEL_L); px(tray, x, 15, STEEL_D)
tray.save(f'{BT}/cable_tray.png')
floor = img()                                         # ladder tray: rungs every 4 px, dark gaps between
for x in range(16):
    for y in range(16):
        rung = (x % 4) in (0, 1)
        px(floor, x, y, noise(STEEL if rung else (70, 74, 80), 3))
floor.save(f'{BT}/cable_tray_floor.png')
rod = img()                                           # threaded drop rod / strut
for x in range(16):
    for y in range(16): px(rod, x, y, noise((110, 114, 120) if y % 2 else (136, 140, 146), 2))
rod.save(f'{BT}/cable_rod.png')
trunk = img()                                         # white PVC office trunking with a lid seam
for x in range(16):
    for y in range(16): px(trunk, x, y, noise((228, 230, 226), 2))
for x in range(16): px(trunk, x, 3, (196, 198, 194)); px(trunk, x, 12, (196, 198, 194))
trunk.save(f'{BT}/cable_trunking.png')

# Item icons
ti = img()                                            # a short length of ladder tray
for x in range(2, 14):
    px(ti, x, 6, STEEL_L); px(ti, x, 11, STEEL_D)
    for y in range(7, 11): px(ti, x, y, STEEL if x % 3 == 0 else (60, 64, 70))
for y in range(4, 12): px(ti, 2, y, STEEL_L); px(ti, 13, y, STEEL_D)
ti.save(f'{IT}/cable_tray.png')
tk = img()                                            # a length of trunking, end on
rect(tk, 2, 4, 13, 11, (226, 228, 224)); rect(tk, 2, 4, 13, 4, (246, 248, 244)); rect(tk, 2, 11, 13, 11, (180, 182, 178))
rect(tk, 2, 6, 13, 6, (200, 202, 198)); rect(tk, 13, 4, 13, 11, (196, 198, 194))
tk.save(f'{IT}/cable_trunking.png')
wr = img()                                            # wrench
for i in range(9):
    x, y = 3 + i, 12 - i
    px(wr, x, y, (150, 156, 166)); px(wr, x + 1, y, (110, 116, 126))
for p in [(11, 1), (12, 1), (13, 2), (13, 3), (12, 4), (10, 2), (9, 3), (10, 3)]: px(wr, *p, (170, 176, 186))
px(wr, 11, 2, (0, 0, 0, 0)); px(wr, 12, 2, (0, 0, 0, 0)); px(wr, 12, 3, (0, 0, 0, 0))
rect(wr, 2, 12, 4, 14, (200, 60, 50)); rect(wr, 3, 11, 5, 13, (200, 60, 50))   # red grip
wr.save(f'{IT}/wrench.png')
for name in ('cable_tray', 'cable_trunking', 'wrench'):
    jdump({'parent': 'minecraft:item/handheld' if name == 'wrench' else 'minecraft:item/generated',
           'textures': {'layer0': f'ccsn:item/{name}'}}, f'{IM}/{name}.json')

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

# The cable is drawn in code from its block entity (CableBakedModel); this model only names the
# texture (used for the strands and break particles).
jdump({'textures': {'particle': 'ccsn:block/network_cable'}}, f'{BM}/network_cable.json')
jdump({'variants': {f'disguised={d}': {'model': 'ccsn:block/network_cable'} for d in ('false', 'true')}},
      f'{BS}/network_cable.json')
for old in ('network_cable_core', 'network_cable_arm'):
    if os.path.exists(f'{BM}/{old}.json'): os.remove(f'{BM}/{old}.json')

ROT = {'north': {}, 'south': {'y': 180}, 'east': {'y': 90}, 'west': {'y': 270},
       'up': {'x': 270}, 'down': {'x': 90}}
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
# Patch leads, modelled for a north-facing jack (plate on the south side, socket at z=14).
# Each lead model = plug boot in the socket + lead out to the block centre + arm to one side.
# CC's cable is 4 px (6..10), so the arms line up with it.
LEAD_ARM = {'front': ([6, 6, 0], [10, 10, 6]), 'west': ([0, 6, 6], [6, 10, 10]),
            'east': ([10, 6, 6], [16, 10, 10]), 'up': ([6, 10, 6], [10, 16, 10]),
            'down': ([6, 0, 6], [10, 6, 10])}
LEAD_TEX = {'lead': 'computercraft:block/cable_side', 'boot': 'ccsn:block/patch_lead_boot',
            'plug': 'ccsn:block/rj45_plug', 'particle': 'computercraft:block/cable_side'}
ALL = ['north', 'south', 'east', 'west', 'up', 'down']
def cube(f, t, tex, skip=()):
    return {'from': f, 'to': t, 'faces': {n: {'texture': tex} for n in ALL if n not in skip}}
for rel, (f, t) in LEAD_ARM.items():
    jdump({'textures': LEAD_TEX, 'elements': [
        # RJ45 plug pushed into the socket (plate front is z=14), latch tab underneath
        cube([5.5, 6, 11], [10.5, 9.5, 14], '#plug', skip=('north',)),
        cube([7, 5.25, 11.5], [9, 6, 13.5], '#plug', skip=('up',)),
        # moulded boot behind the plug, then the lead back to the middle of the block
        cube([5.75, 5.75, 9.5], [10.25, 10.25, 11], '#boot'),
        {'from': [6, 6, 6], 'to': [10, 10, 9.5], 'faces': {
            n: {'texture': '#lead', 'uv': [6, 6, 10, 10]} for n in ALL if n != 'south'}},
        cube(f, t, '#lead'),
    ]}, f'{BM}/wall_jack_lead_{rel}.json')

# Rotations as Minecraft applies them (x first, then y). North-frame side -> world side.
def rotate(d, r):
    for _ in range(r.get('x', 0) // 90):
        d = {'north': 'down', 'down': 'south', 'south': 'up', 'up': 'north'}.get(d, d)
    for _ in range(r.get('y', 0) // 90):
        d = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}.get(d, d)
    return d
REL_DIR = {'front': 'north', 'west': 'west', 'east': 'east', 'up': 'up', 'down': 'down'}
parts = []
for facing, r in ROT.items():
    assert rotate('north', r) == facing
    parts.append({'when': {'facing': facing}, 'apply': {'model': 'ccsn:block/wall_jack', **r}})
    for rel, d in REL_DIR.items():
        world = rotate(d, r)
        parts.append({'when': {'facing': facing, world: 'true'},
                      'apply': {'model': f'ccsn:block/wall_jack_lead_{rel}', **r}})
jdump({'multipart': parts}, f'{BS}/wall_jack.json')

# ================= loot =================
# Cable drops are worked out in code (one cable item per run), so no loot table.
if os.path.exists(f'{LOOT}/network_cable.json'): os.remove(f'{LOOT}/network_cable.json')
jdump({'type': 'minecraft:block', 'pools': [{
    'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
    'entries': [{'type': 'minecraft:item', 'name': 'ccsn:wall_jack'}]}]},
    f'{LOOT}/wall_jack.json')

# ================= names =================
lang_path = f'{ROOT}/lang/en_us.json'
lang = json.load(open(lang_path))
lang['block.ccsn.network_cable'] = 'Network Cable'
lang['block.ccsn.wall_jack'] = 'Wall Jack'
lang.update({
    'chat.ccsn.wall_jack.title': 'Wall Jack',
    'chat.ccsn.wall_jack.run': 'Cable run: %s',
    'chat.ccsn.wall_jack.run_through': 'Cable run: %s (through the wall)',
    'chat.ccsn.wall_jack.no_run': 'Not on a cable run. Put network cable directly behind it, or behind the wall it is on.',
    'chat.ccsn.wall_jack.plugged': 'Plugged in: %s',
    'chat.ccsn.wall_jack.unplugged': 'Nothing plugged in. Run CC cable or a wired modem into the front or sides.',
    'chat.ccsn.wall_jack.devices': 'Devices on the network (%s): %s',
    'chat.ccsn.wall_jack.no_devices': 'No devices on the network yet (right-click the modems on your devices).',
    'chat.ccsn.wall_jack.side.front': 'front',
    'chat.ccsn.wall_jack.side.north': 'north side', 'chat.ccsn.wall_jack.side.south': 'south side',
    'chat.ccsn.wall_jack.side.east': 'east side', 'chat.ccsn.wall_jack.side.west': 'west side',
    'chat.ccsn.wall_jack.side.up': 'top', 'chat.ccsn.wall_jack.side.down': 'bottom',
    'chat.ccsn.wall_jack.other_runs': 'Other runs here: %s (sneak + right-click the jack with a cable to switch)',
    'chat.ccsn.wall_jack.switched': 'Jack switched to the %s run',
    'chat.ccsn.wall_jack.no_such_run': 'There is no %s run behind this jack',
    'chat.ccsn.cable.full': 'This cable already carries %s runs',
    'chat.ccsn.cable.already_covered': 'This cable already has that cover',
    'chat.ccsn.wrench.no_cover': 'Nothing to take off this cable',
    'chat.ccsn.wrench.removed': 'Took off: %s',
    'chat.ccsn.wrench.status': 'Network cable. Runs: %s. Cover: %s (sneak + right-click with the wrench to take it off)',
    'chat.ccsn.wrench.cover_none': 'none',
    'item.ccsn.cable_tray': 'Cable Tray',
    'item.ccsn.cable_trunking': 'Cable Trunking',
    'item.ccsn.wrench': 'Network Wrench',
})
for c in COLOURS:
    lang[f'item.ccsn.network_cable_{c}'] = ' '.join(w.capitalize() for w in c.split('_')) + ' Network Cable'
jdump(lang, lang_path)
print('networking assets written')
