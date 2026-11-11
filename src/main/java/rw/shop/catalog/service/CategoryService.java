package rw.shop.catalog.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.catalog.api.CategoryRequest;
import rw.shop.catalog.api.CategoryResponse;
import rw.shop.catalog.domain.Category;
import rw.shop.catalog.repo.CategoryRepository;
import rw.shop.common.Ids;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String search, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        String query = search == null || search.isBlank() ? "" : search.trim();
        Page<Category> categories = categoryRepository.search(businessId, query,
                PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return new PageResponse<>(categories.getContent().stream().map(this::toResponse).toList(),
                categories.getNumber(), categories.getSize(), categories.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return toResponse(find(id));
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setId(Ids.newId());
        category.setBusinessId(TenantContext.requireBusinessId());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setCreatedAt(clock.instant());
        categoryRepository.save(category);
        return toResponse(category);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = find(id);
        category.setName(request.name().trim());
        category.setDescription(request.description());
        categoryRepository.save(category);
        return toResponse(category);
    }

    private Category find(UUID id) {
        return categoryRepository.findByIdAndBusinessId(id, TenantContext.requireBusinessId())
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getCreatedAt());
    }
}
