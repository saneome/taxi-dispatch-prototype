package ru.mirea.taxi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.dto.CreateOrderRequest;
import ru.mirea.taxi.dto.OrderResponse;
import ru.mirea.taxi.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createAndDispatchOrder(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getRecentOrders() {
        return ResponseEntity.ok(orderService.getRecentOrders());
    }

    @RequestMapping(value = {"/{id}/advance", "/{id}/status"}, method = {RequestMethod.POST, RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<OrderResponse> advanceStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(orderService.advanceOrderStatus(id, status));
    }
}
