package rw.shop.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        Boolean requiresBusinessSelection,
        List<BusinessOption> businesses) {

    public static LoginResponse tokens(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, null, null);
    }

    public static LoginResponse selection(List<BusinessOption> businesses) {
        return new LoginResponse(null, null, null, null, true, businesses);
    }
}
