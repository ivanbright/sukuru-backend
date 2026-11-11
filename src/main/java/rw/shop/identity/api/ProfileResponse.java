package rw.shop.identity.api;

import java.util.UUID;

public record ProfileResponse(UUID id, String fullName, String email, String phone) {
}
