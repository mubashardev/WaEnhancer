package com.waenhancer.xposed.spoofer;


import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.waenhancer.xposed.utils.Utils;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookBL {
    private static KeyPair keyPair_EC;
    private static KeyPair keyPair_RSA;
    private static final LinkedList<Certificate> certs_EC = new LinkedList<>();
    private static final LinkedList<Certificate> certs_RSA = new LinkedList<>();
    private static byte[] attestationChallengeBytes = new byte[1];



    private static KeyPair parseKeyPair(String key, java.security.PublicKey publicKey) throws Throwable {
        String base64Key = key.replaceAll("-----\\s*BEGIN[^-]*-----", "")
                             .replaceAll("-----\\s*END[^-]*-----", "")
                             .replaceAll("\\s", "");
        try {
            byte[] keyBytes = android.util.Base64.decode(base64Key, android.util.Base64.DEFAULT);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            java.security.PrivateKey privateKey;
            try {
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (Throwable t) {
                privateKey = KeyFactory.getInstance("EC").generatePrivate(spec);
            }
            return new KeyPair(publicKey, privateKey);
        } catch (Throwable t) {
            // Fallback to BouncyCastle PEMParser
            Object object;
            try (PEMParser parser = new PEMParser(new StringReader(key))) {
                object = parser.readObject();
            }
            if (object instanceof PEMKeyPair) {
                return new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) object);
            } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                java.security.PrivateKey privateKey = new JcaPEMKeyConverter().getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
                return new KeyPair(publicKey, privateKey);
            }
            throw t;
        }
    }

    private static String getAliasFromKeyPairGenerator(Object obj) {
        if (obj == null) return null;
        try {
            String alias = (String) XposedHelpers.getObjectField(obj, "mEntryAlias");
            if (alias != null) return alias;
        } catch (Throwable ignored) {}
        try {
            KeyGenParameterSpec spec = (KeyGenParameterSpec) XposedHelpers.getObjectField(obj, "mSpec");
            if (spec != null && spec.getKeystoreAlias() != null) {
                return spec.getKeystoreAlias();
            }
        } catch (Throwable ignored) {}
        try {
            Object spi = XposedHelpers.getObjectField(obj, "spi");
            if (spi != null) {
                return getAliasFromKeyPairGenerator(spi);
            }
        } catch (Throwable ignored) {}
        return null;
    }


    private static Certificate parseCert(String cert) throws Throwable {
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new java.io.ByteArrayInputStream(cert.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static Extension addHackedExtension(Extension extension) {
        try {
            ASN1Sequence keyDescription = ASN1Sequence.getInstance(extension.getExtnValue().getOctets());

            ASN1EncodableVector teeEnforcedEncodables = new ASN1EncodableVector();

            ASN1Sequence teeEnforcedAuthList = (ASN1Sequence) keyDescription.getObjectAt(7).toASN1Primitive();

            for (ASN1Encodable asn1Encodable : teeEnforcedAuthList) {

                ASN1TaggedObject taggedObject = (ASN1TaggedObject) asn1Encodable;

                if (taggedObject.getTagNo() == 704) continue;

                teeEnforcedEncodables.add(taggedObject);
            }

            SecureRandom random = new SecureRandom();

            byte[] bytes1 = new byte[32];
            byte[] bytes2 = new byte[32];

            random.nextBytes(bytes1);
            random.nextBytes(bytes2);

            ASN1Encodable[] rootOfTrustEncodables = {new DEROctetString(bytes1), ASN1Boolean.TRUE, new ASN1Enumerated(0), new DEROctetString(bytes2)};

            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEncodables);

            ASN1TaggedObject rootOfTrust = new DERTaggedObject(true, 704, rootOfTrustSeq);

            teeEnforcedEncodables.add(rootOfTrust);

            var attestationVersion = keyDescription.getObjectAt(0);
            var attestationSecurityLevel = keyDescription.getObjectAt(1);
            var keymasterVersion = keyDescription.getObjectAt(2);
            var keymasterSecurityLevel = keyDescription.getObjectAt(3);
            var attestationChallenge = keyDescription.getObjectAt(4);
            var uniqueId = keyDescription.getObjectAt(5);
            var softwareEnforced = keyDescription.getObjectAt(6);
            var teeEnforced = new DERSequence(teeEnforcedEncodables);

            ASN1Encodable[] keyDescriptionEncodables = {attestationVersion, attestationSecurityLevel, keymasterVersion, keymasterSecurityLevel, attestationChallenge, uniqueId, softwareEnforced, teeEnforced};

            ASN1Sequence keyDescriptionHackSeq = new DERSequence(keyDescriptionEncodables);

            ASN1OctetString keyDescriptionOctetStr = new DEROctetString(keyDescriptionHackSeq);

            return new Extension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescriptionOctetStr);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        return extension;
    }

    private static Extension createHackedExtensions() {
        try {
            SecureRandom random = new SecureRandom();

            byte[] bytes1 = new byte[32];
            byte[] bytes2 = new byte[32];

            random.nextBytes(bytes1);
            random.nextBytes(bytes2);

            ASN1Encodable[] rootOfTrustEncodables = {new DEROctetString(bytes1), ASN1Boolean.TRUE, new ASN1Enumerated(0), new DEROctetString(bytes2)};

            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEncodables);

            ASN1Integer[] purposesArray = {new ASN1Integer(0), new ASN1Integer(1), new ASN1Integer(2), new ASN1Integer(3), new ASN1Integer(4), new ASN1Integer(5)};

            ASN1Encodable[] digests = {new ASN1Integer(1), new ASN1Integer(2), new ASN1Integer(3), new ASN1Integer(4), new ASN1Integer(5), new ASN1Integer(6)};

            var Apurpose = new DERSet(purposesArray);
            var Aalgorithm = new ASN1Integer(3);
            var AkeySize = new ASN1Integer(256);
            var Adigest = new DERSet(digests);
            var AecCurve = new ASN1Integer(1);
            var AnoAuthRequired = DERNull.INSTANCE;
            var AosVersion = new ASN1Integer(130000);
            var AosPatchLevel = new ASN1Integer(202401);
            var AcreationDateTime = new ASN1Integer(System.currentTimeMillis());
            var Aorigin = new ASN1Integer(0);

            var purpose = new DERTaggedObject(true, 1, Apurpose);
            var algorithm = new DERTaggedObject(true, 2, Aalgorithm);
            var keySize = new DERTaggedObject(true, 3, AkeySize);
            var digest = new DERTaggedObject(true, 5, Adigest);
            var ecCurve = new DERTaggedObject(true, 10, AecCurve);
            var noAuthRequired = new DERTaggedObject(true, 503, AnoAuthRequired);
            var creationDateTime = new DERTaggedObject(true, 701, AcreationDateTime);
            var origin = new DERTaggedObject(true, 702, Aorigin);
            var rootOfTrust = new DERTaggedObject(true, 704, rootOfTrustSeq);
            var osVersion = new DERTaggedObject(true, 705, AosVersion);
            var osPatchLevel = new DERTaggedObject(true, 706, AosPatchLevel);

            ASN1Encodable[] teeEnforcedEncodables = {purpose, algorithm, keySize, digest, ecCurve, noAuthRequired, creationDateTime, origin, rootOfTrust, osVersion, osPatchLevel};

            ASN1Integer attestationVersion = new ASN1Integer(4);
            ASN1Enumerated attestationSecurityLevel = new ASN1Enumerated(1);
            ASN1Integer keymasterVersion = new ASN1Integer(41);
            ASN1Enumerated keymasterSecurityLevel = new ASN1Enumerated(1);
            ASN1OctetString attestationChallenge = new DEROctetString(attestationChallengeBytes);
            ASN1OctetString uniqueId = new DEROctetString("".getBytes());
            ASN1Sequence softwareEnforced = new DERSequence();
            ASN1Sequence teeEnforced = new DERSequence(teeEnforcedEncodables);

            ASN1Encodable[] keyDescriptionEncodables = {attestationVersion, attestationSecurityLevel, keymasterVersion, keymasterSecurityLevel, attestationChallenge, uniqueId, softwareEnforced, teeEnforced};

            ASN1Sequence keyDescriptionHackSeq = new DERSequence(keyDescriptionEncodables);

            ASN1OctetString keyDescriptionOctetStr = new DEROctetString(keyDescriptionHackSeq);

            return new Extension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescriptionOctetStr);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return null;
    }

    private static Certificate createLeafCert() {
        try {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(notBefore);
            calendar.add(Calendar.HOUR, 1);

            Date notAfter = calendar.getTime();

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(new X500Name("CN=chiteroman"), BigInteger.ONE, notBefore, notAfter, new X500Name("CN=Android Keystore Key"), keyPair_EC.getPublic());

            KeyUsage keyUsage = new KeyUsage(KeyUsage.keyCertSign);
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage);

            certBuilder.addExtension(createHackedExtensions());

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair_EC.getPrivate());

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            return new JcaX509CertificateConverter().getCertificate(certHolder);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return null;
    }

    private static Certificate hackLeafExistingCert(Certificate certificate) {
        try {
            X509CertificateHolder certificateHolder = new X509CertificateHolder(certificate.getEncoded());

            KeyPair keyPair;
            if (KeyProperties.KEY_ALGORITHM_EC.equals(certificate.getPublicKey().getAlgorithm())) {
                keyPair = keyPair_EC;
            } else {
                keyPair = keyPair_RSA;
            }

            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(notBefore);
            calendar.add(Calendar.HOUR, 1);

            Date notAfter = calendar.getTime();

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(certificateHolder.getIssuer(), certificateHolder.getSerialNumber(), notBefore, notAfter, certificateHolder.getSubject(), keyPair.getPublic());

            for (Object extensionOID : certificateHolder.getExtensionOIDs()) {

                ASN1ObjectIdentifier identifier = (ASN1ObjectIdentifier) extensionOID;

                if ("1.3.6.1.4.1.11129.2.1.17".equals(identifier.getId())) continue;

                certBuilder.addExtension(certificateHolder.getExtension(identifier));
            }

            Extension extension = certificateHolder.getExtension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"));

            certBuilder.addExtension(addHackedExtension(extension));

            ContentSigner contentSigner;
            if (KeyProperties.KEY_ALGORITHM_EC.equals(certificate.getPublicKey().getAlgorithm())) {
                contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
            } else {
                contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            }

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            return new JcaX509CertificateConverter().getCertificate(certHolder);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return certificate;
    }

    private static void parseBootloaderSpooferXml(String xmlContent) throws Throwable {

        // Create DOM parser
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlContent)));

        // Extract EC key
        org.w3c.dom.NodeList ecKeys = doc.getElementsByTagName("Key");
        for (int i = 0; i < ecKeys.getLength(); i++) {
            org.w3c.dom.Element keyElement = (org.w3c.dom.Element) ecKeys.item(i);
            String algorithm = keyElement.getAttribute("algorithm");

            // Get private key
            org.w3c.dom.NodeList privateKeyNodes = keyElement.getElementsByTagName("PrivateKey");
            if (privateKeyNodes.getLength() > 0) {
                String privateKeyContent = privateKeyNodes.item(0).getTextContent().replaceAll("\s{2,}", "");

                // Get certificate chain
                org.w3c.dom.NodeList certChainNodes = keyElement.getElementsByTagName("CertificateChain");
                if (certChainNodes.getLength() > 0) {
                    org.w3c.dom.Element certChainElement = (org.w3c.dom.Element) certChainNodes.item(0);
                    org.w3c.dom.NodeList certificateNodes = certChainElement.getElementsByTagName("Certificate");

                    if ("ecdsa".equals(algorithm) || "ec".equals(algorithm)) {
                        certs_EC.clear();
                        for (int j = 0; j < certificateNodes.getLength(); j++) {
                            String certContent = certificateNodes.item(j).getTextContent().replaceAll("\\s{2,}", "");
                            certs_EC.add(parseCert(certContent));
                        }
                        keyPair_EC = parseKeyPair(privateKeyContent, certs_EC.isEmpty() ? null : certs_EC.getFirst().getPublicKey());
                    } else if ("rsa".equals(algorithm)) {
                        certs_RSA.clear();
                        for (int j = 0; j < certificateNodes.getLength(); j++) {
                            String certContent = certificateNodes.item(j).getTextContent().replaceAll("\\s{2,}", "");
                            certs_RSA.add(parseCert(certContent));
                        }
                        keyPair_RSA = parseKeyPair(privateKeyContent, certs_RSA.isEmpty() ? null : certs_RSA.getFirst().getPublicKey());
                    }
                }
            }
        }
    }

    public static void hook(ClassLoader loader, SharedPreferences prefs) {

        boolean useCustomSpoofer = prefs.getBoolean("bootloader_spoofer_custom", false);
        String xmlContent = useCustomSpoofer 
                ? prefs.getString("bootloader_spoofer_xml", "") 
                : prefs.getString("bootloader_spoofer_default_xml", "");
        if (xmlContent != null && !xmlContent.isEmpty()) {
            try {
                parseBootloaderSpooferXml(xmlContent);
            } catch (Throwable t) {
                XposedBridge.log(t);
                if (useCustomSpoofer) {
                    Utils.showToast("Error parsing custom bootloader spoofer XML: " + t.getMessage(), 1);
                }
            }
        }

        if (keyPair_EC == null && keyPair_RSA == null) {
            XposedBridge.log("WAEX-HookBL: No valid spoofer keybox parsed. Bootloader spoofing hook skipped.");
            return;
        }


        final var systemFeatureHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String featureName = (String) param.args[0];

                if (PackageManager.FEATURE_STRONGBOX_KEYSTORE.equals(featureName))
                    param.setResult(Boolean.FALSE);
                else if (PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY.equals(featureName))
                    param.setResult(Boolean.FALSE);
                else if ("android.software.device_id_attestation".equals(featureName))
                    param.setResult(Boolean.FALSE);
                else if ("com.waenhancer.spoofer.active_check".equals(featureName))
                    param.setResult(Boolean.TRUE);
            }
        };

        try {
            Application app = com.waenhancer.xposed.core.FeatureLoader.mApp;

            Class<?> PackageManagerClass, SharedPreferencesClass;

            if (app == null) {
                PackageManagerClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", loader);
                SharedPreferencesClass = XposedHelpers.findClass("android.app.SharedPreferencesImpl", loader);
            } else {
                PackageManagerClass = app.getPackageManager().getClass();
                SharedPreferencesClass = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getClass();
            }

            XposedHelpers.findAndHookMethod(PackageManagerClass, "hasSystemFeature", String.class, systemFeatureHook);
            XposedHelpers.findAndHookMethod(PackageManagerClass, "hasSystemFeature", String.class, int.class, systemFeatureHook);

            XposedHelpers.findAndHookMethod(SharedPreferencesClass, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];

                    if ("prefer_attest_key".equals(key)) param.setResult(Boolean.FALSE);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(KeyGenParameterSpec.Builder.class, "setAttestationChallenge", byte[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    attestationChallengeBytes = (byte[]) param.args[0];
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            KeyPairGeneratorSpi keyPairGeneratorSpi_EC = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            XposedHelpers.findAndHookMethod(keyPairGeneratorSpi_EC.getClass(), "generateKeyPair", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    String alias = getAliasFromKeyPairGenerator(param.thisObject);
                    if ("WaEnhancerX_Device_Key".equals(alias)) {
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                    if (param.thisObject instanceof KeyPairGenerator) {
                        String algo = ((KeyPairGenerator) param.thisObject).getAlgorithm();
                        if ("EC".equalsIgnoreCase(algo)) {
                            return keyPair_EC;
                        } else if ("RSA".equalsIgnoreCase(algo)) {
                            return keyPair_RSA;
                        }
                    }
                    return keyPair_EC;
                }
            });
            KeyPairGeneratorSpi keyPairGeneratorSpi_RSA = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            XposedHelpers.findAndHookMethod(keyPairGeneratorSpi_RSA.getClass(), "generateKeyPair", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    String alias = getAliasFromKeyPairGenerator(param.thisObject);
                    if ("WaEnhancerX_Device_Key".equals(alias)) {
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                    if (param.thisObject instanceof KeyPairGenerator) {
                        String algo = ((KeyPairGenerator) param.thisObject).getAlgorithm();
                        if ("EC".equalsIgnoreCase(algo)) {
                            return keyPair_EC;
                        } else if ("RSA".equalsIgnoreCase(algo)) {
                            return keyPair_RSA;
                        }
                    }
                    return keyPair_RSA;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            KeyStoreSpi keyStoreSpi = (KeyStoreSpi) XposedHelpers.getObjectField(keyStore, "keyStoreSpi");
            XposedHelpers.findAndHookMethod(keyStoreSpi.getClass(), "engineGetCertificateChain", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String alias = (String) param.args[0];
                    if ("WaEnhancerX_Device_Key".equals(alias)) {
                        return; // Don't mock for our license key
                    }
                    Certificate[] certificates = null;

                    try {
                        certificates = (Certificate[]) param.getResultOrThrowable();
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    LinkedList<Certificate> certificateList = new LinkedList<>();

                    if (certificates == null) {

                        certificateList.addAll(certs_EC);
                        certificateList.addFirst(createLeafCert());

                    } else {
                        if (!(certificates[0] instanceof X509Certificate)) return;
                        X509Certificate x509Certificate = (X509Certificate) certificates[0];

                        byte[] bytes = x509Certificate.getExtensionValue("1.3.6.1.4.1.11129.2.1.17");

                        if (bytes == null || bytes.length == 0) return;

                        String algorithm = x509Certificate.getPublicKey().getAlgorithm();
                        if (KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)) {

                            certificateList.addAll(certs_EC);

                        } else if (KeyProperties.KEY_ALGORITHM_RSA.equals(algorithm)) {

                            certificateList.addAll(certs_RSA);
                        }
                        certificateList.addFirst(hackLeafExistingCert(x509Certificate));
                    }

                    param.setResult(certificateList.toArray(new Certificate[0]));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
