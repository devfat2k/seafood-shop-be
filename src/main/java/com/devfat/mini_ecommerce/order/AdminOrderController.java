package com.devfat.mini_ecommerce.order;

import com.devfat.mini_ecommerce.order.dto.OrderResponseDto;
import com.devfat.mini_ecommerce.order.dto.UpdateOrderStatusRequestDto;
import com.devfat.mini_ecommerce.shared.base.ApiResponse;
import com.devfat.mini_ecommerce.shared.base.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin - Order", description = "Admin Order Management APIs")
public class AdminOrderController {
    private final OrderService orderService;


    @Operation(summary = "Get all orders", description = "Retrieve a paginated list of all orders.")
    @GetMapping()
    public ResponseEntity<ApiResponse<PageResponse<OrderResponseDto>>> getAllOrders(
            Pageable pageable
    ) {
        PageResponse<OrderResponseDto> orderResponse = orderService.getAllOrder(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                orderResponse,
                "Get all order successfully"
        ));
    }

    @Operation(summary = "Filter orders", description = "Filter orders by user ID and order status.")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponseDto>>> getAllOrders(
            @PathVariable(name = "userId") Long userId,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable
    ) {
        PageResponse<OrderResponseDto> orderResponse = orderService.getOrderByUserIdAndStatusWithDetails(userId, status, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                orderResponse,
                "Get all order successfully"
        ));
    }

    @Operation(summary = "Update order status", description = "Update status of a specific order.")
    @PatchMapping("/{id}/update-status")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequestDto updateOrderStatusRequestDto
    ) {
        OrderResponseDto orderResponse = orderService.changeStatus(id, updateOrderStatusRequestDto);
        return ResponseEntity.ok().body(
                ApiResponse.success(orderResponse, "Update Order Status Successfully!")
        );
    }
}
