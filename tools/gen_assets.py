"""Generates CC: Servers & Networking textures, models and blockstates.
Run from the repo root: python3 tools/gen_assets.py"""
import json, os, random
from PIL import Image

ROOT = 'src/main/resources/assets/ccsn'
BT = f'{ROOT}/textures/block'; IT = f'{ROOT}/textures/item'; GT = f'{ROOT}/textures/gui'
BM = f'{ROOT}/models/block'; IM = f'{ROOT}/models/item'; BS = f'{ROOT}/blockstates'
for d in (BT, IT, GT, BM, IM, BS): os.makedirs(d, exist_ok=True)
random.seed(7)

def img(w=16, h=16, c=(0, 0, 0, 0)): return Image.new('RGBA', (w, h), c if len(c) == 4 else c + (255,))
def px(im, x, y, c): im.putpixel((x, y), c if len(c) == 4 else c + (255,))
def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1): px(im, x, y, c)
def noise(c, a=4): return tuple(max(0, min(255, v + random.randint(-a, a))) for v in c)
def fill_noise(im, c, a=3, box=None):
    x0, y0, x1, y1 = box or (0, 0, im.width - 1, im.height - 1)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1): px(im, x, y, noise(c, a))
def save(im, path): im.save(path)
def jdump(obj, path):
    with open(path, 'w') as f: json.dump(obj, f, indent=2); f.write('\n')

# ---- palette ----
BODY = (46, 50, 58); BODYL = (60, 65, 74); EDGE = (86, 92, 103); EDGED = (26, 28, 33)
FRAME = (72, 78, 89); FRAMED = (52, 56, 64); VENT = (22, 24, 28); DARK = (12, 13, 15)
LED = (70, 230, 100); LEDD = (24, 90, 40); BLUE = (80, 170, 255); SCREW = (125, 131, 141)
CAD = (32, 34, 40); CADL = (58, 62, 71); LATCH = (66, 70, 80)
TIER = {1: (205, 208, 214), 2: (250, 200, 50), 3: (80, 220, 215)}
TIERD = {1: (130, 134, 140), 2: (170, 125, 20), 3: (30, 140, 150)}

# ================= NAS block textures =================
side = img(); fill_noise(side, BODY)
rect(side, 0, 0, 15, 0, EDGE); rect(side, 0, 15, 15, 15, EDGED)
for y in range(3, 13, 2): rect(side, 3, y, 12, y, VENT); rect(side, 3, y + 1, 12, y + 1, BODYL)
for p in [(1, 2), (14, 2), (1, 13), (14, 13)]: px(side, *p, SCREW)
save(side, f'{BT}/nas_side.png')

top = img(); fill_noise(top, BODYL)
rect(top, 0, 0, 15, 0, EDGE); rect(top, 0, 15, 15, 15, EDGED); rect(top, 0, 0, 0, 15, EDGE); rect(top, 15, 0, 15, 15, EDGED)
for p in [(2, 2), (13, 2), (2, 13), (13, 13)]: px(top, *p, SCREW)
for x in range(4, 12): px(top, x, 6, VENT); px(top, x, 9, VENT)
save(top, f'{BT}/nas_top.png')

back = img(); fill_noise(back, BODY)
rect(back, 0, 0, 15, 0, EDGE); rect(back, 0, 15, 15, 15, EDGED)
rect(back, 3, 3, 12, 6, VENT)                             # fan grille
for x in range(4, 12, 2): rect(back, x, 4, x, 5, BODYL)
for i, x in enumerate([3, 6, 9]): rect(back, x, 10, x + 1, 11, VENT); px(back, x, 9, LED if i == 0 else LEDD)  # ports
rect(back, 12, 10, 13, 12, (40, 40, 44)); px(back, 12, 10, (200, 60, 50))   # power socket
save(back, f'{BT}/nas_back.png')

# front bezel: edge columns, status strip (rows 0-2), bottom rail (row 15)
front = img(); fill_noise(front, FRAME)
rect(front, 0, 0, 0, 15, EDGE); rect(front, 15, 0, 15, 15, EDGED)
rect(front, 1, 0, 14, 0, EDGE); rect(front, 1, 15, 14, 15, EDGED)
rect(front, 1, 1, 14, 2, BODY)
px(front, 13, 1, BLUE); px(front, 11, 1, LED)            # (u is mirrored on north faces: right side = low x)
for x in [2, 4, 6]: rect(front, x, 1, x, 2, VENT)
save(front, f'{BT}/nas_front.png')

frame = img(); fill_noise(frame, FRAMED, 2); save(frame, f'{BT}/nas_frame.png')
bayb = img(); fill_noise(bayb, DARK, 2)
for y0 in (3, 6, 9, 12): rect(bayb, 6, y0 + 1, 9, y0 + 1, (22, 23, 27))   # faint SATA sockets
save(bayb, f'{BT}/nas_bay_back.png')

edge = img(); fill_noise(edge, CAD, 2); rect(edge, 0, 0, 15, 0, CADL); save(edge, f'{BT}/nas_caddy_edge.png')
latch = img(); fill_noise(latch, LATCH, 2); rect(latch, 0, 0, 15, 0, (92, 97, 108)); save(latch, f'{BT}/nas_latch.png')

# caddy face, animated LED. Face uses uv [1,1,14,3]: cols 1..13, rows 1..2. Col 1 = viewer's left.
def caddy_frame(t, led_on):
    im = img(); fill_noise(im, CAD, 2)
    rect(im, 1, 1, 13, 1, CADL); rect(im, 1, 2, 13, 2, CAD)
    px(im, 1, 1, TIER[t]); px(im, 1, 2, TIERD[t])        # tier tab
    for x in (5, 7, 9): px(im, x, 2, VENT)               # vent holes
    px(im, 12, 1, LED if led_on == 2 else (LEDD if led_on == 0 else (50, 170, 80)))
    px(im, 12, 2, LEDD)
    return im
# sheet frames: 0 = on, 1 = dim, 2 = off. Resting state is on, with the odd activity flicker.
blink = {1: [0, 0, 0, 2, 0, 0, 0, 0, 1, 0], 2: [0, 2, 0, 0, 0, 0, 1, 0, 0, 0], 3: [0, 0, 1, 0, 2, 0, 0, 0, 0, 2]}
for t in (1, 2, 3):
    states = [2, 1, 0]
    sheet = img(16, 48)
    for i, s in enumerate(states): sheet.paste(caddy_frame(t, s), (0, i * 16))
    save(sheet, f'{BT}/nas_caddy_t{t}.png')
    jdump({'animation': {'frametime': 3, 'interpolate': False, 'frames': blink[t]}}, f'{BT}/nas_caddy_t{t}.png.mcmeta')

for old in ('nas_front.png',):
    pass

# ================= NAS block models =================
F = lambda tex, **kw: dict(texture=tex, **kw)
base = {
    'parent': 'minecraft:block/block',
    'textures': {'particle': 'ccsn:block/nas_side', 'side': 'ccsn:block/nas_side', 'top': 'ccsn:block/nas_top',
                 'back': 'ccsn:block/nas_back', 'front': 'ccsn:block/nas_front', 'frame': 'ccsn:block/nas_frame',
                 'bay_back': 'ccsn:block/nas_bay_back'},
    'elements': [
        {'name': 'left wall', 'from': [0, 0, 0], 'to': [1, 16, 16], 'faces': {
            'north': F('#front', cullface='north'), 'west': F('#side', cullface='west'), 'south': F('#back', cullface='south'),
            'up': F('#top', cullface='up'), 'down': F('#top', cullface='down'), 'east': F('#frame')}},
        {'name': 'right wall', 'from': [15, 0, 0], 'to': [16, 16, 16], 'faces': {
            'north': F('#front', cullface='north'), 'east': F('#side', cullface='east'), 'south': F('#back', cullface='south'),
            'up': F('#top', cullface='up'), 'down': F('#top', cullface='down'), 'west': F('#frame')}},
        {'name': 'top band', 'from': [1, 13, 0], 'to': [15, 16, 16], 'faces': {
            'north': F('#front', cullface='north'), 'up': F('#top', cullface='up'), 'south': F('#back', cullface='south'),
            'down': F('#frame')}},
        {'name': 'bottom rail', 'from': [1, 0, 0], 'to': [15, 1, 16], 'faces': {
            'north': F('#front', cullface='north'), 'down': F('#top', cullface='down'), 'south': F('#back', cullface='south'),
            'up': F('#frame')}},
        {'name': 'bay back', 'from': [1, 1, 4], 'to': [15, 13, 16], 'faces': {
            'north': F('#bay_back'), 'south': F('#back', cullface='south')}},
    ] + [
        {'name': f'shelf {y}', 'from': [1, y - 0.25, 0.5], 'to': [15, y + 0.25, 4], 'faces': {
            'north': F('#frame'), 'up': F('#frame'), 'down': F('#frame')}} for y in (4, 7, 10)
    ],
}
jdump(base, f'{BM}/nas.json')

BAY_Y = {1: 10, 2: 7, 3: 4, 4: 1}   # bottom of each bay; bay 1 is the top
for b, y0 in BAY_Y.items():
    for t in (1, 2, 3):
        jdump({
            'textures': {'particle': 'ccsn:block/nas_caddy_edge', 'caddy': f'ccsn:block/nas_caddy_t{t}',
                         'edge': 'ccsn:block/nas_caddy_edge', 'latch': 'ccsn:block/nas_latch'},
            'elements': [
                {'name': 'caddy', 'from': [1.5, y0 + 0.5, -0.5], 'to': [14.5, y0 + 2.5, 4], 'faces': {
                    'north': F('#caddy', uv=[1, 1, 14, 3]), 'up': F('#edge'), 'down': F('#edge'),
                    'east': F('#edge'), 'west': F('#edge')}},
                {'name': 'latch', 'from': [12.4, y0 + 0.8, -1.1], 'to': [13.4, y0 + 2.2, -0.5], 'faces': {
                    k: F('#latch') for k in ('north', 'east', 'west', 'up', 'down')}},
            ]}, f'{BM}/nas_drive_b{b}_t{t}.json')

ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
parts = [{'when': {'facing': f}, 'apply': {'model': 'ccsn:block/nas', 'y': r}} for f, r in ROT.items()]
for b in BAY_Y:
    for t in (1, 2, 3):
        for f, r in ROT.items():
            parts.append({'when': {'facing': f, f'bay{b}': f't{t}'},
                          'apply': {'model': f'ccsn:block/nas_drive_b{b}_t{t}', 'y': r}})
jdump({'multipart': parts}, f'{BS}/nas.json')
jdump({'parent': 'ccsn:block/nas'}, f'{IM}/nas.json')

# ================= Drive item (3D) =================
CASE = (150, 154, 160); CASEL = (178, 182, 188); CASED = (110, 114, 121); LABEL = (228, 230, 233); INK = (60, 62, 66)
PIN = (214, 170, 60)
metal = img(); fill_noise(metal, CASE, 3); rect(metal, 0, 13, 15, 13, CASEL); rect(metal, 0, 15, 15, 15, CASED)
save(metal, f'{IT}/drive_metal.png')
for t in (1, 2, 3):
    tp = img(); fill_noise(tp, CASE, 3)
    rect(tp, 3, 1, 12, 1, CASEL); rect(tp, 3, 14, 12, 14, CASED)
    for p in [(4, 2), (11, 2), (4, 13), (11, 13)]: px(tp, *p, CASED)
    rect(tp, 5, 4, 10, 9, LABEL)
    rect(tp, 6, 5, 9, 5, INK); rect(tp, 6, 7, 8, 7, INK); px(tp, 10 - 1, 8, TIER[t])
    save(tp, f'{IT}/drive_top_t{t}.png')
    rail = img(); fill_noise(rail, TIER[t], 3); rect(rail, 0, 15, 15, 15, TIERD[t]); rect(rail, 0, 0, 15, 0, TIER[t])
    save(rail, f'{IT}/drive_rail_t{t}.png')
    face = img(); fill_noise(face, CAD, 2)
    rect(face, 3, 13, 12, 13, CADL)
    px(face, 3, 13, TIER[t]); px(face, 3, 14, TIERD[t]); px(face, 3, 15, TIERD[t])
    for x in (6, 8): px(face, x, 15, VENT)
    px(face, 11, 14, LED)
    save(face, f'{IT}/drive_face_t{t}.png')
bk = img(); fill_noise(bk, CASE, 3); rect(bk, 4, 14, 11, 14, (28, 30, 34))
for x in range(5, 11, 2): px(bk, x, 14, PIN)
save(bk, f'{IT}/drive_back.png')
dedge = img(); fill_noise(dedge, CAD, 2); save(dedge, f'{IT}/drive_edge.png')
dl = img(); fill_noise(dl, LATCH, 2); save(dl, f'{IT}/drive_latch.png')

drive_base = {
    'gui_light': 'side',
    'textures': {'metal': 'ccsn:item/drive_metal', 'back': 'ccsn:item/drive_back', 'edge': 'ccsn:item/drive_edge',
                 'latch': 'ccsn:item/drive_latch'},
    'elements': [
        {'name': 'case', 'from': [3, 0, 1], 'to': [13, 3, 15], 'faces': {
            'up': F('#top', uv=[3, 1, 13, 15]), 'down': F('#metal', uv=[3, 1, 13, 15]),
            'east': F('#metal', uv=[1, 13, 15, 16]), 'west': F('#metal', uv=[1, 13, 15, 16]),
            'south': F('#back', uv=[3, 13, 13, 16])}},
        {'name': 'caddy face', 'from': [2.8, -0.2, 0], 'to': [13.2, 3.2, 1], 'faces': {
            'north': F('#face', uv=[3, 13, 13, 16]), 'up': F('#edge'), 'down': F('#edge'),
            'east': F('#edge'), 'west': F('#edge'), 'south': F('#edge')}},
        {'name': 'latch', 'from': [10.5, 0.6, -0.7], 'to': [12, 2.4, 0], 'faces': {
            k: F('#latch') for k in ('north', 'east', 'west', 'up', 'down')}},
        {'name': 'tier rail', 'from': [13, 0.4, 2], 'to': [13.6, 2.6, 14], 'faces': {
            k: F('#rail') for k in ('north', 'south', 'east', 'up', 'down')}},
    ],
    'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.9, 0.9, 0.9]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [90, 0, 0], 'translation': [0, 0, -1], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    },
}
jdump(drive_base, f'{IM}/drive_base.json')
for t in (1, 2, 3):
    jdump({'parent': 'ccsn:item/drive_base', 'textures': {
        'particle': f'ccsn:item/drive_top_t{t}', 'top': f'ccsn:item/drive_top_t{t}',
        'rail': f'ccsn:item/drive_rail_t{t}', 'face': f'ccsn:item/drive_face_t{t}'}}, f'{IM}/drive_t{t}.json')

# ================= Chipsets (flat items) =================
for t in (1, 2, 3):
    im = img(); c, cd = TIER[t], TIERD[t]; cl = tuple(min(255, v + 40) for v in c)
    for x in range(4, 12, 2): px(im, x, 2, (150, 150, 150)); px(im, x, 13, (150, 150, 150))
    for y in range(4, 12, 2): px(im, 2, y, (150, 150, 150)); px(im, 13, y, (150, 150, 150))
    rect(im, 3, 3, 12, 12, (30, 32, 36)); rect(im, 3, 3, 12, 3, (55, 58, 64))
    rect(im, 5, 5, 10, 10, c); rect(im, 5, 5, 10, 5, cl); rect(im, 5, 5, 5, 10, cl)
    rect(im, 5, 10, 10, 10, cd); rect(im, 10, 5, 10, 10, cd); px(im, 7, 7, cl); px(im, 4, 4, (200, 200, 200))
    save(im, f'{IT}/chipset_t{t}.png')
    jdump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'ccsn:item/chipset_t{t}'}}, f'{IM}/chipset_t{t}.json')

# ================= GUI =================
W, H = 176, 186
gui = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
G = (198, 198, 198); WHITE = (255, 255, 255); SHADE = (85, 85, 85); BLACK = (0, 0, 0); SLOTC = (139, 139, 139)
rect(gui, 2, 2, W - 3, H - 3, G)
rect(gui, 2, 0, W - 4, 0, BLACK); rect(gui, 2, H - 1, W - 4, H - 1, BLACK)
rect(gui, 0, 2, 0, H - 4, BLACK); rect(gui, W - 1, 2, W - 1, H - 4, BLACK)
px(gui, 1, 1, BLACK); px(gui, W - 2, 1, BLACK); px(gui, 1, H - 2, BLACK); px(gui, W - 2, H - 2, BLACK)
rect(gui, 2, 1, W - 4, 2, WHITE); rect(gui, 1, 2, 2, H - 4, WHITE)
rect(gui, 3, H - 3, W - 3, H - 2, SHADE); rect(gui, W - 3, 3, W - 2, H - 3, SHADE)
px(gui, W - 3, 2, G); px(gui, 2, H - 3, G)
def slot(x, y):  # slot item position x,y -> draw 18x18 at x-1,y-1
    rect(gui, x - 1, y - 1, x + 16, y + 16, SLOTC)
    rect(gui, x - 1, y - 1, x + 15, y - 1, (55, 55, 55)); rect(gui, x - 1, y - 1, x - 1, y + 15, (55, 55, 55))
    rect(gui, x, y + 16, x + 16, y + 16, WHITE); rect(gui, x + 16, y, x + 16, y + 16, WHITE)
BAY_X, BAY_Y0, INV_Y = 44, 18, 104
# NAS faceplate behind the bays
rect(gui, 29, 15, 63, 16 + 4 * 18 + 1, (60, 64, 72)); rect(gui, 29, 15, 63, 15, (90, 96, 108))
rect(gui, 29, 16 + 4 * 18 + 1, 63, 16 + 4 * 18 + 1, (34, 36, 42))
px(gui, 31, 17, BLUE); px(gui, 33, 17, LED)
for i in range(4): slot(BAY_X, BAY_Y0 + i * 18)
for r in range(3):
    for c in range(9): slot(8 + c * 18, INV_Y + r * 18)
for c in range(9): slot(8 + c * 18, INV_Y + 58)
gui.save(f'{GT}/nas.png')
print('assets generated')
