package net.minecraft.world.phys.shapes;
public final class Shapes {
    public static VoxelShape box(double a, double b, double c, double d, double e, double f) { return new VoxelShape(); }
    public static VoxelShape or(VoxelShape a, VoxelShape b) { return a; }
    public static VoxelShape empty() { return new VoxelShape(); }
    public static VoxelShape block() { return new VoxelShape(); }
}
