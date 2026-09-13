package com.devfat.mini_ecommerce.product.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@EnableJpaRepositories
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    @EntityGraph(attributePaths = {"category"})
    @Query(
            "SELECT p FROM ProductEntity p " +
                    "WHERE p.isActive = true " +
                    "AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                    "AND (:categoryId IS NULL OR p.category.id = :categoryId)"
    )
    Page<ProductEntity> findByNameAndCategoryIdContainsIgnoreCase(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );


    interface TopProductView {
        String getName();
        BigDecimal getPrice();
        Integer getMostBuy();
    }
    @Query("SELECT " +
            " p.name as name," +
            " p.price as price," +
            " COALESCE(SUM(CASE WHEN o.status IN (com.devfat.mini_ecommerce.order.OrderStatus.CONFIRMED, com.devfat.mini_ecommerce.order.OrderStatus.SHIPPED, com.devfat.mini_ecommerce.order.OrderStatus.DONE) THEN oi.quantity ELSE 0 END), 0) as mostBuy" +
            " FROM ProductEntity p " +
            " LEFT JOIN p.orderItems oi" +
            " LEFT JOIN oi.order o" +
            " GROUP BY  p.id, p.name, p.price " +
            " ORDER BY  COALESCE(SUM(CASE WHEN o.status IN (com.devfat.mini_ecommerce.order.OrderStatus.CONFIRMED, com.devfat.mini_ecommerce.order.OrderStatus.SHIPPED, com.devfat.mini_ecommerce.order.OrderStatus.DONE) THEN oi.quantity ELSE 0 END), 0) DESC")
    List<TopProductView> getTopViewProduct(Pageable pageable);

    interface CategoryRevenueView {
        String getName();
        BigDecimal getRevenue();
    }
    @Query("SELECT c.name as name, COALESCE(SUM(CASE WHEN o.status IN (com.devfat.mini_ecommerce.order.OrderStatus.CONFIRMED, com.devfat.mini_ecommerce.order.OrderStatus.SHIPPED, com.devfat.mini_ecommerce.order.OrderStatus.DONE) THEN oi.unitPrice * oi.quantity ELSE 0 END), 0) as revenue" +
            " FROM CategoryEntity c" +
            " LEFT JOIN c.products p" +
            " LEFT JOIN p.orderItems oi" +
            " LEFT JOIN oi.order o" +
            " GROUP BY  c.id, c.name" +
            " ORDER BY  COALESCE(SUM(CASE WHEN o.status IN (com.devfat.mini_ecommerce.order.OrderStatus.CONFIRMED, com.devfat.mini_ecommerce.order.OrderStatus.SHIPPED, com.devfat.mini_ecommerce.order.OrderStatus.DONE) THEN oi.unitPrice * oi.quantity ELSE 0 END), 0) DESC")
    List<CategoryRevenueView> getCategoryRevenue(Pageable pageable);

    interface MonthlyRevenueView {
        LocalDateTime getMonth();
        BigDecimal getRevenue();
    }
    @Query("  SELECT DATE_TRUNC('month', o.createdAt) as month, COALESCE(SUM(oi.unitPrice * oi.quantity) , 0) as revenue" +
            " FROM OrderEntity o " +
            " LEFT JOIN o.items oi" +
            " WHERE o.status IN (com.devfat.mini_ecommerce.order.OrderStatus.CONFIRMED, com.devfat.mini_ecommerce.order.OrderStatus.SHIPPED, com.devfat.mini_ecommerce.order.OrderStatus.DONE)" +
            " GROUP BY DATE_TRUNC('month', o.createdAt)" +
            " ORDER BY DATE_TRUNC('month', o.createdAt) DESC")
    List<MonthlyRevenueView> getMonthlyRevenue();


    @Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN FETCH p.category " +
            "WHERE p.isFeatured = true AND p.isActive = true")
    List<ProductEntity> findFeaturedActiveProducts();

    @Query("SELECT p FROM ProductEntity p " +
            "LEFT JOIN FETCH p.category " +
            "WHERE p.productType = 'COMBO' AND p.isActive = true " +
            "ORDER BY p.comboSortOrder ASC")
    List<ProductEntity> findActiveComboProducts();


    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decreaseStockAtomically(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int increaseStockAtomically(@Param("id") Long id, @Param("qty") int qty);
}
