package com.pulsepass.backofficegateway.common;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public final class TokenTools {

    private TokenTools() {
    }

    public static PrivateKey privateKey() {
        try (InputStream stream = TokenTools.class.getResourceAsStream("/keys/jwt-private-key.pem")) {
            String pem = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Test private key could not be loaded", e);
        }
    }

    public static PrivateKey otherPrivateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return pair.getPrivate();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Test key pair could not be generated", e);
        }
    }

    public static String sign(
            PrivateKey signingKey,
            String issuer,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, Object> claims
    ) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Test token could not be signed", e);
        }
        return jwt.serialize();
    }

    public static String sign(String issuer, Instant issuedAt, Instant expiresAt, Map<String, Object> claims) {
        return sign(privateKey(), issuer, issuedAt, expiresAt, claims);
    }

    public static String signHmac(
            byte[] secret,
            String issuer,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, Object> claims
    ) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("Test token could not be signed", e);
        }
        return jwt.serialize();
    }

    public static String unsigned(String issuer, Instant issuedAt, Instant expiresAt, Map<String, Object> claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        claims.forEach(builder::claim);
        return new PlainJWT(builder.build()).serialize();
    }
}
