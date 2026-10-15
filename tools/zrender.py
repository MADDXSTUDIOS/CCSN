"""Tiny z-buffered renderer for axis-aligned boxes, for checking cable/tray geometry
before it goes in the game. Boxes are in pixels (16 per block). Not shipped in the jar.

    from zrender import Scene
    s = Scene(); s.box((x1,y1,z1), (x2,y2,z2), (r,g,b)); s.render('out.png')

Faces can be left out by name (skip=) or by a Minecraft-ordinal bitmask (faces=, bit 0 = down,
1 = up, 2 = north, 3 = south, 4 = west, 5 = east). Every face gets a thin dark outline so
separate boxes (slats, rungs, posts) stay readable.
"""
import math
import numpy as np
from PIL import Image

SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.62, 'west': 0.62}
ORDER = ('down', 'up', 'north', 'south', 'west', 'east')


class Scene:
    def __init__(self, yaw=-35, pitch=30):
        self.faces = []
        a, b = math.radians(yaw), math.radians(pitch)
        # camera basis: rotate around Y by yaw, then tilt down by pitch
        self.R = np.array([[math.cos(a), 0, -math.sin(a)],
                           [math.sin(a) * math.sin(b), math.cos(b), math.cos(a) * math.sin(b)],
                           [math.sin(a) * math.cos(b), -math.sin(b), math.cos(a) * math.cos(b)]])

    def box(self, p1, p2, colour, skip=(), faces=63):
        x1, y1, z1 = p1; x2, y2, z2 = p2
        F = {
            'up':    [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
            'down':  [(x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)],
            'north': [(x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1)],
            'south': [(x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)],
            'west':  [(x1, y1, z1), (x1, y1, z2), (x1, y2, z2), (x1, y2, z1)],
            'east':  [(x2, y1, z1), (x2, y1, z2), (x2, y2, z2), (x2, y2, z1)],
        }
        for name, pts in F.items():
            if name in skip or not (faces >> ORDER.index(name)) & 1: continue
            c = tuple(int(v * SHADE[name]) for v in colour)
            self.faces.append((pts, c))

    def render(self, path, size=(900, 700), scale=None, bg=(236, 238, 242), margin=40, outline=True):
        pts_all = np.array([p for f, _ in self.faces for p in f], float)
        cam = pts_all @ self.R.T
        minx, maxx = cam[:, 0].min(), cam[:, 0].max()
        miny, maxy = cam[:, 1].min(), cam[:, 1].max()
        W, H = size
        if scale is None:
            scale = min((W - 2 * margin) / (maxx - minx + 1e-6), (H - 2 * margin) / (maxy - miny + 1e-6))
        img = np.zeros((H, W, 3), np.uint8); img[:] = bg
        zbuf = np.full((H, W), np.inf)
        ids = np.full((H, W), -1, np.int64)
        for fid, (pts, col) in enumerate(self.faces):
            c = np.array(pts, float) @ self.R.T
            sx = (c[:, 0] - minx) * scale + margin
            sy = H - ((c[:, 1] - miny) * scale + margin)
            sz = c[:, 2]
            for tri in ((0, 1, 2), (0, 2, 3)):
                self._tri(img, zbuf, ids, fid, sx[list(tri)], sy[list(tri)], sz[list(tri)], col)
        if outline:
            edge = np.zeros((H, W), bool)
            edge[:, 1:] |= ids[:, 1:] != ids[:, :-1]
            edge[1:, :] |= ids[1:, :] != ids[:-1, :]
            img[edge] = (img[edge] * 0.55).astype(np.uint8)
        Image.fromarray(img).save(path)
        return scale

    @staticmethod
    def _tri(img, zbuf, ids, fid, xs, ys, zs, col):
        H, W = zbuf.shape
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
        if x1 < x0 or y1 < y0: return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, bx, cx), (ay, by, cy) = xs, ys
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9: return
        w1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
        w2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
        w3 = 1 - w1 - w2
        inside = (w1 >= -1e-6) & (w2 >= -1e-6) & (w3 >= -1e-6)
        z = w1 * zs[0] + w2 * zs[1] + w3 * zs[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        m = inside & (z < sub - 1e-4)
        sub[m] = z[m]
        ids[y0:y1 + 1, x0:x1 + 1][m] = fid
        img[y0:y1 + 1, x0:x1 + 1][m] = col
