package com.logistics.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads RSA keys supplied through configuration.
 *
 * <p>Accepts either a raw PEM block or the base64 encoding of that PEM block. Both forms are
 * supported because a PEM is multi-line, which environment variables and {@code .env} files handle
 * badly; base64 collapses it to one line without inventing a bespoke format.
 */
public final class RsaKeyLoader {

    private static final String RSA = "RSA";
    private static final int GENERATED_KEY_SIZE = 2048;

    private RsaKeyLoader() {
        // utility class
    }

    public static RSAPrivateKey loadPrivateKey(String material) {
        byte[] der = derBytes(material, "PRIVATE KEY");
        try {
            return (RSAPrivateKey)
                    KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "security.jwt.private-key is not a valid PKCS#8 RSA private key", e);
        }
    }

    public static RSAPublicKey loadPublicKey(String material) {
        byte[] der = derBytes(material, "PUBLIC KEY");
        try {
            return (RSAPublicKey)
                    KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "security.jwt.public-key is not a valid X.509 RSA public key", e);
        }
    }

    /** Throw-away key pair, used only when no key is configured (development convenience). */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(GENERATED_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation is not available on this JVM", e);
        }
    }

    /**
     * Normalises the supplied material to a PEM block, then strips the armour and decodes the body.
     *
     * @param expectedLabel the label the PEM header must contain, as a sanity check against a
     *                      private key being pasted where a public key is expected
     */
    private static byte[] derBytes(String material, String expectedLabel) {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("No RSA key material supplied");
        }
        String pem = toPem(material.trim());
        if (!pem.contains("-----BEGIN") || !pem.contains(expectedLabel)) {
            throw new IllegalArgumentException(
                    "Expected a PEM block containing '" + expectedLabel + "'");
        }
        String body = pem.replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String toPem(String material) {
        if (material.startsWith("-----BEGIN")) {
            return material;
        }
        // Not a PEM: assume the whole PEM block was base64-encoded to fit on a single line.
        byte[] decoded = Base64.getDecoder().decode(material.replaceAll("\\s", ""));
        return new String(decoded, StandardCharsets.UTF_8).trim();
    }
}
