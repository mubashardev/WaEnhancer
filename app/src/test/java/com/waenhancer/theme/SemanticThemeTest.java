package com.waenhancer.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemanticThemeTest {

    @Test
    public void allNamedPresetsGenerateCompleteTokens() {
        for (String preset : SemanticTheme.presets().keySet()) {
            SemanticTheme.Tokens light = SemanticTheme.fromPreset(preset, false);
            SemanticTheme.Tokens dark = SemanticTheme.fromPreset(preset, true);
            assertTrue(light.asMap().containsKey("primary"));
            assertTrue(light.asMap().containsKey("activeIndicator"));
            assertTrue(dark.asMap().containsKey("outgoingBubble"));
            assertNotEquals(light.get("surface"), dark.get("surface"));
        }
    }

    @Test
    public void normalTextTokensMeetFourPointFiveToOne() {
        for (String preset : SemanticTheme.presets().keySet()) {
            for (boolean dark : new boolean[]{false, true}) {
                SemanticTheme.Tokens tokens = SemanticTheme.fromPreset(preset, dark);
                assertTrue(SemanticTheme.contrastRatio(
                        tokens.get("link"), tokens.get("surface")) >= 4.5);
                assertTrue(SemanticTheme.contrastRatio(
                        tokens.get("onPrimary"), tokens.get("primary")) >= 4.5);
            }
        }
    }

    @Test
    public void controlsMeetThreeToOneAgainstSurface() {
        for (String preset : SemanticTheme.presets().keySet()) {
            for (boolean dark : new boolean[]{false, true}) {
                SemanticTheme.Tokens tokens = SemanticTheme.fromPreset(preset, dark);
                assertTrue(SemanticTheme.contrastRatio(
                        tokens.get("primary"), tokens.get("surface")) >= 3.0);
            }
        }
    }

    @Test
    public void unknownPresetFallsBackToGreen() {
        assertEquals(SemanticTheme.presetColor("green"),
                SemanticTheme.presetColor("unknown"));
    }
}
