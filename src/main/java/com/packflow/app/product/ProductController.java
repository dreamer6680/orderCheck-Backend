package com.packflow.app.product;

import com.packflow.app.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    public ProductController(ProductRepository productRepository, ProductService productService) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> listProducts() {
        return productRepository.findAllByOrderByIdAsc().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return ProductResponse.from(product(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request, Authentication authentication) {
        Product product = productService.create(command(request), usernameOf(authentication));
        return ResponseEntity.created(URI.create("/api/products/" + product.getId())).body(ProductResponse.from(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ProductResponse updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request, Authentication authentication) {
        Product product = productService.update(id, command(request), usernameOf(authentication));
        return ProductResponse.from(product);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, Authentication authentication) {
        productService.disable(id, usernameOf(authentication));
        return ResponseEntity.noContent().build();
    }

    private String usernameOf(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.username();
        }
        return authentication.getName();
    }

    private ProductService.ProductCommand command(ProductRequest request) {
        return new ProductService.ProductCommand(
                request.sku(), request.name(), request.unit(), request.safetyStock(), request.enabled());
    }

    private Product product(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public record ProductRequest(
            @NotBlank @Size(max = 50) String sku,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin(value = "0") BigDecimal safetyStock,
            boolean enabled) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String unit,
            BigDecimal safetyStock,
            boolean enabled) {

        static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getUnit(),
                    product.getSafetyStock(),
                    product.isEnabled());
        }
    }
}
