package com.enterprise.starter.kit.shared.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service that legitimately runs without a current tenant: sign-in and registration
 * (the tenant is not known yet), and payment webhooks that resolve the tenant from the payload.
 *
 * <p>Every other service fails closed: with no tenant in {@link TenantContext}, the tenant filter is
 * enabled with a value that matches nothing, so a background job that forgets to set the tenant
 * sees no data instead of every tenant's data. See {@code TenantAspect}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAgnostic {
}
