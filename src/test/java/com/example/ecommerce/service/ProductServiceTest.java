package com.example.ecommerce.service;

import com.example.ecommerce.dto.PageResponse;
import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.dto.ProductResponse;
import com.example.ecommerce.entity.Category;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    // ---------- search ----------

    @Test
    void search_minPriceAboveMaxPrice_throwsBeforeTouchingDatabase() {
        assertThatThrownBy(() ->
                productService.searchProducts(null, null, 1000.0, 500.0, 0, 10, "id", "asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("minPrice cannot be greater than maxPrice");

        verifyNoInteractions(productRepository);
    }

    @Test
    void search_capsPageSizeAndFixesNegativePage() {
        when(productRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<Product>(List.of()));

        productService.searchProducts(null, null, null, null, -3, 1000, "id", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(isNull(), isNull(), isNull(), isNull(), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void search_unknownSortField_fallsBackToIdWithRequestedDirection() {
        when(productRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<Product>(List.of()));

        productService.searchProducts(null, null, null, null, 0, 10, "banana", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(any(), any(), any(), any(), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("banana")).isNull();
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void search_blankKeywordBecomesNull_andResultsAreMappedToDtos() {
        Product mouse = product(1L, "Mouse", 600.0, 10);
        Page<Product> page = new PageImpl<>(List.of(mouse));
        when(productRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        PageResponse<ProductResponse> result =
                productService.searchProducts("   ", null, null, null, 0, 10, "price", "asc");

        verify(productRepository).search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Mouse");
        assertThat(result.getContent().get(0).getCategoryId()).isNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    // ---------- add / get ----------

    @Test
    void addProduct_withCategory_trimsNameAndReturnsCategoryDetails() {
        ProductRequest request = validRequest();
        request.setName("  Gaming Mouse  ");
        request.setCategoryId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category(1L, "Electronics")));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(10L);
            return product;
        });

        ProductResponse response = productService.addProduct(request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Gaming Mouse");
        assertThat(response.getCategoryId()).isEqualTo(1L);
        assertThat(response.getCategoryName()).isEqualTo("Electronics");
    }

    @Test
    void addProduct_unknownCategory_throwsAndSavesNothing() {
        ProductRequest request = validRequest();
        request.setCategoryId(99L);

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.addProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductById_missingProduct_throwsNotFound() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id: 5");
    }

    // ---------- helpers ----------

    private ProductRequest validRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("Gaming Mouse");
        request.setDescription("RGB gaming mouse");
        request.setPrice(1299.0);
        request.setStock(20);
        return request;
    }

    private Product product(Long id, String name, double price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return product;
    }
}