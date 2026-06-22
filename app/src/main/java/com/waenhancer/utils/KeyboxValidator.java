package com.waenhancer.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.waex.pro.IProService;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Public wrapper for custom KeyBox validation.
 * Delegates the actual validation logic via AIDL to the closed-source
 * pro companion application.
 */
public class KeyboxValidator {

    private static final String TAG = "WAEX-KeyboxValidator";

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
        
        Context context = com.waenhancer.App.getInstance();
        if (context == null) {
            result.errorMsg = "Android Context is not available.";
            return result;
        }

        // Verify if Pro plugin is installed first
        if (!isProPluginInstalled(context)) {
            result.errorMsg = "Verification submodule (Pro APK) is not installed.";
            return result;
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.waex.pro", "com.waex.pro.services.ProService"));

        final CountDownLatch latch = new CountDownLatch(1);
        final IProService[] serviceHolder = new IProService[1];
        ExecutorService connectionExecutor = null;

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "ProService connected");
                serviceHolder[0] = IProService.Stub.asInterface(service);
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "ProService disconnected");
                latch.countDown();
            }
        };

        try {
            boolean bound;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectionExecutor = Executors.newSingleThreadExecutor();
                bound = context.bindService(intent, Context.BIND_AUTO_CREATE, connectionExecutor, conn);
            } else {
                bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            }
            if (!bound) {
                result.errorMsg = "Failed to bind to Pro validation service.";
                return result;
            }

            try {
                boolean connected = latch.await(4, TimeUnit.SECONDS);
                IProService service = serviceHolder[0];
                if (connected && service != null) {
                    Bundle bundle = service.validateKeybox(xmlContent);
                    if (bundle != null) {
                        result.parsed = bundle.getBoolean("parsed", false);
                        result.errorMsg = bundle.getString("errorMsg", "");
                        result.ecKeyPresent = bundle.getBoolean("ecKeyPresent", false);
                        result.ecChainValid = bundle.getBoolean("ecChainValid", false);
                        result.ecChainError = bundle.getString("ecChainError", "");
                        result.ecKeyMatchesCert = bundle.getBoolean("ecKeyMatchesCert", false);
                        result.ecKeyMatchesCertError = bundle.getString("ecKeyMatchesCertError", "");

                        long ecExpiry = bundle.getLong("ecExpiryDate", 0L);
                        if (ecExpiry > 0) {
                            result.ecCerts = createPlaceholderCertList(ecExpiry);
                        }

                        result.rsaKeyPresent = bundle.getBoolean("rsaKeyPresent", false);
                        result.rsaChainValid = bundle.getBoolean("rsaChainValid", false);
                        result.rsaChainError = bundle.getString("rsaChainError", "");
                        result.rsaKeyMatchesCert = bundle.getBoolean("rsaKeyMatchesCert", false);
                        result.rsaKeyMatchesCertError = bundle.getString("rsaKeyMatchesCertError", "");

                        long rsaExpiry = bundle.getLong("rsaExpiryDate", 0L);
                        if (rsaExpiry > 0) {
                            result.rsaCerts = createPlaceholderCertList(rsaExpiry);
                        }
                    } else {
                        result.errorMsg = "Received empty validation data from Pro service.";
                    }
                } else {
                    result.errorMsg = "Pro validation service connection timed out.";
                }
            } catch (Exception e) {
                Log.e(TAG, "IPC calling failed: " + e.toString());
                result.errorMsg = "IPC error: " + e.getMessage();
            } finally {
                context.unbindService(conn);
                if (connectionExecutor != null) {
                    connectionExecutor.shutdownNow();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Binding failed: " + e.toString());
            result.errorMsg = "Binding error: " + e.getMessage();
            if (connectionExecutor != null) {
                connectionExecutor.shutdownNow();
            }
        }

        return result;
    }

    private static boolean isProPluginInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo("com.waex.pro", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static List<X509Certificate> createPlaceholderCertList(final long expiryMs) {
        List<X509Certificate> list = new ArrayList<>();
        list.add(new X509Certificate() {
            @Override
            public void checkValidity() {}

            @Override
            public void checkValidity(Date date) {}

            @Override
            public int getVersion() { return 3; }

            @Override
            public java.math.BigInteger getSerialNumber() { return java.math.BigInteger.ONE; }

            @Override
            public java.security.Principal getIssuerDN() { return null; }

            @Override
            public java.security.Principal getSubjectDN() { return null; }

            @Override
            public Date getNotBefore() { return new Date(); }

            @Override
            public Date getNotAfter() { return new Date(expiryMs); }

            @Override
            public byte[] getTBSCertificate() { return new byte[0]; }

            @Override
            public byte[] getSignature() { return new byte[0]; }

            @Override
            public String getSigAlgName() { return ""; }

            @Override
            public String getSigAlgOID() { return ""; }

            @Override
            public byte[] getSigAlgParams() { return null; }

            @Override
            public boolean[] getIssuerUniqueID() { return null; }

            @Override
            public boolean[] getSubjectUniqueID() { return null; }

            @Override
            public boolean[] getKeyUsage() { return null; }

            @Override
            public int getBasicConstraints() { return -1; }

            @Override
            public byte[] getEncoded() { return new byte[0]; }

            @Override
            public void verify(java.security.PublicKey key) {}

            @Override
            public void verify(java.security.PublicKey key, String sigProvider) {}

            @Override
            public String toString() { return ""; }

            @Override
            public java.security.PublicKey getPublicKey() { return null; }

            @Override
            public java.util.Collection<java.util.List<?>> getSubjectAlternativeNames() { return null; }

            @Override
            public boolean hasUnsupportedCriticalExtension() { return false; }

            @Override
            public java.util.Set<String> getCriticalExtensionOIDs() { return null; }

            @Override
            public java.util.Set<String> getNonCriticalExtensionOIDs() { return null; }

            @Override
            public byte[] getExtensionValue(String oid) { return null; }
        });
        return list;
    }
}
