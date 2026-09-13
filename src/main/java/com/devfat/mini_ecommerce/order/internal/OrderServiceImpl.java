package com.devfat.mini_ecommerce.order.internal;

import com.devfat.mini_ecommerce.notification.EmailService;
import com.devfat.mini_ecommerce.order.OrderService;
import com.devfat.mini_ecommerce.order.OrderStatus;
import com.devfat.mini_ecommerce.order.dto.CreateOrderRequestDto;
import com.devfat.mini_ecommerce.order.dto.OrderResponseDto;
import com.devfat.mini_ecommerce.order.dto.UpdateOrderStatusRequestDto;
import com.devfat.mini_ecommerce.order.exception.InvalidStatusTransitionException;
import com.devfat.mini_ecommerce.product.exception.InsufficientStockException;
import com.devfat.mini_ecommerce.product.internal.ProductEntity;
import com.devfat.mini_ecommerce.product.internal.ProductRepository;
import com.devfat.mini_ecommerce.shared.base.PageResponse;
import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.user.address.internal.AddressRepository;
import com.devfat.mini_ecommerce.user.address.internal.UserAddressEntity;
import com.devfat.mini_ecommerce.user.internal.UserEntity;
import com.devfat.mini_ecommerce.user.internal.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.devfat.mini_ecommerce.order.OrderStatus.*;
import java.math.BigDecimal;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final EmailService emailService;

    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_ORDERS = Map.of(
            PENDING,   Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(SHIPPED, CANCELLED),
            SHIPPED,   Set.of(DONE)
            // DONE, CANCELLED không có entry -> get() trả null -> exception -> đúng vì đây là trạng thái kết thúc
    );

    /**
     * 1. Lấy userId hiện tại từ @AuthenticationPrincipal (giống trên)
     * 2. Lấy role hiện tại từ userPrincipal (dùng lại getAuthorities() đã có, hoặc thêm getRole() nếu UserPrincipal đã có sẵn từ C3)
     * 3. Nếu role là ADMIN -> cho phép xem bất kỳ userId nào trong path (không cần so sánh)
     * 4. Nếu role là USER -> so sánh userId trong path với userId hiện tại:
     * - Khớp -> cho qua
     * - Không khớp -> throw AccessDeniedException (dùng đúng class Spring Security,
     * để rơi vào đúng handler đã có sẵn từ D2 -> tự động trả 403 đúng format)
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponseDto> getOrderByUserIdAndStatusWithDetails(Long userId, OrderStatus status, Pageable pageable)  {
        if(userId == null) throw new ResourceNotFoundException("User id is required!");
        if(userRepository.findById(Objects.requireNonNull(userId)).isEmpty()) {
            throw new ResourceNotFoundException("User not found!");
        }

        Page<OrderResponseDto> order = orderRepository.findByUserIdAndStatusWithDetails(userId, status, pageable).map(
                orderMapper::toResponseDto
        );
        return PageResponse.of(order);
    }


    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto findById(Long orderId, Long userIdInToken, String userRole) throws AccessDeniedException {
        if(orderId == null) {
            throw new ResourceNotFoundException("Order id is null");
        }

        if(userIdInToken == null) {
            throw new AccessDeniedException("User ID is invalid or does not belong to you!");
        }

//        nếu KHÔNG phải ADMIN thì phải đúng userId -> cùng đơn hàng user đang có mới xem được -> Bắt trường hợp xem orderId của người khác
//        if(!(userRole.equalsIgnoreCase("ADMIN"))) {
//            orderRepository.findAllByUserId(userIdInToken).orElseThrow(() -> new AccessDeniedException("Access denied! Order ID is invalid or does not belong to you!"));
//        }

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found!"));

        if (userRole.equalsIgnoreCase("USER") && !order.getUser().getId().equals(userIdInToken)) {
            throw new AccessDeniedException("Access denied. This order does not belong to you!");
        }

        return orderMapper.toResponseDto(order);
    }


    @Override
    @Transactional
    public OrderResponseDto create(Long userId, CreateOrderRequestDto createOrderRequestDto) throws JsonProcessingException {
        // b1. Tìm User theo user id
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        // Bổ sung: Tìm địa chỉ giao hàng
        //Tìm địa chỉ giao hàng
        //Kiểm tra địa chỉ có thuộc user đó không
        Optional<UserAddressEntity> defaultAddress = addressRepository.findByUserIdAndDefaultAddressIsTrue(userId);
        if(defaultAddress.isEmpty()) {
            throw new ResourceNotFoundException("Address not found!");
        }
        //Tạo Json địa chỉ
        String snapShotAddress = objectMapper.writeValueAsString(Map.of(
                "recipientName", defaultAddress.get().getRecipientName(),
                "phone",         defaultAddress.get().getPhone(),
                "province",      defaultAddress.get().getProvince(),
                "district",      defaultAddress.get().getDistrict(),
                "ward",          defaultAddress.get().getWard(),
                "addressDetail", defaultAddress.get().getAddressDetail()
        ));



        // b2. Tạo đơn hàng rỗng - có total amount =0
        OrderEntity order = new OrderEntity();
        order.setUser(Objects.requireNonNull(user));
        order.setStatus(OrderStatus.PENDING); // Bước này tôi nghĩ rằng vừa đúng vừa sai vì tạo đơn hàng rỗng thì nó sẽ PENDING -> lúc tạo thành công sẽ cặp nhât DONE Hoặc Logic nghiệp vụ khác sau này
        order.setTotalAmount(BigDecimal.ZERO);
        order.setShippingAddress(defaultAddress.get());
        order.setShippingAddressSnapshot(snapShotAddress);
        order.setPaymentMethod(createOrderRequestDto.paymentMethod());
        order.setNote(createOrderRequestDto.note());

        /**
         * Lặp qua từng item trong request.items()
         * Tìm Product theo productId — không thấy → exception
         * Kiểm tra product.getStock() >= quantity — không đủ → exception
         * Trừ stock: product.setStock(stock - quantity)
         * Tạo OrderItem mới (product, quantity, unitPrice = giá hiện tại)
         * Gắn item vào order.getItems().add(item) + set item.setOrder(order)
         */
        createOrderRequestDto.items().forEach(requestItem -> {
            ProductEntity product = productRepository.findById(requestItem.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found!"));

            if (!product.isActive()) {
                throw new BadRequestException("Product is inactive: " + product.getName());
            }

            int rowsAffected = productRepository.decreaseStockAtomically(
                    requestItem.productId(), requestItem.quantity());
            if (rowsAffected == 0) {
                throw new InsufficientStockException("Not enough stock for product: " + product.getName());
            }

            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .order(order)
                    .product(product)
                    .quantity(requestItem.quantity())
                    .unitPrice(product.getPrice())
                    .build();
            order.getItems().add(orderItem);
        });
        // Tính total amount
        BigDecimal totalMoney = order.getItems().stream()
                .map(itemPrice -> itemPrice.getUnitPrice().multiply(BigDecimal.valueOf(itemPrice.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalMoney);

        OrderEntity savedOrder = orderRepository.save(order);
        emailService.sendOrderConfirmation(savedOrder.getUser().getEmail(), savedOrder.getId());

        return orderMapper.toResponseDto(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDto changeStatus(Long id, UpdateOrderStatusRequestDto updateOrderStatusRequestDto) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found!"));

        Set<OrderStatus> allowedNext = ALLOWED_ORDERS.get(order.getStatus());
        if (allowedNext == null || !allowedNext.contains(updateOrderStatusRequestDto.orderStatus())) {
            throw new InvalidStatusTransitionException("Do not change from " + order.getStatus() + " to " + updateOrderStatusRequestDto.orderStatus());
        }

        if (updateOrderStatusRequestDto.orderStatus().equals(CANCELLED) && !order.getStatus().equals(CANCELLED)) {
            order.getItems().forEach(item -> {
                productRepository.increaseStockAtomically(item.getProduct().getId(), item.getQuantity());
            });
        }

        order.setStatus(updateOrderStatusRequestDto.orderStatus());
        return orderMapper.toResponseDto(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponseDto> getAllOrder(Pageable pageable) {

        Page<OrderResponseDto> page = orderRepository.findAll(pageable)
                .map(orderMapper::toResponseDto);

        return PageResponse.of(page);

    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponseDto> getMyOrder(Long userId, Pageable pageable) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        Page<OrderResponseDto> orders = orderRepository.findAllByUserId(userId, pageable)
               .map(orderMapper::toResponseDto);

       return PageResponse.of(orders);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) throws AccessDeniedException {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found!"));

        if(!order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }

        if (!order.getStatus().equals(PENDING)) {
            throw new BadRequestException("Only PENDING orders can be cancelled.");
        }

        order.getItems().forEach(item -> {
            productRepository.increaseStockAtomically(item.getProduct().getId(), item.getQuantity());
        });

        order.setStatus(CANCELLED);
        orderRepository.save(order);
    }
}
