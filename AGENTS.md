# AGENTS.md

Project instructions for AI coding agents (Claude Code, OpenAI Codex, Gemini CLI, Cursor, etc.).

## Project Overview

RSpace is an open-source collaborative research data management platform (ELN + Inventory). It enables researchers to plan, conduct, record, and report on their work, with integrations for 30+ external services (Box, GitHub, Figshare, ORCID, MS Teams, Slack, etc.).

Java/Spring backend, React/TypeScript frontend, MariaDB.

**Current version:** See `pom.xml`

## Tech Stack

**Backend:**
- Java 11 source level, Java 17 JDK runtime — do **not** use Java 17+ language features (e.g., records, sealed classes, pattern matching)
- Spring Framework (MVC, Security, WebSocket)
- Maven 3.8.1+ build tool (`./mvnw`)
- MariaDB 10.6 or 10.11 with Hibernate ORM + Envers auditing
- Liquibase for database migrations
- Jetty embedded servlet container (dev)
- Parent POM: `rspace-parent` (controls dependency versions — if a version isn't in this project's `pom.xml`, check the parent)

**Frontend** (`src/main/webapp/ui/`):
- TypeScript + React, Node 24, npm
- Webpack bundler, Material-UI v5
- Vitest (unit tests), Playwright (component/E2E tests)
- MobX (legacy state) + React Query (newer state)

## Build & Run

### Prerequisites
- Java 17 JDK (Adoptium Temurin recommended) — required even though source level is 11
- Maven 3.8.1+ or use `./mvnw`
- MariaDB 10.6 or 10.11
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
./mvnw clean jetty:run -Denvironment=drop-recreate-db -DgenerateReactDist \
  -Dspring.profiles.active=run -DRS.devlogLevel=INFO

# Subsequent runs (keep existing DB)
./mvnw jetty:run -Denvironment=keepdbintact -Dspring.profiles.active=run
```

Access at `http://localhost:8080`. Test users: `user1a`–`user8h`, admin: `sysadmin1`.

### Frontend Development (watch mode)

```bash
cd src/main/webapp/ui
npm ci --force
npm run serve  # Webpack watch mode
```

### Full Build (no tests)

```bash
mvn clean package -DskipTests=true -DgenerateReactDist=clean
```

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
cd src/main/webapp/ui

npm run test          # Vitest unit tests
npm run test:ui       # Vitest with browser UI
npm run test-ct       # Playwright component tests
npm run tsc           # TypeScript type check
npm run lint          # ESLint
npm run lint:fix      # ESLint with auto-fix
```

Run a single Vitest test file:
```bash
npx vitest run src/components/MyComponent/__tests__/MyComponent.test.tsx
```

### Frontend Testing Patterns

- **Custom render:** Import `render` and `within` from `@/__tests__/customQueries` (not directly from `@testing-library/react`). This wrapper provides custom queries like `findTableCell`.
- **Path alias:** `@/` resolves to `src/` in imports.
- **Fetch mocking:** `vitest-fetch-mock` is enabled globally in test setup.
- **Accessibility:** `@sa11y/vitest` is integrated — use `toBeAccessible` matcher.
- **Test setup:** Global setup in `src/__tests__/setup.ts` polyfills `localStorage`, `sessionStorage`, `TextEncoder`/`TextDecoder`.
- **Console suppression:** Use `silenceConsole()` from test helpers to suppress expected errors.
- **Test timeout:** 20 seconds (configured in `vitest.config.ts`).

### Internationalization (i18n)

Message bundles live in `src/main/resources/bundles/` with subdirectories per module (`dashboard/`, `workspace/`, `gallery/`, `groups/`, `inventory/`, `admin/`, `apps/`, `system/`, `public/`). Spring's `ReloadableResourceBundleMessageSource` loads them. The main bundle is `ApplicationResources.properties`.

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
- Each `<changeSet>` requires `id` (date-based, e.g., `2025-06-13a`), `author`, and `context="run"`
- Use standard Liquibase XML elements: `<createTable>`, `<addColumn>`, `<addForeignKeyConstraint>`, `<createIndex>`, etc.
- Baseline migration: `rs-dbbaseline-utf8.sql` (fresh installs only)

### WebSocket

STOMP over WebSocket at `/ws` endpoint with SockJS fallback. Spring's `@EnableWebSocketMessageBroker` with simple broker for `/topic` prefixed destinations. Origin validation via `OriginRefererChecker`.

## Common Development Pitfalls

1. **Java language level:** Source is Java 11 (`<release>11</release>`) despite using JDK 17. Do not use records, sealed classes, text blocks, or other post-Java 11 features.
2. **Transaction boundary:** Calling a DAO directly from a controller will fail silently or throw — always go through a `*Manager` service.
3. **Test class separation:** `*Test.java` (transactional rollback) and `*IT.java` (real commits) run in different Maven phases. Mixing them causes failures.
4. **Lazy loading in tests:** Spring transactional tests with auto-rollback mask lazy-loading exceptions that will surface in production.
5. **Form data size:** Local Jetty limits form data to 200KB (Tomcat: 2MB). Override with `-Dorg.eclipse.jetty.server.Request.maxFormContentSize=2000000`.
6. **Liquibase context:** Use `-Dliquibase.context=run` when running migrations locally.
7. **Service naming:** Service beans must end in `Manager` for AOP transaction proxying (configured in `applicationContext-service.xml`).

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
- ESLint + Prettier enforced; run `npm run lint` before committing

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

## CI/CD

- **GitHub Actions:** `.github/workflows/lint-and-test.yml` (public CI) — auto-detects frontend vs Java changes and runs appropriate test suites
- **Jenkins:** `Jenkinsfile` (internal CI with code coverage)
- Code quality: SonarQube, SpotBugs, Checkstyle
- **PR template:** `.github/pull_request_template.md` — requires a description; design decisions and testing notes optional; include screenshots for UI changes

## Agent-Specific Config Files

- **AGENTS.md** (this file): Primary instructions for all AI agents
- **CLAUDE.md**: Points to AGENTS.md — for Claude Code / Anthropic agents
- **.github/copilot-instructions.md**: Quick-reference for GitHub Copilot — points to AGENTS.md

## Repo-Local Agent Skills

Reusable agent skills (executable playbooks) live under `.agents/skills/`. Each
skill is a directory containing a `SKILL.md` (with YAML frontmatter `name` +
`description`) plus optional `REFERENCE.md` and bundled resources.

Currently available:

- `.agents/skills/rspace-empty-integration/` — scaffolds a new "empty" RSpace
  integration (Liquibase migration, sysadmin/user toggles, Apps-page card,
  TinyMCE toolbar icon opening a blank-titled dialog).

**Discovery:** `.agents/skills/` is the cross-agent convention (used by
agents-md-aware tools, Cursor, Codex CLI, and others that follow the AGENTS.md
spec). Claude Code users who want auto-discovery can either (a) symlink
`.claude/skills -> ../.agents/skills` locally (not committed), or (b) point
Claude at the skill explicitly: "use the skill at `.agents/skills/<name>`".
Either way, every agent can read these files directly — no install step needed
to use them in this repo.

**To add a new skill:** create `.agents/skills/<skill-name>/SKILL.md` with
frontmatter, keep it under ~100 lines, and put bulky templates/recipes in a
sibling `REFERENCE.md`. List it above so the team can discover it.

