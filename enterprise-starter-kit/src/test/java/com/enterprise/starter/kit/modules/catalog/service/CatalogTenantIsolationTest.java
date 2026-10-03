package com.enterprise.starter.kit.modules.catalog.service;

import com.enterprise.starter.kit.modules.catalog.dto.CatalogItemRequest;
import com.enterprise.starter.kit.modules.catalog.dto.CatalogItemResponse;
import com.enterprise.starter.kit.modules.catalog.entity.CatalogItem;
import com.enterprise.starter.kit.modules.catalog.repository.CatalogItemRepository;
import com.enterprise.starter.kit.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-tenant isolation tests for the catalog.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by {@code TenantAspect} for service calls, but a
 * Hibernate filter does NOT apply to a primary-key lookup ({@code findById} / {@code EntityManager.find}).
 * So "logged in as tenant A, read or change tenant B's record by id" is the case that must be tested
 * explicitly, together with the background-job case where no request (and no tenant) exists.
 *
 * <p>Each test seeds one item for tenant A and one for tenant B through the repository (the entity
 * takes its tenant id from {@link TenantContext} on insert), then calls the service as tenant A.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogTenantIsolationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CatalogItemRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long itemAId;
    private Long itemBId;

    @BeforeEach
    void seedTwoTenants() {
        itemAId = seed(TENANT_A, "Item of A");
        itemBId = seed(TENANT_B, "Item of B");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.deleteAllById(
                List.of(itemAId, itemBId)));
    }

    // ---------- listing (the filter is expected to work here) ----------

    @Test
    void listingAsTenantADoesNotReturnTenantBItems() {
        TenantContext.setTenantId(TENANT_A);

        List<String> names = catalogService.getAll().stream().map(CatalogItemResponse::name).toList();

        assertThat(names).contains("Item of A").doesNotContain("Item of B");
    }

    // ---------- by-id access (the filter does not apply to a primary-key lookup) ----------

    @Test
    void readingTenantBItemByIdAsTenantAIsRejected() {
        TenantContext.setTenantId(TENANT_A);

        assertThatThrownBy(() -> catalogService.getById(itemBId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatingTenantBItemByIdAsTenantAIsRejectedAndLeavesItUnchanged() {
        TenantContext.setTenantId(TENANT_A);

        assertThatThrownBy(() -> catalogService.update(itemBId, request("Hijacked by A")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(nameOf(itemBId)).isEqualTo("Item of B");
    }

    @Test
    void deletingTenantBItemByIdAsTenantAIsRejectedAndKeepsTheItem() {
        TenantContext.setTenantId(TENANT_A);

        assertThatThrownBy(() -> catalogService.delete(itemBId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(existsUnfiltered(itemBId)).isTrue();
    }

    // ---------- background jobs / async work ----------

    @Test
    void aBackgroundThreadWithTenantBContextSeesOnlyTenantBItems() throws Exception {
        List<String> names = onNewThread(() -> {
            TenantContext.setTenantId(TENANT_B);
            try {
                return catalogService.getAll().stream().map(CatalogItemResponse::name).toList();
            } finally {
                TenantContext.clear();
            }
        });

        assertThat(names).contains("Item of B").doesNotContain("Item of A");
    }

    @Test
    void aBackgroundThreadWithNoTenantContextMustNotSeeAnyTenantData() throws Exception {
        // ThreadLocal context is not inherited, so a scheduled or @Async job that forgets to set the
        // tenant runs with none. Failing closed (nothing visible) is the safe behaviour.
        List<String> names = onNewThread(() -> catalogService.getAll().stream()
                .map(CatalogItemResponse::name).toList());

        assertThat(names).doesNotContain("Item of A", "Item of B");
    }

    // ---------- helpers ----------

    private Long seed(String tenantId, String name) {
        TenantContext.setTenantId(tenantId);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                CatalogItem item = new CatalogItem();
                item.setName(name);
                item.setUnitPrice(new BigDecimal("10.00"));
                return repository.save(item).getId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    private static CatalogItemRequest request(String name) {
        return new CatalogItemRequest(name, null, new BigDecimal("10.00"), null, true);
    }

    private String nameOf(Long id) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> repository.findById(id).orElseThrow().getName());
    }

    private boolean existsUnfiltered(Long id) {
        // Plain primary-key lookup with no tenant filter, to check the row itself still exists.
        return new TransactionTemplate(transactionManager)
                .execute(status -> repository.findById(id).isPresent());
    }

    private static <T> T onNewThread(Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(task).get();
        } finally {
            executor.shutdownNow();
        }
    }
}
