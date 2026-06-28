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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.SecureRandom;
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

    static {
        try {

            String str = """
                    -----BEGIN EC PRIVATE KEY-----
                    MHcCAQEEICOd8gK7eF5g2diA0hdH8N5/ucVpF3Nto3xuU5yXNGqioAoGCCqGSM49
                    AwEHoUQDQgAE6lXy73P+EknjegJdmuA07/wlu7RPC2CCam0Tiy60PvlOCsWSECTg
                    8BwbTBIzZ2qgSv2nKumUWzrLpWpc0v8PBw==
                    -----END EC PRIVATE KEY-----""";

            keyPair_EC = parseKeyPair(str);

            str = """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIG4wIBAAKCAYEAu+QIhJj7aDTA01fBSB6UX3He4F+I892RZ6sA8SgBubNInrki
                    WDdxOUZZA6O/qddxNuQVlaFWygQwKRabL2t4hueYTHyrvgzJyBWV6gStc1gjqvTo
                    BAThO5Lq2NsQUs0Qe16UXxRwBPwdKbxckG4F/X03BNHVV0rDCep3tovLEgmdp/gt
                    s2YhWuwYEAdhHEU7wH6TWrLGKIe4I9d9rTvMNPzN539LuPgXL18DCuEJUkV4AdFi
                    UnapDjT6uTbQEwekT38R8jprerPUQeQofN1oJBpFXzEBud7y7wukKHNDSoh6jZMj
                    +lWKbE9xiajw9X7ceRAL2ZIaWsUOqSTmg+Y9CDS+azLCjxpYkSvDwgZPNWonbGHr
                    nIzxhlLEYieNsEPt1rNmnxZkTvvwBNkkP/iR2awssZYDWpEoEnyBwraeOwDyE7cl
                    Er3zrYOn4q+ylrpVK3PM8ygUtqBZh0cNuiaFJtrficSepZeaGPt75jLzQRGPGAVr
                    deSFagkcLpvn6C2vAgMBAAECggGABuUeXudSSoetD9RnlmLw5PPDzw4Sc4iM/nXr
                    Ce6C6bKnlpOKrBwUvppTR+vpa60pTW9fT2dlTPKMZeWbekkCWkkDcMMedlH30azh
                    HH5hcxsn6+0i2ornTQ1eKukXF0LJOQ3GehrA5Z3u4Ao2h2JSO/QtYbLllld7AtEk
                    5YEJybaqn3BfFPdJgBGr7GKo8KWlxLGgbLKkzPX2DvKofQP1wXgJglZMjBQmnalp
                    7itF8Uv1VHO/nPEX0RqmnMdjKV+dWuiX34hf5SlCbOCSl2KovKrCSFgRVTDFQTmi
                    rgPj7q00Oblf+qtWcyXAJ9Fy0yOSn0jDB9zT2fIwMIT8fXwTIhIEirhYRK56ca5E
                    zfZ3Ji9+LyMC/xP12lzeZ/hYw5FYoZOWp8bPabjTFauURWwTWiKV650Be4+Rr1EP
                    z4BFJieKV1WP3+6Vhsp46XtkezaQXwwbdhLg4mKD+4LjxZtSJLTCaQ3e/BgZpwxz
                    stTtHbXTlqENJUTOZpbAVKOCBgJRAoHBANz8FFiI6bMfWXTZ3BAE3M4n8+DnCuwR
                    6chvI0L4DfCXj1Je70lT3WBdV9zL/vT1X1ndhFV54vetvDF8WHonbpw2an61Mkpr
                    njYzRQy2Aq9T7aTQMSdiWlER7CUcCCMl+dEvUAOXrT0M2Ozw3jFNd6P4EFNhC6Ew
                    exVc2C/3UIp+fZ8OWEmi+G/o15y1Bvi1kMoZD8JrLxAhpFawQrSMhLsc0vQkxxeP
                    RLI/Et8u1nYD04fGnc+JJsDNaL/+AzgHRwKBwQDZqYwCnycjUa0ihAWJjzDSpa7w
                    5I513c6ACWghxl01M/1VpRGqlgwoIdyfLH9no85axAGKtZKHK/VAttFC2JtQFr63
                    R7QjLeqO1wUEX511hP7ZN9/qr6d1rp0tvkVfwn7wvF97uY8YuJxqi1p+nIsgGhRB
                    y0H2XXTMSt2QnBuCPTaEouxY3RaOnAQd7GkhlGfQidcJh63YYAM2CAUxxC4wdoF7
                    FvMgtGAi62EeyxBFeK2SF95T3gNKDiaroHvkKlkCgcBuBZ9HmRrpkIEkWVdkLleU
                    2HVmkwFwGVcQ8KxYqlGeaIb11shB9NwyHycgifwtD4Fip5Q8Tkv/TmN1K9iNMNa0
                    Na992E7qmHwTtiD5vCDIE/wsY28lkaUv2cF9lGBEx6KCUJEAyOJ6k8vo499sIoqf
                    e2D9ckKtBQsyzp/f+b0CxwlaSHUSbG5OoVm/7q1C5Hrq8+FRxbWPzYAZnPYJGDD5
                    S9eHsEvjYfQs3pRRw+sIpM0LO4rUig9eTKaLeDc4DP8CgcBXbyoU64W3RFn+IXZv
                    +ZstIu0RS16GrmEDQcQYvSw38Ph07OgZ1Ehx3phXQHK1WTHNeCr+Y03HCrtsEYQi
                    DAznsRtPWHheIVW1p14WkaoYySHuc+l4xrLILSpqc6I+g0ymu6THeJSo44/BpNTn
                    Q08HyDIW8+U9Z/FBF1nFe0/5k0lRInk6gSVMiBOHSa45lPnW5WgCJgSJhJgFnlcn
                    1JyRTylYHrHvk0WDAXZz/jI9FerzYq8mlWpQ1zplewQJdZECgcEA2n4Qpg12Em4P
                    osYJAs0GqMQgjhlTNffwe6kFj46Iy8OtR1mG9OXRmzq6NsEIQaKnn28JWibkkz09
                    2NniF6lt53wtJQWSSXFf3LTawRiS7lSUmqZfULiilUoIMmfeBpaj7ZT6NvXzY9PP
                    mYKWVJ+DP1Is12hcGEU9UByDg4/BXzvGzw9o4FcDnNYCepaOj+hItwjqs2oGEWK9
                    drRPQm72SCNy/AITromUFxTV5Cl8tg1IQS4OtiLQEiyco21YyAUu
                    -----END RSA PRIVATE KEY-----""";

            keyPair_RSA = parseKeyPair(str);

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIB8zCCAXqgAwIBAgIRAJNp8u+IlBm41dacSVioZEEwCgYIKoZIzj0EAwIwOTEM
                    MAoGA1UEDAwDVEVFMSkwJwYDVQQFEyAxMWRlZjVlMjVlOTBjZGVhY2ViNjUwYzZj
                    YzkyNWJmMTAeFw0yMDA0MjgxODUyMDlaFw0zMDA0MjYxODUyMDlaMDkxDDAKBgNV
                    BAwMA1RFRTEpMCcGA1UEBRMgODA4Y2I4NGYxYmJhYzk2YWFlNTdkMTMzMDVhMDkz
                    ZTMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATqVfLvc/4SSeN6Al2a4DTv/CW7
                    tE8LYIJqbROLLrQ++U4KxZIQJODwHBtMEjNnaqBK/acq6ZRbOsulalzS/w8Ho2Mw
                    YTAdBgNVHQ4EFgQUqarPnmPZ5Z1nkqZBOA1GsSuZe6AwHwYDVR0jBBgwFoAUba4Y
                    fI7phq7FCcDRVxeT7XrtrxgwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
                    AgQwCgYIKoZIzj0EAwIDZwAwZAIwdHoak9MU22sLH21950DJnxhkNHmpFPqplB3I
                    n5D0Mq2ttqWsXdilh/AFoX+Jo7UpAjB2a2siHNqxGJOX2f/+Zo7vCreVBCMtlGt+
                    xKfNS9swrMrfuWp2br5L2b9TufK/1as=
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIDkzCCAXugAwIBAgIQbS6dRno28uOL1jMOAJztvzANBgkqhkiG9w0BAQsFADAb
                    MRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTIwMDQyODE4NTEyNFoXDTMw
                    MDQyNjE4NTEyNFowOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyAxMWRlZjVlMjVl
                    OTBjZGVhY2ViNjUwYzZjYzkyNWJmMTB2MBAGByqGSM49AgEGBSuBBAAiA2IABH25
                    Qj73BqRX+/NPHaqFU9/5ZjM5xiEsXE3EDv2kniqTK9TtM9IOe2bPPdpLHNZk5LhG
                    XMlFhhsJe2N1KN+goVx1vh3GsfGqWAD3ZEm4nem1l7U3i4m2SXGYFtEQCc/lU6Nj
                    MGEwHQYDVR0OBBYEFG2uGHyO6YauxQnA0VcXk+167a8YMB8GA1UdIwQYMBaAFDZh
                    4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQD
                    AgIEMA0GCSqGSIb3DQEBCwUAA4ICAQCfMm8ozF5fbo1iBldM6WVwkN/n0cd7/T1X
                    LpfAgmo2XpC+k+jFBDWiWhWhKohpS64aRVGXwsFN8pMmqxqTqhlYhmExJi/BJnhD
                    IEACLs42bCKzOYJS0qzGDGL14kzlniorD3IFvq+4U4FMBuXWnqljCKA8QMB2Hu4q
                    HDRRZOyiTXZOfCASRuW6Pkvg14gSqRIJ1KJb2WItCRuwURx+/O3UimGjZorvnQUb
                    M5nT6yF+Csk8C6lfNgJEXxsnP40wnNK109aEKqKh5DM9vNqHN4Vb2ZDj+UdJB2Ax
                    BX5g9zyL+SA5ksFZ5bwNx9EH5rqBUSeH+xZULvai/YB0Uqxyc5YcP4oM40lYqa72
                    wqw+JArMYIiJz83XgjXizEcGIgBDyzpCf8ltnaQ/FO3BvMsSdWLJ3EUOw5sBlQKW
                    rtvYvt9/9OinjtpnAoRVXbRuXgmhhFt27qonN7XziRli2mH234CHAxcxHG+elqjn
                    iouW9h6cL6jZpg41pKoCYL4Qa8xnuIYytwfn8WOqW9u0Dfain2CTj2HImqWpCXNC
                    /Xn84KgGVJgL6+Crdn9IfR3fdVTG0mRT1lPFm2Iz/J3J4uwvvA0+ZzJjE8hto73r
                    TbK6PdDuAObOU8t7nCt8R+pH6YJBd2GVRlf5soK+iZW7f8NODjy/3Vb52/MfwnBQ
                    Fv2AQqR3YA==
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAz
                    NzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                    AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
                    IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
                    VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnu
                    XKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83U
                    h6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cno
                    L/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2ok
                    QBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vA
                    D32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAI
                    mMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoW
                    Fua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91
                    oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09o
                    jm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUB
                    ZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCH
                    ex0SdDrx+tWUDqG8At2JHA==
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIE4DCCAsigAwIBAgIRALK/VaUQzuEAZlfWFPGTilIwDQYJKoZIhvcNAQELBQAw
                    OTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyBkZDFmOGRiM2JmOTdlMGMzYmU4NGQ5
                    MWI0NjZiZmVmNTAeFw0yMTA2MTYxOTI1MjNaFw0zMTA2MTQxOTI1MjNaMDkxDDAKB
                    gNVBAwMA1RFRTEpMCcGA1UEBRMgNmZlMmY5MTljMWU5ZDg3NzY2NTU2YjlhOWYw
                    NzFjNTEwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQC75AiEmPtoNMDT
                    V8FIHpRfcd7gX4jz3ZFnqwDxKAG5s0ieuSJYN3E5RlkDo7+p13E25BWVoVbKBDAp
                    Fpsva3iG55hMfKu+DMnIFZXqBK1zWCOq9OgEBOE7kurY2xBSzRB7XpRfFHAE/B0p
                    vFyQbgX9fTcE0dVXSsMJ6ne2i8sSCZ2n+C2zZiFa7BgQB2EcRTvAfpNassYoh7gj
                    132tO8w0/M3nf0u4+BcvXwMK4QlSRXgB0WJSdqkONPq5NtATB6RPfxHyOmt6s9RB
                    5Ch83WgkGkVfMQG53vLvC6Qoc0NKiHqNkyP6VYpsT3GJqPD1ftx5EAvZkhpaxQ6p
                    JOaD5j0INL5rMsKPGliRK8PCBk81aidsYeucjPGGUsRiJ42wQ+3Ws2afFmRO+/AE
                    2SQ/+JHZrCyxlgNakSgSfIHCtp47APITtyUSvfOtg6fir7KWulUrc8zzKBS2oFmH
                    Rw26JoUm2t+JxJ6ll5oY+3vmMvNBEY8YBWt15IVqCRwum+foLa8CAwEAAaNjMGEw
                    HQYDVR0OBBYEFLILTMjpvEOZhTE2NO/vxzBoDuchMB8GA1UdIwQYMBaAFDlk3/vK
                    LASjKdwFd8yIHu5hZ95SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIE
                    MA0GCSqGSIb3DQEBCwUAA4ICAQA3pRvTnluSLiMVky9j36Q4Zq+8HNDZeRKnODz6
                    UH1wPxmMXP810zvcNDcZyWziJKgxVYnrrNGcvLCQSviDq9aZyGyUykjLLqVvtNe0
                    aRTglBprmyPYRlFoj7dznfNylWSZ532XdzHARveLUfApmYZ4TZOOx4mQmZLWPO/F
                    dYUlBRgzCQ6T4JY75z0WUYWyfYh+i3p8qKaGoq+zhuehlOBlWqy8untmd3LeBZUj
                    A/fRn9hz4+SYSKV5zAgyq0LU1IrdvFDEhPi6EmnXH0m49zhkAjNY17FYkBjkeB80
                    H0bzGYdqA2LDvLyxy4ygquFyPKVehEVS9/k0QJJlEzOeCE17Kk5DoO4gw/Gk/jyS
                    xEacJ+19OxKbmtB46f/CjONBJr7MVVk03dAtDoKS31ypKj6VvQOCDssbly21TbpJ
                    f5257htiHLm8wKohdi9jmyF+9ne6QyL3zozquOimrLbsNBetPP+ZNRVD/X8oxnfj
                    HEsYeSPWxHE6KPtCMrR5F/YBRMRLi4sW/MU4cS5L3/YZwfDCVLrmHavix89rfrsT
                    8g8r6LL6BAFDR59O/66LZuPppM0wR5L4lrEuzBKF7T9baqnZspozFSloBPniakbK
                    H1ZFORhUdRKi4IJN5pBU7JmHnlZHIfY/5wS5VAJVE1Z2oKmhaHbpU/XkH1mhk3wM
                    JJF7xQ==
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFQjCCAyqgAwIBAgIRALgkQE6xGEXpbXg5aRaqEUswDQYJKoZIhvcNAQELBQAw
                    GzEZMBcGA1UEBRMQZjkyMDA5ZTg1M2I2YjA0NTAeFw0yMTA2MTYxOTI2MDVaFw0z
                    MTA2MTQxOTI2MDVaMDkxDDAKBgNVBAwMA1RFRTEpMCcGA1UEBRMgZGQxZjhkYjNi
                    Zjk3ZTBjM2JlODRkOTFiNDY2YmZlZjUwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAw
                    ggIKAoICAQC5bMgIOWvC5Ybfkij01Zm9l1AAbZLbu1+g7ZFj6ZCF9T5UcS67qXn/
                    rHr7jr/6s2I+gdwvefU2y5+zHX8DVw2hmXBh11G5dbxHpGShh6gUpIwYkjPp+8T3
                    CzW4Dwxop0hEYzjFwuprTmqB1DrgE2dH4Ml/naSSvs97mGPWNuPfAD5HkskyQwS6
                    gzjR9S4d3dY8PmHSTfeENNvPRqpSbI2/49iBXxBN8tUmyP4Q14eUOMJGlvryD5E1
                    YbVXGM5m+0kRazzXkrkM7rgTLLGXGeCR3J8hJ/8MqSvIIQ4Vw73fkksS6Xakf9WE
                    hfDAB9QpRzVioHkw4Bl68T9zVjrcOfCXB8zLTjJzKpQh2ooNeU926m7pqRwXOojx
                    tP9PY14awAfyA2qzv+RiaMRc0IXDmY93OFMt06mvrowmfwVJZQn75Y6jTTRn+Dw9
                    jKC/aO2DgcMugp77rU8yhNSi2xqbWiOHd8pSp2/8PCPVLjjZXFSeVl4Hs9EdUKq4
                    prfWNJekF/rU5JXFz3/VjLRV1DLSx3RrdYQRIc8Bjmr3cxr2H9Y/wnOOVmhG3ABY
                    EYUKxpLMAInO5xOjRDlCR/iaPLTeCYh9hj2Pf6L5Byo+cldrMe0b0nbqPPGnsQnDJ
                    HE8f9NrHkcexJ31yxVG59BHVjOJks6TL1W9dsMd1uAHJMd5iSfKFwIDAQABo2Mw
                    YTAdBgNVHQ4EFgQUOWTf+8osBKMp3AV3zIge7mFn3lIwHwYDVR0jBBgwFoAUNmHh
                    AHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
                    AgQwDQYJKoZIhvcNAQELBQADggIBABcHDeZJQh5/WQac8/2rC1hN/sN6W/SQ4K2+
                    glWPXkKnG5QfRZmGdzOdk0uShOu8YqFYi1ka4sipDLDOrieGPTRJ5MVtvhMre+X6
                    9Lh7AQnqGGgGfmcRRhAKfZBrU4k+DVcnyM30FkdD1ZEkxXMKQ8eyTzqYfhZxPHj7
                    ohRSwE/wEBrM/Fiu8k/PgjnqoLtFi/RfLH7qVwcUQ7EagNVlnxDkzofKbxr2Udr4
                    Gq8FxmHVT+4OOooqgk+qq1wpXD4Dn/14wISVP9wuVIsvYfkntMpGnHu7p0Q4V8z/
                    DbYob3SIWkgvjeY/5L0zeK38dRvyuVJ7tl950NrWkGnPFimA+rjiE9hwos0mAe9t
                    AnMcx3nuiV7QZtDgMayURcRUB1Tq/LKA9+3jRZx06aW9gk+WzgfBvX+Ntvu7GVMw
                    piDamW3F16qsWxc5k2Utca6tzv7Pv0m0+D6TsN2uOxvTMcqsOqMw67pqePdeQydl
                    8cNgu4k4XQpZPaYYbdhAf3PxBio7Itk5f6XrgSmdJNv1da5bACUzHUBZISqxOpc7
                    AWIUO4fL8oLM07YT9yV+O4Kf6q7WZ4Oc6Nns5w1T++XUyp3vL5tGZSdHqDbN2q1q
                    YTEyUQE+/ajaDihGTxZ4oUC2B3YGzD9Wr866/mA2tgwKhjYl3fhJWjnKlFO921PH
                    emRD3UR3
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAz
                    NzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                    AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
                    IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
                    VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnu
                    XKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83U
                    h6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cno
                    L/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2ok
                    QBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vA
                    D32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAI
                    mMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoW
                    Fua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91
                    oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09o
                    jm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUB
                    ZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCH
                    ex0SdDrx+tWUDqG8At2JHA==
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));

        } catch (Throwable t) {
            XposedBridge.log(t);
            throw new RuntimeException(t);
        }
    }

    private static KeyPair parseKeyPair(String key) throws Throwable {
        Object object;
        try (PEMParser parser = new PEMParser(new StringReader(key))) {
            object = parser.readObject();
        }

        PEMKeyPair pemKeyPair = (PEMKeyPair) object;

        return new JcaPEMKeyConverter().getKeyPair(pemKeyPair);
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
        PemObject pemObject;
        try (PemReader reader = new PemReader(new StringReader(cert))) {
            pemObject = reader.readPemObject();
        }

        X509CertificateHolder holder = new X509CertificateHolder(pemObject.getContent());

        return new JcaX509CertificateConverter().getCertificate(holder);
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
                        keyPair_EC = parseKeyPair(privateKeyContent);
                        certs_EC.clear();

                        for (int j = 0; j < certificateNodes.getLength(); j++) {
                            String certContent = certificateNodes.item(j).getTextContent().replaceAll("\s{2,}", "");
                            certs_EC.add(parseCert(certContent));
                        }
                    } else if ("rsa".equals(algorithm)) {
                        keyPair_RSA = parseKeyPair(privateKeyContent);
                        certs_RSA.clear();

                        for (int j = 0; j < certificateNodes.getLength(); j++) {
                            String certContent = certificateNodes.item(j).getTextContent().replaceAll("\s{2,}", "");
                            ;
                            certs_RSA.add(parseCert(certContent));
                        }
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
