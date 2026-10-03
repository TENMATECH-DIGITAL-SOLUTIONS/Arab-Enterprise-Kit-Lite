# Changelog

## Lite security update (2026-10-03), security update

### Fixed
- **Cross-tenant access by id.** A logged-in user of one tenant could read or update a record belonging to another tenant by supplying its id, because Hibernate filters do not apply to `findById`. Primary-key lookups now go through the tenant filter (`TenantAwareRepository`).
- **No tenant meant no filter.** Scheduled and `@Async` code running without a tenant could see every tenant's data. The filter now fails closed when no tenant is set.

### Added
- `@TenantAgnostic` marker for services that legitimately run without a tenant (AuthService, CustomUserDetailsService, RefreshTokenService).
- Tenant isolation tests (cross-tenant read/update/delete by id, background thread with and without a tenant) and a register / sign-in / use flow test.

### Upgrading
- Replace `TenantAspect`, add `TenantAwareRepository` and `TenantAgnostic` (`shared/tenant`), add `@EnableJpaRepositories(repositoryBaseClass = TenantAwareRepository.class)` to the application class, and put `@TenantAgnostic` on any service reached without a logged-in user.
- Check your own scheduled and async jobs: they must set the tenant explicitly (see Documentation, Tenant Isolation Hardening).
