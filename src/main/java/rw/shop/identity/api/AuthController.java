package rw.shop.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.common.Ids;
import rw.shop.common.web.TenantContext;
import rw.shop.identity.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-business")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a business and its first owner")
    public RegisterBusinessResponse registerBusiness(@Valid @RequestBody RegisterBusinessRequest request) {
        UUID businessId = Ids.newId();
        TenantContext.set(new TenantContext.Tenant(businessId, null, null, null));
        try {
            return authService.registerBusiness(businessId, request);
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and choose a business")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @GetMapping("/businesses")
    @Operation(summary = "List businesses available to the signed-in user")
    public List<BusinessOption> businesses() {
        return authService.businesses(TenantContext.requireUserId());
    }

    @PostMapping("/switch-business")
    @Operation(summary = "Switch the signed-in user to another active business")
    public TokenResponse switchBusiness(@Valid @RequestBody SwitchBusinessRequest request) {
        return authService.switchBusiness(TenantContext.requireUserId(), request.businessId());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token family")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
}
