package com.packflow.app.product;

import com.packflow.app.user.AppUser;
import com.packflow.app.user.AppUserRepository;
import com.packflow.app.user.Role;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;

    public ProductService(ProductRepository productRepository, AppUserRepository appUserRepository) {
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public Product create(ProductCommand command, String operatorUsername) {
        requireActiveManager(operatorUsername);
        return productRepository.save(new Product(
                command.sku(), command.name(), command.unit(), command.safetyStock(), command.enabled()));
    }

    @Transactional
    public Product update(Long productId, ProductCommand command, String operatorUsername) {
        requireActiveManager(operatorUsername);
        Product product = product(productId);
        product.update(command.sku(), command.name(), command.unit(), command.safetyStock(), command.enabled());
        return product;
    }

    @Transactional
    public void disable(Long productId, String operatorUsername) {
        requireActiveManager(operatorUsername);
        product(productId).disable();
    }

    private void requireActiveManager(String operatorUsername) {
        AppUser user = appUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Product maintenance requires an active manager"));
        if (!user.isEnabled() || user.getRole() != Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Product maintenance requires an active manager");
        }
    }

    private Product product(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public record ProductCommand(
            String sku,
            String name,
            String unit,
            BigDecimal safetyStock,
            boolean enabled) {
    }
}
