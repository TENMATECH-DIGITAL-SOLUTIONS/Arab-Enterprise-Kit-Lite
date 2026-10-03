package com.enterprise.starter.kit.shared.tenant;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Optional;

/**
 * Repository base class that makes {@code findById} honour the Hibernate tenant filter.
 *
 * <p>The stock {@code SimpleJpaRepository.findById} calls {@code EntityManager.find}, and Hibernate
 * filters do not apply to a primary-key lookup. Without this, "logged in as tenant A, ask for
 * tenant B's record by id" returns B's record, and every {@code findById(...).orElseThrow()} in the
 * services (read, update, stock adjust, delete) becomes a cross-tenant hole.
 *
 * <p>Running the lookup as a query makes the filter apply. {@code deleteById} goes through
 * {@code findById}, so it is covered too. Entities without a tenant filter are unaffected.
 */
public class TenantAwareRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;

    public TenantAwareRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("The given id must not be null");
        }
        String idName = entityInformation.getRequiredIdAttribute().getName();
        String jpql = "select e from " + entityInformation.getEntityName() + " e where e." + idName + " = :id";
        return entityManager.createQuery(jpql, getDomainClass())
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }
}
