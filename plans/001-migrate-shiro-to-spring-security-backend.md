# Plan 001: Migrate backend security from Shiro to Spring Security

> **Executor instructions**: Implement every workstream on one dedicated
> migration branch and deliver them together in one PR and one production
> cutover. Run every verification command and confirm its expected result before
> moving on. Intermediate revisions are development checkpoints only: do not
> merge, release, deploy, or advertise them as supported configurations. If a
> STOP condition occurs, stop and report; do not improvise. When complete,
> update the status row in `plans/README.md` unless a reviewer says they maintain
> it.
>
> **Drift check (run first)**:
> `git diff --stat 6f2c3af77..HEAD -- pom.xml src/main/java src/main/resources src/main/webapp/WEB-INF src/test/java src/test/resources DevDocs/DeveloperNotes`
> If any in-scope security file changed, compare the current-state evidence in
> this plan with the live code. Treat a semantic mismatch as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L (multi-week, one repository, one coordinated cutover)
- **Risk**: HIGH
- **Depends on**: the core-model move into rspace-web is complete
- **Category**: migration / security / tests / architecture / docs
- **Planned at**: commit `6f2c3af77`, 2026-07-22

## Outcome and definition of phase one

At the end of this plan, Spring Security 6.5 is authoritative for all backend
request authentication, URL authorization, method authorization, servlet
session persistence, logout, API/WOPI request contexts, async propagation, and
sysadmin impersonation.

The following contracts remain unchanged:

- AJP/Tomcat/Apache/IdP configuration and trusted SSO request attributes.
- `RemoteUserRetrievalPolicy` implementations, selection properties, and their
  externally supplied input names.
- Login, logout, admin-login, SSO redirect, API, WOPI, and run-as URLs; request
  parameter names; HTTP response bodies; and existing 401/403 behavior.
- Session cookie naming, 30-minute timeout, session attributes, analytics and
  cleanup listeners, and post-login side effects.
- React/TypeScript code and visible behavior. JSP markup may change only to
  replace Shiro security tags with Spring/RSpace equivalents.
- Stored permission strings and password hashes.

JSP markup is migrated in the same big-bang change. Add
`spring-security-taglibs`, register its standard URI, and replace the six Shiro
tag forms actually used by RSpace: `authenticated`, `principal`, `hasRole`,
`lacksRole`, `hasAnyRoles`, and `hasPermission`. Use Spring's `sec:authorize`
and `sec:authentication` for authentication, principal, coarse roles, and the
single string-permission expression, delegating that expression to the central
RSpace permission bean. Migrate the three existing RSpace extension tags to the
same actor/permission modules. After cutover there must be no Shiro runtime,
dependency, session, subject, import, tag URI, or tag implementation.

This is a **big-bang, replace-don't-layer migration**. Characterization tests
may capture legacy outcomes, but production code must never contain two
authoritative authentication or authorization implementations. Do not create a
pluggable framework or a feature flag that can switch back to Shiro.

## Why this matters

Shiro is woven through authentication providers, a first-position servlet
filter, custom object permissions, static thread context, HTTP sessions, async
executors, tests, and server-rendered tags. A mechanical library swap would
silently change security semantics. The repository already resolves Spring
Security 6.5.9 for password crypto, so the safe big-bang route is to capture
legacy outcomes as tests, implement the complete Spring replacement on one
branch, replace the servlet/taglib wiring once, and merge only when Shiro has
been removed everywhere.

The plan follows the Spring Security 6.5 architecture: `SecurityFilterChain`
for request rules, typed `AuthenticationProvider`s for distinct trust paths,
explicit `SecurityContextRepository` persistence for programmatic logins,
Spring method authorization, and delegating context propagation for executor
work. See the official references in “Reference material.”

## Current state

### Bootstrap and URL rules

- `src/main/webapp/WEB-INF/web.xml:30-38` loads
  `/WEB-INF/security.xml` into the root context.
- `src/main/webapp/WEB-INF/web.xml:95-103,155-159` registers a
  `DelegatingFilterProxy` called `shiroFilter` first over `/*`.
- `src/main/webapp/WEB-INF/security.xml:37-80` is the authoritative ordered
  route matrix: public/static callbacks, admin/system role gates, sessionless
  API/OAuth/WOPI routes, and an authenticated catch-all.
- `src/main/webapp/WEB-INF/security.xml:83-96` adds conditional SSL,
  OR-role filtering, Shiro lifecycle, and Shiro method advice.

Preserve ordering and use `hasAnyAuthority("ROLE_...")`, not `hasRole`, when
translating existing full role names. RSpace's `AnyOfRolesAuthorizationFilter`
is explicitly OR-based.

### Authentication mechanisms

`src/main/java/com/axiope/service/cfg/SecurityRunProdConfig.java:27-68`
assembles these conditional Shiro realms:

1. Local username/password (standalone, or SSO admin backdoor).
2. Trusted upstream SSO identity.
3. LDAP username/password and optional signup/SID checks.
4. API key and API OAuth token.
5. WOPI access token.
6. Slack and external OAuth callbacks.
7. In-process global-init sysadmin identity.

Do not collapse pass-through mechanisms into one permissive provider. A typed
provider must accept only its own `Authentication` class, and the code that
validated the upstream credential must remain adjacent to creation of that
authenticated token.

The local realm resolves username aliases, rejects LDAP accounts from local
auth, and rejects normal local accounts in SSO mode before validating a salted
SHA-256 hash (`ShiroRealm.java:44-101`). `UserManagerImpl.java:200-205`
creates that existing salt/hash. Implement a compatible Spring
`PasswordEncoder`; do not rehash in this plan.

### Stable SSO/AJP seam

- `RemoteUserRetrievalPolicy.java:25-32` is the framework-neutral request seam.
- `ProductionConfig.java:250-260` selects SAML, OpenID, test, or EASE policy
  from existing deployment properties.
- `SSOShiroFormAuthFilterExt.java:64-149` performs DB alias resolution,
  conflict/backdoor checks, maintenance/enabled checks, remote username session
  storage, optional PI-role update, login, post-login, and redirect behavior.

AJP is configured outside this repository. Keep all upstream configuration and
all policy input names unchanged. Replace only the Shiro token handoff after
the existing policy has extracted the trusted identity. Do not use a generic
request-header filter that would trust a broader input surface.

### Session and login lifecycle

- `BaseLoginHelperImpl.java:65-103` populates the application session, updates
  last-login history, logs security/analytics events, and initializes message
  counts. It must run exactly once after successful interactive login.
- `UserManagerImpl.java:207-233,358-368` reads and refreshes the domain `User`
  stored under `SessionAttributeUtils.USER` via Shiro session APIs.
- `LogoutController.java:30-75` preserves distinct standalone/SSO redirect
  behavior and run-as release.
- Servlet-session destruction drives analytics and record-editor cleanup.
- `web.xml` owns the 30-minute timeout and cookie-only tracking; the session
  cookie name may be overridden by `SessionCookieNameListener`.

Use the servlet `HttpSession` and `HttpSessionSecurityContextRepository`.
Programmatic authentication and logout must explicitly save or clear the
Spring `SecurityContext`; do not merely assign `SecurityContextHolder`.

### Authorization semantics

- `RSpaceRealm.java:31-75` loads role permissions, direct user permissions, and
  inherited UserGroup/Group permissions, including a DB reconciliation when
  eagerly loaded membership is incomplete.
- `PermissionUtils.java:227-282` applies public-link/parent-public-link READ,
  object permission matching, then ACL fallback.
- The in-repository core-model sources expose Shiro `Permission` from
  `IEntityPermission`, `ConstraintBasedPermission`, `ConstraintPermissionResolver`,
  `IPermissionUtils`, `Role`, `User`, `UserGroup`, and related types. At plan
  start, locate their post-move paths with
  `rg --files | rg '/com/researchspace/model/(permissions/)?'`; all are in the
  same reactor and migration branch.
- The custom matcher includes disabled grants, ALL-domain wildcarding,
  property/group/community/id/location constraints, `${self}`, and
  WRITE-implies-READ. Preserve it; string authorities cannot replace it.
- There are exactly six Shiro method annotations, all
  `@RequiresPermissions`, in `FormManagerImpl.java:100,110,124,223,241,306`.
- About 95 production Java files import Shiro `AuthorizationException`; API and
  MVC handlers attach existing status/logging contracts to it.

### Thread context, impersonation, and UI

- `ShiroThreadBindingSubjectThreadPoolExecutor.java:35-57` propagates the
  subject to submitted `Callable`s.
- `PermissionUtils.java:330-350`, `OperateAsUserLookup.java:23-42`, and
  `LogoutController.java:63-75` implement persistent sysadmin impersonation
  through Shiro's principal stack.
- Existing UI submits run-as through `POST /system/ajax/runAs` and exits through
  `GET /logout/runAsRelease`; keep those contracts.
- JSPs contain 50 standard Shiro tag openings across 17 files: 29 `hasRole`,
  10 `hasAnyRoles`, 8 `lacksRole`, and one each of `authenticated`, `principal`,
  and `hasPermission`. Five shared JSP/tag files declare the Shiro URI directly,
  while most pages inherit it from `common/taglibs.jsp`. All declarations and
  usages are in scope for direct migration.

### Test gap

`MVCTestBase` builds MockMvc without Shiro or Spring security filters; common
test contexts do not load `security.xml`. Existing MVC tests can therefore pass
while the actual URL filter chain is broken. Existing direct filter tests are
valuable behavioral examples but do not replace a real-chain matrix.

## Module shape and seams

Use these application-owned seams; do not expose Spring Security types below
the auth package or in the in-repository core-model packages.

### 1. Current actor module

Add a small concrete Spring-backed module in `com.researchspace.auth` whose
interface to callers exposes only:

- Whether an actor is authenticated.
- The effective username.
- The original username when impersonating.
- Whether impersonation is active.

Implement it directly over Spring's `SecurityContextHolder`; do not add a Shiro
adapter or framework-selection interface. Migrate every direct `SecurityUtils`
identity/run-as query in the same branch. Keep servlet-session operations in
web code or an injected request/session collaborator, not in core domain
objects.

### 2. Permission decision module

Deepen the existing `IPermissionUtils`/`PermissionUtils` seam instead of adding
a competing permission facade:

- Extract a single permission-snapshot loader that reproduces
  `RSpaceRealm.doGetAuthorizationInfo`, including membership reconciliation.
- Use role strings only for coarse URL/method gates.
- Keep `ConstraintBasedPermission` matching for object decisions.
- Make “current actor” versus an explicitly supplied `User` unambiguous. The
  current implementation sometimes queries Shiro for grants while using the
  supplied user only for ACL fallback; characterize each caller before changing
  that behavior.
- Keep one cache and one invalidation path. Do not put all mutable object grants
  permanently into session `GrantedAuthority` instances.

### 3. Authentication orchestration

Spring's standard `ProviderManager`/typed providers are the seam. Reuse the
existing `LoginAuthorizer` list and `LoginHelper.postLogin` lifecycle rather
than duplicating account, maintenance, analytics, or first-login rules in each
provider. Providers establish identity; one success handler/orchestrator runs
shared post-auth checks and saves the session context.

### 4. JSP security tags

Add `spring-security-taglibs` at the same managed Spring Security version and
declare `<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>`
in `common/taglibs.jsp` and the five tag files that currently declare Shiro
directly.

Translate markup mechanically:

- `<shiro:authenticated>` → `<sec:authorize access="isAuthenticated()">`.
- `<shiro:principal/>` → `<sec:authentication property="principal.username"/>`
  if the Spring principal is a user-details object, otherwise use
  `property="name"`; lock the chosen shape with a render test.
- `hasRole name="ROLE_X"` → `sec:authorize
  access="hasAuthority('ROLE_X')"`.
- `lacksRole name="ROLE_X"` → `sec:authorize
  access="!hasAuthority('ROLE_X')"`.
- `hasAnyRoles name="ROLE_A,ROLE_B"` → `sec:authorize
  access="hasAnyAuthority('ROLE_A','ROLE_B')"`.
- The single `hasPermission name="Form:Create"` usage → `<sec:authorize
  access="@permissionUtils.isPermitted('FORM:CREATE')">`, delegating to the
  central evaluator bean; do not flatten it into a string authority.

Migrate `FormPermissionTag`, `RecordPermissionTag`, and `IsRunAs` to the Spring
current-actor/permission modules and rename the extension TLD descriptions away
from Shiro. Preserve body-inclusion and principal-output behavior exactly.

## Reference material

- Spring Security 6.5 authentication architecture:
  https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html
- Pre-authentication (the model for trusted upstream SSO identity):
  https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/preauth.html
- Method security and custom authorization integration:
  https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html
- Session persistence and explicit context saving:
  https://docs.spring.io/spring-security/reference/6.5/migration/servlet/session-management.html
- Concurrency context propagation:
  https://docs.spring.io/spring-security/reference/6.5/features/integrations/concurrency.html
- MockMvc security setup:
  https://docs.spring.io/spring-security/reference/6.5/servlet/test/mockmvc/setup.html
- JSP tag libraries (`sec:authorize`, `sec:authentication`, and the standard
  tag URI):
  https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/jsp-taglibs.html
- Password storage and legacy-format migration support:
  https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html

## Commands and expected results

Never run Maven `install`, any `install:*` goal, or a deploy goal.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Format check | `./mvnw -q spotless:check` | exit 0 |
| Focused pure tests | `./mvnw test -Dtest=<comma-separated classes> -Dfast=true` | exit 0, requested tests run |
| Fast backend suite | `./mvnw clean test -Dfast=true` | exit 0 |
| DB-backed tests | `./mvnw clean test -Denvironment=drop-recreate-db` | exit 0; requires test DB and resets it |
| Full verification | `./mvnw clean verify -Denvironment=drop-recreate-db` | exit 0; requires test DB and resets it |
| Production WAR | `./mvnw clean package -DgenerateReactDist -DskipTests=true` | exit 0, WAR built |
| Dependency graph | `./mvnw dependency:tree -Dincludes=org.springframework.security,org.apache.shiro` | one Spring Security version; no Shiro artifacts |
| No Shiro | `rg -n -i 'org\.apache\.shiro|SecurityUtils|<artifactId>shiro-|http://shiro\.apache\.org/tags|<shiro:' pom.xml src/main src/test` | no output |
| No React/TS diff | `git diff --name-only 6f2c3af77..HEAD -- src/main/webapp/ui` | no output |

Frontend TypeScript is unchanged by design, so `pnpm tsc` is not needed unless
the executor violates scope and touches frontend files; in that case STOP.

## Scope

### In scope

- `pom.xml`
- `src/main/java/com/researchspace/auth/**`
- `src/main/java/com/axiope/service/cfg/**` security/provider/executor beans
- `src/main/java/com/researchspace/webapp/filter/**` security filters
- `src/main/java/com/researchspace/webapp/controller/**` identity, logout,
  exception mapping, method authorization, and run-as call sites only
- `src/main/java/com/researchspace/api/v1/auth/**`
- `src/main/java/com/researchspace/api/v1/controller/ApiAuthenticationInterceptor.java`
- `src/main/java/com/researchspace/webapp/integrations/wopi/WopiAuthorisationInterceptor.java`
- `src/main/java/com/researchspace/spring/taskexecutors/**`
- Remaining production Java files only where they import Shiro, call
  `SecurityUtils`, or expose Shiro exception/permission types
- `src/main/webapp/WEB-INF/web.xml` filter proxy name/mapping only
- `src/main/webapp/WEB-INF/security.xml` replacement/removal
- Security-specific tests and test infrastructure under `src/test/**`
- Relevant docs under `DevDocs/DeveloperNotes/**`
- JSP tag declarations/usages, `shiroExtensions.tld`, and the three RSpace tag
  classes
- The in-repository core-model permission interfaces/types/tests at their
  post-move paths

### Explicitly out of scope

- External Apache/Tomcat/AJP/IdP configuration.
- Changes to `RemoteUserRetrievalPolicy` behavior, policy selection, header or
  request-attribute names, or SSO deployment property names/values.
- `src/main/webapp/ui/**`, JSP content unrelated to security tags, styles, and
  visible behavior.
- CSRF enablement or adding CSRF tokens to UI requests.
- Password rehashing, schema changes, or login-form changes.
- New OAuth/OIDC/SAML protocols or Spring Authorization Server.
- New session-concurrency limits, remember-me, MFA, or security headers.
- Changes to API authentication headers or error payload/status contracts.
- Changes to logout URLs or the externally configured SSO logout target.
- Monitoring credential remediation (coordinate separately).

## Git workflow

- Use one dedicated migration branch/PR in rspace-web. Do not merge or deploy it
  until every workstream and final negative-Shiro gate passes.
- Intermediate logical commits are encouraged for review and recovery, but
  none represents a supported partial migration. Existing history uses
  ticket-prefixed imperative subjects (for example `RSDEV-444: ...`); match that
  style when a ticket exists.
- Core-model and web-security edits are part of the same branch/reactor after
  the prerequisite move. Do not retain a published binary pin or create a
  second repository workflow for this migration.
- Do not push, open a PR, or merge unless explicitly instructed.

## Big-bang implementation workstreams

Execute these in order on the migration branch because later work depends on
earlier tests and types. They are not independently mergeable or deployable.

### Workstream 1: Add dependencies and characterize the real security contract

1. Add one managed Spring Security 6.5.9 version and direct dependencies on
   `spring-security-core`, `spring-security-web`, and `spring-security-config`;
   add `spring-security-taglibs` and test-scoped `spring-security-test`. Keep
   existing Shiro dependencies only inside this unmerged characterization
   checkpoint. Do not register `springSecurityFilterChain` yet.
2. Add `WebSecurityRouteCharacterizationIT` using the real current Shiro filter
   in a production-like root web context. Do not model this after `MVCTestBase`
   without adding the actual filter: that base intentionally omits security.
3. Table-drive representative rules from `security.xml`:
   public/static; login/adminLogin/signup/externalAuth; self-service PI;
   system sysadmin/admin combinations; community admin; monitoring;
   `/api/**`; `/oauth/**`; `/wopi/**`; authenticated catch-all; SSL enabled and
   disabled. Assert status/redirect, OR-role behavior, and session creation.
4. Add full-chain standalone login cases for valid/invalid credentials, alias,
   LDAP routing, locked/disabled/review-required accounts, AJAX 403 body, normal
   redirect, admin-login redirect, legacy failure request attributes, one
   post-login call, and next request with the same session.
5. Add full-chain SSO cases for the existing trusted input styles using mocked
   servlet request input: known/unknown user, username alias, alias conflict,
   backdoor conflict, disabled/maintenance, remote username session attribute,
   PI attribute update, post-login, `/login` redirect, and SSO logout target.
   These are app tests, not AJP configuration tests.
6. Add session lifecycle cases: current cookie-name override contract,
   session-id behavior at login (record current behavior; do not silently
   change it), 30-minute container ownership, logout invalidation, analytics and
   editor cleanup exactly once.
7. Preserve and reuse the behavior in:
   `StandaloneShiroFormAuthFilterExtTest`, `SSOShiroFormAuthFilterTest`,
   `LogoutControllerTest`, `LoginHelperTest`, and `SecurityRealmConfigTest`.

**Verify**:

```bash
./mvnw test -Dtest=WebSecurityRouteCharacterizationIT,StandaloneShiroFormAuthFilterExtTest,SSOShiroFormAuthFilterTest,LogoutControllerTest,SecurityRealmConfigTest
```

Expected: all tests pass against the still-authoritative Shiro filter; Maven
dependency convergence reports only Spring Security 6.5.9 modules.

### Workstream 2: Replace actor access and framework exception leakage

1. Add the Spring-backed current-actor module described above. Do not add a
   Shiro adapter or runtime switch.
2. Migrate all production `SecurityUtils` identity, authentication, run-as, and
   previous-principal reads through that module. Do not yet migrate login,
   logout, object permission evaluation, or JSP compatibility.
3. Introduce an application-owned authorization-denied exception. Replace
   Shiro `AuthorizationException` in application interfaces, implementations,
   controller advice, catches, and tests without changing current response
   status, payload, message-bundle, or security-log behavior.
   Extend `ApiControllerAdviceTest` and create a focused
   `ControllerExceptionHandlerTest` to lock those HTTP and logging contracts
   before changing the exception type.
4. Do not mechanically replace domain `User.hasRole(...)`; it is already a
   framework-neutral any-of comparison and most `.hasRole` calls use it.
5. Update test helpers to install/clear Spring actor state per test. Legacy
   outcome tests may retain isolated Shiro fixtures only until the final branch
   verification; production code must not.

**Verify**:

```bash
./mvnw test -Dtest=PermissionUtilsTest,OperateAsUserLookupTest,ApiControllerAdviceTest,ControllerExceptionHandlerTest -Dfast=true
rg -n 'SecurityUtils\.getSubject\(\).*(getPrincipal|isAuthenticated|isRunAs|getPreviousPrincipals)' src/main/java
```

Expected: tests pass; the search returns no production application call sites.

### Workstream 3: Decouple the in-repository domain permission model from Shiro

This plan assumes the core-model sources have already moved into the rspace-web
workspace and participate in its reactor build. At plan start, record their
actual post-move module/path in the PR description and update commands below if
the move used a nested Maven module rather than `src/main/java`.

1. Introduce an RSpace-owned permission interface (or use concrete
   `ConstraintBasedPermission` types where polymorphism is unnecessary).
   Remove Shiro inheritance/imports from `IEntityPermission`,
   `ConstraintBasedPermission`, `ConstraintPermissionResolver`,
   `IPermissionUtils`, `Permissable`, `Role`, `User`, `UserGroup`, and related
   permission collection signatures.
2. Preserve the persisted string grammar and database mapping exactly.
3. Preserve all matcher behavior: enabled flag; ALL domain; action matching;
   WRITE-implies-READ; property, group, community, id, and location constraints;
   `${self}` using the explicitly supplied permission owner; and cleanup of
   transient owner state after each comparison.
4. Remove the fallback from property matching to static Shiro subject state.
   Pass the permission owner/actor explicitly at the application evaluator.
5. Replace any Shiro authorization exception leaked by public interfaces with
   a neutral RSpace type in a lower/shared package. Do not introduce a
   core-model dependency on Spring Security or on controller/service packages.
6. Expand the moved matcher tests before changing signatures. Add one case for
   every implication dimension and round-trip persisted permission strings.
7. Verify the prerequisite move already removed the external
   `rspace-core-model` dependency/pin. If it did not, STOP because the
   prerequisite is incomplete; do not absorb repository relocation into this
   security migration.

**Verify in the rspace-web reactor**:

```bash
./mvnw test -Dtest=PermissionUtilsTest,GroupManagerTest,RecordManagerTest -Dfast=true
rg -n 'org\.apache\.shiro' src/main/java/com/researchspace/model
```

Expected: permission/service tests pass; the search returns no domain-model
Shiro imports. If the moved module uses another source root, run the same search
against that root.

### Workstream 4: Make the RSpace permission evaluator authoritative

1. Extract `RSpaceRealm.doGetAuthorizationInfo` into one Spring-managed
   permission snapshot loader. Load role permissions, direct user permissions,
   inherited UserGroup and Group permissions, and preserve the explicit
   `groupMgr.findByUserId` reconciliation.
2. Refactor `PermissionUtils` to evaluate snapshots and
   `ConstraintBasedPermission` directly, using the current-actor module for
   current-user checks. Preserve public-link and parent-public-link READ and ACL
   fallback in the same order.
3. Preserve current role/permission cache visibility: the existing default
   authorization TTL is 121 seconds, while permission mutation can notify an
   active user/group for earlier refresh. Use one JCache/Spring Cache-backed
   store and one invalidation path; remove proxy casting and realm iteration.
4. Make explicit-user checks actually use the named user for both grants and
   ACL, unless a characterization test proves a caller intentionally relies on
   current-actor grants. For any such intentional mixed case, name the method
   accordingly and document it; do not preserve accidental ambiguity.
5. Before removing the old classes, capture their outputs as immutable,
   framework-neutral test cases for role/direct/UserGroup/Group grants,
   `includePermissions=false`, stale eager membership, every constraint,
   disabled grants, wildcard, `${self}`, WRITE-implies-READ, ACL fallback, and
   public records. Run the new evaluator against those fixtures. Do not ship a
   second runtime evaluator or a production dual-decision mode.

**Verify**:

```bash
./mvnw test -Dtest=PermissionUtilsTest,PermissionsPerformanceTest,SSORunAsAcquiresAllPermissionsTest -Dfast=true
```

Expected: all captured legacy outcomes pass through the new evaluator;
grant/revoke cache tests prove the chosen visibility contract.

### Workstream 5: Implement every Spring authentication provider

1. Add typed Spring `Authentication` classes/providers for local password,
   LDAP, SSO pre-auth, API token, WOPI, Slack/external OAuth, and global-init.
   Each provider's `supports` must accept only its own token class.
2. Local auth must preserve alias resolution, LDAP-account rejection, SSO
   backdoor restrictions, and the exact salted SHA-256 comparison through a
   compatible `PasswordEncoder`. Do not use `NoOpPasswordEncoder`; do not alter
   stored hashes.
3. SSO pre-auth must call the unchanged `RemoteUserRetrievalPolicy`, preserve
   all current conflict/signup/maintenance/enabled/PI-update branches, and never
   treat the repository's placeholder SSO password as a credential.
4. Preserve the order and semantics of existing `LoginAuthorizer`s. Shared
   interactive success handling calls `LoginHelper.postLogin` exactly once and
   explicitly saves the Spring context to the repository.
5. Replace `SecurityRealmConfigTest` with a provider-composition test for
   standalone, LDAP, cloud, SSO, SSO admin-login, WOPI/Collabora, Slack, API,
   and global-init deployment combinations. Preserve the old matrix as test
   data, not as a second production configuration.
6. Add direct provider tests for wrong token type, invalid credentials,
   disabled/locked accounts, alias, and provider exceptions. No pass-through
   provider may authenticate a token before its upstream validator succeeds.

**Verify**:

```bash
./mvnw test -Dtest=SpringSecurityProviderConfigTest,LocalAuthenticationProviderTest,SsoPreAuthenticationProviderTest,LdapAuthenticationProviderTest,ApiAuthenticationProviderTest,WopiAuthenticationProviderTest -Dfast=true
```

Expected: all provider and deployment-composition tests pass on the migration
branch. The branch is still non-deployable until the complete cutover passes.

### Workstream 6: Build and test the complete ordered Spring filter chains

Build the full chains and their tests before replacing `web.xml`, but do not
merge or deploy this intermediate branch state.

1. Create an ordered API chain for `/api/**`. Preserve the ability to read an
   existing UI session without creating a new one (`SessionCreationPolicy.NEVER`,
   not blindly `STATELESS`), and ensure external token authentication is
   request-only and is never written into the UI session. Replace the
   interceptor's Shiro login/logout with Spring request context setup/cleanup
   while preserving the request `user` attribute and headers/error payloads.
2. Create request-only WOPI and other token/callback handling with their current
   anonymous URL reachability. Move authentication from the WOPI interceptor to
   a typed provider/filter only when its exact login/logout lifetime is covered.
3. Create the stateful web chain. Select local form login versus SSO pre-auth
   from existing deployment properties. Preserve POST `/login`, parameter and
   failure attributes, `/workspace` success, admin-login, AJAX 403, and saved
   request behavior.
4. Translate every ordered `security.xml` rule, including public callbacks,
   role OR rules with `hasAnyAuthority`, monitoring, API/OAuth/WOPI, and the
   authenticated catch-all. Keep a deny-by-default/catch-all rule.
5. Preserve conditional SSL behavior and port 8443 mapping using Spring channel
   security or a framework-neutral filter. Do not retain
   `ShiroSslFilterMavenAgnostic` after cutover.
6. Explicitly disable/ignore CSRF to match phase-one behavior and add a comment
   naming the UI-phase removal condition. Do not disable Spring's standard
   security headers unless a characterized response proves incompatibility.
7. Configure servlet-session security context persistence. Add tests proving
   no context/session leakage between sequential requests, external API calls,
   and thread reuse.
8. Run the Workstream 1 route/login/SSO/session suite unchanged against the Spring
   chain. A table row must not be rewritten merely to make Spring pass.

**Verify**:

```bash
./mvnw test -Dtest=SpringWebSecurityRouteIT,WebSecurityRouteCharacterizationIT,SpringSessionSecurityIT,ApiAuthenticationFilterIT,WopiAuthenticationFilterIT
```

Expected: the Spring chain satisfies every captured legacy route/login/session
outcome; external API/WOPI calls do not create or overwrite a UI security
session. No dual-filter production mode is configured.

### Workstream 7: Migrate method authorization, impersonation, and async context

1. Enable Spring method security in the correct root context without pulling
   provider dependencies into early bean initialization.
2. Replace the six `FormManagerImpl` Shiro annotations with Spring method
   authorization calling the central RSpace evaluator. Preserve all deeper
   object-specific checks in those methods.
3. Extend `TransactionAdviceStartupCheck` or an equivalent production-context
   test to prove representative `Manager` beans retain transaction advice and
   method authorization advice. Do not delete the existing transaction guard.
4. Replace persistent Shiro run-as with an `ImpersonationManager` that stores
   original and effective identities in the Spring `Authentication`, saves the
   context, preserves `SessionAttributeUtils.IS_RUN_AS`, active-user tracking,
   audit/email behavior, and the existing enter/exit URLs. On exit and logout,
   restore/clear the original context explicitly.
5. Do not use deprecated Spring `RunAsManager`; RSpace's feature is persistent
   user switching across requests, not temporary method invocation elevation.
6. Replace `ShiroThreadBindingSubjectThreadPoolExecutor` with Spring's
   `DelegatingSecurityContext*` support or a `TaskDecorator` applied to every
   executor currently built through `ProductionConfig` and `TestAppConfig`.
   Prove the context is cleared in `finally` and rejected tasks do not leak it.
7. Replace in-process global-init login/logout with a scoped helper that
   installs the system Spring context for the callback and restores the prior
   context in `finally`.

**Verify**:

```bash
./mvnw test -Dtest=FormManagerSecurityIT,ImpersonationManagerIT,SSORunAsAcquiresAllPermissionsTest,SecurityContextTaskExecutorTest,ProdConfigWiringTest
```

Expected: target-user permissions replace—not augment—admin permissions during
impersonation; exit restores admin; async tasks see the submitting actor and the
next pooled task sees no stale actor; production beans have both required
advice types.

### Workstream 8: Replace the JSP tag library and cut over the servlet filter

1. Replace the Shiro directive in `src/main/webapp/common/taglibs.jsp` and in
   `helpDocSection.tag`, `publishDlg.tag`, `shareDlg.tag`, and `crudops.tag` with
   the Spring Security taglib directive using prefix `sec`.
2. Replace all 50 `<shiro:...>` openings/closings using the mechanical mapping
   in “JSP security tags.” For the single `Form:Create` permission, call the
   central `permissionUtils` bean from `sec:authorize`. Rename
   `shiroExtensions.tld` to a framework-neutral security TLD/URI and update its
   declaration in `common/taglibs.jsp`; migrate `FormPermissionTag`,
   `RecordPermissionTag`, and `IsRunAs` off Shiro.
3. Render representative pages/tags for anonymous, user, PI, admin, sysadmin,
   Form:Create allowed/denied, record allowed/denied, and impersonated users.
   Compare rendered visibility and principal text with captured Shiro output.
4. Register `springSecurityFilterChain` through `DelegatingFilterProxy` at the
   existing first-filter position in `web.xml`. Do not change other filter
   ordering.
5. Remove Shiro URL/method authority from `security.xml` and delete the old
   `shiroFilter` factory, realm configuration, lifecycle/advisor, SSL filter,
   any-role filter, and early-initialization workarounds only after the
   production-context advice gate passes.
6. Remove the Shiro taglib JAR and prove every JSP resolves only Spring or
   RSpace security tag URIs. No legacy Shiro directive or subject lifecycle may
   remain.
7. Run the same route/login/SSO/session/render suite after the proxy switch.
   There must be exactly one authoritative request security chain and one
   Spring-backed JSP decision path.

**Verify**:

```bash
./mvnw test -Dtest=SpringWebSecurityRouteIT,JspSpringSecurityTagsIT,StandaloneAuthenticationIT,SsoAuthenticationIT,LogoutControllerTest,SpringSessionSecurityIT
./mvnw clean package -DgenerateReactDist -DskipTests=true
```

Expected: all tests and production WAR build pass; `web.xml` maps only
`springSecurityFilterChain` as the application security proxy; JSP output is
unchanged; JSP source uses only Spring/RSpace security tags; the WAR contains no
Shiro taglib.

### Workstream 9: Remove all Shiro code/dependencies and migrate the test harness

1. Remove Shiro realms, tokens, cache adapters, filter classes, thread executor,
   lazy advisor/factory classes, and Shiro-specific tests now covered by outcome
   tests. Preserve test cases; delete only framework-specific setup/assertions.
2. Replace `SecurityTestConfig`, static `SecurityUtils.setSecurityManager`,
   `ThreadContext` resets, and `RSpaceTestUtils` Shiro login/logout with a
   per-test Spring security-context factory/listener and MockMvc request
   processors. Clear the context after every test.
3. Remove `shiro-core`, `shiro-web`, `shiro-spring`, their exclusions/config,
   and every transitive Shiro dependency from the complete in-repository Maven
   reactor.
4. Add a CI grep/architecture test that fails on any `org.apache.shiro`,
   `SecurityUtils`, Shiro bean/filter/session/realm class, or Shiro Maven
   dependency, tag URI, or `<shiro:` markup. There is no allowlist.
5. Verify no Shiro permission cache, subject, session, lifecycle, or test
   security manager exists.

**Verify**:

```bash
./mvnw clean test -Dfast=true
./mvnw dependency:tree -Dincludes=org.apache.shiro,org.springframework.security
rg -n -i 'org\.apache\.shiro|SecurityUtils|<artifactId>shiro-|http://shiro\.apache\.org/tags|<shiro:' pom.xml src/main src/test
```

Expected: fast suite passes; all Spring modules resolve to 6.5.9; the dependency
tree contains no Shiro artifact; the repository search has no output.

### Workstream 10: Update documentation and run the single release gate

1. Rewrite `DevDocs/DeveloperNotes/SecurityAndPermissions.md` to document
   Spring providers/filter chains, the current-actor module, permission snapshot
   loading/cache invalidation, application-owned permission grammar, method
   authorization, impersonation, async propagation, tests, Spring Security JSP
   tags, and RSpace constraint-permission tags.
2. Update `SecureConnectionConfig.md` to point at Spring's conditional channel
   enforcement while preserving existing operator-facing SSL properties.
3. Update the Shiro early-init note in `UpgradingToSpring6.md` and
   `applicationContext-service.xml` only to the extent behavior changed. Keep
   the transaction-advice startup guard documented.
4. Document deferred work: enable CSRF with UI tokens and optionally upgrade
   hashes on successful login. No Shiro or JSP-tag migration is deferred.
5. Run DB-backed and production WAR gates. CI must exercise both supported
   MariaDB versions before merge.

**Verify**:

```bash
./mvnw -q spotless:check
./mvnw clean test -Dfast=true
./mvnw clean test -Denvironment=drop-recreate-db
./mvnw clean verify -Denvironment=drop-recreate-db
./mvnw clean package -DgenerateReactDist -DskipTests=true
```

Expected: every command exits 0; CI passes on both supported MariaDB versions;
production-context startup proves transaction and authorization advice.

## Test plan summary

Retain framework-neutral outcome tests after any temporary legacy test harness
used to capture expected results is removed:

- **Route matrix**: every class of ordered URL rule, OR roles, SSL toggle,
  redirect/status, session creation, authenticated catch-all.
- **Local login**: success/failure, alias, signup source, LDAP handoff,
  SSO-backdoor restriction, disabled/review/locked/maintenance/IP policy,
  AJAX/admin redirects, session persistence, post-login exactly once.
- **SSO backend handoff**: each existing policy input style, identity/alias
  mapping, conflicts, signup/info redirects, PI update, session attributes,
  logout URL. No AJP/IdP reconfiguration tests.
- **API/WOPI/callbacks**: token validation, disabled access, stateless external
  request, existing UI-session reuse, mismatched token/session isolation,
  context cleanup on success and exceptions.
- **Permission engine**: every constraint and aggregation source, disabled,
  wildcard, `${self}`, WRITE-implies-READ, public link/parent, ACL, refresh after
  grant/revoke.
- **Method security**: six Form operations plus their object checks; transaction
  and authorization advisors both present.
- **Impersonation**: enter, target grants, no retained admin grants, SSO-origin
  parity, original/effective audit identity, exit, logout, active-user/session
  flags.
- **Concurrency/global init**: context propagation and cleanup across pooled,
  nested, failed, and rejected tasks; scoped system identity restoration.
- **JSP migration**: Spring tags for anonymous/authenticated/principal and all
  coarse roles; RSpace tags for string/object/form/record permissions and
  impersonation rendering; WAR packaging with no Shiro taglib.
- **Test isolation**: every test begins and ends with an empty Spring context
  unless explicitly annotated; no static manager cross-context workaround.

## Done criteria

All must hold:

- [ ] Spring Security 6.5.9 is the only backend request/method/session security
      framework and all Spring Security artifacts share one managed version.
- [ ] `springSecurityFilterChain` is the first application security filter over
      `/*`; no Shiro filter makes URL/authentication decisions.
- [ ] Every old `security.xml` route has an outcome test and equivalent ordered
      Spring rule, including OR roles, API/OAuth/WOPI, SSL, and catch-all.
- [ ] Local, LDAP, SSO, API, WOPI, Slack/external OAuth, and global-init paths
      use typed Spring providers or scoped context helpers.
- [ ] AJP/proxy/IdP config, remote identity inputs/properties, React/TypeScript
      source, password hashes, URLs, rendered JSP behavior, and response
      contracts are unchanged. JSP security-tag markup is migrated.
- [ ] Application session attributes/listeners and post-login actions are
      preserved; logout invalidates/clears exactly once.
- [ ] Object permission matching and invalidation preserve every documented
      RSpace semantic; domain permission types no longer depend on Shiro.
- [ ] Six Shiro method annotations are replaced and transaction advice remains
      verified in a production-like context.
- [ ] Impersonation and executor/global-init contexts restore/clear reliably.
- [ ] The repository and built WAR contain no Shiro class, Maven artifact,
      filter, realm, subject, session, cache, annotation, import, test harness,
      tag URI, or `<shiro:` markup.
- [ ] `./mvnw -q spotless:check`, focused tests, fast tests, DB tests, full
      verify, and production WAR package all exit 0.
- [ ] No React/TypeScript files are modified; JSP changes are limited to
      security tag directives/usages and required framework-neutral TLD names.
- [ ] Documentation describes the new source of truth and deferred UI phase.
- [ ] `plans/README.md` marks this plan DONE.

## STOP conditions

Stop and report instead of improvising if any occurs:

- The live route matrix, authentication mechanisms, or pinned dependency
  versions differ materially from this plan.
- The core-model prerequisite move is incomplete, its sources are still
  resolved only from an external artifact, or it is not part of the same
  rspace-web reactor/workspace.
- Removing Shiro types from core-model changes persisted permission strings,
  Hibernate mappings, equality/hash behavior, or matcher outcomes.
- Spring Security/RSpace JSP tags cannot reproduce the current rendered
  visibility or principal text without changing unrelated UI behavior.
- Existing AJP/SSO/header/property configuration must change to make Spring
  authentication work. Phase one must consume the existing request contract.
- Existing requests require CSRF tokens after cutover. Restore explicit
  phase-one compatibility and defer CSRF; do not expand this plan into React or
  form-submission changes.
- Spring's session fixation behavior changes an externally relied-upon session
  contract and the characterization test cannot be updated without UI/client
  changes. Escalate the security/compatibility decision.
- Spring status/redirect defaults change existing API or UI contracts and no
  explicit compatibility handler can preserve them locally.
- A provider would need to trust a generic header, dummy password, or
  already-authenticated token without an adjacent upstream validator.
- Transaction advice or authorization advice is absent in the production-like
  context, even if unit tests pass.
- A focused verification fails twice after a reasonable local correction, or a
  step requires an out-of-scope file/configuration change.

## Maintenance and follow-up

- A later UI-capable phase may enable CSRF with tokens in forms/AJAX requests.
  It does not need to migrate tags or remove Shiro; this big-bang plan already
  completes both.
- Consider opportunistic password rehash to a modern adaptive format only after
  login parity is stable; use a delegating encoder so old accounts continue to
  authenticate during rollout.
- Review permission cache invalidation whenever role/group mutation paths are
  added. A session authority snapshot must never become the only copy of
  mutable object permissions.
- Review every new async executor for context propagation and cleanup.
- Never add another authentication path by accepting the SSO placeholder
  password. It is not a credential.
- Separately rotate and externalize the committed monitoring credential and
  constrain broad SSO debug logging so request headers/environment values are
  not emitted. These are real findings but not prerequisites for this scoped
  framework migration.
