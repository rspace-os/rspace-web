# Plan 014: Add a resource-scoped effective-access lookup widget

> **Executor instructions**: Read this plan fully before changing code. Follow
> the steps in order, run every verification command, and confirm the expected
> result before continuing. If a STOP condition occurs, stop and report it. Do
> not improvise a broader identity-search or reporting feature. When done,
> update this plan's row in `plans/README.md` unless a reviewer says they
> maintain the index.
>
> **Drift check, run first**:
>
> ```bash
> git diff --stat 5f230b8da..HEAD -- \
>   plans/012-booking-permission-ui-prototypes.md \
>   plans/013-resource-role-permissions.md \
>   src/main/java/com/researchspace/api/v2 \
>   src/main/java/com/researchspace/service/resourceaccess \
>   src/main/java/com/researchspace/model/resourceaccess \
>   src/test/java/com/researchspace/api/v2 \
>   src/test/java/com/researchspace/service/resourceaccess \
>   src/main/webapp/ui/src/modules/common/resource-access \
>   src/main/webapp/ui/src/modules/booking/pages/bookable-items
> ```
>
> A large diff is expected because Plan 013 is a prerequisite and none of its
> production resource-access modules existed at the planning commit. Confirm
> the prerequisite contract in Step 1. Stop if the completed code no longer
> has a generic resolver, a resource-scoped access controller, a bounded
> grantee directory, or a generic production editor.

## Status

- **Priority**: P1
- **Effort**: M, estimated at 4 to 6 ideal engineering days
- **Risk**: MED
- **Depends on**: Plans 012 and 013 must both be DONE
- **Category**: direction
- **Planned at**: commit `5f230b8da`, 2026-08-30
- **Status**: TODO

## Why this matters

The access editor explains effective access for principals that already have a
visible assignment row. It cannot answer the adjacent support question: "What
access does this active user have to this resource?" A user may have no direct
row and still inherit access through one or more assigned groups, the All users
audience, or an implicit scheme role.

The browser cannot derive that answer safely. Doing so would require group
membership data that the access document deliberately does not expose. This
plan adds one caller-authorized server lookup and a small read-only widget in
the selected resource-access editor. It reuses Plan 013's resolver and grantee
directory, adds no persistence, and never turns the lookup into a mutation or a
cross-resource report.

## Fixed product contract

Implement exactly this scope:

- The lookup concerns one registered protected resource at a time.
- Only a caller who may view that resource's access document may use it.
- Search candidates are active users returned by the existing access-grantee
  directory under the represented subject's scope. Groups and audiences are
  not lookup subjects.
- Selecting a user returns their current saved effective role, or no access,
  plus every applicable direct, active-group, All users, and implicit source.
- The result is read-only. Assignment changes remain in the editor's normal
  draft controls.
- The lookup never enumerates group members, never searches all resources, and
  never accepts a caller-supplied group or audience as the subject.
- The response contains safe identity fields, `effectiveRole`, and
  `roleSources`. It does not return the selected user's capabilities, email
  address, group membership list, or unrelated profile fields.
- The lookup reflects persisted server state. When the editor has an unsaved
  draft, label the result "Current saved access" so it cannot be mistaken for a
  preview of the draft.
- Authorization uses `ApiV2Caller.subject()`. Under run-as,
  `ApiV2Caller.actor()` does not widen resource visibility or user-directory
  scope.
- An unreadable resource, a caller without the scheme's view-access
  capability, an inactive user, and a user outside the caller's directory
  scope all return the same concealed 404 used by Plan 013.
- A valid visible user with no applicable source returns 200 with
  `effectiveRole: null` and an empty `roleSources` array.
- Responses are private and `no-store`. No audit event is emitted for lookup.

## API contract

Add this route only for resources registered for generic resource access:

```text
GET /api/v2/{resource}/{id}/access/effective?userId=<positive long>
```

The conceptual 200 response is:

```json
{
  "user": {
    "kind": "USER",
    "id": 12,
    "key": "user:12",
    "name": "Ada Lovelace",
    "detail": "ada",
    "available": true
  },
  "effectiveRole": "MANAGER",
  "roleSources": [
    { "kind": "DIRECT", "role": "VIEWER" },
    {
      "kind": "GROUP",
      "role": "MANAGER",
      "grantee": {
        "kind": "GROUP",
        "id": 41,
        "key": "group:41",
        "name": "Imaging Lab",
        "available": true
      }
    }
  ]
}
```

Use the same grantee and role-source value types as the access document. Do not
create a second wire representation. The resource id parser, access
registration, role scheme, concealment rules, and represented-subject handling
must also be the same ones used by GET `/api/v2/{resource}/{id}/access`.

Status behavior:

| Condition | Status |
|---|---:|
| Valid visible user, with or without effective access | 200 |
| Missing, zero, negative, or malformed `userId` | 400 |
| Anonymous request | 401 |
| Unknown/unregistered resource or resource id | 404 |
| Resource unreadable or caller lacks view-access capability | 404 |
| User missing, inactive, unavailable, or outside directory scope | 404 |

Do not use 403 for any resource/user concealment case. Do not add a bulk
variant.

## Current state and prerequisite contract

At commit `5f230b8da`, the production resource-access backend and frontend
directories do not exist. Plan 013 owns their creation. This plan starts only
after Plan 013 is DONE and must reuse, rather than copy, these completed
contracts:

- `ResourceAccessManager.resolve(ResourceAccess, User)` resolves one user and
  returns the highest effective role plus every applicable source.
- `ResourceAccessManager.resolveAll(...)` performs batched page decoration.
  The new explicit lookup uses the single-user resolver and must not be added
  to list rendering.
- The generic access controller resolves protected resources through an
  `ApiV2ResourceCatalog` registration, applies view-access authorization, and
  conceals denial as 404.
- The access-grantee directory bounds queries and limits ordinary callers to
  active co-members while sysadmins may search all active users. The lookup
  must call the same manager/policy to authorize its selected user id. A user
  id supplied directly in the URL must not bypass directory scope.
- `src/main/webapp/ui/src/modules/common/resource-access/` contains Valibot
  schemas, transport, React Query hooks, the generic `ResourceAccessEditor`,
  draft helpers, and tests with both Booking and a fake role scheme.
- Booking's selected editor is rendered in the bookable-item Access tab and
  browser-tested in
  `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx`.

The Plan 012 prototype already establishes the presentation language. Its
source disclosure is equivalent to:

```tsx
<summary>
  Effective {row.effectiveLabel} · {row.sources.length} sources
</summary>
<ul>
  {row.sources.map((source) => (
    <li>{source.label}: {roleOf(ctx.scheme, source.role).label}</li>
  ))}
</ul>
```

Reuse the selected production editor's source-list component instead of
copying this prototype JSX. The product vocabulary is **role scheme**, **role
assignment**, **grantee**, **capability**, and **effective role**. Use
"effective access" for the complete lookup result and "role source" for each
reason. Do not use "sharee", "calculated permission", or "inherited user".

## Repository conventions

- Backend dependencies flow Controller -> Manager -> DAO. Controllers never call
  DAOs. A transactional Spring service ends in `Manager` or declares an
  explicit transaction boundary.
- Reuse `ApiV2Caller.subject()` for authorization and `actor()` only for audit.
  This route emits no audit.
- Externalize backend error text into the appropriate bundle. Reuse Plan 013's
  concealed not-found problem instead of adding a lookup-specific detail.
- Frontend server state belongs in React Query. Do not add a Zustand store or
  mirror the lookup into editor draft state.
- Parse every lookup response with Valibot using the access document's shared
  grantee and source schemas.
- Use semantic frontend translation keys. Author temporary English with
  `defaultValue`, extract once with `--sync-primary`, then remove
  `defaultValue`.
- Browser tests use Vitest Browser Mode, MSW with strict unhandled-request
  errors, semantic locators, and the existing bookable-item page object.
- Run frontend commands from the repository root. Do not add a standalone `--`
  after a pnpm script name.
- Never run Maven `install`, deploy goals, or `install:install-file`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Backend unit tests | `mvn test -Dtest=ResourceAccessManagerTest -Dfast=true` | selected tests pass |
| Backend controller/OpenAPI tests | `mvn test -Dtest=ResourceEffectiveAccessControllerMVCIT,ApiV2OpenApiGeneratorTest` | selected tests pass |
| Generic frontend tests | `pnpm test src/modules/common/resource-access` | selected tests pass |
| Type check | `pnpm tsc` | exit 0, no errors |
| Lint | `pnpm lint` | exit 0, no fixes applied |
| i18n extraction | `pnpm run i18n:extract --sync-primary` | only intended primary catalogs change |
| i18n types | `pnpm run i18n:types` | exit 0 |
| i18n lint/check | `pnpm run i18n:lint && pnpm run i18n:check` | both exit 0 |
| Chromium browser test | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | selected spec passes |
| Cross-browser test | `pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | Chromium, Firefox, and WebKit pass |
| Patch check | `git diff --check` | no output |

## Suggested executor toolkit

- Use `react-testing-library` for the generic component tests.
- Use `rspace-browser-tests` for the `*.spec.tsx` browser coverage.
- No route file is in scope, so TanStack Router guidance is not required. If a
  route change appears necessary, stop rather than editing it.

## Scope

### In scope

The exact Plan 013 class names may differ, but changes must stay within these
paths and responsibilities:

- `src/main/java/com/researchspace/service/resourceaccess/`
  - extend the existing access manager with one resource-scoped effective-user
    lookup;
  - reuse existing grantee/source records and resolver;
  - add a response/value record only if no existing document type fits.
- `src/main/java/com/researchspace/api/v2/controller/`
  - extend Plan 013's generic resource-access controller with the GET route;
  - do not create a Booking-only controller.
- `src/main/java/com/researchspace/api/v2/openapi/`
  - document the route only for registered access resources.
- `src/main/resources/bundles/`
  - only if a new user-facing validation message is unavoidable.
- `src/test/java/com/researchspace/service/resourceaccess/`
  - extend resolver/manager tests.
- `src/test/java/com/researchspace/api/v2/`
  - create `ResourceEffectiveAccessControllerMVCIT.java` and extend OpenAPI
    coverage.
- `src/main/webapp/ui/src/modules/common/resource-access/`
  - create `EffectiveAccessLookup.tsx`;
  - extend shared schemas, transport, query keys/hooks, source rendering, and
    nearby tests without adding another editor state model.
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/`
  - extend existing MSW fixtures/page objects and
    `BookableItemPage.spec.tsx` for the Booking journey.
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/`
  - add the common lookup strings generated by the documented extraction flow.
- `DevDocs/DeveloperNotes/`
  - update the REST API v2/resource-access note created by Plan 013 with the new
    route and concealment behavior.
- `plans/README.md`
  - status update when complete.

### Out of scope

- Any Liquibase, entity, table, index, Envers, migration, or audit-delta change.
- Changes to role precedence, capability matrices, assignment replacement,
  leave semantics, ETag handling, Owner invariants, or unavailable-principal
  persistence.
- Cross-resource lookup, a permissions report, export, pagination, saved
  searches, recent searches, or bulk user input.
- Group or audience lookup subjects and any endpoint that enumerates group
  members.
- Email addresses, community membership, Inventory permissions, workspace
  permissions, profile details, or unrestricted user search.
- A second resource-specific effective-access endpoint or Booking-specific
  resolver branch.
- Mutation controls inside the lookup result. Use the existing assignment
  editor for add, change, remove, and leave.
- A route or standalone page for the widget.
- Changes to Plan 012's prototype files or restoration of prototype code into
  production.

## Git workflow

- Branch: `feat/effective-access-lookup`
- Keep backend contract/service, frontend widget, and browser/i18n work in
  separate logical commits when practical. Match the repository's imperative
  commit style, for example `Add effective access lookup`.
- Do not push or open a PR unless the operator instructs it.

## Steps

### Step 1: Confirm prerequisites and freeze the executable contract

Confirm `plans/012-booking-permission-ui-prototypes.md` and
`plans/013-resource-role-permissions.md` both say DONE and contain no pending
verdict or STOP condition. Locate the completed equivalents of:

- `ResourceAccessManager.resolve` and its returned effective role/sources;
- the generic resource-access GET/PUT/DELETE-me controller;
- the access-grantee directory manager and controller;
- the resource access OpenAPI registration;
- `ResourceAccessEditor`, shared source renderer, schemas, transport, and
  queries;
- the selected Booking editor browser fixture.

Record the live symbol-to-path mapping in a temporary note under `.claude/`.
Do not create compatibility wrappers merely to match names in this plan.

Write failing contract tests before production code:

- a manager test for direct Viewer plus group Manager resolving to Manager with
  both sources;
- a controller test for 200 with sources and 200 with no access;
- a controller concealment test for a user outside the caller's directory
  scope;
- an OpenAPI assertion for the registered route and its absence on a resource
  without access registration;
- a generic component test that selects a user and expects effective role plus
  sources.

**Verify**:

```bash
mvn test -Dtest=ResourceAccessManagerTest -Dfast=true
mvn test -Dtest=ResourceEffectiveAccessControllerMVCIT,ApiV2OpenApiGeneratorTest
pnpm test src/modules/common/resource-access
```

Expected at the initial red gate: new tests compile and fail only because the
lookup manager method, route, and component do not exist. Existing Plan 013
tests remain green.

### Step 2: Add a safe single-user lookup to the generic manager

Extend the completed generic manager rather than introducing a controller-only
service. The public method should conceptually accept:

```java
EffectiveAccessLookupDocument lookupEffectiveAccess(
    ResourceAccessRegistration<?, ?> registration,
    String resourceId,
    long targetUserId,
    User subject);
```

Adapt the id type to the existing registration parser rather than hard-casting
the protected resource id. The implementation must:

1. resolve the protected resource through the registration's caller-constrained
   read path;
2. require the scheme capability used by GET access;
3. resolve the target user through the same availability and directory-scope
   policy as access-grantee search;
4. call the existing single-user resolver with the target user;
5. map the result to shared grantee/source document types;
6. return a nullable effective role and all applicable sources;
7. perform no mutation, flush, audit publication, or assignment-list expansion.

The target user's system role determines their implicit scheme source. The
request caller's actor never does. Keep the query bounded to the selected user
and the selected resource. Do not turn this into `resolveAll` over search
results.

Add manager tests for direct, several active groups, All users, implicit role,
no source, unavailable source ignored, inactive target concealed, directory
scope, and run-as actor/subject separation. Assert no audit publisher call.

**Verify**:

```bash
mvn test -Dtest=ResourceAccessManagerTest -Dfast=true
```

Expected: all selected manager tests pass, including the fake second scheme;
no test relies on Booking role-name branches in the generic package.

### Step 3: Expose and document the resource-scoped GET route

Add `GET /{resource}/{id}/access/effective` to Plan 013's generic access
controller. Validate `userId` as a positive long at the HTTP boundary. Use the
registered resource id parser and the manager method from Step 2.

Return the same concealed not-found problem as the existing access GET for
resource denial and for a target user that the caller may not discover. Mark
the response private and `no-store`. Do not add ETag because the result may
change when group membership changes without changing the access aggregate
version.

Extend OpenAPI generation so only a registered access resource gets this path.
Document `userId`, 200, 400, 401, and concealed 404. Reuse the grantee and
role-source schemas from the access document.

Controller tests must cover:

- Owner and Manager success;
- lower role and unreadable resource concealment;
- unknown registration/id;
- malformed, missing, zero, and negative user id;
- inactive and out-of-directory users;
- direct, group, audience, implicit, and no-access bodies;
- run-as scope based on represented subject;
- private/no-store headers;
- no audit side effect.

**Verify**:

```bash
mvn test -Dtest=ResourceEffectiveAccessControllerMVCIT,ApiV2OpenApiGeneratorTest
```

Expected: all selected tests pass. A resource without access registration has
no generated effective-access path.

### Step 4: Add the typed frontend query without coupling it to draft state

Extend the generic resource-access Valibot schemas with an effective-access
lookup document built from the existing grantee, role key, and role-source
schemas. Add a transport function and React Query hook with a key containing:

```text
resource type + resource id + selected user id
```

The hook must remain disabled until a user is selected. Forward cancellation
through the transport's existing request signal. Parse every 200 response. Let
the shared REST problem handling render failures.

Invalidate the selected lookup after a successful assignment replacement or
self-removal because a direct source may have changed. Do not store the result
in the editor reducer, local storage, or Zustand. Do not predict membership
changes client-side.

Use the existing access-grantee query for candidate search and present only its
active user results in this widget. Client filtering is acceptable because the
server still authorizes `userId` through the same directory policy. Do not add
an unrestricted users endpoint.

**Verify**:

```bash
pnpm test src/modules/common/resource-access
pnpm tsc
```

Expected: query/schema tests pass and TypeScript exits 0. Tests prove that the
query is disabled without a selection, keys do not collide across resources or
users, response parsing rejects malformed sources, and successful access
mutations invalidate the lookup.

### Step 5: Build the read-only widget inside the selected editor

Create `EffectiveAccessLookup.tsx` under the generic resource-access module.
Place it in the selected Plan 012 editor layout without rearranging the chosen
assignment workflow. Use the production combobox/search component already used
for grantee selection.

Required states:

- no selection: short explanation and user search;
- searching: accessible busy state without replacing the entire editor;
- selected/loading: identify the selected user and announce loading;
- access: translated effective role label plus the shared source list;
- no access: explicit "No effective access" and no sources;
- failed: existing retry/error presentation;
- dirty editor draft: label the result "Current saved access".

The role labels and descriptions come from the resource adapter. The component
must work with Booking and the fake Contributor/Reader scheme without checking
role strings. Reuse the editor's grantee identity and source components. Do not
add edit buttons to the result.

Keyboard and accessibility requirements:

- the search has a persistent accessible name;
- results identify user name, username/detail, and availability without color;
- selecting a result moves focus predictably to the result heading or leaves
  it on the combobox according to the existing combobox convention;
- status changes use the editor's polite announcement region;
- clearing selection returns to the empty state;
- Escape retains the inline editor's draft without navigating away or applying it;
- the widget causes no horizontal page scrolling at 320 CSS pixels.

Add user-centric component tests for every state, keyboard selection, clearing,
retry, dirty-draft wording, Booking labels, fake-scheme labels, and proof that
lookup never changes the assignment draft.

**Verify**:

```bash
pnpm test src/modules/common/resource-access
pnpm tsc
pnpm lint
```

Expected: all selected tests and static checks pass. Searching the generic
production module for Booking leakage returns no matches except test fixtures:

```bash
rg -n 'BOOKER|VIEWER|Booking|booking-configurations' \
  src/main/webapp/ui/src/modules/common/resource-access \
  --glob '!**/*.test.*' --glob '!**/__tests__/**'
```

Expected: no output.

### Step 6: Extract translations and add the Booking browser journey

Wrap all new production copy in semantic common/resource-access translation
keys. Use literal `defaultValue` only during authoring. Run primary extraction,
review the diff, remove every new production `defaultValue`, and regenerate
types. Never run `--sync-all`.

Extend the existing bookable-item browser spec and MSW handlers. Add one Owner
journey that:

1. opens the Access tab;
2. searches for a user with direct Viewer plus Imaging Lab Manager;
3. selects the user by keyboard;
4. sees effective Manager and both sources;
5. selects a visible user with no applicable source and sees No effective
   access;
6. stages an assignment change and confirms the lookup says Current saved
   access without changing the draft;
7. saves, waits for lookup invalidation/refetch, and sees the new persisted
   answer;
8. repeats the core lookup at 320 CSS pixels without horizontal page scroll.

Add a Manager case if the existing fixture does not already exercise the same
view-access capability. Confirm Booker and Viewer cannot reach the access
editor or lookup. MSW must reject unexpected lookup requests.

**Verify**:

```bash
pnpm run i18n:extract --sync-primary
pnpm run i18n:types
pnpm run i18n:lint
pnpm run i18n:check
pnpm tsc
VITEST_BROWSERS=chromium pnpm test-browser \
  src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
pnpm test-browser \
  src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
```

Expected: catalogs contain only the intended new keys, static checks pass, and
the final browser command passes in Chromium, Firefox, and WebKit.

### Step 7: Document the contract and run final verification

Update the resource-access REST v2 developer note created by Plan 013. Document
the route, represented-subject authorization, directory-scope recheck, nullable
effective role, source semantics, no-store response, and concealed 404. State
plainly that this is an explicit single-user query and must never be called once
per table row.

Run the focused suites together, followed by patch and scope checks:

```bash
mvn test -Dtest=ResourceAccessManagerTest -Dfast=true
mvn test -Dtest=ResourceEffectiveAccessControllerMVCIT,ApiV2OpenApiGeneratorTest
pnpm test src/modules/common/resource-access
pnpm tsc
pnpm lint
pnpm run i18n:check
pnpm run i18n:lint
git diff --check
git status --short
```

Expected: every command exits 0, `git diff --check` prints nothing, and status
shows only files permitted by this plan plus the plan-index status update.

## Test plan

### Generic backend

- Resolution: direct, several groups, direct lower than inherited, All users,
  implicit scheme role, unavailable source ignored, and no source.
- Authorization: Owner/Manager view-access capability, lower-role denial,
  unreadable resource, unregistered resource, run-as subject versus actor.
- Directory privacy: active visible user succeeds; inactive, removed, unknown,
  and out-of-scope users all use concealed 404.
- Contract: positive `userId`, every invalid id form, shared grantee/source
  schema, nullable role, private/no-store, no ETag, and no audit.
- OpenAPI: path and schemas appear only on registered access resources.

### Generic frontend

- Valibot parses valid role/no-role documents and rejects malformed identities
  or sources.
- Query keys include resource and user, stay disabled without selection, honor
  cancellation, and invalidate after access mutation.
- Widget states cover empty, search, loading, access, no access, error/retry,
  clear, and dirty-draft wording.
- Booking and fake schemes render through the same component without role-name
  branches.
- Keyboard, accessible names, announcements, focus, and 320 px behavior pass.
- No lookup interaction mutates or replaces the editor draft.

### Booking browser journey

- Owner and Manager can use lookup.
- Booker and Viewer cannot reach it.
- A direct Viewer plus group Manager displays effective Manager and both
  sources.
- A visible user with no sources displays No effective access.
- Dirty draft is labelled saved state; successful Save invalidates/refetches.
- Strict MSW handlers fail unexpected endpoint shapes or duplicate requests.

## Done criteria

- [ ] Plans 012 and 013 are DONE and their verification remains green.
- [ ] One generic registered route implements the documented single-user
  lookup; no Booking-only endpoint exists.
- [ ] Candidate user authorization reuses the access-grantee directory policy
  and cannot be bypassed by supplying an id directly.
- [ ] The server returns every applicable source and nullable effective role
  without group-member enumeration, capabilities, PII, mutation, audit, or
  ETag.
- [ ] Resource and candidate denial use the same concealed 404 contract.
- [ ] The generic widget uses React Query and shared schemas/components, stays
  separate from draft state, and renders the fake scheme.
- [ ] Dirty-draft copy identifies the response as current saved access.
- [ ] Owner/Manager, lower-role denial, inherited source, no-access, run-as,
  keyboard, error, and 320 px cases have automated coverage.
- [ ] Focused Maven, frontend unit, TypeScript, lint, i18n, Chromium, and
  cross-browser commands pass.
- [ ] `git diff --check` prints nothing and only in-scope files changed.
- [ ] REST v2/resource-access developer notes describe the route and warn
  against per-row use.
- [ ] `plans/README.md` marks Plan 014 DONE.

## STOP conditions

Stop and report. Do not improvise if:

- Plan 012 lacks a complete human-reviewed verdict or Plan 013 is not DONE.
- The completed Plan 013 code cannot resolve one arbitrary active user through
  the generic resolver without adding resource-specific branches.
- The grantee directory has no reusable manager-level policy for authorizing a
  user id selected outside the search response. Add that policy to Plan 013 or
  revise this plan before implementation; do not trust the client.
- The selected Plan 012 editor has no credible place for the widget at 320 px
  without changing its chosen information architecture. Return to a short
  design review rather than forcing it in.
- Correct implementation requires a schema migration, group-member endpoint,
  unrestricted user search, per-row lookup, or changes to role/capability
  semantics.
- Product scope expands to group subjects, cross-resource answers, export,
  bulk lookup, or draft-result prediction. Those need a separate plan and cost
  review.
- Showing role sources would reveal a group name that the existing access
  document is required to conceal from the caller.
- Any verification command fails twice after one reasonable correction.
- An implementation step requires editing a route file or another out-of-scope
  area.

## Maintenance notes

- Effective access can change when group membership or All users applicability
  changes without an access-aggregate version bump. Keep the response
  uncached/no-store and do not introduce ETag-based assumptions later.
- The query is allowed only after an explicit user selection. Reviewers should
  reject list rendering that calls it once per principal.
- If a later product request needs "which resources can this user access?", it
  needs a database-filtered, paginated collection query. Do not loop this
  single-resource endpoint across resources.
- If group/audience lookup subjects become meaningful in another role scheme,
  define their semantics and privacy rules in a separate ADR/plan first.
- Review the PR closely for actor/subject confusion under run-as and for direct
  user-id enumeration that bypasses the grantee directory.
