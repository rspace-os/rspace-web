# AGENTS.md

RSpace is a Java 17/Spring application with a React/TypeScript frontend, MUI, Axios, pnpm, Liquibase, Hibernate, and MariaDB.

These instructions apply to the whole repository. A nested `AGENTS.md` takes precedence for files below it.

## Project map

- `src/main/java/` - backend Java code
- `src/test/java/` - backend tests
- `src/main/webapp/ui/src/` - React/TypeScript frontend
- `src/main/resources/` - configuration, message bundles, and Liquibase resources
- `src/main/resources/sqlUpdates/` - database changesets
- `DevDocs/DeveloperNotes/` - developer documentation
- `.agents/skills/` - repository-local playbooks

Core domain models, utilities, audit-trail code, and test utilities now live in this repository. External consumers still use the released `rspace-core-util` 2.0.0 through JitPack. Treat `JacksonUtil`, `TransformerUtils`, and `zipprocessing` as frozen APIs for those consumers. Vendor a class into the consumer when a change is needed there.

<important if="you need to build, test, lint, generate code, or run the application">

Run frontend commands from the repository root. Root scripts change into `src/main/webapp/ui`, where Vite owns the module aliases.

pnpm forwards arguments directly to scripts. Never add a standalone `--` after `pnpm test`, `pnpm run test`, or another pnpm script. Pass options immediately after the script name.

| Command | Purpose |
|---|---|
| `pnpm install --frozen-lockfile` | Install frontend dependencies |
| `pnpm test` | Run Vitest/jsdom unit tests |
| `pnpm test src/components/MyComponent/__tests__/MyComponent.test.tsx` | Run one unit-test file |
| `pnpm test --verbose <filter>` | Run tests with an option and filter |
| `pnpm test-browser` | Run Browser Mode tests in Chromium, Firefox, and WebKit |
| `pnpm test-browser src/components/MyComponent.spec.tsx` | Run one Browser Mode test file |
| `VITEST_BROWSERS=chromium pnpm test-browser <file>` | Run a Browser Mode file in Chromium |
| `pnpm tsc` | Type-check the frontend |
| `pnpm lint` | Run the read-only Biome check |
| `pnpm lint:fix` | Apply Biome fixes |
| `pnpm serve` | Serve the frontend |
| `pnpm run i18n:extract --sync-primary` | Extract and sync primary English translations |
| `pnpm run i18n:types` | Generate i18n types |
| `pnpm run i18n:lint` | Lint translation catalogs |
| `mvn test -Dtest=MyClassName -Dfast=true` | Run one pure backend unit-test class |
| `mvn clean test -Dfast=true` | Run all pure backend unit tests |
| `mvn test -Dtest=MyClassName` | Run one Spring transactional test class |
| `mvn clean test` | Run unit and Spring tests; requires a database |
| `mvn clean verify -Denvironment=drop-recreate-db` | Run verification including `*IT`; resets the database |
| `mvn clean package -DgenerateReactDist -DskipTests=true` | Build a WAR with the frontend |

Never run `mvn install`, `./mvnw install`, `install:install-file`, or any deploy goal. These write artifacts to the local repository and can shadow the JitPack dependency. Use `compile`, `package`, `test`, or `verify`. To test unpublished sibling-project changes, push them, wait for the remote build, then update this project's pinned commit hash.
</important>

<important if="you are changing repository files">

- Keep changes narrowly scoped and preserve unrelated user-authored work.
- Check nested `AGENTS.md` files before editing their directories.
- Use `rg` and `rg --files` for searches.
- Treat vendored code, minified files, build output, `target/`, `dist/`, and `node_modules/` as read-only unless the task explicitly targets them.
- Put plans and scratch notes in `.claude/`, which is gitignored.
- Do not add dependencies unless necessary.
- Update nearby developer documentation when behavior or workflows change.
</important>

<important if="you are changing backend controllers, services, DAOs, or transactions">

Backend dependencies flow downward: `Controller -> Service (*Manager) -> DAO -> Hibernate/MariaDB`.

- Controllers validate input and call services, never DAOs.
- Transactional Spring services should end in `Manager` so AOP pointcuts in `applicationContext-service.xml` apply.
- A non-`Manager` service may declare its boundary with `@Transactional`; `TransactionAdviceStartupCheck` verifies annotation-driven advice at startup.
- DAOs require an active transaction.
- Do not import from a lower layer into a higher one.
</important>

<important if="you are changing frontend application code">

Use React functional components with TypeScript. Prefer React Query for new server state; MobX remains in legacy areas. Sanitize user-generated HTML with DOMPurify.
</important>

<important if="you are writing or modifying frontend tests">

- Use Vitest and Testing Library. For `*.spec.tsx` Browser Mode tests, follow the `rspace-browser-tests` skill.
- Import `render` and `within` directly from `@testing-library/react`. Use `findTableCell` / `getIndexOfTableCell` from `@/__tests__/tableQueries` and `expectAccessible` from `@/__tests__/accessibility` when needed.
- MSW uses the shared Node server in `src/__tests__/mswServer.ts`, enabled by `src/__tests__/setup.ts`; register test-local handlers with its shared `server` export. Unhandled requests fail tests.
- Use `toBeAccessible` for accessibility checks and `silenceConsole()` for expected console errors.
- Prefer semantic jest-dom assertions such as `toBeInTheDocument`, `toHaveAttribute`, and `toBeDisabled`.
- Assign mocked methods to a local before calling `vi.mocked` to satisfy the unbound-method lint rule.
- Give MUI `Select` controls inside `FormField` an accessible name through `SelectDisplayProps` and query them by role and name.
</important>

<important if="you are writing or modifying backend tests">

- Tests use JUnit 6 (Jupiter) imports from `org.junit.jupiter`.
- Surefire discovers `Test*`, `*Test`, `*Tests`, and `*TestCase` in the `test` phase. `*IT` runs only through the `integration-tests` execution in `pom.xml`, unless named with `-Dtest=`. Other names are not reported as tests.
- DAO tests extend `SpringTransactionalTest`.
- Service behavior that requires commits extends `RealTransactionSpringTestBase` and uses an `*IT.java` name.
- Controller tests extend `MVCTestBase` and use an `*IT.java` name.
- For Spring context-only tests, annotate the class with `@WithSpringContext` and a context annotation such as `@DefaultTestContext` or `@ContextConfiguration`. Do not replace Spring's default test execution listeners.
- For several class-level configurations, use one outer class with a `@Nested` class per configuration. Put `@TestPropertySource` and `@ContextConfiguration` on the nested classes, not the outer class.
- Gate tests unavailable in CI with class-level `@EnabledIfSystemProperty` when the whole class is gated; method-level gating still loads a Spring context.
- Use the inherited `assertExceptionThrown`, `assertAuthorisationExceptionThrown`, and `assertLazyInitializationExceptionThrown` helpers on `BaseManagerTestCaseBase` when applicable.
</important>

<important if="you are changing frontend or backend behavior that users can see">

- Externalize backend user-facing text in `src/main/resources/bundles/` or `ApplicationResources.properties` and resolve it through the injected message source. Logs and internal-only messages are exempt.
- Frontend English catalogs live in `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/`; use semantic keys. Follow `DevDocs/DeveloperNotes/i18n.md`.
- For new frontend text, use a literal `defaultValue` with `t()`, run `pnpm run i18n:extract --sync-primary`, review the catalog, remove `defaultValue`, then run `pnpm run i18n:types`, `pnpm run i18n:lint`, and `pnpm run tsc`. Never use `--sync-all`.
- Wrap raw JSX text in `t()` before extraction. Use ICU syntax for interpolation and plurals.
</important>

<important if="you are changing database schema or persistence">

Use a new Liquibase changeset under `src/main/resources/sqlUpdates/`; never edit the baseline for an existing schema change. Follow `DatabaseChangeGuidelines.md`. New changesets normally use `context="run"`; reserve `dev-test` and `cloud` for environment-specific data. Use soft deletion where the domain supports it.
</important>

<important if="you are adding Java code or changing Java error handling, resources, or entities">

- Use try-with-resources for streams and files.
- Log caught exceptions at WARN or ERROR, never credentials or other sensitive data, and do not leave empty catch blocks.
- Add Javadoc to service interface methods and non-trivial entity methods.
</important>

<important if="you are finishing a feature or code/configuration change">

Run relevant focused tests. For frontend changes, run `pnpm tsc` at minimum, plus focused tests for behavioral changes and `pnpm lint` for linted or formatted code. Do not replace focused tests with the full suite unless broad verification is warranted.
</important>

<important if="you are verifying that a feature works in the running application">

Use Browser Automation against this worktree's Docker Dev Stack to exercise the completed user flow. The stack is a throwaway instance, so updating fixtures and application settings during verification is allowed.
</important>

<important if="you need to run RSpace locally or reproduce behavior end to end">

Normal Jetty setup is documented in `DevDocs/DeveloperNotes/GettingStarted/GettingStarted.md`.

The per-worktree Docker stack is in `docker/dev/rspace-dev`; read `docker/dev/README.md` and the `rspace-dev-stack` skill first. Start it only when the user explicitly asks, because it launches MariaDB, the JVM, and Node. `down` is reversible. Confirm before `nuke`, which permanently deletes that worktree's local data.
</important>

<important if="you are using or creating repository skills, or need project workflow references">

Read the matching playbook in `.agents/skills/`. Keep new skills concise and put bulky material in a sibling `REFERENCE.md`.

Useful references:

- Setup and workflows: `DevDocs/DeveloperNotes/GettingStarted`
- Transactions: `DevDocs/DeveloperNotes/Transactions.md`
- Security: `DevDocs/DeveloperNotes/SecurityAndPermissions.md`
- Logging: `DevDocs/DeveloperNotes/Logging.md`
- CI: `.github/workflows/lint-and-test.yml` and `Jenkinsfile`
</important>
