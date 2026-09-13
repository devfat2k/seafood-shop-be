package com.devfat.mini_ecommerce.product.internal;

import com.devfat.mini_ecommerce.category.internal.CategoryEntity;
import com.devfat.mini_ecommerce.product.ProductService;
import com.devfat.mini_ecommerce.category.internal.CategoryRepository;
import com.devfat.mini_ecommerce.product.dto.*;
import com.devfat.mini_ecommerce.product.exception.InsufficientStockException;
import com.devfat.mini_ecommerce.product.specification.ProductSpecification;
import com.devfat.mini_ecommerce.product.validation.ProductSearchCriteriaValidator;
import com.devfat.mini_ecommerce.product.validation.ProductSortValidator;
import com.devfat.mini_ecommerce.shared.base.PageResponse;
import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;
    private final ProductMapper productMapper;
    private final ProductSearchCriteriaValidator searchCriteriaValidator;
    private final ProductSortValidator sortValidator;

    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto create(CreateProductRequestDto createProductRequestDto) {
        CategoryEntity category = categoryRepository.findById(createProductRequestDto.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + createProductRequestDto.categoryId()));
        ProductEntity product = new ProductEntity();
        product.setName(createProductRequestDto.name());
        product.setCategory(category);
        product.setDescription(createProductRequestDto.description());
        product.setPrice(createProductRequestDto.price());
        product.setStock(createProductRequestDto.stock());
        product.setActive(createProductRequestDto.isActive());

        return productMapper.toResponseDto(productRepository.save(product));
    }


    @Override
    @Cacheable(
            value = "product:category_browse",
            keyGenerator = "productCategoryKeyGenerator",
            condition = "#criteria != null && #criteria.search() == null && #criteria.minPrice() == null && #criteria.maxPrice() == null"
    )
    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDto> getProductsWithSearch(ProductSearchCriteria criteria, Pageable pageable) {
        searchCriteriaValidator.validate(criteria);
        sortValidator.validate(pageable.getSort());

        Page<ProductResponseDto> product =
                productRepository.findAll(ProductSpecification.withCriteria(criteria), pageable)
                .map(productMapper::toResponseDto);
        return PageResponse.of(product);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponseDto findById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto update(Long id, UpdateProductRequestDto updateProductRequest) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if(updateProductRequest.name() != null) product.setName(updateProductRequest.name());
        if(updateProductRequest.description() != null)  product.setDescription(updateProductRequest.description());
        if(updateProductRequest.stock() != null) product.setStock(updateProductRequest.stock());
        if (updateProductRequest.isActive() != null) product.setActive(updateProductRequest.isActive());
        if(updateProductRequest.price() != null) {
            if(updateProductRequest.price().compareTo(BigDecimal.ZERO) <= 0) throw new BadRequestException("Price must be greater than 0");
            product.setPrice(updateProductRequest.price());
        }
        if(updateProductRequest.categoryId() != null) {
            CategoryEntity category = categoryRepository.findById(updateProductRequest.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + updateProductRequest.categoryId()));
            product.setCategory(category);
        }

        return productMapper.toResponseDto(productRepository.save(product));
    }


    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto decreaseStock(Long id, int quantity) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if(quantity <= 0) throw new BadRequestException("Quantity must be greater than 0");
        if(product.getStock() < quantity) throw new InsufficientStockException("The product is unavailable. Please try again or choose another product.");

        product.setStock(product.getStock() - quantity);
        return productMapper.toResponseDto(product);
    }


    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto increaseStock(Long id, int quantity) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if(quantity <= 0) throw new BadRequestException("Quantity must be greater than 0");

        product.setStock(product.getStock() + quantity);
        return productMapper.toResponseDto(product);
    }


    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public Boolean softDelete(Long id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if(!(product.isActive())) throw new BadRequestException("Product is already inactive");

        product.setActive(false);
        return true;
    }

    @Override
    @Cacheable(value = "analytics", key = "'top-products'")
    @Transactional(readOnly = true)
    public List<TopProductResponseDto> getTopProducts(int limit) {
        return productRepository.getTopViewProduct(PageRequest.of(0, limit))
                .stream()
                .map(v -> new TopProductResponseDto(v.getName(), v.getPrice(), v.getMostBuy()))
                .toList();
    }

    @Override
    @Cacheable(value = "analytics", key = "'category-revenue'")
    @Transactional(readOnly = true)
    public List<CategoryRevenueResponseDto> getCategoryRevenue(Pageable pageable) {
        return productRepository.getCategoryRevenue(pageable)
                .stream()
                .map(v -> new CategoryRevenueResponseDto(v.getName(), v.getRevenue()))
                .toList();
    }

    @Override
    @Cacheable(value = "analytics", key = "'monthly-revenue'")
    @Transactional(readOnly = true)
    public List<MonthlyRevenueResponseDto> getMonthlyRevenue() {
        return productRepository.getMonthlyRevenue()
                .stream()
                .map(v -> new MonthlyRevenueResponseDto(v.getMonth(), v.getRevenue()))
                .toList();
    }

    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto uploadProductImage(Long id, MultipartFile file) {
       ProductEntity product = productRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        String url = storageService.uploadFile(file,"productImage", true);
        product.setImageUrl(url);

        return productMapper.toResponseDto(product);
    }

    @Override
    @CacheEvict(value = {"products", "product:category_browse", "home:featuredProducts", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto toggleFeaturedProduct(Long id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if(!(product.isActive())) throw new BadRequestException("Product is inactive");

        product.setFeatured(!(product.isFeatured()));
        log.info("Featured product is set to {}", product.isFeatured());
       return productMapper.toResponseDto(productRepository.save(product));
    }

    @Override
    @CacheEvict(value = {"products", "home:comboSets"}, allEntries = true)
    @Transactional
    public ProductResponseDto configureCombo(Long id, ConfigureProductComboRequestDto request) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        // Chuyển loại sản phẩm sang COMBO
        product.setProductType(ProductType.COMBO);
        if (request.comboCategory() != null) product.setComboCategory(request.comboCategory());
        if (request.comboTheme() != null) product.setComboTheme(request.comboTheme());
        if (request.comboTag() != null) product.setComboTag(request.comboTag());
        if (request.comboCtaText() != null) product.setComboCtaText(request.comboCtaText());
        if (request.comboHref() != null) product.setComboHref(request.comboHref());
        if (request.isBreakout() != null) product.setBreakout(request.isBreakout());
        if (request.comboSortOrder() != null) product.setComboSortOrder(request.comboSortOrder());
        return productMapper.toResponseDto(productRepository.save(product));
    }
}
