# Spring 6 / Hibernate 6 Upgrade — Migration Tracker

**Ticket:** RSDEV-444
**Branch (all projects):** `rsdev-444-upgrade-to-spring-6`
**Last review:** 2026-05-14 (per-project change summaries below)
**Last status sweep:** 2026-05-19 (sections below)
**Last merge sweep:** 2026-05-19 — all 32 projects now current with `origin/main` (or `upstream/main` for rspace-web). The two previously-blocked merges (`rspace-core-model`, `rspace-web`) have landed.
**PARKED 2026-06-22 (RSDEV-444):** work paused pending the Tomcat 10 customer-deployment rollout (higher-priority work has the runway). Two staleness notes for whoever resumes: (1) the "0 behind main" status below is out of date. As of 2026-06-22 the rspace-web branch is **37 behind / 90 ahead** of `upstream/main`; the other 31 repos were not re-checked and have likely drifted too, so a re-merge is owed. (2) the "Latest update 2026-06-10" line below stops at the merge fixups (`1fa02048c`); the review-fix commits M3 (`9a12b89ee`), M13, B1a (`5c0040ff5`), M19 byte-buddy, and the M1 attempt-then-revert (current HEAD `3309c75d5`) all landed 2026-06-10 *after* it. See `spring-6-docs/README.md` on the branch for the full handover.

**Latest update:** 2026-06-10 — re-merge sweep completed and pushed: all 32 projects are again 0 behind their `main` (or `upstream/main` for rspace-web). `rspace-core-model` merged `origin/main` (RSDEV-1125 offline-model removal; merge `b89d7b4`, accepted main's deletions). `rspace-web` merged `upstream/main` (9 commits incl. MUI v9, DMP Assistant RSDEV-1102, offline/Word-replace removal RSDEV-1125/1136; merge `febe109a4`) with post-merge fixups in `1fa02048c`: DMP Assistant javax→jakarta + `HttpStatusCode.getReasonPhrase()` removal, 4 test imports, `/offline/*` dropped from `DispatcherServletInitializer`. Both branches compile (main + test) and the touched unit tests pass (35/35). Earlier 2026-06-05: two confirmed search regressions fixed on the `rspace-web` branch (review findings M11/M12, commit `b8886a625`): inventory attachment-filename search restored (`FieldNames.FILES_FIELD_DATA` added and wired into the inventory search, after the HS6 embed split moved filenames off `fields.fieldData`) and the explicit owner search now covers comments via a shared `ownerUsernameMatch` helper. Guarded by two new `FullTextSearcherTest` tests. Earlier on 2026-06-05: B6 CI blocker resolved on the `rspace-core-model` branch (`HibernateSearchTest` fixed for Hibernate Search 7, commit `20a54d4`; see the consolidated review `investigations/RSDEV-444-spring6-review.md`). Note: `origin/main` has advanced since the 2026-05-19 sweep — `rspace-core-model` is now 4 behind / 27 ahead (re-merge of the 4 new `main` commits still owed; other projects not re-swept).

> **Compliance reminder:** Anyone working on this migration must follow the AI and Third Party code policy in Drata before introducing or upgrading dependencies.

---

## Readiness Status (2026-05-19)

32 projects carry the `rsdev-444-upgrade-to-spring-6` branch. As of the 2026-05-19 sweep all branches were 0 commits behind their respective `main` (or `upstream/main` for `rspace-web`). **Update 2026-06-05:** `origin/main` has since advanced for `rspace-core-model` (now 4 behind / 27 ahead); a re-merge of those 4 commits is owed. Other projects not re-checked since 2026-05-19.

### One-line summary per project

| Project | Branch version | main version | Behind / Ahead of main | Dirty? | Notes |
|---|---|---|---|---|---|
| rspace-parent | 3.0.0 | 2.1.6 | 0 / 14 | – | Foundational pom — merge first. |
| rspace-core-util | 2.0.0 | 1.1.0 | 0 / 3 | DS_Store only | |
| rspace-core-model | 3.0.0 | 2.21.2 | 0 / 31 *(re-merged + pushed 2026-06-10, merge `b89d7b4`)* | stray `SPRING6-MIGRATION-REVIEW.md` + `pom.xml.versionsBackup` | Merged `origin/main` 2026-05-19 (commit `d0490e4`); inventory model migration to Jakarta + Hibernate Search 6 in `18893ae`. Pom version set to `3.0.0` (matches the value it carried before the SNAPSHOT specifier was applied in `903862d`). B6 CI blocker fixed 2026-06-05 (`20a54d4`): test analysis configurer + HS7 search-test field names. `origin/main` advanced 4 commits since the last sweep — re-merge owed. |
| rspace-test-util | 3.0.0 | 2.0.4 | 0 / 3 | DS_Store only | Test class renames are downstream-breaking. |
| rspace-audit | 1.0.0 | 0.14.3 | 0 / 3 | DS_Store only | |
| rspace-rest-api-utils | 2.0.0 | 1.3.4 | 0 / 3 | DS_Store only | |
| rspace-repository-spi | 2.0.0 | 1.1.2 | 0 / 5 | – | |
| rspace-document-conversion-spi | 2.0.0 | 1.0.1 | 0 / 2 | – | |
| rspace-license-server-entities | 1.0.0 | 0.8.2 | 0 / 4 | – | |
| rspace-evernote-parser | 2.0.0 | 1.1.3 | 0 / 3 | – | |
| rspace-external-auth | 2.0.1 | 1.1.3 | 0 / 5 | – | |
| rspace-egnyte | 2.0.0 | 1.0.2 | 0 / 3 | – | |
| rspace-dataverse-adapter | 4.0.0 | 3.0.2 | 0 / 9 | DS_Store only | |
| rspace-digital-commons-data-adapter | 1.0.0 | 0.0.6 | 0 / 7 | – | Bumped 2026-05-18 (`3a4496f` then corrected by `879f953`). Pom history only ever shipped 0.0.x, so first major is 1.0.0 (not 2.0.0 as the earlier changelog/review claimed). |
| rspace-figshare-adapter | 2.0.0 | 1.1.2 | 0 / 3 | DS_Store only | |
| rspace-dryad-adapter | 2.0.0 | 1.0.4 | 0 / 4 | DS_Store only | |
| rspace-zenodo-adapter | 1.0.0 | 0.3.5 | 0 / 4 | – | |
| rspace-snapgene-adapter | 2.0.0 | 1.0.3 | 0 / 2 | DS_Store only | |
| argos-java-client | 1.0.0 | 0.1.1 | 0 / 2 | – | |
| datacite-java-client | 1.0.0 | 0.2.0 | 0 / 2 | – | |
| dataverse-client-java | v3.0.0 | v2.0.1 | 0 / 17 | `.factorypath` only | Gradle project. |
| digital-commons-data-java-client | 1.0.0 | – | 0 / 1 | – | |
| dmptool-java-client | 1.0.0 | 0.4.1 | 0 / 3 | – | |
| dryad-java-client | 1.0.0 | 0.2.2 | 0 / 3 | – | |
| fieldmark-java-client | 4.0.0 | 3.2.0 | 0 / 6 | – | |
| figshare-client-java | 1.0.0 | 0.7.1 | 0 / 3 | – | |
| galaxy-java-client | 2.0.0 | 1.1.0 | 0 / 4 | – | |
| protocols-java-client | 2.0.0 | 1.2.0 | 0 / 2 | – | |
| raid-java-client | 4.0.0 | 3.1.0 | 0 / 3 | DS_Store only | |
| snapgene-java-client | 2.0.0 | 1.0.3 | 0 / 5 | DS_Store only | |
| zenodo-java-client | 1.0.0 | 0.2.2 | 0 / 4 | – | |
| **rspace-web** | 3.0.0 | 2.23.0-SNAPSHOT | **0 / 89** *(vs upstream/main; re-merged + pushed 2026-06-10, merge `febe109a4` + fixups `1fa02048c`)* | clean (untracked tooling dirs only) | WIP committed (`4510f3996` programmatic DispatcherServlet); merged `upstream/main` 2026-05-19 (`e05231be0`). Review blockers B1–B6 addressed; search regressions M11/M12 fixed 2026-06-05 (`b8886a625`). See `investigations/RSDEV-444-spring6-review.md` for the open list (G1, dep pinning + offline build, B1/B5 populated-DB verification, doc sweep). |

### Major-version bump audit

User requirement: every in-scope project should carry a major version bump on the branch.

- **32 / 32 projects** — major bump confirmed on `rsdev-444-upgrade-to-spring-6` vs `origin/main` (or `upstream/main` for rspace-web).
- ✅ **rspace-core-model** — pom version set to `3.0.0` (2026-05-19), matching the value it carried before the SNAPSHOT specifier was applied.
- ✅ **rspace-digital-commons-data-adapter** — pom on the branch is `1.0.0` (verified 2026-05-19). Bump landed via `3a4496f` (initial, wrongly 2.0.0) then `879f953` (corrected to 1.0.0).

### rspace-web — WIP landed (2026-05-19)

The programmatic-`DispatcherServlet` work is now committed (`4510f3996 register DispatcherServlet programmatically for dynamic multipart limits`) and `upstream/main` has been merged in (`e05231be0`); follow-up commits `332515361` (versions) and `ef898232e` (jakarta merge fixes) tidy the result. Branch is 0 behind / 50 ahead of `upstream/main`.

Two untracked review docs (`spring6-migration-review.md`, `spring6-migration-review-guide.md`) — leave untracked or delete; they don't belong in the PR.

### Main-merge results (2026-05-18)

Fetched `origin` for all 30 non-rspace-web projects, then attempted `git merge --no-edit origin/main` on each `rsdev-444-upgrade-to-spring-6` branch.

- **27 projects:** 0 commits behind `origin/main` — no-op.
- **rspace-audit:** 4 commits ahead on main (CLA workflow + RSDEV-1062 audit-domain addition). Merge conflict in `pom.xml` only (rsdev-444 `1.0.0` vs main `0.14.4`, and the `rspace-core-util` dependency specifier). Resolved by keeping HEAD (rsdev-444 major bump + jitpack SNAPSHOT specifier). RSDEV-1062 code change to `AuditDomain.java` auto-merged. Merge commit `6e72404`.
- **rspace-core-util:** 2 commits ahead on main (RSDEV-1062 global prefixes for Instrument / InstrumentTemplate). Conflicts in `pom.xml` (version) and `Changelog.md`. Resolved by keeping rsdev-444 `2.0.0` in pom; preserved the new `1.2.0 2026-05-15` changelog entry directly beneath the `2.0.0` heading. Merge commit `46a3630`.
- ✅ **rspace-core-model:** previously-aborted merge resolved 2026-05-19 (merge commit `d0490e4`). Inventory model migrated to Jakarta + Hibernate Search 6 in follow-up `18893ae` (replaces the deleted `SampleField` lineage with the new `InventoryEntityField` hierarchy from RSDEV-1062).
- ✅ **rspace-web:** WIP committed (`4510f3996`) and `upstream/main` merged in (`e05231be0`) 2026-05-19.

Per-project mechanic used:
- `git -C <project> fetch origin`
- `GIT_EDITOR=true git -C <project> merge --no-edit origin/main` (preserves history; merge commits)

When ready to do `rspace-web`: `git -C rspace-web fetch upstream && git -C rspace-web merge upstream/main` (commit the in-flight WIP first).

### Pre-PR checklist (live)

- [x] Fix artifact versions in `galaxy-java-client` (2.0.0), `snapgene-java-client` (2.0.0), `rspace-test-util` (3.0.0)
- [x] Fix changelog/pom version mismatches (`rspace-dataverse-adapter` → 4.0.0, `rspace-external-auth` → 2.0.1)
- [x] `rspace-evernote-parser`: removed hardcoded JAXB version, inherits from parent (4.0.0)
- [x] `rspace-license-server-entities`: migrated commons-lang → commons-lang3
- [x] `rspace-parent`: corrected changelog Shiro version to 2.1.0
- [x] `snapgene-java-client`: removed hardcoded JUnit versions, inherits from parent
- [x] `fieldmark`, `raid`, `dryad-adapter`, `zenodo-adapter`: removed hardcoded jackson-annotations versions
- [x] **rspace-core-model**: pom artifact version set to `3.0.0` (2026-05-19). Parent pom + inter-project dependency specifiers still on `rsdev-444-upgrade-to-spring-6-SNAPSHOT` and will be pinned to release versions in the final tag sweep.
- [x] **rspace-digital-commons-data-adapter**: bumped to `1.0.0` (first major; pom history only ever shipped 0.0.x). Commits `3a4496f` (initial, wrongly 2.0.0) then `879f953` (corrected to 1.0.0, changelog updated)
- [x] **rspace-web**: commit `BaseConfig.java` / `web.xml` / `DispatcherServletInitializer.java` WIP (`4510f3996`)
- [x] Fetch latest origins for the 30 non-rspace-web projects (2026-05-18)
- [x] Merge `origin/main` into the 30 non-rspace-web branches (27 no-op, 2 with trivial pom/changelog conflicts resolved, 1 originally aborted — now resolved 2026-05-19)
- [x] **rspace-core-model**: resolve substantive merge conflicts with `origin/main` (merge commit `d0490e4`, follow-up `18893ae`)
- [x] **rspace-web**: fetch `upstream/main` and merge after WIP is committed (`e05231be0`)
- [ ] After all dependencies are merged, update jitpack specifiers to release versions
- [x] Verify all downstream consumers updated for `JakartaValidatorTest` rename (2026-05-19 — in-tree sweep of `/Users/fraser/dev/` clean: 3 classes in rspace-core-model, 11 in rspace-web, all on the new name).

---

## Per-project change summary (snapshot from 2026-05-14 review)

**Scope of this section:** all projects with `rsdev-444-upgrade-to-spring-6` branch, excluding `rspace-web` and `rspace-core-model`

---

## Code Review Concerns

### Version Issues — FIXED

Note: jitpack-style versions (`rsdev-444-upgrade-to-spring-6-SNAPSHOT`, branch-name, or branch-name-commit-hash) are expected for inter-project dependencies while branches are unmerged. These will resolve to release versions once each project is merged and tagged.

1. ~~**galaxy-java-client** — Artifact version set to `2.0.0`~~ FIXED
2. ~~**snapgene-java-client** — Artifact version set to `2.0.0`~~ FIXED
3. ~~**rspace-test-util** — Artifact version set to `3.0.0`~~ FIXED
4. ~~**rspace-dataverse-adapter** — Changelog updated to `4.0.0`~~ FIXED
5. ~~**rspace-external-auth** — Changelog updated to `2.0.1`~~ FIXED

### Dependency Concerns

8. ~~**rspace-evernote-parser** — Removed hardcoded `jakarta.xml.bind-api` version, now inherits `4.0.0` from parent~~ FIXED

9. ~~**fieldmark-java-client, raid-java-client, rspace-dryad-adapter, rspace-zenodo-adapter** — Removed hardcoded `jackson-annotations` version, now inherits from parent~~ FIXED

10. ~~**snapgene-java-client** — Removed hardcoded `junit-jupiter` versions, now inherits from parent~~ FIXED

11. ~~**rspace-license-server-entities** — Migrated from `commons-lang:2.6` to `commons-lang3`, updated imports in `CustomerContact.java` and `CustomerContactTest.java`~~ FIXED

12. ~~**rspace-parent** — `jaxb-runtime` scope changed from `<scope>test</scope>` to default (compile).~~ FIXED 2026-05-19. Investigation confirmed the change was intentional (commit `1177c97`: EhCache 3.x needs JAXB at runtime to parse `ehcache.xml`; EhCache 3 lives only in `rspace-web`). Narrowed parent's dependencyManagement scope to `runtime` (was: unspecified/compile). Removed the explicit `<scope>compile</scope>` in `rspace-core-model` so it inherits `runtime`. `rspace-evernote-parser`'s `test` override is unchanged (correct). Compile classpath of consumers shrinks; runtime behaviour unchanged.

13. ~~**rspace-parent** — Changelog Shiro version corrected to `2.1.0`~~ FIXED

14. ~~**rspace-dataverse-adapter** — Adds explicit `commons-compress:1.28.0` at compile scope.~~ FIXED 2026-05-19 (`78088df`). Investigation showed nothing in the adapter, dataverse-client-java, or rspace-core-util's archive code path imports `org.apache.commons.compress`; archive handling uses `java.util.zip`. Added speculatively in `bd9db1d` ("add missing dep"). Removed; `mvn clean verify` green (12 tests, 2 expected skips); `mvn dependency:analyze` doesn't flag it as used-undeclared.

### Code Change Concerns

15. **rspace-audit** — `LogLineContentProviderImpl.java`: SpEL expression changed from `#logFile.` to `#p1.`. **DEFERRED** 2026-05-19. The `#p1` form is correct on Spring 6 and works today; the fragility (silent cache-key drift on signature change) is real but the method is stable internal infrastructure. The "proper" fix — adding `-parameters` to `rspace-parent`'s compiler config and reverting to `#logFile.` — is cross-cutting (changes bytecode for every child, may surface in reflective consumers like MyBatis/OpenAPI/Jackson) and shouldn't ride along with this migration. Follow-up ticket: file post-merge as "enable `-parameters` in rspace-parent, audit reflective consumers, revert #p1 in rspace-audit".

16. **rspace-test-util** — `JavaxValidatorTest` renamed to `JakartaValidatorTest` and `JavaxValidatorTestJU5` renamed to `JakartaValidatorTestJU5`. This is a **breaking change** for any downstream project that extends these classes. Ensure all consumers (especially `rspace-web` and `rspace-core-model`) have been updated to use the new names.

17. ~~**figshare-client-java** — `FigshareError.status` type changed from `HttpStatus` to `HttpStatusCode`.~~ RESOLVED 2026-05-19. Grep of `rspace-web` and `rspace-figshare-adapter` source: zero references to `FigshareError`. The type is internal to figshare-client-java; no downstream callers exist.

18. ~~**rspace-egnyte** — `EgnyteResult.getStatusCode()` and `ResponseError.httpStatus` changed from `HttpStatus` to `HttpStatusCode`.~~ RESOLVED 2026-05-19. Three downstream sites in `rspace-web` audited: `EgnyteFileStoreAdapter` uses `.getStatusCode().value()` (fine on `HttpStatusCode`); `EgnyteFileSearcher` only uses the type, no status access; `EgnyteFileSearcherTest` passes `HttpStatus.OK` to the `HttpStatusCode` parameter (`HttpStatus` implements `HttpStatusCode`, assignable). Internal `rspace-egnyte` usages also compatible. No `.name()`, `.series()`, enum switch, or direct `HttpStatus` assignment patterns present.

### Minor / Cosmetic

19. **Several projects** — Whitespace-only or EOL-at-EOF changes mixed into functional diffs. Not a problem, just noise.

20. **rspace-zenodo-adapter** — Changelog fixes an existing formatting issue (`- ## 0.3.4` → `## 0.3.4`). Good cleanup.

---

## Per-Project PR Summaries (refreshed 2026-05-19)

### rspace-parent
**Changes:** Version bump 2.1.6 → 3.0.0. Spring 5.3.39 → 6.2.15, Hibernate ORM 5.6.5 → 6.4.4, Hibernate Search 5.11.10 → 7.0.1, Hibernate Validator 5.3.0 → 8.0.1, Shiro 1.13.0 → 2.1.0, Servlet API 3.1.0 → 6.1.0, Lombok 1.18.22 → 1.18.42, JAXB 2.3.1 → 4.0.0, Jackson 2.20.0. All `javax.*` managed dependencies remapped to `jakarta.*` (`javax.xml.bind:jaxb-api` → `jakarta.xml.bind:jakarta.xml.bind-api`, `javax.validation` → `jakarta.validation`, `javax-mail-api` → `jakarta.mail-api`). Added `commons-lang3` 3.18.0 and `commons-text` 1.14.0 to dependency management. `jaxb-runtime` managed scope narrowed to `runtime` (required by EhCache 3 in rspace-web; was previously `test`). Changelog Shiro entry corrected to 2.1.0.

**PR message:**
> Upgrade rspace-parent to 3.0.0 for Spring 6 / Jakarta EE migration (RSDEV-444).
>
> Bumps Spring Framework 5 → 6, Hibernate ORM 5 → 6, Hibernate Search 5 → 7, Shiro 1.13 → 2.1, Servlet API 3.1 → 6.1, and JAXB 2.3 → 4.0. Remaps all managed `javax.*` coordinates to `jakarta.*`. `jaxb-runtime` is now `runtime` scope so the impl no longer leaks onto consumers' compile classpath.

**PR:** https://github.com/rspace-os/rspace-parent/pull/7

---

### rspace-core-util
**Changes:** Version bump 1.1.0 → 2.0.0. `javax.servlet` → `jakarta.servlet` (in `RequestUtil`, `ResponseUtil` and their tests), `javax.xml.bind` → `jakarta.xml.bind` (in `XMLReadWriteUtils` and test fixtures `TestXMLRootObject`/`TestXMLNonRootObject`), and `org.apache.shiro.util.ByteSource` → `org.apache.shiro.lang.util.ByteSource` (Shiro 2.x relocation, in `SecureStringUtils`). Parent pom bumped to the Spring 6 SNAPSHOT. Merge from `origin/main` 2026-05-18 brought in RSDEV-1062 instrument-prefix changes (no conflicts on the Jakarta migration code).

**PR message:**
> Upgrade rspace-core-util to 2.0.0 for Spring 6 / Jakarta namespace migration (RSDEV-444).
>
> Migrates servlet and JAXB imports from `javax.*` to `jakarta.*` and switches Shiro's `ByteSource` to its 2.x relocated package.

**PR:** https://github.com/rspace-os/rspace-core-util/pull/8

---

### rspace-test-util
**Changes:** Version bump 2.0.4 → 3.0.0. `javax.validation` → `jakarta.validation`. Renames `JavaxValidatorTest` → `JakartaValidatorTest` and `JavaxValidatorTestJU5` → `JakartaValidatorTestJU5` (breaking for downstream subclasses; all known consumers in rspace-core-model and rspace-web have been swept onto the new names). Parent pom bumped to the Spring 6 SNAPSHOT.

**PR message:**
> Upgrade rspace-test-util to 3.0.0 for Spring 6 / Jakarta namespace migration (RSDEV-444).
>
> Migrates validation imports from `javax.*` to `jakarta.*`. Renames `JavaxValidatorTest`/`JavaxValidatorTestJU5` to `JakartaValidator*`; all downstream consumers (rspace-core-model, rspace-web) have been updated.

**PR:** https://github.com/rspace-os/rspace-test-util/pull/3

---

### rspace-audit
**Changes:** Version bump 0.14.3 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT and `rspace-core-util` dependency switched to the feature-branch SNAPSHOT. Removes hardcoded `jackson-*` versions (now inherits from parent BOM). `LogLineContentProviderImpl` SpEL cache-key expression changed from `#logFile.` to `#p1.` to work without `-parameters` compilation under Spring 6. Merge from `origin/main` 2026-05-18 brought in RSDEV-1062 `AuditDomain` additions (auto-merged) with pom-only conflict resolved in favour of the branch's major bump.

**PR message:**
> Upgrade rspace-audit to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Bumps parent pom and switches the SpEL cache key in `LogLineContentProviderImpl` to a positional `#p1` reference so the expression resolves without `-parameters` under Spring 6.

**PR:** https://github.com/rspace-os/rspace-audit/pull/5

---

### rspace-rest-api-utils
**Changes:** Version bump 1.3.4 → 2.0.0. `javax.validation` / `javax.servlet` → `jakarta.*`. `RestControllerAdvice` updated for Spring 6: overridden method signatures use `HttpStatusCode` instead of `HttpStatus`; `handleBindException` converted from override to `@ExceptionHandler` (the override no longer exists on `ResponseEntityExceptionHandler` in Spring 6.2). `RestUtil.isError()` simplified to delegate to `HttpStatusCode.isError()`. `LoggingResponseErrorHandler` gains the new `handleError(URI, HttpMethod, ClientHttpResponse)` override required by Spring 6. Test for `UnknownHttpStatusCodeException` updated from status `1000` to `700` (Spring 6 rejects 4-digit codes as invalid before reaching the unknown-status path).

**PR message:**
> Upgrade rspace-rest-api-utils to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates `javax.*` to `jakarta.*` and adapts `RestControllerAdvice`/`LoggingResponseErrorHandler` to Spring 6 API changes: `HttpStatus` → `HttpStatusCode` on overrides, `handleBindException` re-implemented via `@ExceptionHandler` (no longer overridable), and the new `handleError(URI, HttpMethod, ...)` hook implemented.

**PR:** https://github.com/rspace-os/rspace-rest-api-utils/pull/4

---

### rspace-repository-spi
**Changes:** Version bump 1.1.2 → 2.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. Removes hardcoded `jackson-*` versions (now inherits from parent BOM) and the empty `<properties>` block. No source changes.

**PR message:**
> Upgrade rspace-repository-spi to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: bumps parent and drops redundant Jackson version pins so they inherit from the parent BOM.

**PR:** https://github.com/rspace-os/rspace-repository-spi/pull/3

---

### rspace-document-conversion-spi
**Changes:** Version bump 1.0.1 → 2.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade rspace-document-conversion-spi to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only parent bump; no source changes required.

**PR:** https://github.com/rspace-os/rspace-document-conversion-spi/pull/2

---

### rspace-license-server-entities
**Changes:** Version bump 0.8.2 → 1.0.0. Full `javax.*` → `jakarta.*` migration across persistence, validation, and EL imports in `CustomerContact`, `CustomerInfo`, `License`, `ServerInfo` and their tests. Replaces Hibernate `@Length` with Jakarta `@Size`; `@Email` / `@NotEmpty` moved from `org.hibernate.validator.constraints` to `jakarta.validation.constraints`. Replaces legacy `commons-lang:2.6` with `commons-lang3` and updates imports accordingly. Parent pom bumped to the Spring 6 SNAPSHOT; hardcoded Jackson versions removed.

**PR message:**
> Upgrade rspace-license-server-entities to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates JPA, validation, and EL imports from `javax.*` to `jakarta.*`, swaps Hibernate Validator's `@Length`/`@Email`/`@NotEmpty` for the Jakarta-namespaced equivalents, and replaces `commons-lang` 2.6 with `commons-lang3`.

**PR:** https://github.com/rspace-os/rspace-license-server-entities/pull/1

---

### rspace-evernote-parser
**Changes:** Version bump 1.1.3 → 2.0.0. `javax.xml.bind` → `jakarta.xml.bind` across all JAXB-annotated model classes (`EnExport`, `EvernoteParser`, `NodeAttributes`, `Note`, `Resource`, `ResourceAttributes`). Parent pom bumped to the Spring 6 SNAPSHOT; hardcoded `jakarta.xml.bind-api` version removed (now inherits 4.0.0 from parent).

**PR message:**
> Upgrade rspace-evernote-parser to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates JAXB imports from `javax.xml.bind` to `jakarta.xml.bind` across all model classes and inherits the API version from the parent BOM.

**PR:** https://github.com/rspace-os/rspace-evernote-parser/pull/3

---

### rspace-external-auth
**Changes:** Version bump 1.1.3 → 2.0.1. `google-api-client` 1.35.2 → 2.2.0 (required for Jakarta-compatible HTTP transport) and added `google-api-client-gson` 2.2.0. Parent pom bumped to the Spring 6 SNAPSHOT; hardcoded `jackson-*` versions removed. No source changes.

**PR message:**
> Upgrade rspace-external-auth to 2.0.1 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Upgrades `google-api-client` 1.35.2 → 2.2.0 (Jakarta-compatible) and adds the `google-api-client-gson` module. Pom-only; no source changes.

**PR:** https://github.com/rspace-os/rspace-external-auth/pull/3

---

### rspace-egnyte
**Changes:** Version bump 1.0.2 → 2.0.0. `javax.validation` → `jakarta.validation` in `SearchRequest` and `SimpleFileUploadRequest`; `javax.el` → `jakarta.el` in test setup. `EgnyteResult.getStatusCode()` and `ResponseError.httpStatus` switched from `HttpStatus` to `HttpStatusCode` (Spring 6 type). Downstream callers in rspace-web have been audited as compatible (`HttpStatus` implements `HttpStatusCode`).

**PR message:**
> Upgrade rspace-egnyte to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates validation and EL imports from `javax.*` to `jakarta.*` and switches `EgnyteResult`/`ResponseError` status fields from `HttpStatus` to `HttpStatusCode`. Downstream callers in rspace-web are source-compatible.

**PR:** https://github.com/rspace-os/rspace-egnyte/pull/3

---

### rspace-dataverse-adapter
**Changes:** Version bump 3.0.2 → 4.0.0. `javax.annotation` → `jakarta.annotation` in `DataverseRepoConfigurer`. `dataverse-client-java` coordinates moved from `com.github.iqss` to `com.github.rspace-os` to consume the Spring 6 fork. Parent pom and inter-project deps bumped to the Spring 6 SNAPSHOT. Speculatively-added `commons-compress:1.28.0` removed 2026-05-19 (`78088df`) — `mvn dependency:analyze` confirmed unused, archive handling uses `java.util.zip`.

**PR message:**
> Upgrade rspace-dataverse-adapter to 4.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates `javax.annotation` to `jakarta.annotation` and re-points `dataverse-client-java` at the `com.github.rspace-os` Spring 6 fork. No behavioural change.

**PR:** https://github.com/rspace-os/rspace-dataverse-adapter/pull/16

---

### rspace-digital-commons-data-adapter
**Changes:** Version bump 0.0.6 → 1.0.0 (first major; pom history only ever shipped 0.0.x). `javax.servlet-api` → `jakarta.servlet-api`. Obsolete `org.glassfish:javax.servlet:3.0` test dependency removed. Parent pom bumped to the Spring 6 SNAPSHOT; hardcoded `jackson-*` versions removed. `rspace-repository-spi` and `digital-commons-data-java-client` dependency specifiers switched to the feature-branch SNAPSHOT. No source changes.

**PR message:**
> Upgrade rspace-digital-commons-data-adapter to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> First major bump (history only shipped 0.0.x). Pom-only: migrates servlet API from `javax.*` to `jakarta.*`, drops the obsolete Glassfish servlet test dependency, and inherits Jackson versions from the parent BOM.

**PR:** https://github.com/rspace-os/rspace-digital-commons-data-adapter/pull/7

---

### rspace-figshare-adapter
**Changes:** Version bump 1.1.2 → 2.0.0. `javax.servlet-api` → `jakarta.servlet-api`; obsolete `org.glassfish:javax.servlet` test dependency removed. Parent pom and inter-project deps bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade rspace-figshare-adapter to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: migrates servlet API to Jakarta and drops the obsolete Glassfish servlet test dependency.

**PR:** https://github.com/rspace-os/rspace-figshare-adapter/pull/6

---

### rspace-dryad-adapter
**Changes:** Version bump 1.0.4 → 2.0.0. `javax.servlet-api` → `jakarta.servlet-api`; obsolete `org.glassfish:javax.servlet` test dependency removed. Parent pom and inter-project deps bumped to the Spring 6 SNAPSHOT. Hardcoded `jackson-annotations` version removed (now inherits from parent BOM). No source changes.

**PR message:**
> Upgrade rspace-dryad-adapter to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: migrates servlet API to Jakarta, drops the obsolete Glassfish servlet test dependency, and inherits Jackson versions from the parent BOM.

**PR:** https://github.com/rspace-os/rspace-dryad-adapter/pull/5

---

### rspace-zenodo-adapter
**Changes:** Version bump 0.3.5 → 1.0.0. `javax.servlet-api` → `jakarta.servlet-api`; obsolete `org.glassfish:javax.servlet` test dependency removed. Parent pom and inter-project deps bumped to the Spring 6 SNAPSHOT. Hardcoded `jackson-annotations` version removed (inherits from parent BOM). Changelog formatting cleanup (`- ## 0.3.4` → `## 0.3.4`). No source changes.

**PR message:**
> Upgrade rspace-zenodo-adapter to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: migrates servlet API to Jakarta, drops the obsolete Glassfish servlet test dependency, and inherits Jackson versions from the parent BOM.

**PR:** https://github.com/rspace-os/rspace-zenodo-adapter/pull/6

---

### rspace-snapgene-adapter
**Changes:** Version bump 1.0.3 → 2.0.0. `javax.annotation` → `jakarta.annotation` in `SnapgeneWSClientImpl`. Parent pom and inter-project deps bumped to the Spring 6 SNAPSHOT.

**PR message:**
> Upgrade rspace-snapgene-adapter to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Migrates `javax.annotation` to `jakarta.annotation` and bumps parent / inter-project dependencies.

**PR:** https://github.com/rspace-os/rspace-snapgene-adapter/pull/4

---

### dataverse-client-java
**Changes:** Version bump v2.0.1 → v3.0.0. Gradle project — `build.gradle` switched to Spring 6, Java 17, Lombok updated. Group ID changed from `com.github.iqss` to `com.github.rspace-os` to avoid clashing version numbers with upstream. Removes the local `RestUtil` utility class in favour of Spring 6's built-in `HttpStatusCode.isError()` (inlined into `LoggingResponseErrorHandler`). Replaces deprecated `getStatusCodeValue()` with `getStatusCode().value()` in `AbstractOpsImplV1` / `DataverseOperationsImplV1`. Adds assertions to a test that previously only printed JSON.

**PR message:**
> Upgrade dataverse-client-java to v3.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Bumps the Gradle build to Spring 6 / Java 17 and re-publishes under `com.github.rspace-os` to avoid clashing with upstream. Removes the local `RestUtil` shim in favour of `HttpStatusCode.isError()` and switches off the deprecated `getStatusCodeValue()` API.

**PR:** https://github.com/rspace-os/dataverse-client-java/pull/3

---

### datacite-java-client
**Changes:** Version bump 0.2.0 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. `DataCiteClientImpl` replaces deprecated Spring `Base64Utils` with `java.util.Base64`; removes the now-unused `@SneakyThrows` and `URISyntaxException` import.

**PR message:**
> Upgrade datacite-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Bumps parent pom and replaces deprecated Spring `Base64Utils` with `java.util.Base64`.

**PR:** https://github.com/rspace-os/datacite-java-client/pull/5

---

### figshare-client-java
**Changes:** Version bump 0.7.1 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. `FigshareTemplate` switches the error-logging call from `HttpStatus.name()` to `HttpStatusCode.toString()`; `FigshareError.status` field type changed from `HttpStatus` to `HttpStatusCode`. Type is internal — no downstream consumers in rspace-web or rspace-figshare-adapter.

**PR message:**
> Upgrade figshare-client-java to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Bumps parent pom and switches `FigshareError.status` from `HttpStatus` to `HttpStatusCode` for Spring 6 compatibility. No downstream callers of the changed type.

**PR:** https://github.com/rspace-os/figshare-client-java/pull/9

---

### digital-commons-data-java-client
**Changes:** First versioned release: 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. `DigitalCommonsDataClientImpl.testConnection()` now branches on the numeric status code (`404`) rather than matching the exception message string, since Spring 6 changed the message format. Test assertions relaxed from exact message match to `containsString`; `HttpStatus` → `HttpStatusCode` in mock setup.

**PR message:**
> Upgrade digital-commons-data-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Bumps parent pom and switches `testConnection()` to branch on the numeric status code instead of the Spring exception message text (whose format changed in Spring 6).

**PR:** https://github.com/rspace-os/digital-commons-data-java-client/pull/5

---

### argos-java-client
**Changes:** Version bump 0.1.1 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade argos-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only parent bump; no source changes required.

**PR:** https://github.com/rspace-os/argos-java-client/pull/4

---

### dmptool-java-client
**Changes:** Version bump 0.4.1 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade dmptool-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only parent bump; no source changes required.

**PR:** https://github.com/rspace-os/dmptool-java-client/pull/12

---

### dryad-java-client
**Changes:** Version bump 0.2.2 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. Adds newline at EOF in `pom.xml`. No source changes.

**PR message:**
> Upgrade dryad-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only parent bump; no source changes required.

**PR:** https://github.com/rspace-os/dryad-java-client/pull/3

---

### galaxy-java-client
**Changes:** Version bump 1.1.0 → 2.0.0 (artifact version corrected to 2.0.0 to match changelog). Parent pom and `rspace-core-util` dependency bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade galaxy-java-client to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: bumps parent and `rspace-core-util`; no source changes required.

**PR:** https://github.com/rspace-os/galaxy-java-client/pull/8

---

### protocols-java-client
**Changes:** Version bump 1.2.0 → 2.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. Removes the explicit Lombok dependency block (now inherited from parent). No source changes.

**PR message:**
> Upgrade protocols-java-client to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: bumps parent and drops the redundant local Lombok declaration so it inherits from the parent BOM.

**PR:** https://github.com/rspace-os/protocols-java-client/pull/6

---

### raid-java-client
**Changes:** Version bump 3.1.0 → 4.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. Hardcoded `jackson-annotations` version removed (now inherits from parent BOM). No source changes.

**PR message:**
> Upgrade raid-java-client to 4.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: bumps parent and inherits Jackson versions from the parent BOM.

**PR:** https://github.com/rspace-os/raid-java-client/pull/6

---

### fieldmark-java-client
**Changes:** Version bump 3.2.0 → 4.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. Hardcoded `jackson-annotations` version removed (inherits from parent BOM). `FieldmarkTypeExtractorFactory` adopts main's `NUMBER/INTEGER → Integer.class` after a merge from `origin/main`.

**PR message:**
> Upgrade fieldmark-java-client to 4.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only Spring 6 bump plus a merge from main that brings `FieldmarkTypeExtractorFactory` to its current `NUMBER/INTEGER → Integer.class` mapping.

**PR:** https://github.com/rspace-os/fieldmark-java-client/pull/8

---

### snapgene-java-client
**Changes:** Version bump 1.0.3 → 2.0.0 (artifact version corrected to 2.0.0 to match changelog). Parent pom and `rspace-core-util` bumped to the Spring 6 SNAPSHOT. Hardcoded JUnit Jupiter version pins removed (now inherits from parent BOM). No source changes.

**PR message:**
> Upgrade snapgene-java-client to 2.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only: bumps parent and `rspace-core-util`, and inherits JUnit versions from the parent BOM.

**PR:** https://github.com/rspace-os/snapgene-java-client/pull/4

---

### zenodo-java-client
**Changes:** Version bump 0.2.2 → 1.0.0. Parent pom bumped to the Spring 6 SNAPSHOT. No source changes.

**PR message:**
> Upgrade zenodo-java-client to 1.0.0 for Spring 6 / Jakarta migration (RSDEV-444).
>
> Pom-only parent bump; no source changes required.

**PR:** https://github.com/rspace-os/zenodo-java-client/pull/8

---

*Pre-PR checklist has moved to the **Readiness Status** section at the top of this file.*
