"""Renders the real block/item model JSON + textures, to sanity-check geometry and UVs.
python3 tools/preview_models.py out.png"""
import json, sys
from PIL import Image, ImageDraw, ImageFont
R = 'src/main/resources/assets/ccsn'
_tex = {}
def tex(ref):
    p = ref.split(':')[1]
    if p not in _tex:
        im = Image.open(f'{R}/textures/{p}.png').convert('RGBA')
        _tex[p] = im.crop((0, 0, im.width, im.width))
    return _tex[p]
def load(model):
    m = json.load(open(f'{R}/models/{model}.json'))
    textures, elements = {}, None
    if 'parent' in m and m['parent'].startswith('ccsn:'):
        tp, ep = load(m['parent'].split(':')[1]); textures.update(tp); elements = ep
    textures.update(m.get('textures', {}))
    if 'elements' in m: elements = m['elements']
    return textures, elements
def resolve(textures, ref):
    while ref.startswith('#'): ref = textures[ref[1:]]
    return ref
def auto_uv(face, f, t):
    x1, y1, z1 = f; x2, y2, z2 = t
    return {'north': [16-x2, 16-y2, 16-x1, 16-y1], 'south': [x1, 16-y2, x2, 16-y1],
            'east': [16-z2, 16-y2, 16-z1, 16-y1], 'west': [z1, 16-y2, z2, 16-y1],
            'up': [x1, z1, x2, z2], 'down': [x1, 16-z2, x2, 16-z1]}[face]
def corners(face, f, t):
    """returns function (s,t in 0..1 across uv) -> 3D point"""
    x1, y1, z1 = f; x2, y2, z2 = t
    L = lambda a, b, s: a + (b - a) * s
    return {'north': lambda s, v: (L(x2, x1, s), L(y2, y1, v), z1),
            'south': lambda s, v: (L(x1, x2, s), L(y2, y1, v), z2),
            'east':  lambda s, v: (x2, L(y2, y1, v), L(z2, z1, s)),
            'west':  lambda s, v: (x1, L(y2, y1, v), L(z1, z2, s)),
            'up':    lambda s, v: (L(x1, x2, s), y2, L(z1, z2, v)),
            'down':  lambda s, v: (L(x1, x2, s), y1, L(z2, z1, v))}[face]
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}
def quads(model_list):
    out = []
    for model, yrot in model_list:
        textures, elements = load(model)
        for e in elements:
            f, t = e['from'], e['to']
            for face, fd in e['faces'].items():
                uv = fd.get('uv') or auto_uv(face, f, t)
                im = tex(resolve(textures, fd['texture']))
                u1, v1, u2, v2 = uv
                nu, nv = max(1, round(abs(u2 - u1))), max(1, round(abs(v2 - v1)))
                P = corners(face, f, t)
                for i in range(nu):
                    for j in range(nv):
                        su, sv = (u1 + (u2 - u1) * (i + .5) / nu), (v1 + (v2 - v1) * (j + .5) / nv)
                        c = im.getpixel((min(15, int(su)), min(15, int(sv))))
                        if c[3] < 10: continue
                        pts = [P(i / nu, j / nv), P((i + 1) / nu, j / nv), P((i + 1) / nu, (j + 1) / nv), P(i / nu, (j + 1) / nv)]
                        if yrot: pts = [rot(p, yrot) for p in pts]
                        col = tuple(int(c[k] * SHADE[face]) for k in range(3))
                        out.append((pts, col, face))
    return out
def rot(p, deg):
    x, y, z = p[0] - 8, p[1], p[2] - 8
    for _ in range(deg // 90): x, z = -z, x
    return (x + 8, y, z + 8)
def render(draw, qs, O, u, view):
    # view 'nw': see north, west, up ; 'ne': see north, east, up
    kx, ky = 0.42, 0.30
    if view == 'nw':
        P = lambda p: (O[0] + ((16 - p[0]) + p[2] * kx) * u, O[1] - (p[1] + p[2] * ky) * u)
        vis = {'north', 'west', 'up'}
    else:
        P = lambda p: (O[0] + ((16 - p[0]) - p[2] * kx) * u, O[1] - (p[1] + p[2] * ky) * u)
        vis = {'north', 'east', 'up'}
    def key(q):
        pts = q[0]; n = len(pts)
        return -sum(p[2] for p in pts) / n + 0.001 * sum(p[1] for p in pts) / n
    for pts, col, face in sorted(qs, key=key):
        if face not in vis: continue
        draw.polygon([P(p) for p in pts], fill=col)

if __name__ == '__main__':
    out = sys.argv[1]
    W, H = 1500, 820
    cv = Image.new('RGB', (W, H), (236, 238, 242)); d = ImageDraw.Draw(cv)
    Fn = lambda s, b=False: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if b else ''), s)
    d.text((40, 20), 'v0.2 models, rendered from the real model files', fill=(30, 33, 38), font=Fn(28, True))
    nas = [('block/nas', 0), ('block/nas_drive_b1_t3', 0), ('block/nas_drive_b2_t1', 0), ('block/nas_drive_b4_t2', 0)]
    render(d, quads(nas), (60, 700), 34, 'nw')
    d.text((60, 740), 'NAS: bay 1 Diamond, bay 2 Iron, bay 3 empty, bay 4 Gold', fill=(30, 33, 38), font=Fn(20))
    for i, t in enumerate((1, 2, 3)):
        render(d, quads([(f'item/drive_t{t}', 0)]), (1010, 230 + i * 210), 17, 'ne')
        d.text((1260, 175 + i * 210), f'T{t} drive', fill=(30, 33, 38), font=Fn(20, True))
    cv.save(out)
