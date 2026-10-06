package hgloot.plugin;

enum SpleefCollapsePattern {
    OUTSIDE_IN,
    RINGS,
    ALTERNATING_QUADRANTS,
    STAR,
    WAVES;

    static SpleefCollapsePattern selectFor(long matchSeed) {
        SpleefCollapsePattern[] patterns = values();
        return patterns[Math.floorMod(Long.hashCode(matchSeed), patterns.length)];
    }

    int section(int x, int z, int centerX, int centerZ) {
        int dx = x - centerX;
        int dz = z - centerZ;
        return switch (this) {
            case OUTSIDE_IN -> Math.max(Math.abs(dx), Math.abs(dz)) >= 16 ? 0 : 1;
            case RINGS -> Math.floorDiv((int) Math.floor(Math.hypot(dx, dz)), 6) & 1;
            case ALTERNATING_QUADRANTS -> ((dx >= 0) == (dz >= 0)) ? 0 : 1;
            case STAR -> Math.floorMod((int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 5.0D)), 2);
            case WAVES -> Math.floorMod(Math.floorDiv(dx, 8), 2);
        };
    }
}
