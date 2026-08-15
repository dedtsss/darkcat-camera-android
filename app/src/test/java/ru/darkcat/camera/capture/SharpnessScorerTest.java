package ru.darkcat.camera.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class SharpnessScorerTest {
    @Test public void checkerboardIsSharperThanFlatPlane() {
        int width = 64, height = 64;
        byte[] flat = new byte[width * height];
        byte[] checker = new byte[width * height];
        java.util.Arrays.fill(flat, (byte) 80);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            checker[y * width + x] = (byte) ((((x / 4) + (y / 4)) & 1) == 0 ? 0 : 255);
        assertEquals(0.0, SharpnessScorer.varianceOfLaplacian(flat, width, height), 0.0001);
        assertTrue(SharpnessScorer.varianceOfLaplacian(checker, width, height) > 100.0);
    }

    @Test public void invalidPlaneIsSafe() {
        assertEquals(0.0, SharpnessScorer.varianceOfLaplacian(new byte[2], 4, 4), 0.0);
    }
}
