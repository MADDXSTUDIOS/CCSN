import net.minecraft.world.item.DyeColor;
import uk.maddx.ccsn.network.CableLanes;
import uk.maddx.ccsn.network.CableLanes.Box;
import java.util.*;

/** Proves no two strands overlap (any colours, any directions) and no strand goes through tray steel. */
public class Check {
    static boolean overlap(Box a, Box b) {
        float e = 1e-4f;
        return a.x1() < b.x2() - e && b.x1() < a.x2() - e && a.y1() < b.y2() - e && b.y1() < a.y2() - e
            && a.z1() < b.z2() - e && b.z1() < a.z2() - e;
    }
    public static void main(String[] args) {
        DyeColor[] all = DyeColor.values();
        long checks = 0, bad = 0;
        for (int i = 0; i < 16; i++) for (int j = i + 1; j < 16; j++)
            for (int ma = 0; ma < 64; ma++) for (int mb = 0; mb < 64; mb++) {
                for (Box a : CableLanes.strand(all[i], ma, 0)) for (Box b : CableLanes.strand(all[j], mb, 0)) {
                    checks++;
                    if (overlap(a, b) && bad++ < 5) System.out.println("STRAND " + all[i] + " m" + ma + " vs " + all[j] + " m" + mb + ": " + a + " / " + b);
                }
            }
        System.out.println("strand pairs: " + checks + " checks, " + bad + " overlaps");
        checks = 0; bad = 0;
        int[] solids = { 0, 63, 1, 2, 4, 8, 16, 32, 1 | 16, 2 | 8 };
        for (int union = 0; union < 64; union++) for (int solid : solids) {
            List<Box> tray = CableLanes.tray(union, solid);
            for (int m = union; ; m = (m - 1) & union) {
                for (DyeColor c : all) for (Box s : CableLanes.strand(c, m, 0)) for (Box t : tray) {
                    checks++;
                    if (overlap(s, t) && bad++ < 8) System.out.println("TRAY u" + union + " solid" + solid + " " + c + " m" + m + ": " + s + " / " + t);
                }
                if (m == 0) break;
            }
        }
        System.out.println("strand vs tray: " + checks + " checks, " + bad + " overlaps");
    }
}
