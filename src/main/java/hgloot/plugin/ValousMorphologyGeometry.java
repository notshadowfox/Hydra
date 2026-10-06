package hgloot.plugin;

import java.util.ArrayList;
import java.util.List;

final class ValousMorphologyGeometry {
    private ValousMorphologyGeometry() {
    }

    static List<ValousRoutePostProcessor.Point> verticalBreachAxis(
        ValousRoutePostProcessor.Point surface,
        ValousRoutePostProcessor.Point underground,
        long seed
    ) {
        int verticalDistance = Math.abs(surface.y() - underground.y());
        int steps = Math.max(1, (int) Math.ceil(verticalDistance / 2.0D));
        double direction = unit(seed ^ (((long) surface.x()) << 32) ^ surface.z()) * Math.PI * 2.0D;
        double directionX = Math.cos(direction);
        double directionZ = Math.sin(direction);
        List<ValousRoutePostProcessor.Point> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double bow = Math.sin(Math.PI * t) * 4.0D;
            int x = (int) Math.round(surface.x()
                + ((underground.x() - surface.x()) * t)
                + (directionX * bow));
            int y = (int) Math.round(surface.y() + ((underground.y() - surface.y()) * t));
            int z = (int) Math.round(surface.z()
                + ((underground.z() - surface.z()) * t)
                + (directionZ * bow));
            addDistinct(points, new ValousRoutePostProcessor.Point(x, y, z));
        }
        return List.copyOf(points);
    }

    static List<ValousRoutePostProcessor.Point> broadCurve(
        ValousRoutePostProcessor.Point start,
        ValousRoutePostProcessor.Point end,
        long seed,
        double bendScale
    ) {
        double dx = end.x() - start.x();
        double dz = end.z() - start.z();
        double horizontal = Math.max(1.0D, Math.hypot(dx, dz));
        int steps = Math.max(4, Math.max(
            (int) Math.ceil(horizontal / 3.0D),
            (int) Math.ceil(Math.abs(end.y() - start.y()) / 2.0D)
        ));
        double perpendicularX = -dz / horizontal;
        double perpendicularZ = dx / horizontal;
        double direction = ((seed & 1L) == 0L) ? 1.0D : -1.0D;
        double bend = Math.min(34.0D, horizontal * 0.18D) * bendScale * direction;
        List<ValousRoutePostProcessor.Point> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double curve = Math.sin(Math.PI * t) * bend;
            double ridge = Math.sin(Math.PI * 2.0D * t) * Math.min(4.0D, horizontal / 24.0D);
            addDistinct(points, new ValousRoutePostProcessor.Point(
                (int) Math.round(start.x() + (dx * t) + (perpendicularX * curve)),
                (int) Math.round(start.y() + ((end.y() - start.y()) * t) + ridge),
                (int) Math.round(start.z() + (dz * t) + (perpendicularZ * curve))
            ));
        }
        return List.copyOf(points);
    }

    static EntranceMetrics entranceMetrics(List<ValousRoutePostProcessor.Point> axis) {
        if (axis == null || axis.size() < 2) {
            return new EntranceMetrics(0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                0, true, Double.POSITIVE_INFINITY);
        }
        ValousRoutePostProcessor.Point surface = axis.getFirst();
        double maxDrift = 0.0D;
        double totalDrift = 0.0D;
        double horizontalTravel = 0.0D;
        int reversals = 0;
        double previousDx = 0.0D;
        double previousDz = 0.0D;
        double cumulativeTurn = 0.0D;
        for (int index = 0; index < axis.size(); index++) {
            ValousRoutePostProcessor.Point point = axis.get(index);
            double drift = Math.hypot(point.x() - surface.x(), point.z() - surface.z());
            maxDrift = Math.max(maxDrift, drift);
            totalDrift += drift;
            if (index == 0) {
                continue;
            }
            ValousRoutePostProcessor.Point previous = axis.get(index - 1);
            double dx = point.x() - previous.x();
            double dz = point.z() - previous.z();
            horizontalTravel += Math.hypot(dx, dz);
            if ((previousDx != 0.0D || previousDz != 0.0D) && (dx != 0.0D || dz != 0.0D)) {
                double dot = (previousDx * dx) + (previousDz * dz);
                double cross = (previousDx * dz) - (previousDz * dx);
                double turn = Math.atan2(cross, dot);
                cumulativeTurn += turn;
                if (dot < 0.0D && Math.abs(turn) > Math.PI * 0.55D) {
                    reversals++;
                }
            }
            if (dx != 0.0D || dz != 0.0D) {
                previousDx = dx;
                previousDz = dz;
            }
        }
        int verticalDrop = Math.abs(surface.y() - axis.getLast().y());
        double horizontalToVertical = horizontalTravel / Math.max(1.0D, verticalDrop);
        return new EntranceMetrics(
            verticalDrop,
            maxDrift,
            totalDrift / axis.size(),
            reversals,
            cumulativeTurn >= Math.PI * 1.75D,
            horizontalToVertical
        );
    }

    static boolean horizontalEnvelopeInside(
        List<ValousRoutePostProcessor.Point> path,
        int carveRadius,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ,
        int protectedCenterRadius
    ) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (ValousRoutePostProcessor.Point point : path) {
            if (point.x() < minimumX
                || point.x() > maximumX
                || point.z() < minimumZ
                || point.z() > maximumZ
                || Math.hypot(point.x(), point.z()) - carveRadius < protectedCenterRadius) {
                return false;
            }
        }
        return true;
    }

    private static double unit(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed & 0xffffL) / 65535.0D;
    }

    private static void addDistinct(
        List<ValousRoutePostProcessor.Point> points,
        ValousRoutePostProcessor.Point point
    ) {
        if (points.isEmpty() || !points.getLast().equals(point)) {
            points.add(point);
        }
    }

    record EntranceMetrics(
        int verticalDrop,
        double maximumHorizontalDrift,
        double averageHorizontalDrift,
        int directionalReversals,
        boolean circlesCenter,
        double horizontalToVerticalRatio
    ) {
        boolean passed() {
            return verticalDrop >= 32
                && maximumHorizontalDrift <= 8.0D
                && directionalReversals <= 2
                && !circlesCenter
                && horizontalToVerticalRatio < 0.45D;
        }
    }
}
