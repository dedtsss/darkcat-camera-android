package ru.darkcat.camera.location;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class LiveAccuracyFormatterTest {
    @Test public void keepsSubTenMeterDecimalPrecision() { assertEquals("±4.2 м", LiveAccuracyFormatter.format(4.24f)); }
    @Test public void neverInventsAccuracyForMissingFix() { assertEquals("±— м", LiveAccuracyFormatter.format(Float.NaN)); }
}
