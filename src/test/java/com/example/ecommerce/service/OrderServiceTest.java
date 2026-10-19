package com.example.ecommerce.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private OrderService orderService;

    // ---------- placeOrder ----------

    @Test
    void placeOrder_reducesStock_createsOrderAndEmptiesCart() {
        User user = user(1L, Role.USER);
        Product mouse = product(1L, "Mouse", 600.0, 10);
        Product keyboard = product(2L, "Keyboard", 2500.0, 5);

        Cart cart = new Cart();
        cart.setUser(user);
        addToCart(cart, mouse, 2);
        addToCart(cart, keyboard, 1);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(placeOrderRequest());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.getTotalAmount()).isEqualTo(3700.0);
        assertThat(response.getItems()).hasSize(2);
        assertThat(mouse.getStock()).isEqualTo(8);
        assertThat(keyboard.getStock()).isEqualTo(4);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void placeOrder_notEnoughStock_throwsAndNeverSavesOrder() {
        User user = user(1L, Role.USER);
        Product mouse = product(1L, "Mouse", 600.0, 10);
        Product keyboard = product(2L, "Keyboard", 2500.0, 1);

        Cart cart = new Cart();
        cart.setUser(user);
        addToCart(cart, mouse, 2);
        addToCart(cart, keyboard, 3);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(placeOrderRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Not enough stock for Keyboard");

        verify(orderRepository, never()).save(any());
        assertThat(cart.getItems()).hasSize(2);
        // The real database rollback of the mouse's stock is NOT checked here.
        // A mock has no database; that part is proven by the manual rollback test.
    }

    @Test
    void placeOrder_emptyCart_throws() {
        User user = user(1L, Role.USER);
        Cart cart = new Cart();
        cart.setUser(user);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(placeOrderRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your cart is empty");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_userHasNoCart_throws() {
        User user = user(1L, Role.USER);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(placeOrderRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your cart is empty");
    }

    // ---------- viewing orders (ownership) ----------

    @Test
    void getOrderById_userCannotSeeSomeoneElsesOrder() {
        User otherUser = user(2L, Role.USER);

        when(currentUserProvider.getCurrentUser()).thenReturn(otherUser);
        when(orderRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found with id: 5");
    }

    @Test
    void getOrderById_adminCanSeeAnyOrder() {
        User admin = user(9L, Role.ADMIN);
        User owner = user(1L, Role.USER);
        Order order = order(5L, owner, OrderStatus.PLACED, product(1L, "Mouse", 600.0, 8), 2);

        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(5L);

        assertThat(response.getOrderId()).isEqualTo(5L);
        assertThat(response.getUserEmail()).isEqualTo("user1@example.com");
        verify(orderRepository, never()).findByIdAndUserId(any(), any());
    }

    // ---------- cancel ----------

    @Test
    void cancelMyOrder_restoresStockAndMarksCancelled() {
        User user = user(1L, Role.USER);
        Product mouse = product(1L, "Mouse", 600.0, 8);
        Order order = order(5L, user, OrderStatus.PLACED, mouse, 2);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelMyOrder(5L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(mouse.getStock()).isEqualTo(10);
    }

    @Test
    void cancelMyOrder_shippedOrder_throwsAndKeepsStock() {
        User user = user(1L, Role.USER);
        Product mouse = product(1L, "Mouse", 600.0, 8);
        Order order = order(5L, user, OrderStatus.SHIPPED, mouse, 2);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelMyOrder(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only orders in PLACED status can be cancelled");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(mouse.getStock()).isEqualTo(8);
    }

    // ---------- admin status updates ----------

    @Test
    void updateStatus_placedToShipped_works() {
        Order order = order(5L, user(1L, Role.USER), OrderStatus.PLACED, product(1L, "Mouse", 600.0, 8), 2);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateStatus(5L, statusRequest(OrderStatus.SHIPPED));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateStatus_deliveredOrder_cannotBeChanged() {
        Order order = order(5L, user(1L, Role.USER), OrderStatus.DELIVERED, product(1L, "Mouse", 600.0, 8), 2);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(5L, statusRequest(OrderStatus.SHIPPED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already DELIVERED");
    }

    @Test
    void updateStatus_backToPlaced_isRejected() {
        Order order = order(5L, user(1L, Role.USER), OrderStatus.SHIPPED, product(1L, "Mouse", 600.0, 8), 2);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(5L, statusRequest(OrderStatus.PLACED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("An order cannot be moved back to PLACED");
    }

    @Test
    void updateStatus_adminCancel_restoresStock() {
        Product mouse = product(1L, "Mouse", 600.0, 8);
        Order order = order(5L, user(1L, Role.USER), OrderStatus.PLACED, mouse, 2);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateStatus(5L, statusRequest(OrderStatus.CANCELLED));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(mouse.getStock()).isEqualTo(10);
    }

    // ---------- helpers: build test data ----------

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("HASHED");
        user.setRole(role);
        return user;
    }

    private Product product(Long id, String name, double price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return product;
    }

    private void addToCart(Cart cart, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        cart.getItems().add(item);
    }

    private Order order(Long id, User owner, OrderStatus status, Product product, int quantity) {
        Order order = new Order();
        order.setId(id);
        order.setUser(owner);
        order.setStatus(status);
        order.setOrderDate(LocalDateTime.now());
        order.setShippingAddress("12-3 Main Road");
        order.setTotalAmount(product.getPrice() * quantity);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setPrice(product.getPrice());
        item.setQuantity(quantity);
        order.getItems().add(item);

        return order;
    }

    private PlaceOrderRequest placeOrderRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setShippingAddress("12-3 Main Road");
        return request;
    }

    private UpdateOrderStatusRequest statusRequest(OrderStatus status) {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(status);
        return request;
    }
}