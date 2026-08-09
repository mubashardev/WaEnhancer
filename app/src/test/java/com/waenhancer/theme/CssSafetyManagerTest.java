package com.waenhancer.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CssSafetyManagerTest {

    @Test
    public void acceptsBalancedLocalCss() {
        CssSafetyManager.ValidationResult result = CssSafetyManager.validate(
                "#toolbar { opacity: 0.9; }\n.chat { color: #ffffff; }");
        assertTrue(result.valid);
    }

    @Test
    public void rejectsRemoteImportAndJavascriptUrls() {
        assertFalse(CssSafetyManager.validate(
                "@import url(https://example.com/theme.css);").valid);
        assertFalse(CssSafetyManager.validate(
                ".x { background: url(javascript:alert(1)); }").valid);
    }

    @Test
    public void rejectsUnbalancedBraces() {
        assertFalse(CssSafetyManager.validate(".x { color: red;").valid);
    }

    @Test
    public void warnsAboutUniversalSelectors() {
        CssSafetyManager.ValidationResult result = CssSafetyManager.validate(
                "* { opacity: 0.8; }");
        assertTrue(result.valid);
        assertFalse(result.warnings.isEmpty());
    }
}
