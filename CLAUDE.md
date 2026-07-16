# CLAUDE.md

Project instructions for AI coding agents (Claude Code, OpenAI Codex, Gemini CLI, Cursor, etc.).

## Scope

These instructions apply to the entire repository unless a deeper `AGENTS.md` overrides them.

## Repository Shape & Working Rules

- This repository is primarily a Java application with a TypeScript/React frontend under `src/main/webapp/ui`.
- Treat `src/main/webapp/scripts/bower_components` and other vendored third-party assets as read-only unless the task explicitly requires patching vendored code.
- Build outputs and dependency directories must stay out of diffs. Do not commit generated files from `target`, `dist`, or `node_modules`.
- Keep changes narrowly scoped to the user request. Do not refactor unrelated areas.
- Match existing code style and conventions in the touched area.
- Check for nested `AGENTS.md` files before editing files in subdirectories.
- When searching the repo, prefer `rg` and `rg --files`.
- Do not edit minified files unless the user explicitly asks for it.
- Plan documents (design notes, multi-step implementation plans, scratch analyses) must be written to the `.claude/` folder, which is gitignored. Never place plan files at the repository root or anywhere else inside `src/`. The `.claude/` folder is the only sanctioned location for ephemeral working notes that should not be committed.
- **Never run the Maven `install` phase or install dependencies into the local Maven repository.** Do not run `mvn install`, `./mvnw install`, `mvn install:install-file`, `mvn deploy:deploy-file`, or any command that would write artefacts into `~/.m2/repository`. This applies to this project, to sibling projects in the workspace (notably `rspace-core-model`), and to any dependency the user is iterating on. Reason: locally installed artefacts silently shadow the jitpack-built artefact at the same Maven coordinate, so the running JVM ends up with bytecode that does not match what the pom claims to use. That mismatch produces failures that look like a code bug but are really a classpath bug, and it wastes hours to track down. Stick to phases that do not touch `~/.m2`: `mvn compile`, `mvn package`, `mvn test`, `mvn verify`. Dependencies are consumed via jitpack at the version pinned in `pom.xml` — if the user wants to test an unpublished change to a sibling project, ask them to push it and trigger a remote build, then bump the pom to the new commit-hash version. Do not work around this by installing locally.

## Project Overview

RSpace is an open-source collaborative research data management platform (ELN + Inventory). It enables researchers to plan, conduct, record, and report on their work, with integrations for 30+ external services (Box, GitHub, Figshare, ORCID, MS Teams, Slack, etc.).

Java/Spring backend, React/TypeScript frontend, MariaDB.

**Current version:** See `pom.xml`

## Tech Stack

**Backend:**
- Java 17 source level and Java 17 JDK runtime (Adoptium Temurin); Java 17 language features are allowed
- Spring Framework (MVC, Security, WebSocket)
- Maven 3.8.1+ build tool (`./mvnw`)
- MariaDB 10.11 with Hibernate ORM + Envers auditing
- Liquibase for database migrations
- Jetty embedded servlet container (dev)
- Parent POM: `rspace-parent` (controls dependency versions — if a version isn't in this project's `pom.xml`, check the parent)

**Frontend** (`src/main/webapp/ui/`):
- TypeScript + React, Node 24, pnpm
- Vite bundler, Material-UI v5
- Vitest (jsdom unit tests + Browser Mode component tests in real browsers via the Playwright provider + MSW)
- MobX (legacy state) + React Query (newer state)

## Build & Run

### Prerequisites
- Java 17 JDK (Adoptium Temurin recommended)
- Maven 3.8.1+ or use `./mvnw`
- MariaDB 10.11
- Node 24 (frontend dev only)
- `~/.m2/toolchains.xml` configured with Java 17 path

### Database Setup (first time)
```bash
mysql -u root -p"<root-password>" -e "
  CREATE USER 'rspacedbuser'@'127.0.0.1' IDENTIFIED BY '<db-password>';
  CREATE DATABASE rspace collate 'utf8mb4_unicode_ci';
  GRANT ALL ON rspace.* TO 'rspacedbuser'@'127.0.0.1';
"
```

The `rspacedbuser` credentials must match `src/main/resources/rs.properties`.

### Running the Application

```bash
# First run (creates/resets DB)
./mvnw clean jetty:run -Denvironment=drop-recreate-db -DreactDevMode=true \
  -Dspring.profiles.active=run -DRS.devlogLevel=INFO

# Subsequent runs (keep existing DB)
./mvnw jetty:run -Denvironment=keepdbintact -Dspring.profiles.active=run -DreactDevMode=true
```

> **Dev cache-busting for legacy assets:** in dev mode (`reactDevMode=true`), legacy
> `/scripts/` and `/styles/` assets (e.g. `recordInfoPanel.js`) are served without a `?v=`
> cache-buster, so the browser keeps serving a previously cached copy after you edit them.
> If a legacy JS/CSS change does not show up, hard-refresh (Cmd+Shift+R), or add
> `-DlegacyAssetCacheBustingInDevMode=true` to the `jetty:run` command so those assets are
> cache-busted automatically. The React/Vite bundle is unaffected (it is always served fresh).

Access at `http://localhost:8080`. Test users: `user1a`–`user8h`, admin: `sysadmin1`.

### Experimental: Dockerized dev stack (per worktree)

An experimental Docker workflow can boot the whole stack (MariaDB + Jetty
backend + Vite frontend) for the current git worktree, with the worktree source
bind-mounted in (frontend hot-reloads; Java changes apply via `rspace-dev
reload`). It lives in `docker/dev/` (launcher `docker/dev/rspace-dev`, docs in
`docker/dev/README.md`). Each worktree gets its own isolated instance with
auto-assigned host ports, so several can run concurrently.

```bash
./docker/dev/rspace-dev up      # build + start db, backend, frontend
./docker/dev/rspace-dev down    # stop + remove containers (keeps data + caches)
./docker/dev/rspace-dev nuke    # destroy this worktree's instance AND its volumes
```

**Agent guidance:** when a developer wants to run RSpace locally — especially
across multiple worktrees — you MAY mention this option and explain how it
works. Do NOT start it on their behalf: never run `rspace-dev up` (or otherwise
launch the containers) unless the user explicitly asks you to. It is
resource-heavy (a full JVM, database, and Node server per worktree) and not yet
officially supported, so leave the decision to launch it to the developer.

**Tearing down:** `rspace-dev down` stops an instance (reversible; keeps the
database, node_modules, build output, and shared caches), while `rspace-dev
nuke` destroys it along with this worktree's volumes (database, node_modules,
build output, search indices, filestore). Both are scoped to the current
worktree and never affect another worktree or the shared Maven/pnpm caches. You
MAY run these when the user asks you to stop or destroy their instance. `nuke`
permanently deletes that worktree's local dev data, so confirm first unless the
user was explicit, and never run it on your own initiative.

### Frontend Development (watch mode)

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm run serve  # Vite dev server
```

### Full Build (no tests)

```bash
mvn clean package -DgenerateReactDist -DskipTests=true
```

The `-DgenerateReactDist` flag activates the `generateReactDistFiles` profile that
runs `pnpm install --frozen-lockfile` and the Vite production build and bundles `dist/` into the WAR. It is
opt-in: omit it and `mvn compile`/`test`/`package` skip the frontend build entirely
(useful for fast backend-only builds, but the resulting WAR has no frontend).

## Testing

### Backend

Three test tiers, run progressively:

```bash
# 1. Pure unit tests only (no Spring, no DB) — fastest, use during development
mvn clean test -Dfast=true

# Run a single test class (fast)
mvn test -Dtest=MyClassName -Dfast=true

# 2. Unit tests + Spring integration tests (auto-rolled-back, requires DB)
mvn clean test

# Run a single Spring integration test class
mvn test -Dtest=MyClassName

# 3. Full suite including MVC/IT tests (real DB commits, requires DB reset)
mvn clean verify -Denvironment=drop-recreate-db
```

Three test categories:
- **Pure unit** (`*Test.java`, `-Dfast=true`) — plain JUnit + Mockito, no Spring context
- **Spring transactional** (`*Test.java`) — Spring context, auto-rolled-back; extend `SpringTransactionalTest`
- **Integration/MVC** (`*IT.java`) — real DB commits; extend `RealTransactionSpringTestBase` or `MVCTestBase`; runs only in `mvn verify`

Use JUnit 5 for new test classes unless the file already uses JUnit 4. Don't mix assertion styles within a class.

Choosing a base class:
- DAO test → `SpringTransactionalTest`
- Service needing real commits (caching, auditing, permissions) → `RealTransactionSpringTestBase`, suffix `*IT.java`
- Controller → `MVCTestBase`, suffix `*IT.java`

### Frontend

```bash
pnpm run test          # Vitest unit tests (jsdom)
pnpm run test:ui       # Vitest with browser UI
pnpm run test-browser  # Vitest Browser Mode component tests (real browsers via Playwright + MSW)
                       # all of chromium,firefox,webkit by default; override with VITEST_BROWSERS=chromium
pnpm run tsc           # TypeScript type check
pnpm run lint          # Biome lint + format check (read-only)
pnpm run lint:fix      # Biome lint + format with auto-fix
pnpm run format        # Biome format only (write)
```

When changing frontend code in `src/main/webapp/ui`, run the most relevant checks before finishing. At minimum, run `pnpm run tsc`; run `pnpm run test` for behavioral changes and `pnpm run lint` when the change could affect linted code or formatting.

Run a single Vitest test file:
```bash
pnpm run test -- src/components/MyComponent/__tests__/MyComponent.test.tsx
```

### Frontend Testing Patterns

- **Path alias:** `@/` resolves to `src/` in imports.
- **Fetch mocking:** `vitest-fetch-mock` is enabled globally in test setup.
- **Accessibility:** `@sa11y/vitest` is integrated — use `toBeAccessible` matcher.
- **Test setup:** Global setup in `src/__tests__/setup.ts` polyfills `localStorage`, `sessionStorage`, `TextEncoder`/`TextDecoder`.
- **Console suppression:** Use `silenceConsole()` from test helpers to suppress expected errors.
- **Test timeout:** 20 seconds (configured in `vitest.config.ts`).
- **Vitest must execute with `src/main/webapp/ui` as its working directory.** The `vite.config.ts` that owns module aliases (`@/` -> `src/`, `Styles` -> `src/util/styles.ts`) lives there. Running `vitest` from any other directory produces module-resolution failures such as `Cannot find package '@/__tests__/customQueries'` or `Cannot find package 'Styles'`, which look like a source bug but are a cwd problem. Since the pnpm migration the package.json lives at the repo root, so the sanctioned entry is `pnpm test <path-relative-to-ui>` from the repo root (the script cd's into `ui` itself). `npx vitest run <path>` from inside `ui` also works. Beware: `pnpm exec vitest` from inside `ui` can fail with `ERR_PNPM_RECURSIVE_EXEC_NO_PACKAGE` because `ui` no longer has its own package.json.
- **Lint dialect required by this repo's ESLint config — write it compliant from the first draft instead of fixing up afterwards:**
  - Use `expect(node).toBeInTheDocument()` / `not.toBeInTheDocument()`, never `.toBeTruthy()` / `toBeNull()` for DOM existence.
  - Use `expect(node).toHaveAttribute(name, value)`, never `expect(node.getAttribute(name)).toBe(value)`.
  - Use `expect(button).toBeDisabled()`, never `expect((button as HTMLButtonElement).disabled).toBe(true)`.
  - For `vi.mocked(obj.method)`, extract the method to a local first (`const m = obj.method; vi.mocked(m)`), otherwise `@typescript-eslint/unbound-method` fires.
  - `testing-library/no-container` and `no-node-access` are enabled. If you genuinely need `container.querySelector` (rare — usually to inspect a non-semantic child of a MUI component), wrap the block in `/* eslint-disable testing-library/no-node-access, testing-library/no-container */` with a comment explaining why.
- **MUI `Select` inside `FormField` has a broken accessible name.** Its `aria-labelledby` is set to its own id rather than the label's, so `getByRole('combobox', { name: /field type/i })` returns no matches and `getByLabelText('Field type')` resolves to the FormControl wrapper rather than the select itself, so neither query reliably reaches the control. The reliable query is a stable `data-testid` on the display element: pass `SelectDisplayProps={{ "data-testid": "MySelect" } as React.HTMLAttributes<HTMLDivElement>}` and then `screen.getByTestId('MySelect')`.

### Internationalization (i18n)

#### Frontend

Frontend English catalogs live in
`src/main/webapp/ui/src/modules/common/i18n/locales/en-US/`. Finished code uses
semantic keys rather than English strings. See
`DevDocs/DeveloperNotes/FrontendI18nKeys.md` for naming and namespace rules.

Developers may work key-first, or temporarily author English in an explicit
`defaultValue` passed to a literal `t()` call. For the English-first workflow:

1. Add `t("feature.role", { defaultValue: "English text" })`.
2. Run `pnpm run i18n:extract --sync-primary` from the repository root.
3. Review the catalog diff because `--sync-primary` can update existing English
   values. Do not use `--sync-all`; it also clears matching secondary-locale
   values.
4. Remove `defaultValue` from the source, leaving only the key.
5. Run `pnpm run i18n:types`, `pnpm run i18n:lint`, and `pnpm run tsc`.

Raw JSX text is not extracted by `i18n:extract`; wrap it in `t()` first. Use ICU
syntax in `defaultValue` for interpolation and plurals.

#### Backend

Message bundles live in `src/main/resources/bundles/` with subdirectories per module (`dashboard/`, `workspace/`, `gallery/`, `groups/`, `inventory/`, `admin/`, `apps/`, `system/`, `public/`). Spring's `ReloadableResourceBundleMessageSource` loads them. The main bundle is `ApplicationResources.properties`.

**Rule — externalize all user-displayed text in backend code.** Any string that can reach a user must be resolved from a message bundle key, not hard-coded as a Java string literal. This covers, at minimum: validation and error messages, exception messages surfaced to the UI or API, email subjects and bodies, notification text, audit/event descriptions shown to users, and API error responses.

- **New backend code:** every user-facing string MUST be a bundle key from day one. Add the key to the module-specific bundle under `src/main/resources/bundles/` (e.g., `inventory/`, `workspace/`) or to `ApplicationResources.properties` if cross-cutting. Resolve via the injected `MessageSource` / `MessageSourceUtils` — do not instantiate a new message source per call.
- **Refactored code:** when you touch a method, class, or block that contains hard-coded user-displayed strings, externalize those strings into the appropriate bundle as part of the refactor. Treat this as in-scope cleanup, not a separate task — the rule travels with the code you are already changing.
- **Out of scope:** log messages, internal exception messages that are never shown to users, debug output, and developer-facing tooling text. Comments and Javadoc remain in English.

## Configuration

### Property File Layering

Configuration is loaded in layers (later layers override earlier ones):

1. `src/main/resources/deployments/defaultDeployment.properties` — default values
2. `src/main/resources/deployments/dev/deployment.properties` — local dev overrides
3. External runtime properties (production deployments)

Key properties:
- `rs.filestore=LOCAL` — file storage backend (`LOCAL` or `EGNYTE`)
- Database credentials in `src/main/resources/rs.properties`

> **Gotcha:** If a property is defined in `defaultDeployment.properties`, Spring EL default values (`${prop:default}`) won't work — the property is always present.

### API Authentication

The REST API (`/api/v1/`) supports two authentication methods:
- **API Key:** `apiKey` header with user's API key
- **OAuth Bearer Token:** Standard `Authorization: Bearer <token>` header

API keys are managed by `UserApiKeyManager`. Authentication is implemented via Apache Shiro (not Spring Security).

## Architecture

### Multi-project layout

This repo (`rspace-web`) is one of several sibling projects. Core domain models (`User`, `Record`, `Folder`, etc.) live in `rspace-core-model` — if a class isn't found here, check the sibling repos in the parent directory (`cd ..`).

### Backend layers

```
Controller  →  Service (Manager)  →  DAO  →  Hibernate/DB
```

- **Controllers** (`com.researchspace.api.v1.controller`, `com.researchspace.webapp.controller`): input validation only; never call DAOs directly
- **Services** (`com.researchspace.service`): transactional; names must end in `Manager` for AOP transaction wrapping (`applicationContext-service.xml`)
- **DAOs** (`com.researchspace.dao`): assume an active transaction; use `sessionFactory.getCurrentSession()`
- **Legacy core** (`com.axiope`): older DAOs, model, search components

**No upward dependencies.** Imports flow downward only. A class in `com.researchspace.service.*` must never import from `com.researchspace.*.controller.*`, and a class in `com.researchspace.dao.*` must never import from `com.researchspace.service.*` or `com.researchspace.*.controller.*`. If a helper, validator, or utility is used by service-layer code, it belongs in the service layer — even if it is also called from a controller. A fully-qualified reference to a higher-layer package is a smell: stop, classify the target type's layer, and relocate it before merging.

### Frontend structure

```
src/main/webapp/ui/src/
  components/          # Shared components
  Inventory/           # Inventory module (samples, containers, etc.)
  eln/                 # ELN (notebook) module
  hooks/               # Custom React hooks
  stores/              # MobX stores (legacy)
```

State management: MobX in older areas, React Query in newer code. Use React Query for new work.

### API

REST v1 under `/api/v1/`. Each controller has a co-located validator class. Pagination uses `ApiPaginationCriteria`. Supports JSON and CSV responses. Swagger specs: `src/main/webapp/resources/rspace_api_specs_*.yaml`.

### Security

Apache Shiro (not Spring Security). Permission syntax: `DOMAIN:ACTION:IDENTIFIER` (e.g., `RECORD:READ:123`). Check permissions via `PermissionUtils.isPermitted()`. Four roles: User, PI, Community Admin, Sysadmin.

Spring profiles: `dev` (tests, automatic), `run` (jetty:run, automatic), `prod` (production).

### Database

All schema changes via Liquibase changesets in `src/main/resources/sqlUpdates/`. Hibernate Envers provides full audit trail on entities. Use soft deletes, not hard deletes.

**Liquibase changeset format:**
- File name: `changeLog-<ticket-id>.xml`
- Each `<changeSet>` requires `id` (date-based, e.g., `2025-06-13a`), `author`, and a `context`. RSpace deployments use one of three context strings: `run` (production), `run,dev-test` (local dev/tests), `run,cloud` (Community). Tag schema changes `context="run"` (applies in all three); use `dev-test` for local/test-only data and `cloud` for Community-only data. See `src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md`.
- Use standard Liquibase XML elements: `<createTable>`, `<addColumn>`, `<addForeignKeyConstraint>`, `<createIndex>`, etc.
- Baseline migration: `rs-dbbaseline-utf8.sql` (fresh installs only)

### WebSocket

STOMP over WebSocket at `/ws` endpoint with SockJS fallback. Spring's `@EnableWebSocketMessageBroker` with simple broker for `/topic` prefixed destinations. Origin validation via `OriginRefererChecker`.

## Common Development Pitfalls

1. **Java language level:** Source and runtime are both Java 17 (`<release>17</release>`); Java 17 language features (records, sealed classes, text blocks, pattern matching) are allowed.
2. **Transaction boundary:** Calling a DAO directly from a controller will fail silently or throw — always go through a `*Manager` service.
3. **Test class separation:** `*Test.java` (transactional rollback) and `*IT.java` (real commits) run in different Maven phases. Mixing them causes failures.
4. **Lazy loading in tests:** Spring transactional tests with auto-rollback mask lazy-loading exceptions that will surface in production.
5. **Form data size:** Local Jetty limits form data to 200KB (Tomcat: 2MB). Override with `-Dorg.eclipse.jetty.server.Request.maxFormContentSize=2000000`.
6. **Liquibase context:** A changeset only runs when its `context` matches the active launch string. Local dev/test default to `run,dev-test` (set in `dev/deployment.properties`); production is `run`; Community is `run,cloud`. Tag new changesets `run` unless they are dev/test- or cloud-only — see `src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md`.
7. **Service naming:** Service beans must end in `Manager` for AOP transaction proxying (configured in `applicationContext-service.xml`).
8. **TinyMCE plugin iframes:** Plugin dialogs (e.g., `internalLink.jsp`) load in iframes and don't inherit the main page decorator. If they use `global.js` functions that depend on DOM templates (e.g., `blockUI.html` for `RS.blockPage()`), those templates must be explicitly `<jsp:include>`d.

## Key Conventions

### Java

- Google Java Style Guide, enforced by Spotless Maven Plugin (`mvn spotless:apply`)
- Import `intellij-java-google-style.xml` (project root) for IDE formatting
- Lombok is used; install the IDE plugin
- Javadoc required on service interface methods and non-trivial entity methods
- Log all caught exceptions at WARN or ERROR; never use empty catch blocks
- Use try-with-resources for file/stream handling; no empty catch blocks
- Never log passwords or sensitive data
- Input validation at Controller layer; services assume valid input

### React/TypeScript

- Functional components with hooks only
- Axios for API calls (centralized)
- DOMPurify for any user-generated HTML (XSS prevention)
- Biome (lint + format) configured; run `pnpm run lint` (or `pnpm run lint:fix`) before committing
- Use the existing workspace package manager (`pnpm` for `src/main/webapp/ui`)
- Install dependencies only when needed for the task
- Do not add new dependencies unless they are necessary to solve the requested problem

### Transactions

Calling a DAO method directly from a controller will fail — always go through a service. Each service call from a controller runs in its own transaction; for multi-step operations that must be atomic, write a single service method.

## Key Directories

```
src/main/java/com/axiope/          # Legacy core components (DAOs, model, search)
src/main/java/com/researchspace/   # Newer modules
  api/v1/controller/               # REST API controllers
  api/v1/model/                    # API DTOs
src/main/webapp/ui/                # React frontend
  src/components/                  # Reusable components
  src/Inventory/                   # Inventory module
  src/eln/                         # ELN module
  src/hooks/                       # Custom React hooks
src/main/resources/
  applicationContext-*.xml         # Spring configuration
  sqlUpdates/                      # Liquibase migrations
  velocityTemplates/               # Email templates
src/test/java/                     # All backend tests
DevDocs/                           # Developer documentation
```

## Developer Documentation

- `DevDocs/DeveloperNotes/GettingStarted/GettingStarted.md` — Setup guide
- `DevDocs/DeveloperNotes/GettingStarted/CodingStandards.md` — Conventions & testing strategy
- `DevDocs/DeveloperNotes/MavenModuleOverview.md` — Architecture overview
- `DevDocs/DeveloperNotes/SecurityAndPermissions.md` — Permission model
- `DevDocs/DeveloperNotes/Transactions.md` — Transaction boundaries
- `DevDocs/DeveloperNotes/Caching.md` — Caching strategies
- `DevDocs/DeveloperNotes/Logging.md` — Logging guidelines
- Update nearby documentation when behavior or developer workflow changes
- Keep documentation concise and specific to this repository

## CI/CD

- **GitHub Actions:** `.github/workflows/lint-and-test.yml` (public CI) — auto-detects frontend vs Java changes and runs appropriate test suites
- **Jenkins:** `Jenkinsfile` (internal CI with code coverage)
- Code quality: SonarQube, Checkstyle
- **PR template:** `.github/pull_request_template.md` — requires a description; design decisions and testing notes optional; include screenshots for UI changes

## Agent-Specific Config Files

- **AGENTS.md**: Primary instructions for all AI agents. We make a copy of Agents.md in this file.
- **CLAUDE.md**: A copy of AGENTS.md — for Claude Code / Anthropic agents
- **.github/copilot-instructions.md**: Quick-reference for GitHub Copilot — points to AGENTS.md

## Repo-Local Agent Skills

Reusable agent skills (executable playbooks) live under `.agents/skills/`. Each
skill is a directory containing a `SKILL.md` (with YAML frontmatter `name` +
`description`) plus optional `REFERENCE.md` and bundled resources.

Currently available:

- `.agents/skills/rspace-empty-integration/` — scaffolds a new "empty" RSpace
  integration (Liquibase migration, sysadmin/user toggles, Apps-page card,
  TinyMCE toolbar icon opening a dialog whose chrome title is the integration's name).
- `.agents/skills/rebrand-integration/` — rebrand an existing integration in place
  when the third-party service renames (swap the two SVG icons, update the brand colour,
  replace every user-facing occurrence of the old name; code identifiers and URLs untouched).
- `.agents/skills/rspace-dev-stack/` — boot, drive, and tear down the per-worktree
  Dockerized dev stack (`docker/dev/rspace-dev`) to reproduce bugs, diagnose issues,
  and manually test functionality against a real running RSpace instance.
- `.agents/skills/rspace-browser-tests/` — write, run, and debug frontend
  component tests in Vitest Browser Mode (real chromium/firefox/webkit via the
  Playwright provider + MSW + a Page Object Model; `*.spec.tsx`). Covers the
  four-artifact pattern, shared infra, and the cross-engine gotchas.

**Discovery:** `.agents/skills/` is the cross-agent convention (used by
agents-md-aware tools, Cursor, Codex CLI, and others that follow the AGENTS.md
spec). Claude Code users who want auto-discovery can either (a) symlink
`.claude/skills -> ../.agents/skills` locally (not committed), or (b) point
Claude at the skill explicitly: "use the skill at `.agents/skills/<name>`".
Either way, every agent can read these files directly — no install step needed
to use them in this repo.

**To add a new skill:** create `.agents/skills/<skill-name>/SKILL.md` with
frontmatter, keep it under ~150 lines, and put bulky templates/recipes in a
sibling `REFERENCE.md`. List it above so the team can discover it.
