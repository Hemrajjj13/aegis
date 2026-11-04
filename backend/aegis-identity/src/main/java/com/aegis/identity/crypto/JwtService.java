package com.aegis.identity.crypto;

import com.aegis.identity.model.TenantUser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String ISSUER = "aegis-identity";
    public static final String AUDIENCE = "aegis-console";

    private final Environment environment;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public record TokenClaims(
        UUID userId,
        UUID tenantId,
        String role,
        String email,
        String fullName,
        boolean mfaPending,
        boolean mfaSetup,
        Instant expiresAt
    ) {}

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        boolean isTier0 = isProfileActive("tier0");
        boolean isTier1 = isProfileActive("tier1");
        boolean isTier2 = isProfileActive("tier2");

        String privEnv = environment != null ? environment.getProperty("AEGIS_JWT_PRIVATE_KEY") : System.getenv("AEGIS_JWT_PRIVATE_KEY");
        String pubEnv = environment != null ? environment.getProperty("AEGIS_JWT_PUBLIC_KEY") : System.getenv("AEGIS_JWT_PUBLIC_KEY");

        if (privEnv != null && !privEnv.isBlank() && pubEnv != null && !pubEnv.isBlank()) {
            loadKeysFromStrings(privEnv, pubEnv);
            return;
        }

        if (isTier0) {
            initTier0Keys();
        } else {
            throw new IllegalStateException(
                "FATAL: JWT signing keys (AEGIS_JWT_PRIVATE_KEY and AEGIS_JWT_PUBLIC_KEY) must be configured in tier1/tier2 profiles."
            );
        }
    }

    private boolean isProfileActive(String profile) {
        if (environment == null || environment.getActiveProfiles() == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private void initTier0Keys() {
        try {
            Path devKeysDir = Path.of(".dev-keys");
            if (!Files.exists(devKeysDir)) {
                Files.createDirectories(devKeysDir);
            }
            Path privPath = devKeysDir.resolve("jwt-private.pem");
            Path pubPath = devKeysDir.resolve("jwt-public.pem");

            if (Files.exists(privPath) && Files.exists(pubPath)) {
                loadKeysFromStrings(Files.readString(privPath), Files.readString(pubPath));
                log.info("Loaded tier0 JWT signing keys from {}", devKeysDir.toAbsolutePath());
            } else {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                this.privateKey = (RSAPrivateKey) kp.getPrivate();
                this.publicKey = (RSAPublicKey) kp.getPublic();

                String privPem = "-----BEGIN PRIVATE KEY-----\n" +
                        Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(this.privateKey.getEncoded()) +
                        "\n-----END PRIVATE KEY-----\n";
                String pubPem = "-----BEGIN PUBLIC KEY-----\n" +
                        Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(this.publicKey.getEncoded()) +
                        "\n-----END PUBLIC KEY-----\n";

                Files.writeString(privPath, privPem);
                Files.writeString(pubPath, pubPem);
                log.info("Generated new tier0 JWT signing keys in {}", devKeysDir.toAbsolutePath());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize tier0 JWT signing keys", e);
        }
    }

    private void loadKeysFromStrings(String privPem, String pubPem) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            String cleanPriv = privPem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] privBytes = Base64.getDecoder().decode(cleanPriv);
            this.privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

            String cleanPub = pubPem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] pubBytes = Base64.getDecoder().decode(cleanPub);
            this.publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA PEM key format", e);
        }
    }

    public String issueAccessToken(TenantUser user, UUID tenantId) {
        return issueTokenWithExpiration(user, tenantId, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    public String issueTokenWithExpiration(TenantUser user, UUID tenantId, Instant exp) {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.user_id().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("role", user.role())
                .claim("email", user.email())
                .claim("full_name", user.full_name())
                .claim("permissions", List.of("ROLE_" + user.role().toUpperCase()))
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();

        return sign(claims);
    }

    public String issueMfaChallengeToken(UUID userId, UUID tenantId) {
        Instant now = Instant.now();
        Instant exp = now.plus(5, ChronoUnit.MINUTES);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId != null ? tenantId.toString() : null)
                .claim("mfa_pending", true)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();

        return sign(claims);
    }

    public String issueSetupToken(UUID userId, UUID tenantId) {
        Instant now = Instant.now();
        Instant exp = now.plus(15, ChronoUnit.MINUTES);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId != null ? tenantId.toString() : null)
                .claim("mfa_setup", true)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();

        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
            );
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public TokenClaims verifyAndParse(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Missing token");
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Strictly reject algorithm confusion and alg none
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                throw new SecurityException("Unsupported JWT algorithm: " + signedJWT.getHeader().getAlgorithm());
            }

            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new SecurityException("JWT signature verification failed");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new SecurityException("JWT token has expired");
            }

            if (!ISSUER.equals(claims.getIssuer())) {
                throw new SecurityException("Invalid JWT issuer: " + claims.getIssuer());
            }

            if (claims.getAudience() == null || !claims.getAudience().contains(AUDIENCE)) {
                throw new SecurityException("Invalid JWT audience: " + claims.getAudience());
            }

            UUID userId = UUID.fromString(claims.getSubject());
            String tenantIdStr = (String) claims.getClaim("tenant_id");
            UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
            String role = (String) claims.getClaim("role");
            String email = (String) claims.getClaim("email");
            String fullName = (String) claims.getClaim("full_name");
            Boolean mfaPending = (Boolean) claims.getClaim("mfa_pending");
            Boolean mfaSetup = (Boolean) claims.getClaim("mfa_setup");

            return new TokenClaims(
                    userId,
                    tenantId,
                    role,
                    email,
                    fullName,
                    Boolean.TRUE.equals(mfaPending),
                    Boolean.TRUE.equals(mfaSetup),
                    exp.toInstant()
            );
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Malformed JWT token: " + e.getMessage(), e);
        }
    }
}

