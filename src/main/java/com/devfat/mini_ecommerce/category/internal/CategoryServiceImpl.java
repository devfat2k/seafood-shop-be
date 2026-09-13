package com.devfat.mini_ecommerce.category.internal;

import com.devfat.mini_ecommerce.category.CategoryService;
import com.devfat.mini_ecommerce.category.dto.CategoryResponseDto;
import com.devfat.mini_ecommerce.category.dto.ConfigureCategoryHomeRequestDto;
import com.devfat.mini_ecommerce.category.dto.CreateCategoryRequestDto;
import com.devfat.mini_ecommerce.category.exception.CategoryHasProductsException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final StorageService storageService;

    public boolean existsByName(String categoryName) {
        return categoryRepository.existsByNameIgnoreCase(categoryName);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponseDto create(CreateCategoryRequestDto createCategoryRequestDto) {
        boolean isExistsCategoryName = existsByName(createCategoryRequestDto.name());
        if(isExistsCategoryName) {
            throw new ResourceNotFoundException("Category name is already exists. Category name = " + createCategoryRequestDto.name());
        }
        CategoryEntity categoryEntity = new CategoryEntity();
        categoryEntity.setName(createCategoryRequestDto.name());
        return  categoryMapper.toResponseDto(categoryRepository.save(categoryEntity));

    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponseDto findById(Long id) {
        return categoryRepository.findById(id)
                .map(categoryMapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Category is not found. Id = " + id));
    }


    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponseDto update(Long id,CreateCategoryRequestDto createCategoryRequestDto) {
        CategoryEntity categoryEntity = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category is not found. id = " + id));
        categoryEntity.setName(createCategoryRequestDto.name());
        return  categoryMapper.toResponseDto(categoryRepository.save(categoryEntity));
    }


    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public Boolean deleteById(Long id) {
        CategoryEntity categoryEntity = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category is not found. id = " + id));
        if (!categoryEntity.getProducts().isEmpty()) {
            throw new CategoryHasProductsException("Category is had products. Do not delete any products.");
        }
        categoryRepository.delete(categoryEntity);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> countActiveCategories() {
       return categoryRepository.countActiveCategories().stream()
               .map(categoryMapper::toResponseDto)
               .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "home:categories", allEntries = true)
    @Transactional
    public CategoryResponseDto uploadCategoryImage(Long id, MultipartFile file) {
        CategoryEntity category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category is not found. id = " + id));

        String url = storageService.uploadFile(file, "categoryImage", true);
        category.setImageUrl(url);
        return categoryMapper.toResponseDto(categoryRepository.save(category));
    }

    @Override
    @CacheEvict(value = "home:categories", key = "'all'")
    @Transactional
    public CategoryResponseDto configureHome(Long id, ConfigureCategoryHomeRequestDto request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id = " + id));
        if (request.badge() != null) category.setBadge(request.badge());
        if (request.badgeType() != null) category.setBadgeType(request.badgeType());
        if (request.iconName() != null) category.setIconName(request.iconName());
        if (request.homeDisplayStyle() != null) category.setHomeDisplayStyle(request.homeDisplayStyle());
        if (request.homeSortOrder() != null) category.setHomeSortOrder(request.homeSortOrder());
        if (request.homeIsActive() != null) category.setHomeIsActive(request.homeIsActive());
        return categoryMapper.toResponseDto(categoryRepository.save(category));
    }
}
