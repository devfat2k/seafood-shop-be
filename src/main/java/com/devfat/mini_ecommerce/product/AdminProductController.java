package com.devfat.mini_ecommerce.product;

import com.devfat.mini_ecommerce.product.dto.*;
import com.devfat.mini_ecommerce.shared.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin - Product", description = "Admin: Quản lý sản phẩm")
public class AdminProductController {
    private final ProductService productService;

    @Operation(
            summary = "Admin - Create product",
            description = "Create a new product using the request body."
    )
    @PreAuthorize("hasAuthority('PRODUCT_CREATE') or hasRole('ADMIN')")
    @PostMapping()
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(
            @Valid @RequestBody() CreateProductRequestDto createProductRequest
    ) {
        ProductResponseDto productResponseDto = productService.create(createProductRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                productResponseDto,
                "Create Product Successfully!"
        ));
    }

    @Operation(
            summary = "Admin - Update product",
            description = "Update one or more product fields. Only provided fields will be updated."
    )
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE') or hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequestDto updateProductRequest
    ) {
        ProductResponseDto productResponse = productService.update(id, updateProductRequest);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                productResponse,
                "Update Product Successfully!"
        ));
    }

    @Operation(
            summary = "Admin - Soft delete product",
            description = "Mark the product as inactive."
    )
    @PreAuthorize("hasAuthority('PRODUCT_DELETE') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteProduct(
            @PathVariable Long id
    ) {

        boolean isSoftDelete = productService.softDelete(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                isSoftDelete,
                "Delete product successfully!"
        ));
    }

    @Operation(
            summary = "Admin - Increase product stock",
            description = "Increase the stock quantity of a product."
    )
    @PatchMapping("/increase/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> increaseStock(
            @PathVariable Long id,
            @RequestParam int quantity
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                productService.increaseStock(id, quantity),
                "Increase Stock Successfully!"
        ));
    }

    @Operation(
            summary = "Decrease product stock",
            description = "Decrease the stock quantity of a product."
    )
    @PatchMapping("/decrease/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> decreaseStock(
            @PathVariable Long id, @RequestParam int quantity
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                productService.decreaseStock(id, quantity),
                "Decrease Stock Successfully!"
        ));
    }

    @Operation(
            summary = "Admin - Get Top Product",
            description = "Get Top Product Buy For Admin Dashboard"
    )
    @GetMapping("/top-buy")
    public ResponseEntity<ApiResponse<List<TopProductResponseDto>>> getTopBuyProduct(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok().body(ApiResponse.success(
                productService.getTopProducts(limit),
                "Get Top Product Successfully!"
        ));
    }

    @Operation(
            summary = "Admin - Get Top Product By Category",
            description = "Get Top Product Buy By Category For Admin Dashboard"
    )
    @GetMapping("/revenue-by-category")
    public ResponseEntity<ApiResponse<List<CategoryRevenueResponseDto>>> getRevenueByCategory() {
        return ResponseEntity.ok().body(ApiResponse.success(
                productService.getCategoryRevenue(PageRequest.of(0, 10)),
                "Get Revenue By Category Success!"
        ));
    }

    @Operation(
            summary = "Admin - Get Top Revenue Product",
            description = "Get Top Revenue Product Buy For Admin Dashboard"
    )
    @GetMapping("/revenue-in-month")
    public ResponseEntity<ApiResponse<List<MonthlyRevenueResponseDto>>> getMonthlyRevenue() {
        return ResponseEntity.ok().body(
                ApiResponse.success(
                        productService.getMonthlyRevenue(),
                        "Get Monthly Revenue Success!"
                ));
    }

    @Operation(
            summary = "Admin - Upload Product Image",
            description = "Upload Product Image For Admin"
    )
    @PostMapping(value="/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductResponseDto>> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        return  ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                productService.uploadProductImage(id, file),
                "Upload Image Product Successfully!"
        ));
    }

    @PatchMapping("/{id}/featured")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateFeatured(
            @PathVariable("id") Long id
    ) {
        return  ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                productService.toggleFeaturedProduct(id),
                "Featured Product Successfully!"
        ));
    }

    @Operation(summary = "Configure product combo for home page", description = "Admin API — Upgrade product to COMBO type and set home combo card properties.")
    @PatchMapping("/{id}/combo-config")
    public ResponseEntity<ApiResponse<ProductResponseDto>> configureCombo(
            @PathVariable Long id,
            @Valid @RequestBody ConfigureProductComboRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.configureCombo(id, request),
                "Configure product combo successfully"
        ));
    }
}
