package com.enterprise.starter.kit.config;

import com.enterprise.starter.kit.shared.tenant.TenantAgnostic;
import com.enterprise.starter.kit.shared.tenant.TenantContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * AOP aspect that enables the Hibernate multi-tenant filter before any service method.
 *
 * <p>With a tenant in {@link TenantContext} the filter is scoped to that tenant. With <b>no</b>
 * tenant it fails closed: the filter is enabled with a value that matches no row, so a scheduled or
 * {@code @Async} job that forgets to set the tenant sees nothing instead of every tenant's data.
 * Services that legitimately run without a tenant (sign-in, registration, payment webhooks) opt out
 * with {@link TenantAgnostic}.
 *
 * <p>Global / shared entities (e.g. subscription_plans) that do NOT carry a
 * {@code @FilterDef(name="tenantFilter")} annotation are silently skipped.
 *
 * <p>Primary-key lookups are covered separately by
 * {@link com.enterprise.starter.kit.shared.tenant.TenantAwareRepository}, because a Hibernate filter
 * does not apply to {@code EntityManager.find}.
 */
@Aspect
@Component
public class TenantAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantAspect.class);

    /** Matches no real tenant id; used to fail closed when no tenant is set. */
    static final String NO_TENANT = "__no_tenant__";

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.enterprise.starter.kit.modules..service..*(..))")
    public void enableTenantFilter(JoinPoint joinPoint) {
        String tenantId;
        try {
            tenantId = TenantContext.getTenantId();
        } catch (Exception e) {
            log.warn("Could not get tenantId from TenantContext: {}", e.getMessage());
            return;
        }

        if (tenantId == null) {
            if (isTenantAgnostic(joinPoint)) return;
            log.debug("No tenant in context for {}: failing closed", joinPoint.getSignature().toShortString());
            tenantId = NO_TENANT;
        }

        try {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            log.debug("Tenant filter enabled for tenantId={}", tenantId);
        } catch (Exception ex) {
            log.trace("Could not enable tenantFilter (entity may not define it): {}", ex.getMessage());
        }
    }

    private static boolean isTenantAgnostic(JoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target == null) return false;
        return AnnotationUtils.findAnnotation(AopUtils.getTargetClass(target), TenantAgnostic.class) != null;
    }
}
