package uk.maddx.ccsn;

import net.neoforged.neoforge.common.ModConfigSpec;
import uk.maddx.ccsn.network.CableLanes;

/** Server config: lives in <world>/serverconfig/ccsn-server.toml (synced to clients). */
public final class CCSNConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.LongValue[] DRIVE_CAPACITY = new ModConfigSpec.LongValue[3];
    private static final long[] DEFAULT_CAPACITY = { 4L << 20, 16L << 20, 64L << 20 };
    private static final ModConfigSpec.IntValue MAX_RUNS;
    private static final int DEFAULT_MAX_RUNS = 6;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("NAS drive capacities, in bytes.",
                "For comparison: a CC floppy is 125 KB and a CC computer is 1 MB.").push("drives");
        String[] names = { "iron", "gold", "diamond" };
        for (int i = 0; i < 3; i++) {
            DRIVE_CAPACITY[i] = builder
                .comment("T" + (i + 1) + " (" + names[i] + ") drive capacity. Default " + (DEFAULT_CAPACITY[i] >> 20) + " MiB.")
                .defineInRange("t" + (i + 1) + "_capacity", DEFAULT_CAPACITY[i], 64L * 1024, 4L * 1024 * 1024 * 1024);
        }
        builder.pop();

        builder.comment("Network cable.").push("cables");
        MAX_RUNS = builder
            .comment("How many separate runs (colours) can share one cable block. Default " + DEFAULT_MAX_RUNS
                + ", up to " + CableLanes.ABSOLUTE_MAX_RUNS + ".",
                "Lowering it never removes runs already placed; it only stops more being added.")
            .defineInRange("max_runs_per_block", DEFAULT_MAX_RUNS, 1, CableLanes.ABSOLUTE_MAX_RUNS);
        builder.pop();

        SPEC = builder.build();
    }

    /** Capacity of a drive tier (1-3), in bytes. Safe to call before a world is loaded. */
    public static long driveCapacity(int tier) {
        int i = Math.max(1, Math.min(3, tier)) - 1;
        return SPEC.isLoaded() ? DRIVE_CAPACITY[i].get() : DEFAULT_CAPACITY[i];
    }

    /** How many runs one cable block can carry. Safe to call before a world is loaded. */
    public static int maxRunsPerBlock() {
        return SPEC.isLoaded() ? MAX_RUNS.get() : DEFAULT_MAX_RUNS;
    }

    private CCSNConfig() {
    }
}
