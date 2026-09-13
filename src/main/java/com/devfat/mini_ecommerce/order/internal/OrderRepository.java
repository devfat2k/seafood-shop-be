package com.devfat.mini_ecommerce.order.internal;

import com.devfat.mini_ecommerce.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    //tìm orders theo userId
    Page<OrderEntity> findAllByUserId(Long userId, Pageable pageable);

    //Tìm orders theo userId VÀ status
    //@Query JPQL: lấy orders kèm user và items (JOIN FETCH cả 2), lọc theo userId
    @Query("SELECT o from OrderEntity o JOIN FETCH o.user where o.user.id = :userId AND o.status = :status")
    List<OrderEntity> findAllByUserIdAndStatus(@Param("userId") Long userId, @Param("status") OrderStatus status);


    @Query(value = "SELECT DISTINCT o FROM OrderEntity o " +
            "JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "WHERE o.user.id = :userId" +
            " AND (:status IS NULL OR o.status = :status)",
           countQuery = "SELECT COUNT(o) FROM OrderEntity o " +
            "WHERE o.user.id = :userId" +
            " AND (:status IS NULL OR o.status = :status)")
    Page<OrderEntity> findByUserIdAndStatusWithDetails(@Param("userId") Long userId, @Param("status") OrderStatus status, Pageable pageable);



    // ĐẾM SỐ ĐƠN GIAO HÀNG THÀNH CÔNG
    // @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.status = OrderStatus.DONE")
    long countByStatus(OrderStatus status);



}
