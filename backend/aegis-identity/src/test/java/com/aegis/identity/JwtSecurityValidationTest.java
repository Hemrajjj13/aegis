package com.aegis.identity;

import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.model.TenantUser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestIdentityApplication.class)
@ActiveProfiles("tier0")
class JwtSecurityValidationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("JWT parser strictly rejects alg none")
    void testRejectAlgNone() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(JwtService.ISSUER)
                .audience(JwtService.AUDIENCE)
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .build();

        PlainJWT plainJWT = new PlainJWT(claims);
        String noneToken = plainJWT.serialize();

        assertThatThrownBy(() -> jwtService.verifyAndParse(noneToken))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("JWT parser rejects token signed with an untrusted key")
    void testRejectWrongKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair foreignKp = kpg.generateKeyPair();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(JwtService.ISSUER)
                .audience(JwtService.AUDIENCE)
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .build();

        SignedJWT foreignJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claims
        );
        foreignJwt.sign(new RSASSASigner((RSAPrivateKey) foreignKp.getPrivate()));
        String foreignToken = foreignJwt.serialize();

        assertThatThrownBy(() -> jwtService.verifyAndParse(foreignToken))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    @DisplayName("JWT parser rejects expired tokens")
    void testRejectExpiredToken() {
        TenantUser user = new com.aegis.identity.model.TenantUser(
                UUID.randomUUID(), "test@aegis.in", "+919988001122", "Test", "developer", "ACTIVE", true
        );
        String expiredToken = jwtService.issueTokenWithExpiration(user, UUID.randomUUID(), Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> jwtService.verifyAndParse(expiredToken))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("JWT parser rejects tokens with wrong issuer or audience")
    void testRejectWrongIssuerOrAudience() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        JWTClaimsSet wrongIssuerClaims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("untrusted-issuer")
                .audience(JwtService.AUDIENCE)
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), wrongIssuerClaims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));

        assertThatThrownBy(() -> jwtService.verifyAndParse(jwt.serialize()))
                .isInstanceOf(SecurityException.class);
    }
}

