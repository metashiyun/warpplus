package com.scchan.warpplus;

import java.util.Objects;
import java.util.UUID;

public record WarpLocation(UUID worldId, String worldName, double x, double y, double z, float yaw, float pitch) {
    public WarpLocation {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Destination coordinates must be finite.");
        }
    }
}
