package com.example.ecommerce.service;

import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.dto.CartItemResponse;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.dto.UpdateCartItemRequest;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    public CartResponse getMyCart() {
        Cart cart = getOrCreateCart();
        return toResponse(cart);
    }

    public CartResponse addToCart(AddToCartRequest request) {
        Cart cart = getOrCreateCart();

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.getProductId()));

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        int newQuantity = request.getQuantity() + (existing != null ? existing.getQuantity() : 0);

        if (newQuantity > product.getStock()) {
            throw new BadRequestException("Only " + product.getStock() + " item(s) in stock");
        }

        if (existing != null) {
            existing.setQuantity(newQuantity);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(newQuantity);
            CartItem saved = cartItemRepository.save(item);
            cart.getItems().add(saved);
        }

        return toResponse(cart);
    }

    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart();
        CartItem item = findItem(cart, itemId);

        if (request.getQuantity() > item.getProduct().getStock()) {
            throw new BadRequestException("Only " + item.getProduct().getStock() + " item(s) in stock");
        }

        item.setQuantity(request.getQuantity());
        return toResponse(cart);
    }

    public CartResponse removeItem(Long itemId) {
        Cart cart = getOrCreateCart();
        CartItem item = findItem(cart, itemId);

        cart.getItems().remove(item);
        return toResponse(cart);
    }

    // ---------- helpers ----------

    private Cart getOrCreateCart() {
        User user = currentUserProvider.getCurrentUser();

        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    private CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getQuantity(),
                        item.getProduct().getPrice() * item.getQuantity()))
                .toList();

        double total = items.stream().mapToDouble(CartItemResponse::getSubtotal).sum();

        return new CartResponse(cart.getId(), items, total);
    }
}