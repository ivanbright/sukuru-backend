package rw.shop.identity.api;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
