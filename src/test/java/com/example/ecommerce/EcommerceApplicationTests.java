package com.example.ecommerce;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs MySQL and environment variables; the API is covered by unit tests and manual tests")
class EcommerceApplicationTests {

    @Test
    void contextLoads() {
    }
}