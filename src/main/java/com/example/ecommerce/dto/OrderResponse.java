package com.example.ecommerce.dto;

import com.example.ecommerce.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private String userEmail;
    private OrderStatus status;
    private LocalDateTime orderDate;
    private String shippingAddress;
    private double totalAmount;
    private List<OrderItemResponse> items;
}