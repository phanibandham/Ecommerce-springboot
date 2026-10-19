package com.example.ecommerce.service;

import com.example.ecommerce.dto.LoginRequest;
import com.example.ecommerce.dto.LoginResponse;
import com.example.ecommerce.dto.RegisterRequest;
import com.example.ecommerce.dto.UserResponse;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.UnauthorizedException;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    // ---------- register ----------

    @Test
    void register_savesUserWithHashedPasswordAndUserRole() {
        RegisterRequest request = registerRequest();

        when(userRepository.existsByEmail("ravi@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPassword()).isEqualTo("HASHED");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("ravi@example.com");
    }

    @Test
    void register_duplicateEmail_throwsAndSavesNothing() {
        when(userRepository.existsByEmail("ravi@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).save(any());
    }

    // ---------- login ----------

    @Test
    void login_correctCredentials_returnsToken() {
        User user = existingUser();

        when(userRepository.findByEmail("ravi@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "HASHED")).thenReturn(true);
        when(jwtUtil.generateToken("ravi@example.com", Role.USER)).thenReturn("fake.jwt.token");

        LoginResponse response = authService.login(loginRequest("secret123"));

        assertThat(response.getToken()).isEqualTo("fake.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void login_wrongPassword_throwsAndIssuesNoToken() {
        when(userRepository.findByEmail("ravi@example.com")).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    void login_unknownEmail_givesSameErrorMessage() {
        when(userRepository.findByEmail("ravi@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("secret123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(passwordEncoder, jwtUtil);
    }

    // ---------- helpers ----------

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ravi");
        request.setEmail("ravi@example.com");
        request.setPassword("secret123");
        return request;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("ravi@example.com");
        request.setPassword(password);
        return request;
    }

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setName("Ravi");
        user.setEmail("ravi@example.com");
        user.setPassword("HASHED");
        user.setRole(Role.USER);
        return user;
    }
}