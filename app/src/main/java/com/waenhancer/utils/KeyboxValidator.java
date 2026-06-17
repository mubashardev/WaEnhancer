package com.waenhancer.utils;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Public wrapper for custom KeyBox validation.
 * The actual validation logic is located in the obfuscated pro submodule
 * to keep the proprietary implementation details private.
 */
public class KeyboxValidator {

    public static class ValidationResult {
        public boolean parsed = false;
        public String errorMsg = "";

        public boolean ecKeyPresent = false;
        public List<X509Certificate> ecCerts = new ArrayList<>();
        public boolean ecChainValid = false;
        public String ecChainError = "";
        public boolean ecKeyMatchesCert = false;
        public String ecKeyMatchesCertError = "";

        public boolean rsaKeyPresent = false;
        public List<X509Certificate> rsaCerts = new ArrayList<>();
        public boolean rsaChainValid = false;
        public String rsaChainError = "";
        public boolean rsaKeyMatchesCert = false;
        public String rsaKeyMatchesCertError = "";
    }

    public static ValidationResult validate(String xmlContent) {
        ValidationResult result = new ValidationResult();
        if (!com.waenhancer.BuildConfig.HAS_PRO_FEATURES) {
            result.errorMsg = "Verification is only supported in builds compiled with pro features.";
            return result;
        }

        try {
            Class<?> implClass = Class.forName("com.waenhancer.pro.utils.KeyboxValidatorImpl");
            java.lang.reflect.Method method = implClass.getMethod("validate", String.class, Object.class);
            method.invoke(null, xmlContent, result);
        } catch (Exception e) {
            result.parsed = false;
            result.errorMsg = "Failed to load verification submodule: " + e.getMessage();
        }
        return result;
    }
}
