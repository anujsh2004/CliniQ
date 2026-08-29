package com.clinic.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 webhook signature checking, the way Razorpay specifies it.
 */
public final class SignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private SignatureVerifier() {
    }

    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IllegalStateException("Could not compute the webhook signature", ex);
        }
    }

    /**
     * Compares in constant time. A plain string equality check leaks, through
     * how long it takes to fail, how much of a forged signature was correct.
     */
    public static boolean matches(String payload, String secret, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        byte[] expected = sign(payload, secret).getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedSignature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
