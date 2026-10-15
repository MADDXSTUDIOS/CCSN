import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import uk.maddx.ccsn.network.CableLanes;
import uk.maddx.ccsn.network.CableRender;
import java.io.*;
import java.util.*;

/** Reads a scene (C x y z colourBits cover | S x y z | J x y z), prints every box the real code makes. */
public class Dump {
    record P(int x, int y, int z) { P rel(Direction d) { return new P(x + d.dx, y + d.dy, z + d.dz); } }
    public static void main(String[] args) throws Exception {
        Map<P, int[]> cables = new LinkedHashMap<>();
        Set<P> solids = new HashSet<>(), jacks = new HashSet<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (String line; (line = in.readLine()) != null; ) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 4) continue;
            P p = new P(Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]));
            switch (t[0]) {
                case "C" -> cables.put(p, new int[] { Integer.parseInt(t[4]), Integer.parseInt(t[5]) });
                case "S" -> solids.add(p);
                case "J" -> jacks.add(p);
            }
        }
        for (var en : cables.entrySet()) {
            P p = en.getKey();
            int bits = en.getValue()[0], cover = en.getValue()[1];
            int[] masks = new int[16];
            int solid = 0, cab = 0;
            for (Direction d : Direction.values()) {
                P o = p.rel(d);
                if (solids.contains(o)) solid |= 1 << d.ordinal();
                if (cables.containsKey(o)) cab |= 1 << d.ordinal();
                for (int c = 0; c < 16; c++) {
                    if ((bits & (1 << c)) == 0) continue;
                    boolean link = (cables.containsKey(o) && (cables.get(o)[0] & (1 << c)) != 0) || jacks.contains(o);
                    if (link) masks[c] |= 1 << d.ordinal();
                }
            }
            CableRender r = CableRender.of(bits, masks, solid, cab, cover);
            CableLanes.shape(r);
            for (DyeColor col : r.present()) {
                for (CableLanes.Box b : CableLanes.strand(col, r.mask(col), 0)) out(p, b, "strand" + col.ordinal());
            }
            for (CableLanes.Box b : CableLanes.cover(r)) {
                out(p, b, switch (b.tex()) { case CableLanes.TEX_TRAY -> "tray"; case CableLanes.TEX_ROD -> "rod"; default -> "trunking"; });
            }
        }
        for (P s : solids) System.out.printf("%d %d %d %d %d %d 63 solid%n", s.x * 16, s.y * 16, s.z * 16, s.x * 16 + 16, s.y * 16 + 16, s.z * 16 + 16);
        for (P j : jacks) System.out.printf("%d %d %d %d %d %d 63 jack%n", j.x * 16 + 4, j.y * 16 + 3, j.z * 16 + 4, j.x * 16 + 12, j.y * 16 + 13, j.z * 16 + 12);
    }
    static void out(P p, CableLanes.Box b, String kind) {
        System.out.printf("%s %s %s %s %s %s %d %s%n", p.x * 16 + b.x1(), p.y * 16 + b.y1(), p.z * 16 + b.z1(),
            p.x * 16 + b.x2(), p.y * 16 + b.y2(), p.z * 16 + b.z2(), b.faces(), kind);
    }
}
