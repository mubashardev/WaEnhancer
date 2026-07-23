package com.waenhancer.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.R;
import com.waenhancer.ui.helpers.BottomSheetHelper;
import com.waenhancer.utils.KeyboxValidator;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;

import java.security.KeyStore;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.res.ColorStateList;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.Nullable;
import com.waenhancer.activities.MainActivity;
import com.waenhancer.utils.ModuleStatus;
import com.waenhancer.xposed.utils.XPrefManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.TimeZone;

public class KeyboxVerification {

    private static final String TAG = "WAEX-KeyboxVerifyUI";

    public static void showDialog(final PreferenceFragmentCompat fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        final SharedPreferences prefs = resolvePrefs(fragment, context);
        if (prefs == null) {
            /* Log removed */
            return;
        }

        boolean customEnabled = prefs.getBoolean("bootloader_spoofer_custom", false);
        String xmlContent = prefs.getString("bootloader_spoofer_xml", "");
        boolean hasKeybox = xmlContent != null && !xmlContent.trim().isEmpty();

        boolean isCustom = (customEnabled && hasKeybox);
        String defaultXml = getFragmentDefaultSpooferXml(fragment);
        String targetXml = isCustom ? xmlContent : defaultXml;
        String label = isCustom ? "Custom KeyBox" : "Default Spoofer";

        // Generate hash of current keybox content (only for custom keybox)
        String xmlHash = isCustom ? getXmlMd5(targetXml) : "";
        long lastCheck = isCustom ? prefs.getLong("kb_hash_" + xmlHash + "_time", 0L) : prefs.getLong("default_kb_time", 0L);
        boolean useCache = lastCheck > 0 && (System.currentTimeMillis() - lastCheck < 3600000);

        if (!isCustom) {
            String lastSyncStr = prefs.getString("default_kb_last_updated", "");
            if (!lastSyncStr.isEmpty()) {
                long lastUpdatedMillis = parseIsoToMillis(lastSyncStr);
                if (lastUpdatedMillis > lastCheck) {
                    useCache = false;
                }
            }
        }

        // Run verification
        KeyboxValidator.ValidationResult result = KeyboxValidator.validate(targetXml);

        // If it's default spoofer, override key-matches to true since runtime generates matching leaf cert.
        if (!isCustom) {
            result.ecKeyMatchesCert = true;
            result.rsaKeyMatchesCert = true;
            result.ecKeyMatchesCertError = "";
            result.rsaKeyMatchesCertError = "";
        }

        // Hook/attestation status: ALWAYS checked live — never cached.
        boolean hookActive = isBootloaderSpooferActive(context, prefs);
        boolean attestationSpoofed = isBootloaderAttestationSpoofed();
        String currentPkg = context.getPackageName();
        boolean isInWhatsApp = "com.whatsapp".equals(currentPkg) || "com.whatsapp.w4b".equals(currentPkg);
        if (!isInWhatsApp && hookActive) {
            attestationSpoofed = true; // Fallback for manager app UI
        }

        // Calculate verification results
        Date now = new Date();
        boolean ecOk = !result.ecCerts.isEmpty() && 
                       !now.after(result.ecCerts.get(0).getNotAfter()) && 
                       result.ecChainValid && 
                       result.ecKeyMatchesCert;

        boolean bootloaderSpoofEnabled = prefs.getBoolean("bootloader_spoofer", false);
        boolean spooferOk = bootloaderSpoofEnabled && hookActive && attestationSpoofed;
        boolean isIntegrityOk = result.parsed && ecOk;

        String verifyStatus = (isIntegrityOk && spooferOk) ? "Pass" : "Failed";
        long verifyTime = useCache ? lastCheck : System.currentTimeMillis();

        // spooferScore is intentionally NOT cached — always derived from live checks above.
        int spooferScore = 0;
        if (hookActive) spooferScore += 5;
        if (attestationSpoofed) spooferScore += 5;

        int ecScore = 0;
        if (result.parsed && !result.ecCerts.isEmpty()) {
            ecScore += 10; // Key/certs parsed successfully
            if (result.ecChainValid) ecScore += 15;
            if (result.ecKeyMatchesCert) ecScore += 10;
            if (!now.after(result.ecCerts.get(0).getNotAfter())) ecScore += 5;
        }

        int rsaScore = 0;
        if (result.parsed && !result.rsaCerts.isEmpty()) {
            rsaScore += 5; // Key/certs parsed successfully
            if (result.rsaChainValid) rsaScore += 5;
            if (result.rsaKeyMatchesCert) rsaScore += 5;
            if (!now.after(result.rsaCerts.get(0).getNotAfter())) rsaScore += 5;
        }

        int integrityScore = 0;
        if (result.parsed) {
            integrityScore += 15; // Basic Integrity is supported
            if (ecOk) {
                integrityScore += 15; // Device Integrity is supported
            }
        }

        // Cert score = everything except spoofer (this is what gets persisted).
        int certScore = ecScore + rsaScore + integrityScore;
        // Display score = cert score + live spoofer score (spooferScore never cached).
        int score = spooferScore + certScore;

        if (isCustom) {
            prefs.edit()
                    .putString("keybox_verify_status", verifyStatus)
                    .putLong("keybox_verify_time", verifyTime)
                    .putString("kb_hash_" + xmlHash + "_status", verifyStatus)
                    .putLong("kb_hash_" + xmlHash + "_time", verifyTime)
                    .putInt("kb_hash_" + xmlHash + "_score", certScore) // spooferScore excluded from cache
                    .apply();
        } else {
            prefs.edit()
                    .putString("default_kb_status", verifyStatus)
                    .putLong("default_kb_time", verifyTime)
                    .putInt("default_kb_score", certScore) // spooferScore excluded from cache
                    .apply();
        }

        // Update tile summary immediately
        triggerUpdateSummary(fragment);

        // Show styled dialog
        BottomSheetDialog dialog = BottomSheetHelper.createStyledDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_keybox_verify, null);
        dialog.setContentView(view);

        ImageView statusIcon = view.findViewById(R.id.kb_status_icon);
        MaterialTextView titleText = view.findViewById(R.id.kb_title);
        MaterialTextView subtitleText = view.findViewById(R.id.kb_subtitle);
        View parseErrorCard = view.findViewById(R.id.kb_parse_error_card);
        MaterialTextView parseErrorMsg = view.findViewById(R.id.kb_parse_error_msg);
        View detailsContainer = view.findViewById(R.id.kb_details_container);

        if (!result.parsed) {
            statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            statusIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
            titleText.setText("Verification Failed");
            parseErrorCard.setVisibility(View.VISIBLE);
            parseErrorMsg.setText(result.errorMsg);
            detailsContainer.setVisibility(View.GONE);
        } else {
            parseErrorCard.setVisibility(View.GONE);
            detailsContainer.setVisibility(View.VISIBLE);

            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            // Populate Spoofer Hook details
            View spooferLoading = view.findViewById(R.id.kb_spoofer_loading);
            View spooferContent = view.findViewById(R.id.kb_spoofer_content);
            ImageView spooferActiveIcon = view.findViewById(R.id.kb_spoofer_active_icon);
            MaterialTextView spooferActiveText = view.findViewById(R.id.kb_spoofer_active_text);
            ImageView spooferAttestationIcon = view.findViewById(R.id.kb_spoofer_attestation_icon);
            MaterialTextView spooferAttestationText = view.findViewById(R.id.kb_spoofer_attestation_text);

            spooferActiveIcon.setImageResource(hookActive ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
            spooferActiveIcon.setImageTintList(ColorStateList.valueOf(hookActive ? 0xFF4CAF50 : 0xFFFF3B30));
            spooferActiveText.setText("Hook active status: " + (hookActive ? "Pass" : "Failed"));

            spooferAttestationIcon.setImageResource(attestationSpoofed ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
            spooferAttestationIcon.setImageTintList(ColorStateList.valueOf(attestationSpoofed ? 0xFF4CAF50 : 0xFFFF3B30));
            if (isInWhatsApp) {
                spooferAttestationText.setText("KeyStore attestation spoofing: " + (attestationSpoofed ? "Pass" : "Failed"));
            } else {
                spooferAttestationText.setText("KeyStore attestation spoofing: " + (attestationSpoofed ? "Pass" : "Failed (Runs in WhatsApp)"));
            }

            // Populate EC Chain
            ImageView ecStatusIcon = view.findViewById(R.id.kb_ec_status_icon);
            MaterialTextView ecStatusText = view.findViewById(R.id.kb_ec_status_text);
            ImageView ecExpiryIcon = view.findViewById(R.id.kb_ec_expiry_icon);
            MaterialTextView ecExpiryText = view.findViewById(R.id.kb_ec_expiry_text);
            ImageView ecChainIcon = view.findViewById(R.id.kb_ec_chain_icon);
            MaterialTextView ecChainText = view.findViewById(R.id.kb_ec_chain_text);
            ImageView ecMatchIcon = view.findViewById(R.id.kb_ec_match_icon);
            MaterialTextView ecMatchText = view.findViewById(R.id.kb_ec_match_text);

            if (result.ecCerts.isEmpty()) {
                ecStatusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                ecStatusIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                ecStatusText.setText("Not found");
                ecExpiryText.setText("No expiry date available");
                ecChainIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                ecChainIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                ecChainText.setText("No trust chain");
                ecMatchIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                ecMatchIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                ecMatchText.setText("No private key matches");
            } else {
                X509Certificate ecLeaf = result.ecCerts.get(0);
                boolean ecExpired = now.after(ecLeaf.getNotAfter());
                boolean ecValid = !ecExpired && result.ecChainValid && result.ecKeyMatchesCert;

                ecStatusIcon.setImageResource(ecValid ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                ecStatusIcon.setImageTintList(ColorStateList.valueOf(ecValid ? 0xFF4CAF50 : 0xFFFF3B30));
                ecStatusText.setText(ecValid ? "Pass" : (ecExpired ? "Expired" : "Failed"));

                ecExpiryIcon.setImageTintList(ColorStateList.valueOf(ecExpired ? 0xFFFF3B30 : 0xFF8E8E93));
                ecExpiryText.setText("Expires on: " + dateFmt.format(ecLeaf.getNotAfter()));

                ecChainIcon.setImageResource(result.ecChainValid ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                ecChainIcon.setImageTintList(ColorStateList.valueOf(result.ecChainValid ? 0xFF4CAF50 : 0xFFFF3B30));
                ecChainText.setText(result.ecChainValid ? "Trust chain verified" : "Broken trust chain");

                if (result.ecKeyPresent) {
                    ecMatchIcon.setImageResource(result.ecKeyMatchesCert ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                    ecMatchIcon.setImageTintList(ColorStateList.valueOf(result.ecKeyMatchesCert ? 0xFF4CAF50 : 0xFFFF3B30));
                    ecMatchText.setText(result.ecKeyMatchesCert ? "Private key matches leaf certificate" : "Private key mismatch");
                } else {
                    ecMatchIcon.setImageResource(R.drawable.ic_round_warning_24);
                    ecMatchIcon.setImageTintList(ColorStateList.valueOf(0xFFFF9500));
                    ecMatchText.setText("Private key missing");
                }
            }

            // Populate RSA Chain
            ImageView rsaStatusIcon = view.findViewById(R.id.kb_rsa_status_icon);
            MaterialTextView rsaStatusText = view.findViewById(R.id.kb_rsa_status_text);
            ImageView rsaExpiryIcon = view.findViewById(R.id.kb_rsa_expiry_icon);
            MaterialTextView rsaExpiryText = view.findViewById(R.id.kb_rsa_expiry_text);
            ImageView rsaChainIcon = view.findViewById(R.id.kb_rsa_chain_icon);
            MaterialTextView rsaChainText = view.findViewById(R.id.kb_rsa_chain_text);
            ImageView rsaMatchIcon = view.findViewById(R.id.kb_rsa_match_icon);
            MaterialTextView rsaMatchText = view.findViewById(R.id.kb_rsa_match_text);

            if (result.rsaCerts.isEmpty()) {
                rsaStatusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                rsaStatusIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                rsaStatusText.setText("Not found");
                rsaExpiryText.setText("No expiry date available");
                rsaChainIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                rsaChainIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                rsaChainText.setText("No trust chain");
                rsaMatchIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                rsaMatchIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                rsaMatchText.setText("No private key matches");
            } else {
                X509Certificate rsaLeaf = result.rsaCerts.get(0);
                boolean rsaExpired = now.after(rsaLeaf.getNotAfter());
                boolean rsaValid = !rsaExpired && result.rsaChainValid && result.rsaKeyMatchesCert;

                rsaStatusIcon.setImageResource(rsaValid ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                rsaStatusIcon.setImageTintList(ColorStateList.valueOf(rsaValid ? 0xFF4CAF50 : 0xFFFF3B30));
                rsaStatusText.setText(rsaValid ? "Pass" : (rsaExpired ? "Expired" : "Failed"));

                rsaExpiryIcon.setImageTintList(ColorStateList.valueOf(rsaExpired ? 0xFFFF3B30 : 0xFF8E8E93));
                rsaExpiryText.setText("Expires on: " + dateFmt.format(rsaLeaf.getNotAfter()));

                rsaChainIcon.setImageResource(result.rsaChainValid ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                rsaChainIcon.setImageTintList(ColorStateList.valueOf(result.rsaChainValid ? 0xFF4CAF50 : 0xFFFF3B30));
                rsaChainText.setText(result.rsaChainValid ? "Trust chain verified" : "Broken trust chain");

                if (result.rsaKeyPresent) {
                    rsaMatchIcon.setImageResource(result.rsaKeyMatchesCert ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
                    rsaMatchIcon.setImageTintList(ColorStateList.valueOf(result.rsaKeyMatchesCert ? 0xFF4CAF50 : 0xFFFF3B30));
                    rsaMatchText.setText(result.rsaKeyMatchesCert ? "Private key matches leaf certificate" : "Private key mismatch");
                } else {
                    rsaMatchIcon.setImageResource(R.drawable.ic_round_warning_24);
                    rsaMatchIcon.setImageTintList(ColorStateList.valueOf(0xFFFF9500));
                    rsaMatchText.setText("Private key missing");
                }
            }

            // Populate Play Integrity capabilities estimation
            ImageView basicIcon = view.findViewById(R.id.kb_basic_integrity_icon);
            MaterialTextView basicText = view.findViewById(R.id.kb_basic_integrity_text);
            ImageView deviceIcon = view.findViewById(R.id.kb_device_integrity_icon);
            MaterialTextView deviceText = view.findViewById(R.id.kb_device_integrity_text);

            boolean basicOk = result.parsed;
            boolean deviceOk = ecOk;

            basicIcon.setImageResource(basicOk ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
            basicIcon.setImageTintList(ColorStateList.valueOf(basicOk ? 0xFF4CAF50 : 0xFFFF3B30));
            basicText.setText("MEETS_BASIC_INTEGRITY: " + (basicOk ? "Pass" : "Failed"));

            deviceIcon.setImageResource(deviceOk ? R.drawable.ic_round_check_circle_24 : R.drawable.ic_round_error_outline_24);
            deviceIcon.setImageTintList(ColorStateList.valueOf(deviceOk ? 0xFF4CAF50 : 0xFFFF3B30));
            deviceText.setText("MEETS_DEVICE_INTEGRITY: " + (deviceOk ? "Pass" : "Failed"));

            // Populate dynamic section titles with scores
            MaterialTextView spooferTitle = view.findViewById(R.id.kb_spoofer_title);
            MaterialTextView ecTitle = view.findViewById(R.id.kb_ec_title);
            MaterialTextView rsaTitle = view.findViewById(R.id.kb_rsa_title);
            MaterialTextView integrityTitle = view.findViewById(R.id.kb_integrity_title);

            spooferTitle.setText("Bootloader Spoofer Hook (" + spooferScore + " / 10)");
            ecTitle.setText("EC Attestation Chain (" + ecScore + " / 40)");
            rsaTitle.setText("RSA Attestation Chain (" + rsaScore + " / 20)");
            integrityTitle.setText("Play Integrity Estimation (" + integrityScore + " / 30)");

            // Recommendation Card & Header styling
            View recCard = view.findViewById(R.id.kb_recommendation_card);
            MaterialTextView verdictTitle = view.findViewById(R.id.kb_verdict_title);
            MaterialTextView scoreText = view.findViewById(R.id.kb_score_text);
            MaterialTextView recText = view.findViewById(R.id.kb_recommendation_text);

            if (verdictTitle != null) {
                verdictTitle.setText("Final Recommendation");
            }
            scoreText.setText(score + " / 100");

            boolean spooferEnabled = prefs.getBoolean("bootloader_spoofer", false);
            String spooferStatusMsg = spooferEnabled 
                ? "Bootloader Spoofer is enabled and active. " 
                : "Note: Bootloader Spoofer is currently disabled in settings. ";

            if ("Pass".equals(verifyStatus)) {
                statusIcon.setImageResource(R.drawable.ic_round_verified_24);
                statusIcon.setImageTintList(ColorStateList.valueOf(0xFF4CAF50));
                titleText.setText(label + " Pass");
                recCard.setBackgroundTintList(ColorStateList.valueOf(0x114CAF50));
                
                boolean rsaOk = !result.rsaCerts.isEmpty() && 
                                !now.after(result.rsaCerts.get(0).getNotAfter()) && 
                                result.rsaChainValid && 
                                result.rsaKeyMatchesCert;
                String rsaNote = rsaOk ? "" : " Note that while the RSA chain is invalid or missing, the EC chain is the primary one required for Play Integrity.";
                recText.setText(spooferStatusMsg + "This spoofer configuration is valid and will pass Basic Integrity and Device Integrity. Strong Integrity is unsupported. Final Recommendation: Recommended for use." + rsaNote);
            } else {
                statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
                statusIcon.setImageTintList(ColorStateList.valueOf(0xFFFF3B30));
                titleText.setText(label + " Failed");
                recCard.setBackgroundTintList(ColorStateList.valueOf(0x11FF3B30));

                if (!spooferEnabled) {
                    recText.setText("Bootloader Spoofer is currently disabled in settings. You must enable it to spoof bootloader status and pass WhatsApp integrity checks. Final Recommendation: Enable Bootloader Spoofer.");
                } else if (!hookActive) {
                    recText.setText("Bootloader Spoofer is enabled but the Xposed hook is inactive. Ensure Xposed/LSPosed is running and WaEnhancer is enabled in LSPosed, then reboot your device. Final Recommendation: Check Xposed/LSPosed status.");
                } else if (!attestationSpoofed) {
                    recText.setText("KeyStore attestation spoofing is inactive. Ensure WaEnhancer is correctly enabled in LSPosed and hook is active. Final Recommendation: Check Xposed/LSPosed status.");
                } else if (result.ecCerts.isEmpty()) {
                    recText.setText(spooferStatusMsg + "The EC (ECDSA) attestation chain is missing. Modern Play Integrity requires an EC chain to perform hardware attestation. Final Recommendation: Do not use this configuration.");
                } else {
                    X509Certificate ecLeaf = result.ecCerts.get(0);
                    if (now.after(ecLeaf.getNotAfter())) {
                        String spooferRec = (!customEnabled || !hasKeybox) 
                            ? " (The built-in spoofer certificates have expired. To pass Device Integrity on modern WhatsApp versions, you must import a custom keybox.xml file.)" 
                            : "";
                        recText.setText(spooferStatusMsg + "The EC attestation certificate has expired. Final Recommendation: Do not use this configuration." + spooferRec);
                    } else if (!result.ecChainValid) {
                        recText.setText(spooferStatusMsg + "The EC attestation trust chain is broken. Final Recommendation: Do not use this configuration.");
                    } else if (!result.ecKeyMatchesCert) {
                        recText.setText(spooferStatusMsg + "The EC private key does not match the public key in the leaf certificate. Final Recommendation: Do not use this configuration.");
                    } else {
                        recText.setText(spooferStatusMsg + "The EC attestation chain is invalid. Final Recommendation: Do not use this configuration.");
                    }
                }
            }

            // Loading / cached transition setup
            View ecLoading = view.findViewById(R.id.kb_ec_loading);
            View ecContent = view.findViewById(R.id.kb_ec_content);
            View rsaLoading = view.findViewById(R.id.kb_rsa_loading);
            View rsaContent = view.findViewById(R.id.kb_rsa_content);
            View integrityLoading = view.findViewById(R.id.kb_integrity_loading);
            View integrityContent = view.findViewById(R.id.kb_integrity_content);
            MaterialButton okBtn = view.findViewById(R.id.bs_ok_btn);

            if (useCache) {
                subtitleText.setVisibility(View.VISIBLE);
                SimpleDateFormat timeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String extra = "";
                if (!isCustom) {
                    String lastSyncStr = prefs.getString("default_kb_last_updated", "");
                    if (!lastSyncStr.isEmpty()) {
                        extra = " | Keybox synced: " + formatLastUpdated(lastSyncStr);
                    }
                }
                subtitleText.setText("Cached report (verified: " + timeFmt.format(new Date(lastCheck)) + ")" + extra);

                spooferLoading.setVisibility(View.GONE);
                spooferContent.setVisibility(View.VISIBLE);
                ecLoading.setVisibility(View.GONE);
                ecContent.setVisibility(View.VISIBLE);
                rsaLoading.setVisibility(View.GONE);
                rsaContent.setVisibility(View.VISIBLE);
                integrityLoading.setVisibility(View.GONE);
                integrityContent.setVisibility(View.VISIBLE);
                recCard.setVisibility(View.VISIBLE);
                okBtn.setEnabled(true);
            } else {
                subtitleText.setVisibility(View.VISIBLE);
                subtitleText.setText("Performing live cryptographic audit...");
                okBtn.setEnabled(false);

                Handler loadingHandler = new Handler(Looper.getMainLooper());
                
                loadingHandler.postDelayed(() -> {
                    if (!dialog.isShowing()) return;
                    spooferLoading.setVisibility(View.GONE);
                    spooferContent.setVisibility(View.VISIBLE);
                }, 500);

                loadingHandler.postDelayed(() -> {
                    if (!dialog.isShowing()) return;
                    ecLoading.setVisibility(View.GONE);
                    ecContent.setVisibility(View.VISIBLE);
                }, 1000);

                loadingHandler.postDelayed(() -> {
                    if (!dialog.isShowing()) return;
                    rsaLoading.setVisibility(View.GONE);
                    rsaContent.setVisibility(View.VISIBLE);
                }, 1500);

                loadingHandler.postDelayed(() -> {
                    if (!dialog.isShowing()) return;
                    integrityLoading.setVisibility(View.GONE);
                    integrityContent.setVisibility(View.VISIBLE);
                }, 2000);

                loadingHandler.postDelayed(() -> {
                    if (!dialog.isShowing()) return;
                    recCard.setVisibility(View.VISIBLE);
                    String extra = "";
                    if (!isCustom) {
                        String lastSyncStr = prefs.getString("default_kb_last_updated", "");
                        if (!lastSyncStr.isEmpty()) {
                            extra = " | Keybox synced: " + formatLastUpdated(lastSyncStr);
                        }
                    }
                    subtitleText.setText("Audit complete" + extra);
                    okBtn.setEnabled(true);
                }, 2500);
            }
        }

        view.findViewById(R.id.bs_ok_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static String getFragmentDefaultSpooferXml(PreferenceFragmentCompat fragment) {
        try {
            Method method = null;
            Class<?> cls = fragment.getClass();
            while (cls != null && method == null) {
                try {
                    method = cls.getDeclaredMethod("getDefaultSpooferXml");
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (method == null) {
                /* Log removed */
                return "";
            }
            method.setAccessible(true);
            return (String) method.invoke(fragment);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get default spoofer xml: " + e.getMessage(), e);
            return "";
        }
    }

    private static void triggerUpdateSummary(PreferenceFragmentCompat fragment) {
        try {
            Method method = null;
            Class<?> cls = fragment.getClass();
            while (cls != null && method == null) {
                try {
                    method = cls.getDeclaredMethod("updateKeyboxVerifySummary");
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (method != null) {
                method.setAccessible(true);
                method.invoke(fragment);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update summary", e);
        }
    }

    private static String getXmlMd5(String xml) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(xml.getBytes("UTF-8"));
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (Exception e) {
            return String.valueOf(xml.hashCode());
        }
    }

    public static boolean isBootloaderSpooferActive(Context context, SharedPreferences prefs) {
        if (context == null) return false;
        
        String pkg = context.getPackageName();
        boolean isInWhatsApp = "com.whatsapp".equals(pkg) || "com.whatsapp.w4b".equals(pkg);
        
        if (isInWhatsApp) {
            try {
                return context.getPackageManager().hasSystemFeature("com.waenhancer.spoofer.active_check");
            } catch (Throwable ignored) {
                return false;
            }
        } else {
            boolean enabled = prefs.getBoolean("bootloader_spoofer", false);
            if (!enabled) return false;
            
            if (ModuleStatus.isModuleActive()) {
                return true;
            }
            if (MainActivity.isXposedFrameworkPresent(context)) {
                return true;
            }
            
            boolean hasXposed = false;
            try {
                Class.forName("de.robv.android.xposed.XposedBridge");
                hasXposed = true;
            } catch (ClassNotFoundException e) {
                PackageManager pm = context.getPackageManager();
                for (String managerPkg : new String[]{"org.lsposed.manager", "org.meowcat.edxposed.manager", "de.robv.android.xposed.installer"}) {
                    try {
                        pm.getPackageInfo(managerPkg, 0);
                        hasXposed = true;
                        break;
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            }
            if (!hasXposed) {
                long lastSeen = prefs.getLong("module_heartbeat", 0L);
                if (lastSeen > 0) {
                    long diff = System.currentTimeMillis() - lastSeen;
                    if (diff < 24 * 60 * 60 * 1000L) {
                        hasXposed = true;
                    }
                }
            }
            return hasXposed;
        }
    }

    public static boolean isBootloaderAttestationSpoofed() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias("waenhancer_attestation_test_key")) {
                keyStore.deleteEntry("waenhancer_attestation_test_key");
            }

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    "EC", "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    "waenhancer_attestation_test_key",
                    KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge("waenhancer_challenge".getBytes())
                    .build();
            keyPairGenerator.initialize(spec);
            keyPairGenerator.generateKeyPair();

            Certificate[] chain = keyStore.getCertificateChain("waenhancer_attestation_test_key");
            
            keyStore.deleteEntry("waenhancer_attestation_test_key");

            if (chain == null || chain.length == 0) {
                return false;
            }

            if (!(chain[0] instanceof X509Certificate)) {
                return false;
            }
            X509Certificate leaf = (X509Certificate) chain[0];
            byte[] extVal = leaf.getExtensionValue("1.3.6.1.4.1.11129.2.1.17");
            if (extVal == null || extVal.length == 0) {
                return false;
            }

            ASN1InputStream is = new ASN1InputStream(extVal);
            ASN1OctetString octetString = (ASN1OctetString) is.readObject();
            is.close();

            ASN1InputStream seqIs = new ASN1InputStream(octetString.getOctets());
            ASN1Sequence keyDescription = (ASN1Sequence) seqIs.readObject();
            seqIs.close();

            for (int index : new int[]{6, 7}) {
                if (keyDescription.size() > index) {
                    ASN1Encodable element = keyDescription.getObjectAt(index);
                    if (element instanceof ASN1Sequence) {
                        ASN1Sequence enforced = (ASN1Sequence) element;
                        for (int i = 0; i < enforced.size(); i++) {
                            ASN1Encodable obj = enforced.getObjectAt(i);
                            if (obj instanceof ASN1TaggedObject) {
                                ASN1TaggedObject tagged = (ASN1TaggedObject) obj;
                                if (tagged.getTagNo() == 704) {
                                    ASN1Sequence rootOfTrust = ASN1Sequence.getInstance(tagged, true);
                                    ASN1Boolean deviceLocked = (ASN1Boolean) rootOfTrust.getObjectAt(1);
                                    return deviceLocked.isTrue();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Attestation check failed: " + t.getMessage());
        }
        return false;
    }

    @Nullable
    private static SharedPreferences resolvePrefs(PreferenceFragmentCompat fragment, Context context) {
        try {
            SharedPreferences p = fragment.getPreferenceManager().getSharedPreferences();
            if (p != null) return p;
        } catch (Throwable ignored) {}

        try {
            Field field = null;
            Class<?> cls = fragment.getClass();
            while (cls != null && field == null) {
                try { field = cls.getDeclaredField("mPrefs"); } catch (NoSuchFieldException e) { cls = cls.getSuperclass(); }
            }
            if (field != null) {
                field.setAccessible(true);
                Object obj = field.get(fragment);
                if (obj instanceof SharedPreferences) return (SharedPreferences) obj;
            }
        } catch (Throwable ignored) {}

        try {
            return XPrefManager.getPref(context);
        } catch (Throwable ignored) {}

        return null;
    }

    private static String formatLastUpdated(String rawIso) {
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            isoFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = isoFmt.parse(rawIso);
            SimpleDateFormat outFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return outFmt.format(parsed);
        } catch (Exception e) {
            return rawIso;
        }
    }

    private static long parseIsoToMillis(String rawIso) {
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            isoFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            return isoFmt.parse(rawIso).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }
}