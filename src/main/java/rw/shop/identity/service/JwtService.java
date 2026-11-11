package rw.shop.identity.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import rw.shop.common.error.UnauthenticatedException;
import rw.shop.config.JwtProperties;

@Service
public class JwtService {

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            JwtProperties properties,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, UUID businessId, UUID membershipId, String role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES))
                .claim("type", "access")
                .claim("userId", userId.toString())
                .claim("businessId", businessId.toString())
                .claim("membershipId", membershipId.toString())
                .claim("role", role)
                .build();
        return encode(claims);
    }

    public String issueRefreshToken(UUID userId, UUID businessId, UUID membershipId, UUID familyId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.getRefreshTokenDays(), ChronoUnit.DAYS))
                .claim("type", "refresh")
                .claim("userId", userId.toString())
                .claim("businessId", businessId.toString())
                .claim("membershipId", membershipId.toString())
                .claim("familyId", familyId.toString())
                .build();
        return encode(claims);
    }

    public Jwt decodeRefreshToken(String token) {
        try {
            return refreshTokenDecoder.decode(token);
        } catch (Exception ex) {
            throw new UnauthenticatedException("Invalid refresh token");
        }
    }

    public Instant refreshTokenExpiry() {
        return clock.instant().plus(properties.getRefreshTokenDays(), ChronoUnit.DAYS);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public static JwtEncoder encoder(String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
