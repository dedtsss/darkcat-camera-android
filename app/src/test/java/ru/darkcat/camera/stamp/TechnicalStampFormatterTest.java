package ru.darkcat.camera.stamp;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TechnicalStampFormatterTest {
    @Test public void formatsTechnicalBlockWithoutDecorations() {
        List<String> lines = TechnicalStampFormatter.lines(64.588210, 30.599140, 4.2f, 427,
                Arrays.asList("СКЛАД", "ВХОД"), "Доп. текст", true, true, true, true, true);
        assertEquals("64.588210, 30.599140 ±4 м", lines.get(0));
        assertEquals("№ 00427", lines.get(1));
        assertEquals("СКЛАД · ВХОД", lines.get(2));
        assertEquals("Доп. текст", lines.get(3));
    }
}
