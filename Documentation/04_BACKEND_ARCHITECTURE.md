

### Tenant Isolation Hardening (Lite security update)

The tenant filter is a Hibernate filter. Hibernate does **not** apply filters to primary-key lookups, and the old
aspect did nothing when no tenant was set. Both gaps are now closed:

| Layer | What it does |
|---|---|
| `TenantAwareRepository` | Repository base class (registered with `@EnableJpaRepositories(repositoryBaseClass = ...)`). `findById` runs as a query, so the tenant filter applies. A tenant can no longer read, change or delete another tenant's record by guessing its id. `deleteById` goes through `findById`, so it is covered too. |
| `TenantAspect` | Enables the filter before every service method. With **no** tenant in `TenantContext` it now **fails closed**: the filter is enabled with a value that matches nothing, so a scheduled or `@Async` job that forgets to set the tenant sees no data instead of every tenant's data. |
| `@TenantAgnostic` | Opt-out for services that legitimately run without a tenant: sign-in and registration (the tenant is not known yet), and payment webhooks (the tenant comes from the payload). Currently on: AuthService, CustomUserDetailsService, RefreshTokenService. |

**Rules when you add code**
- A scheduled or `@Async` job that touches tenant data must set the tenant itself, in a `try`/`finally`:
  `TenantContext.setTenantId(tenantId); try { ... } finally { TenantContext.clear(); }`
- A new service that is reached **without** a logged-in user (for example an API-key authentication lookup) needs `@TenantAgnostic`, otherwise it sees nothing.
- A native SQL query is never filtered by Hibernate; always add `tenant_id = :tenantId` yourself.
- Cross-tenant admin code must switch the filter off explicitly, and only for the platform admin.

**Tests**: `CatalogTenantIsolationTest` (or `ApiKeyTenantIsolationTest`) seeds two tenants and checks that tenant A cannot read, update or delete tenant B's records by id, and that a thread with no tenant sees nothing. `TenantFlowIntegrationTest` checks that registration, sign-in and normal tenant work still function.
