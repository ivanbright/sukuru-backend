package rw.shop.parties.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.common.Ids;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.parties.api.CustomerRequest;
import rw.shop.parties.api.CustomerResponse;
import rw.shop.parties.domain.Customer;
import rw.shop.parties.repo.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, Clock clock) {
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        String query = search == null || search.isBlank() ? "" : search.trim();
        Page<Customer> customers = customerRepository.search(businessId, query,
                PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return new PageResponse<>(customers.getContent().stream().map(this::toResponse).toList(),
                customers.getNumber(), customers.getSize(), customers.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return toResponse(find(id));
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setId(Ids.newId());
        customer.setBusinessId(TenantContext.requireBusinessId());
        customer.setName(request.name().trim());
        customer.setPhone(trimToNull(request.phone()));
        customer.setEmail(trimToNull(request.email()));
        customer.setAddress(trimToNull(request.address()));
        customer.setIsActive(true);
        customer.setCreatedAt(clock.instant());
        customerRepository.save(customer);
        return toResponse(customer);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = find(id);
        customer.setName(request.name().trim());
        customer.setPhone(trimToNull(request.phone()));
        customer.setEmail(trimToNull(request.email()));
        customer.setAddress(trimToNull(request.address()));
        customerRepository.save(customer);
        return toResponse(customer);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public void delete(UUID id) {
        Customer customer = find(id);
        customer.setIsActive(false);
        customerRepository.save(customer);
    }

    private Customer find(UUID id) {
        return customerRepository.findByIdAndBusinessId(id, TenantContext.requireBusinessId())
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
