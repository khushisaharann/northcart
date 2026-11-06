package com.northcart.catalog.service;

import com.northcart.catalog.dto.CategoryResponse;
import com.northcart.catalog.dto.CreateCategoryRequest;
import com.northcart.catalog.entity.Category;
import com.northcart.catalog.repository.CategoryRepository;
import com.northcart.common.exception.DuplicateResourceException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findActiveWithProductCount();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A category with this name already exists");
        }
        Category category = new Category();
        category.setName(name);
        category.setSlug(SlugUtils.uniqueSlug(name, categoryRepository::existsBySlug));
        category.setDescription(request.description() == null ? null : request.description().trim());
        categoryRepository.save(category);
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), 0L);
    }

    /** Used by product admin to validate categoryId. */
    @Transactional(readOnly = true)
    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
