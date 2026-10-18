package com.example.ecommerce.service;

import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.dto.PlaceOrderRequest;
import com.example.ecommerce.dto.UpdateOrderStatusRequest;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    // ---------- Customer actions ----------

    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User user = currentUserProvider.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PLACED);
        order.setOrderDate(LocalDateTime.now());
        order.setShippingAddress(request.getShippingAddress());

        double total = 0;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            if (cartItem.getQuantity() > product.getStock()) {
                throw new BadRequestException("Not enough stock for " + product.getName()
                        + " (available: " + product.getStock() + ")");
            }

            product.setStock(product.getStock() - cartItem.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            order.getItems().add(orderItem);

            total += product.getPrice() * cartItem.getQuantity();
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        cart.getItems().clear();

        return toResponse(saved);
    }

    public List<OrderResponse> getMyOrders() {
        User user = currentUserProvider.getCurrentUser();
        return orderRepository.findByUserIdOrderByOrderDateDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        User user = currentUserProvider.getCurrentUser();

        Order order = (user.getRole() == Role.ADMIN)
                ? orderRepository.findById(id).orElse(null)
                : orderRepository.findByIdAndUserId(id, user.getId()).orElse(null);

        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + id);
        }
        return toResponse(order);
    }

    public OrderResponse cancelMyOrder(Long id) {
        User user = currentUserProvider.getCurrentUser();

        Order order = orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Only orders in PLACED status can be cancelled");
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);

        return toResponse(order);
    }

    // ---------- Admin actions ----------

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        OrderStatus current = order.getStatus();
        OrderStatus next = request.getStatus();

        if (current == OrderStatus.DELIVERED || current == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already " + current + " and cannot be changed");
        }
        if (next == OrderStatus.PLACED) {
            throw new BadRequestException("An order cannot be moved back to PLACED");
        }
        if (next == OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(next);
        return toResponse(order);
    }

    // ---------- helpers ----------

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getPrice() * item.getQuantity()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUser().getEmail(),
                order.getStatus(),
                order.getOrderDate(),
                order.getShippingAddress(),
                order.getTotalAmount(),
                items);
    }
}