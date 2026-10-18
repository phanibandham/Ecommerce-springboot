package com.example.ecommerce.dto;

import com.example.ecommerce.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private String email;
    private Role role;
}