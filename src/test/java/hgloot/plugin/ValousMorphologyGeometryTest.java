package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValousMorphologyGeometryTest {
    @Test
    void verticalBreachStaysStraightAndDoesNotCircle() {
        var axis = ValousMorphologyGeometry.verticalBreachAxis(
            new ValousRoutePostProcessor.Point(-128, 78, 128),
            new ValousRoutePostProcessor.Point(-128, -28, 128),
            -6121076216497635564L
        );

        var metrics = ValousMorphologyGeometry.entranceMetrics(axis);
        assertTrue(metrics.passed(), metrics.toString());
        assertTrue(metrics.maximumHorizontalDrift() <= 8.0D);
        assertFalse(metrics.circlesCenter());
    }

    @Test
    void legacyHelixIsRejected() {
        List<ValousRoutePostProcessor.Point> helix = new ArrayList<>();
        for (int step = 0; step <= 106; step++) {
            double angle = step * 0.14D;
            helix.add(new ValousRoutePostProcessor.Point(
                -128 + (int) Math.round(Math.cos(angle) * 16.0D),
                78 - step,
                128 + (int) Math.round(Math.sin(angle) * 16.0D)
            ));
        }

        var metrics = ValousMorphologyGeometry.entranceMetrics(helix);
        assertFalse(metrics.passed());
        assertTrue(metrics.circlesCenter());
    }

    @Test
    void broadCurveRejectsAHiddenBoundaryExcursion() {
        var path = ValousMorphologyGeometry.broadCurve(
            new ValousRoutePostProcessor.Point(200, -64, -80),
            new ValousRoutePostProcessor.Point(200, -72, 80),
            1L,
            1.0D
        );

        assertFalse(ValousMorphologyGeometry.horizontalEnvelopeInside(
            path, 24, -227, 227, -227, 227, 128
        ));
    }

    @Test
    void broadCurveAcceptsAContainedSameHemispherePair() {
        var path = ValousMorphologyGeometry.broadCurve(
            new ValousRoutePostProcessor.Point(-176, -64, 96),
            new ValousRoutePostProcessor.Point(-96, -72, 176),
            2L,
            1.0D
        );

        assertTrue(ValousMorphologyGeometry.horizontalEnvelopeInside(
            path, 24, -227, 227, -227, 227, 128
        ));
    }
}
