package uk.maddx.ccsn;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: lives in <world>/serverconfig/ccsn-server.toml */
public final class CCSNConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.LongValue NAS_CAPACITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("nas");
        NAS_CAPACITY = builder
            .comment("Storage capacity of each NAS block, in bytes. Default is 32 MiB.",
                     "For comparison: a CC floppy is 125 KB and a CC computer is 1 MB.")
            .defineInRange("capacity", 32L * 1024 * 1024, 64L * 1024, 1024L * 1024 * 1024);
        builder.pop();

        SPEC = builder.build();
    }

    private CCSNConfig() {
    }
}
