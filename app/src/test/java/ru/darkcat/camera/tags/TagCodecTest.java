package ru.darkcat.camera.tags;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TagCodecTest {
    @Test public void roundTripPreservesEmojiAndSymbols() {
        assertEquals(Arrays.asList("СКЛАД", "Вход №2", "⚠️ срочно"),
                TagCodec.decode(TagCodec.encode(Arrays.asList("СКЛАД", "Вход №2", "⚠️ срочно"))));
    }

    @Test public void malformedTailDoesNotLoseValidPrefix() {
        assertEquals(Arrays.asList("one"), TagCodec.decode("3:one99:x"));
    }
}
