# CLAUDE.md

See [AGENTS.md](./AGENTS.md) for full project context (build, test, architecture, conventions).

## PR Descriptions

When creating or updating GitHub pull request descriptions:
- Start by stating the investigation/fix was done by GitHub Copilot AI agent on behalf of the user
- Follow with a one-sentence summary of what the PR fixes or adds
- Keep the description concise — avoid exhaustive detail
- Include a **Root causes** section (brief bullet points) when it's a bug fix
- Include a **Changes** section listing the key files/methods touched
- Reference any linked Jira ticket (e.g. RSDEV-1081)
- Do not exceed ~15 lines total

## Pull Requests

- **Branch naming:** `<TICKET-ID>-<short-description>` e.g. `RSDEV-967-fullname-sort`
- **PR source:** branch on `ResearchSpace-ELN/rspace-web`
- **PR target:** `main` on `rspace-os/rspace-web` (the upstream OSS repo)

## Jira Comments

When adding comments to Jira tickets:
- Start by stating the investigation/fix was done by GitHub Copilot AI agent on behalf of the user
- Keep comments brief — aim for under 10 lines
- Cover: root cause (1-2 sentences), fix (1-2 sentences), links to PRs or commits
- Use **bold** for section labels (Root cause, Fix, PRs)
- Do not repeat information already in the ticket description

## Tech Stack (RSpace)

**Backend:** Java 11 source level on JDK 17 runtime, Spring Framework (MVC, Security, WebSocket), Maven, MariaDB + Hibernate ORM, Liquibase migrations, Jetty (dev).
**Frontend:** TypeScript + React, Material-UI v5, Webpack, MobX (legacy) + React Query (new work), Vitest, Playwright.

### Java / Backend conventions
- **Do NOT use Java 17+ language features** (no records, sealed classes, text blocks, pattern matching) — source level is Java 11
- Do NOT use Spring Boot; Spring context is configured via `applicationContext-*.xml` files
- Follow the layering: `Controller → Service (Manager) → DAO → Hibernate/DB`
    - Controllers: input validation only — never call DAOs directly
    - Services: must be named ending in `Manager` for AOP transaction proxying
    - DAOs: assume an active transaction; use `sessionFactory.getCurrentSession()`
- Prefer constructor injection; avoid `@Autowired` on fields
- Security is **Apache Shiro** (not Spring Security); check permissions via `PermissionUtils.isPermitted()`
- Use soft deletes — never hard-delete records
- All schema changes via **Liquibase** changesets in `src/main/resources/sqlUpdates/`
- Code style: **Google Java Style Guide** enforced by Spotless (`mvn spotless:apply`)
- Lombok is used; Javadoc required on service interface methods and non-trivial entity methods
- Log caught exceptions at WARN or ERROR; no empty catch blocks; never log sensitive data

### Testing (backend)
Three tiers — run in order:
1. **Pure unit** (`*Test.java`, `-Dfast=true`): plain JUnit 5 + Mockito, no Spring context
2. **Spring transactional** (`*Test.java`): Spring context, auto-rolled-back; extend `SpringTransactionalTest`
3. **Integration/MVC** (`*IT.java`): real DB commits; extend `RealTransactionSpringTestBase` or `MVCTestBase`; runs in `mvn verify`

Use JUnit 5 for new test classes. Do not mix assertion styles within a class.

### Frontend conventions
- Functional components with hooks only; use React Query for new state management
- Import `render`/`within` from `@/__tests__/customQueries`, not directly from `@testing-library/react`
- Axios for API calls; DOMPurify for any user-generated HTML
- Run `npm run lint` and `npm run tsc` before committing

### General
- Keep PRs focused; prefer small, reviewable changes
- Reference the linked Jira ticket in commits and PRs