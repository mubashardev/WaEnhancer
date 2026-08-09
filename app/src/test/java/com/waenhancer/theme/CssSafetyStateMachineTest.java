package com.waenhancer.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.waenhancer.testing.FakeSharedPreferences;

import org.junit.Before;
import org.junit.Test;

/** Covers the save / test / rollback / safe-mode state machine, not just validate(). */
public class CssSafetyStateMachineTest {

    private static final String FIRST = ".a { color: red; }";
    private static final String SECOND = ".b { color: blue; }";

    private FakeSharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
    }

    @Test
    public void saveStoresLastKnownGoodAndActivatesCss() {
        assertTrue(CssSafetyManager.save(prefs, FIRST).saved);

        assertEquals(FIRST, prefs.getString("custom_css", ""));
        assertEquals(FIRST, prefs.getString(CssSafetyManager.KEY_LAST_VALID, ""));
        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void invalidCssIsNeverPersisted() {
        CssSafetyManager.save(prefs, FIRST);

        CssSafetyManager.SaveResult result = CssSafetyManager.save(prefs, ".broken { color: red;");

        assertFalse(result.saved);
        assertEquals(FIRST, prefs.getString("custom_css", ""));
        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void rollbackRestoresThePreviousSave() {
        CssSafetyManager.save(prefs, FIRST);
        CssSafetyManager.save(prefs, SECOND);
        assertEquals(SECOND, CssSafetyManager.effectiveCss(prefs));

        assertTrue(CssSafetyManager.rollback(prefs));

        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void safeModeSuppressesCssWithoutDestroyingIt() {
        CssSafetyManager.save(prefs, FIRST);

        CssSafetyManager.enableSafeMode(prefs);
        assertEquals("", CssSafetyManager.effectiveCss(prefs));
        assertEquals(FIRST, prefs.getString("custom_css", ""));

        CssSafetyManager.disableSafeMode(prefs);
        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void temporaryTestAppliesThenExpiresBackToTheSavedTheme() {
        CssSafetyManager.save(prefs, FIRST);

        assertTrue(CssSafetyManager.beginTest(prefs, SECOND, 60_000L).saved);
        assertEquals(SECOND, CssSafetyManager.effectiveCss(prefs));
        assertEquals(FIRST, prefs.getString("custom_css", ""));

        // Expiry is reconciled from the stored deadline, not from a live timer.
        prefs.edit().putLong(CssSafetyManager.KEY_TEST_EXPIRES_AT,
                System.currentTimeMillis() - 1L).commit();

        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void savingAfterATestDiscardsTheTemporaryCss() {
        CssSafetyManager.save(prefs, FIRST);
        CssSafetyManager.beginTest(prefs, SECOND, 60_000L);

        CssSafetyManager.save(prefs, FIRST);

        assertFalse(prefs.contains(CssSafetyManager.KEY_TEST_CSS));
        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void threeFailuresArmSafeModeAutomatically() {
        CssSafetyManager.save(prefs, FIRST);

        CssSafetyManager.recordThemeFailure(null, prefs);
        CssSafetyManager.recordThemeFailure(null, prefs);
        assertFalse(prefs.getBoolean(CssSafetyManager.KEY_SAFE_MODE, false));

        CssSafetyManager.recordThemeFailure(null, prefs);

        assertTrue(prefs.getBoolean(CssSafetyManager.KEY_SAFE_MODE, false));
        assertEquals(3, prefs.getInt(CssSafetyManager.KEY_FAILURE_COUNT, 0));
        assertEquals("", CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void aSuccessfulPassClearsTheFailureCounter() {
        CssSafetyManager.recordThemeFailure(null, prefs);
        CssSafetyManager.recordThemeFailure(null, prefs);

        CssSafetyManager.clearFailureState(null, prefs);

        assertEquals(0, prefs.getInt(CssSafetyManager.KEY_FAILURE_COUNT, 0));
    }

    @Test
    public void corruptedActiveCssFallsBackToLastKnownGood() {
        CssSafetyManager.save(prefs, FIRST);
        // Simulate an external writer corrupting the active sheet.
        prefs.edit().putString("custom_css", ".broken { color: red;").commit();

        assertEquals(FIRST, CssSafetyManager.effectiveCss(prefs));
    }

    @Test
    public void bracesInsideCommentsDoNotFailValidation() {
        assertTrue(CssSafetyManager.validate("/* TODO: close this { */ .a { color: red; }").valid);
    }

    @Test
    public void remoteImportsAreRejectedInEveryForm() {
        assertFalse(CssSafetyManager.validate("@import url('http://evil/x.css');").valid);
        assertFalse(CssSafetyManager.validate("@import\"//evil/x.css\";").valid);
        assertFalse(CssSafetyManager.validate("@import '//evil/x.css';").valid);
    }
}
