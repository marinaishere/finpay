package com.finpay.common.utils;

import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility class for loading RSA keys from PEM files.
 * Used for JWT token signing and validation in the authentication system.
 */
public class PemUtils {

    /**
     * Reads and cleans PEM file content from classpath.
     * Removes header/footer markers and whitespace.
     *
     * @param path Classpath resource path to the PEM file
     * @return Cleaned PEM content (Base64-encoded key data only)
     * @throws Exception If file cannot be read
     */
    private static String readPem(String path) throws Exception {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String pem = new String(inputStream.readAllBytes());
            // Remove PEM headers/footers and whitespace
            return pem.replaceAll("-----\\w+ PRIVATE KEY-----", "")
                    .replaceAll("-----\\w+ PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        }
    }

    /**
     * Loads RSA private key from PEM file for JWT signing.
     *
     * @param path Classpath resource path to private key PEM file
     * @return RSAPrivateKey instance for JWT signing
     * @throws Exception If key cannot be loaded or parsed
     */
    public static RSAPrivateKey loadPrivateKey(String path) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(readPem(path));
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    /**
     * Loads RSA public key from PEM file for JWT validation.
     *
     * @param path Classpath resource path to public key PEM file
     * @return RSAPublicKey instance for JWT validation
     * @throws Exception If key cannot be loaded or parsed
     */
    public static RSAPublicKey loadPublicKey(String path) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(readPem(path));
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }
}
