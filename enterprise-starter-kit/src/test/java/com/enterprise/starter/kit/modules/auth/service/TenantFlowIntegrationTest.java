package com.enterprise.starter.kit.modules.auth.service;

import com.enterprise.starter.kit.modules.auth.dto.AuthResponse;
import com.enterprise.starter.kit.modules.auth.dto.LoginRequest;
import com.enterprise.starter.kit.modules.auth.dto.TenantRegistrationRequest;
import com.enterprise.starter.kit.modules.catalog.dto.CatalogItemRequest;
import com.enterprise.starter.kit.modules.catalog.service.CatalogService;
import com.enterprise.starter.kit.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check, with the real wiring, that failing closed on a missing tenant did not break the
 * flows that legitimately run without one: tenant registration and sign-in (the tenant is not known
 * until the user is found), followed by normal tenant-scoped work.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantFlowIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private CatalogService catalogService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void registerThenSignInWithNoTenantThenUseTheCatalogAsThatTenant() {
        String email = "owner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "S3cure-pass!";

        AuthResponse registered = authService.registerTenant(new TenantRegistrationRequest(
                "Flow Test Shop " + UUID.randomUUID().toString().substring(0, 4), email, password));
        assertThat(registered.token()).isNotBlank();

        // Sign-in happens before any tenant is known: no TenantContext, as in a real login request.
        TenantContext.clear();
        AuthResponse signedIn = authService.login(new LoginRequest(email, password));
        assertThat(signedIn.token()).isNotBlank();

        // Once the tenant is known (set from the JWT in the real filter), the catalog works as before.
        String tenantId = tenantIdFromRegistration(email);
        TenantContext.setTenantId(tenantId);
        catalogService.create(new CatalogItemRequest("Flow item", null, new BigDecimal("3.50"), null, true));
        assertThat(catalogService.getAll()).extracting(r -> r.name()).contains("Flow item");
    }

    @Autowired
    private com.enterprise.starter.kit.modules.auth.repository.UserRepository userRepository;

    private String tenantIdFromRegistration(String email) {
        return userRepository.findByUsernameOrEmail(email, email).orElseThrow().getTenantId();
    }
}
