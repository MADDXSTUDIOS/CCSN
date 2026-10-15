"""Preview scenes rendered from the REAL Java geometry (via Dump) with tools/zrender.py. Use run.sh."""
import subprocess, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from zrender import Scene

GEO = sys.argv[1] if len(sys.argv) > 1 else 'build/geocheck'   # where run.sh compiled the classes
DYE = [(249,255,254),(249,128,29),(199,78,189),(58,179,218),(254,216,61),(128,199,31),(243,139,170),(71,79,82),
       (157,157,151),(22,156,156),(137,50,184),(60,68,170),(131,84,50),(94,124,22),(176,46,38),(29,29,33)]
COL = {'tray': (205, 210, 214), 'rod': (95, 100, 108), 'trunking': (236, 236, 228), 'solid': (150, 140, 128), 'jack': (225, 222, 210)}
NONE, TRAY, TRUNK = 0, 1, 2

def bits(*c): return sum(1 << x for x in c)
RUNS6 = bits(14, 4, 15, 5, 11, 0)   # red yellow black lime blue white

def run(lines, path, yaw=-35, pitch=30, size=(900, 700), hide=()):
    out = subprocess.run(['java', '-cp', GEO, 'Dump'], input='\n'.join(lines), capture_output=True, text=True)
    if out.returncode: print(out.stderr); sys.exit(1)
    s = Scene(yaw, pitch)
    for ln in out.stdout.splitlines():
        t = ln.split()
        if len(t) < 8: continue
        x1, y1, z1, x2, y2, z2 = map(float, t[:6]); faces = int(t[6]); kind = t[7]
        if kind in hide: continue
        col = DYE[int(kind[6:])] if kind.startswith('strand') else COL[kind]
        s.box((x1, y1, z1), (x2, y2, z2), col, faces=faces)
    s.render(path, size=size)

def C(x, y, z, b=RUNS6, cover=TRAY): return f'C {x} {y} {z} {b} {cover}'
def S(x, y, z): return f'S {x} {y} {z}'
def J(x, y, z): return f'J {x} {y} {z}'

out = sys.argv[2] if len(sys.argv) > 2 else GEO
os.makedirs(out, exist_ok=True)
scenes = {}
# A: tray on the floor: straight, turn, T, dead end
a = [S(x, -1, z) for x in range(4) for z in range(3)]
a += [C(0, 0, 0), C(1, 0, 0), C(2, 0, 0), C(2, 0, 1), C(2, 0, 2), C(3, 0, 1, bits(14, 4))]
scenes['a_floor'] = a
# B: tray on a north wall, turning up into a riser with clips, then off east at the top
b = [S(x, y, -1) for x in range(5) for y in range(5)]
b += [C(0, 1, 0), C(1, 1, 0), C(2, 1, 0), C(2, 2, 0), C(2, 3, 0), C(3, 3, 0), C(4, 3, 0)]
scenes['b_wall_riser'] = b
# C: hung from a ceiling
c = [S(x, 2, z) for x in range(3) for z in range(2)]
c += [C(0, 1, 0), C(1, 1, 0), C(2, 1, 0), C(1, 1, 1, bits(14, 4, 15))]
scenes['c_ceiling'] = c
# D: riser standing on the floor going up, free-standing
d = [S(x, -1, z) for x in range(2) for z in range(2)]
d += [C(0, 0, 0), C(0, 1, 0), C(0, 2, 0), C(1, 0, 0, bits(14, 4, 15))]
scenes['d_riser_floor'] = d
# E: trunking along a north wall, ending in air at one end, open into a tray at the other, plus jack stub
e = [S(x, y, -1) for x in range(-1, 4) for y in range(3)]
e += [C(-1, 1, 0, RUNS6, TRAY), C(0, 1, 0, RUNS6, TRUNK), C(1, 1, 0, RUNS6, TRUNK), C(2, 1, 0, RUNS6, TRUNK), C(2, 1, 1, bits(14), TRUNK), J(2, 1, 2)]
scenes['e_trunking'] = e
# F: bare strands crossing at a 3D junction
f = [C(1, 1, 1, RUNS6 | bits(1, 9), NONE),
     C(0, 1, 1, bits(14, 4, 0), NONE), C(2, 1, 1, bits(15, 5, 1), NONE), C(1, 1, 0, bits(11, 14, 9), NONE),
     C(1, 1, 2, bits(4, 15, 0), NONE), C(1, 2, 1, bits(5, 11, 1), NONE), C(1, 0, 1, bits(9, 0, 14), NONE)]
scenes['f_strands'] = f

for name, lines in scenes.items():
    run(lines, f'{out}/{name}.png')
    print('rendered', name)
