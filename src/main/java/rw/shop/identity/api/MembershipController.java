package rw.shop.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.common.pagination.PageResponse;
import rw.shop.identity.service.MembershipService;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    @Operation(summary = "List memberships of the current business")
    public PageResponse<MembershipResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return membershipService.list(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user and membership")
    public MembershipResponse create(@Valid @RequestBody CreateMembershipRequest request) {
        return membershipService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a membership role or active state")
    public MembershipResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMembershipRequest request) {
        return membershipService.update(id, request);
    }
}
