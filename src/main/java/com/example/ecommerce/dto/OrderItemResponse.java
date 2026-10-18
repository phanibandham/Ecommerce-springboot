package com.example.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderItemResponse {

    private Long productId;
    private String productName;
    private double price;
    private int quantity;
    private double subtotal;
}