<!-- intent-skills:start -->
# TanStack Intent - before editing files, run the matching guidance command.
tanstackIntent:
  - id: "@tanstack/router-core#router-core"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core"
    for: "Framework-agnostic core concepts for TanStack Router: route trees, createRouter, createRoute, createRootRoute, createRootRouteWithContext, addChildren, Register type declaration, route matching, route sorting, file naming conventions. Entry point for all router skills."
  - id: "@tanstack/router-core#router-core/auth-and-guards"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/auth-and-guards"
    for: "Route protection with beforeLoad, redirect()/throw redirect(), isRedirect helper, authenticated layout routes (_authenticated), non-redirect auth (inline login), RBAC with roles and permissions, auth provider integration (Auth0, Clerk, Supabase), router context for auth state."
  - id: "@tanstack/router-core#router-core/code-splitting"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/code-splitting"
    for: "Automatic code splitting (autoCodeSplitting), .lazy.tsx convention, createLazyFileRoute, createLazyRoute, lazyRouteComponent, getRouteApi for typed hooks in split files, codeSplitGroupings per-route override, splitBehavior programmatic config, critical vs non-critical properties."
  - id: "@tanstack/router-core#router-core/data-loading"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/data-loading"
    for: "Route loader option, loaderDeps for cache keys, staleTime/gcTime/ defaultPreloadStaleTime SWR caching, pendingComponent/pendingMs/ pendingMinMs, errorComponent/onError/onCatch, beforeLoad, router context and createRootRouteWithContext DI pattern, router.invalidate, Await component, deferred data loading with unawaited promises."
  - id: "@tanstack/router-core#router-core/navigation"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/navigation"
    for: "Link component, useNavigate, Navigate component, router.navigate, ToOptions/NavigateOptions/LinkOptions, from/to relative navigation, activeOptions/activeProps, preloading (intent/viewport/render), preloadDelay, navigation blocking (useBlocker, Block), createLink, linkOptions helper, scroll restoration, MatchRoute."
  - id: "@tanstack/router-core#router-core/not-found-and-errors"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/not-found-and-errors"
    for: "notFound() function, notFoundComponent, defaultNotFoundComponent, notFoundMode (fuzzy/root), errorComponent, CatchBoundary, CatchNotFound, isNotFound, NotFoundRoute (deprecated), route masking (mask option, createRouteMask, unmaskOnReload)."
  - id: "@tanstack/router-core#router-core/path-params"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/path-params"
    for: "Dynamic path segments ($paramName), splat routes ($ / _splat), optional params ({-$paramName}), prefix/suffix patterns ({$param}.ext), useParams, params.parse/stringify, pathParamsAllowedCharacters, i18n locale patterns."
  - id: "@tanstack/router-core#router-core/search-params"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/search-params"
    for: "validateSearch, search param validation with Zod/Valibot/ArkType adapters, fallback(), search middlewares (retainSearchParams, stripSearchParams), custom serialization (parseSearch, stringifySearch), search param inheritance, loaderDeps for cache keys, reading and writing search params."
  - id: "@tanstack/router-core#router-core/ssr"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/ssr"
    for: "Non-streaming and streaming SSR, RouterClient/RouterServer, renderRouterToString/renderRouterToStream, createRequestHandler, defaultRenderHandler/defaultStreamHandler, HeadContent/Scripts components, head route option (meta/links/styles/scripts), ScriptOnce, automatic loader dehydration/hydration, memory history on server, data serialization, document head management."
  - id: "@tanstack/router-core#router-core/type-safety"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/type-safety"
    for: "Full type inference philosophy (never cast, never annotate inferred values), Register module declaration, from narrowing on hooks and Link, strict:false for shared components, getRouteApi for code-split typed access, addChildren with object syntax for TS perf, LinkProps and ValidateLinkOptions type utilities, as const satisfies pattern."
<!-- intent-skills:end -->

# Agent instructions

These instructions apply to the whole repository. A nested `AGENTS.md` takes
precedence for files below it.

## Working rules

- Keep changes narrowly scoped. Preserve unrelated and user-authored work.
- Match the conventions of the code you touch and check for nested
  `AGENTS.md` files first.
- Use `rg` and `rg --files` for searches.
- Treat vendored code, minified files, build output, and dependency directories
  as read-only unless the task explicitly targets them. Do not add `target/`,
  `dist/`, or `node_modules/` to diffs.
- Put plans and scratch notes only in `.claude/`, which is gitignored.
- Do not add dependencies unless they are necessary.
- Update nearby developer documentation when behavior or workflows change.

### Maven safety

Never run a Maven phase or goal that writes artifacts to the local repository.
In particular, do not run `mvn install`, `./mvnw install`,
`install:install-file`, or any deploy goal in this or sibling projects. Local
artifacts can silently shadow the JitPack dependency pinned in `pom.xml`.

Use `compile`, `package`, `test`, or `verify`. To test unpublished changes in a
sibling project, ask the user to push them, wait for the remote build, and then
bump this project's pinned commit hash.

## Project shape

RSpace is a Java 17/Spring application with a React/TypeScript frontend and
MariaDB:

- Backend: `src/main/java`, tests in `src/test/java`
- Frontend: `src/main/webapp/ui/src`
- Configuration and Liquibase: `src/main/resources`
- Developer documentation: `DevDocs/DeveloperNotes`
- Core domain models may live in the sibling `rspace-core-model` repository.

Backend dependencies flow downward:

```text
Controller -> Service (*Manager) -> DAO -> Hibernate/MariaDB
```

Controllers validate input and call services, never DAOs. Services used as
transactional Spring beans should end in `Manager`, which puts them under the
AOP pointcuts in `applicationContext-service.xml`. A non-`Manager` service may
instead declare its transaction boundary explicitly with `@Transactional`;
`TransactionAdviceStartupCheck` verifies at startup that annotation-driven
transaction advice was applied. DAOs assume an active transaction. Do not
introduce imports from a lower layer to a higher one.

Frontend code uses React functional components, TypeScript, MUI, Axios, and
pnpm. Prefer React Query for new server state; MobX remains in legacy areas.
Sanitize user-generated HTML with DOMPurify.

## Build and test

Run frontend commands from the repository root. The root scripts change into
`src/main/webapp/ui`, where Vite owns the module aliases.

### pnpm arguments: never add a separator

pnpm passes arguments after a script name directly to that script. Do **not**
insert a standalone `--` after `pnpm test`, `pnpm run test`, or any other pnpm
script. Unlike npm, pnpm forwards a literal `--` into the command instead of
stripping it, so Vitest treats it as the end of its options and silently
discards everything after it, including a file filter.

```bash
# Correct: one unit-test file
pnpm test src/components/MyComponent/__tests__/MyComponent.test.tsx

# Correct: one browser-test file
pnpm test-browser src/components/MyComponent.spec.tsx
```

This rule also applies to options: pass them immediately after the script name,
for example `pnpm test --verbose <filter>`.

### Frontend commands

```bash
pnpm install --frozen-lockfile
pnpm test                         # Vitest/jsdom unit tests
pnpm test-browser                 # Browser Mode in Chromium, Firefox, WebKit
VITEST_BROWSERS=chromium pnpm test-browser <file>
pnpm tsc
pnpm lint                         # read-only Biome check
pnpm lint:fix
pnpm serve
```

For frontend changes, run `pnpm tsc` at minimum. Also run focused tests for
behavioral changes and `pnpm lint` for linted or formatted code. Do not replace
a focused test with the full suite unless broad verification is warranted.

Frontend test conventions:

- Import `render` and `within` directly from `@testing-library/react`. Import
  focused helpers only when needed: `findTableCell` / `getIndexOfTableCell`
  from `@/__tests__/tableQueries`, and `expectAccessible` from
  `@/__tests__/accessibility`.
- `vitest-fetch-mock` is enabled globally in test setup (`src/__tests__/setup.ts`)
  but is deprecated for new components. Use MSW (`msw/node`'s `setupServer`,
  scoped per test file) instead; see `nextMaintenance.test.ts` for the pattern.
- Use `toBeAccessible` for accessibility assertions and `silenceConsole()` for
  expected console errors.
- Prefer semantic jest-dom assertions such as `toBeInTheDocument`,
  `toHaveAttribute`, and `toBeDisabled`.
- With `vi.mocked(obj.method)`, first assign the method to a local variable to
  avoid the unbound-method lint rule.
- MUI `Select` inside `FormField` needs an explicit accessible name. Pass an
  `aria-label` via `SelectDisplayProps` and query it by role and name.
- Use the `rspace-browser-tests` skill for `*.spec.tsx` Browser Mode work.

### Backend commands

```bash
mvn test -Dtest=MyClassName -Dfast=true        # pure unit test
mvn clean test -Dfast=true                     # all pure unit tests
mvn test -Dtest=MyClassName                    # Spring transactional test
mvn clean test                                 # unit + Spring tests; needs DB
mvn clean verify -Denvironment=drop-recreate-db # includes *IT; resets DB
```

Use JUnit 5 for new tests unless the existing class uses JUnit 4. Do not mix
assertion styles in one class.

- DAO test: extend `SpringTransactionalTest`.
- Service behavior requiring commits: extend `RealTransactionSpringTestBase`
  and name it `*IT.java`.
- Controller test: extend `MVCTestBase` and name it `*IT.java`.

Build a WAR with the frontend using:

```bash
mvn clean package -DgenerateReactDist -DskipTests=true
```

## Implementation constraints

### User-facing backend text

Externalize all backend text that can reach users into the appropriate bundle
under `src/main/resources/bundles/` (or `ApplicationResources.properties` for
cross-cutting text), and resolve it through the injected message source. When
touching a block that contains hard-coded user-facing text, externalize it as
part of the change. Logs and internal-only developer messages are exempt.

### Frontend i18n

Frontend English catalogs live in
`src/main/webapp/ui/src/modules/common/i18n/locales/en-US/`; finished code
uses semantic keys, not literal English strings (see
`DevDocs/DeveloperNotes/FrontendI18nKeys.md` for naming and namespace rules).
To author English inline while developing, pass a literal `defaultValue` to
`t()`, then run `pnpm run i18n:extract --sync-primary` from the repo root,
review the catalog diff, remove `defaultValue`, and run `pnpm run i18n:types`,
`pnpm run i18n:lint`, and `pnpm run tsc`. Never use `--sync-all`; it also
clears matching secondary-locale values. Wrap raw JSX text in `t()` first —
`i18n:extract` does not see it. Use ICU syntax in `defaultValue` for
interpolation and plurals.

### Zustand stores (new `modules/` surface)

- One small store per domain in `src/modules/common/stores/`, created by an
  exported `create<Name>Store()` factory (`createStore` from `zustand/vanilla`)
  wrapped in `devtools` with a stable name, and delivered through React context
  (`<NameStoreProvider>` plus a `use<Name>Store(selector)` hook that throws
  outside the provider). No module-level global stores.
- Subscribe with single-value selectors; use `useShallow` when a component
  needs several fields. Never subscribe to the whole store.
- Server state belongs in React Query, not in a store. Do not mirror query
  results into zustand; derive router state from router hooks, not a store.
- Persist user preferences inside the store's actions (or `persist`
  middleware), not in ad-hoc per-hook localStorage code.

### Database changes

Use Liquibase changesets in `src/main/resources/sqlUpdates/`; never edit the
baseline for an existing schema change. Follow
`DatabaseChangeGuidelines.md`. New schema changes normally use `context="run"`;
reserve `dev-test` and `cloud` for data specific to those environments. Use
soft deletion where the domain supports it.

### Java

- Follow Google Java Style; Spotless is configured in Maven.
- Use try-with-resources for streams and files.
- Log caught exceptions at WARN or ERROR; do not leave empty catch blocks.
- Never log credentials or sensitive data.
- Add Javadoc to service interface methods and non-trivial entity methods.

## Running RSpace locally

Normal Jetty commands and setup are documented in
`DevDocs/DeveloperNotes/GettingStarted/GettingStarted.md`.

The per-worktree Docker stack lives at `docker/dev/rspace-dev`; its instructions
are in `docker/dev/README.md` and the `rspace-dev-stack` skill. Never start it
unless the user explicitly asks because it launches a database, JVM, and Node
server. `down` is reversible. `nuke` permanently deletes that worktree's local
data, so confirm unless the user explicitly requested destruction.

## Repository skills and references

Repo-local playbooks live in `.agents/skills/`. Read the matching `SKILL.md`
when a task fits one. Keep new skills concise and put bulky material in a
sibling `REFERENCE.md`.

Useful references:

- Setup and workflows: `DevDocs/DeveloperNotes/GettingStarted`
- Architecture: `DevDocs/DeveloperNotes/MavenModuleOverview.md`
- Transactions: `DevDocs/DeveloperNotes/Transactions.md`
- Security: `DevDocs/DeveloperNotes/SecurityAndPermissions.md`
- Logging: `DevDocs/DeveloperNotes/Logging.md`
- CI: `.github/workflows/lint-and-test.yml` and `Jenkinsfile`
