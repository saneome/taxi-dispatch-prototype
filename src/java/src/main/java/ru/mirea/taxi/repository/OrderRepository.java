package ru.mirea.taxi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mirea.taxi.model.Order;
import ru.mirea.taxi.model.OrderStatus;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(OrderStatus status);
    List<Order> findTop10ByOrderByCreatedAtDesc();
}
