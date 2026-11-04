# AGENTS.md

RSpace is a Java/Spring application (built for Java 17, runs on 17 or 21) with a React/TypeScript frontend, MUI, Axios, pnpm, Liquibase, Hibernate, and MariaDB.

Applies repository-wide; nested `AGENTS.md` files take precedence.

## Project map

- `src/main/java/` - backend Java code
- `src/test/java/` - backend tests
- `src/main/webapp/ui/src/` - React/TypeScript frontend
- `src/main/resources/` - configuration, message bundles, and Liquibase resources
- `src/main/resources/sqlUpdates/` - database changesets
- `DevDocs/DeveloperNotes/` - developer documentation
- `.agents/skills/` - repository-local playbooks

Core models, utilities, audit-trail code, and test utilities live here. External consumers use `rspace-core-util` 2.0.0 from JitPack. Keep `JacksonUtil`, `TransformerUtils`, and `zipprocessing` frozen; vendor changed classes into consumers.

<important if="building, testing, linting, generating code, or running the app">

Run frontend commands from the repository root. Scripts enter `src/main/webapp/ui`, where Vite owns module aliases. Pass pnpm script options directly, without a standalone `--`.

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
| `./gradlew test -Dtest=MyClassName -Dfast=true` | Run one pure backend unit-test class |
| `./gradlew test -Dfast=true` | Run all pure backend unit tests |
| `./gradlew test -Dtest=MyClassName` | Run one Spring transactional test class |
| `./gradlew test` | Run unit and Spring tests; requires a database |
| `./gradlew integrationTest -Dtest=MyClassIT` | Run one `*IT` class; requires a database |
| `./gradlew verify -Denvironment=drop-recreate-db` | Run unit, Spring and `*IT` tests and build the WAR; resets the database |
| `./gradlew war -DgenerateReactDist` | Build a WAR with the frontend |
| `./gradlew spotlessApply` | Format Java |

Gradle is the only build; see `DevDocs/DeveloperNotes/Gradle.md`. Run it on Java 17 or 21 under a UTF-8 locale, never `LC_ALL=C`. Pass build settings as `-D<name>=<value>`; they reach resource filtering and the test JVMs. Unchanged test tasks are skipped; add `--rerun` to run one again.

Dependencies resolve from Maven Central, JitPack (`com.github.*`) and DICE only, never `mavenLocal()`; do not add it. A dependency change also updates its pin in `gradle/pinned-versions.txt`; `./gradlew checkPinnedVersions` confirms. For sibling-project changes, push, wait for JitPack to build the release, then update the version in `build.gradle` and `gradle/pinned-versions.txt`.
</important>

<important if="editing repository files">

- Keep changes scoped; preserve unrelated work.
- Read nested `AGENTS.md` files before editing.
- Use `rg` and `rg --files` for searches.
- Only edit vendored code, minified files, build output, `build/`, `dist/`, or `node_modules/` when explicitly targeted.
- Put plans and scratch notes in gitignored `.claude/`.
- Add dependencies only when necessary.
- Update nearby developer documentation when behavior or workflows change.
</important>

<important if="asked to post or edit a GitHub comment">

Every GitHub comment posted or edited by a coding agent must begin with this attribution preamble: `> Written by [coding agent name], an AI coding agent.` Replace the placeholder with the agent's name.
</important>

<important if="changing backend layers or transactions">

Backend dependencies flow downward: `Controller -> Service (*Manager) -> DAO -> Hibernate/MariaDB`.

- Controllers validate input and call services, never DAOs.
- Name transactional Spring services `*Manager` for AOP in `applicationContext-service.xml`.
- Other services may use `@Transactional`; `TransactionAdviceStartupCheck` verifies advice at startup.
- DAOs require an active transaction.
- Keep imports consistent with this downward dependency flow.
</important>

<important if="changing frontend code">

Use React functional components with TypeScript. Prefer React Query for new server state; MobX remains in legacy areas. Sanitize user-generated HTML with DOMPurify.
</important>

<important if="writing frontend tests">

- Use Vitest and Testing Library. Follow `react-testing-library` for `*.test.tsx` and `rspace-browser-tests` for `*.spec.tsx`.
- Import `render` and `within` from `@testing-library/react`. Reuse `findTableCell` / `getIndexOfTableCell` from `@/__tests__/tableQueries` and `expectAccessible` from `@/__tests__/accessibility`.
- For jsdom, register MSW handlers with `server` from `src/__tests__/mswServer.ts`. `src/__tests__/setup.ts` enables it; unhandled requests fail tests.
- Use `toBeAccessible` for accessibility checks and `silenceConsole()` for expected console errors.
- Prefer semantic jest-dom assertions such as `toBeInTheDocument`, `toHaveAttribute`, and `toBeDisabled`.
- Assign mocked methods to a local before `vi.mocked` to avoid unbound-method lint errors.
- Name MUI `Select` controls inside `FormField` through `SelectDisplayProps`; query by role and name.
</important>

<important if="writing backend tests">

- Use JUnit 6 Jupiter imports from `org.junit.jupiter`.
- The `test` task runs `Test*`, `*Test`, `*Tests`, and `*TestCase` classes. `integrationTests` runs `*IT` classes (`integrationTest`, plus `integrationTestRun` and `integrationTestProblematic` for classes `build.gradle` lists). `-Dtest=` replaces those patterns in every test task. Other names are not discovered.
- DAO tests extend `SpringTransactionalTest`.
- Commit-dependent service tests extend `RealTransactionSpringTestBase`; name them `*IT.java`.
- Controller tests extend `MVCTestBase`; name them `*IT.java`.
- Context-only tests need `@WithSpringContext` plus `@DefaultTestContext` or `@ContextConfiguration`. Keep Spring's default test execution listeners.
- For multiple configurations, use one outer class and a `@Nested` class per configuration. Put `@TestPropertySource` and `@ContextConfiguration` on nested classes.
- Gate whole classes unavailable in CI with `@EnabledIfSystemProperty`; method-level gates still load Spring.
- Use JUnit's `assertThrows` directly, keeping setup outside its executable. For exceptions already captured by MockMvc, use `assertInstanceOf` on `getResolvedException()`.
- Use JUnit assertions for equality, booleans, nullness, identity, type, and exceptions. AssertJ is appropriate for collection, map, string-content, `Optional`, numeric-comparison, and file assertions. Do not replace named JUnit assertions with AssertJ equivalents or replace `assertThrows` with `assertThatThrownBy`.
- Do not add direct Hamcrest assertions. Hamcrest remains as compile support for Spring's matcher-typed MockMvc signatures. Use `MVCTestBase` helpers such as `viewNameContains`, `modelAttributeContains`, `modelAttributeDoesNotContain`, and `headerContains`.
- Keep `MockitoExtension`'s strict stubs. An unused stub means the setup is wrong; delete it or move it into the test that uses it. Do not use Mockito `lenient()`, `@MockitoSettings(strictness = LENIENT)`, or `@Mock(strictness = LENIENT)`. When you remove a stub that let a path through, prove the short-circuit with `verifyNoInteractions` or `never()`; an unstubbed mock returns `false` and can hide a deleted guard.
</important>

<important if="changing user-visible behavior">

- Put backend user-facing text in `src/main/resources/bundles/` or `ApplicationResources.properties`; resolve through the injected message source. Exempt logs and internal messages.
- Frontend English catalogs live in `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/`; use semantic keys. Follow `DevDocs/DeveloperNotes/i18n.md`.
- New frontend text: use `t()` with literal `defaultValue`, run `pnpm run i18n:extract --sync-primary`, review catalogs, remove `defaultValue`, then run `pnpm run i18n:types`, `pnpm run i18n:lint`, and `pnpm run tsc`. Never use `--sync-all`.
- Wrap raw JSX text in `t()` before extraction. Use ICU syntax for interpolation and plurals.
</important>

<important if="changing schema or persistence">

Add Liquibase changesets under `src/main/resources/sqlUpdates/`; never change an existing schema through baseline edits. Follow `DatabaseChangeGuidelines.md`. Default to `context="run"`; use `dev-test` and `cloud` only for environment-specific data. Prefer supported soft deletion.
</important>

<important if="adding Java or changing its errors, resources, or entities">

- Use try-with-resources for streams and files.
- Log caught exceptions at WARN or ERROR. Never log sensitive data or leave empty catch blocks.
- Add Javadoc to service interface methods and non-trivial entity methods.
</important>

<important if="finishing code or configuration changes">

Run focused tests. Frontend changes require `pnpm tsc`, focused tests for behavior changes, and `pnpm lint` for linted or formatted code. Run the full suite only when broader verification is warranted.
</important>

<important if="finishing a coding round">

Remove duplicate tests and comments that restate code. Keep a minimal check for non-trivial logic and coverage or comments required for security, accessibility, data safety, constraints, or explicit requirements.
</important>

<important if="verifying runtime behavior">

Use both MCP tools against this worktree's Docker Dev Stack:

- Playwright `mcp__playwright__browser_*` for repeatable flows and accessibility snapshots.
- Chrome DevTools `mcp__chrome_devtools__*` for console/network errors, layout/reflow, performance traces, and Lighthouse.

Cover happy paths and likely regressions, including accessibility, failed requests, layout shifts, responsiveness, and performance. Report unavailable tools; do not claim full browser verification without both. Fixture and settings changes are allowed in this throwaway stack.
</important>

<important if="running RSpace locally">

For Jetty setup, see `DevDocs/DeveloperNotes/GettingStarted/GettingStarted.md`. For Docker, read `docker/dev/README.md` and `rspace-dev-stack` before using `docker/dev/rspace-dev`. It runs MariaDB, JVM, and Node per worktree. `down` is reversible; confirm before `nuke` deletes local data.
</important>

<important if="using or creating skills, or consulting workflows">

Read matching `.agents/skills/` playbooks. Keep skills concise; put details in sibling `REFERENCE.md` files.

Useful references:

- Setup and workflows: `DevDocs/DeveloperNotes/GettingStarted`
- Transactions: `DevDocs/DeveloperNotes/Transactions.md`
- Security: `DevDocs/DeveloperNotes/SecurityAndPermissions.md`
- Logging: `DevDocs/DeveloperNotes/Logging.md`
- CI: `.github/workflows/lint-and-test.yml` and `Jenkinsfile`
</important>
