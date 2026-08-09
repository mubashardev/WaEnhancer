package com.waenhancer.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BottomBarPreferenceSchemaTest {

    @Test
    public void clampsLegacyStringAboveMaximum() {
        assertEquals(64f,
                BottomBarPreferenceSchema.normalize("floating_bottom_bar_radius", "96"),
                0.001f);
    }

    @Test
    public void acceptsIntegerAndAlignsStep() {
        assertEquals(35f,
                BottomBarPreferenceSchema.normalize(
                        "floating_bottom_bar_glass_opacity", 34),
                0.001f);
    }

    @Test
    public void usesDefaultForInvalidNumber() {
        assertEquals(24f,
                BottomBarPreferenceSchema.normalize(
                        "floating_bottom_bar_icon_size", "not-a-number"),
                0.001f);
    }

    @Test
    public void preservesNegativeOffsetWithinRange() {
        assertEquals(-12f,
                BottomBarPreferenceSchema.normalize(
                        "floating_bottom_bar_indicator_offset", -12f),
                0.001f);
    }
}
