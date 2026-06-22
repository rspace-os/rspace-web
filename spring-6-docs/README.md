# RSDEV-444 Spring 6 / Hibernate 6 / Jakarta: PARKED

**Status: PARKED (paused), as of 2026-06-22.**
**Reason:** higher-priority work means there is not enough runway to fully test and
roll out the change, which requires a Tomcat 10 upgrade on every customer
deployment. The branch is functionally complete and reviewed, but unmerged.

**Parked branch (every repo):** `rsdev-444-upgrade-to-spring-6`
**Parked anchor tag (recommended):** `rsdev-444-parked-2026-06-22`
**Branch HEAD at parking (rspace-web):** `3309c75d5` (RSDEV-444: revert M1 - explicit tx advisors are load-bearing at runtime)

If you are picking this up cold, read this file first, then the three companion
docs snapshotted alongside it in this directory (see "Companion docs" below).

---

## 1. One-paragraph summary of the work

RSDEV-444 upgrades the whole rspace fleet (32 repos) from Spring 5.3 / Hibernate
ORM 5.6 / Hibernate Search 5 / Shiro 1.13 / Servlet 3.1 / `javax.*` to Spring 6.2
/ Hibernate ORM 6.4 / Hibernate Search 7 / Shiro 2.1 / Servlet (Jakarta EE 10) /
`jakarta.*`. Every in-scope repo carries a major version bump and the
`rsdev-444-upgrade-to-spring-6` branch. Because the `javax` to `jakarta` rename is
binary (a class either imports one or the other), this lands as a single
coordinated big-bang merge across all repos, not incremental per-project PRs. The
runtime target moves from Tomcat 9 / Jetty 11 to Tomcat 10.1.

## 2. Why it is parked, not abandoned

The code is done and has been reviewed in depth (see the review doc). The blocker
is operational: shipping it means upgrading the servlet container to Tomcat 10 on
every customer deployment, plus a live data migration (id-sequence reseed, Spring
Batch 3 to 5 schema swap) and a full search reindex on each. That rollout needs
dedicated testing and coordination time that is not available right now.

## 3. The fleet at parking time

All 32 repos carry the branch, each with a confirmed major version bump. As of the
last full sweep (2026-06-10) every branch was level with its `main` (or
`upstream/main` for rspace-web). The full per-project version table and PR-message
drafts live in `/Users/fraser/dev/spring-6-upgrade.md` (the fleet tracker),
snapshotted here as `RSDEV-444-fleet-tracker.md`.

Foundational merge order when restarting: `rspace-parent` first, then
`rspace-core-util` / `rspace-core-model` / the other libs, then the adapters and
clients, then `rspace-web` last.

## 4. IMPORTANT: branch drift since parking

The fleet tracker and the devops-handover doc both state the branches were "0
behind main as of 2026-06-10". **That is no longer true.** As of 2026-06-22,
`rspace-web`'s branch is **37 commits behind / 90 ahead** of `upstream/main`. The
other 31 repos have not been re-checked and have likely drifted too.

Several of the 37 new `upstream/main` commits interact directly with this migration
and must be handled carefully on the re-merge:

- `62cd5a1d1 Bump Java source level from 11 to 17 (#853)` - main has now done the
  Java 17 bump the branch already carried. Reduces conflict, but confirm they agree.
- `954b3ff57` + `43dc16ef8 RSDEV-416: squash / clean liquibase changelogs` - main
  has reworked the Liquibase changelog/baseline. The branch adds new changesets
  (`changeLog-rsdev-444.xml`: `hibernate6-reseed-sequences`,
  `hibernate6-batch5-schema-migration`). Re-merge must re-validate that these still
  apply against the squashed baseline.
- `dbed50d59 Use pnpm and introduce lefthook (#812)`, `b6f42e450 Switch to Biome for
  linting`, `82472e7c0 OpenAPI v3 + Scalar`, `c48a60542 Remove SpotBugs` - frontend
  and build-tooling changes on main that will need reconciling.

**Re-entry decision to make:** either re-merge `main` into all 32 branches now (and
again periodically while parked) to keep conflicts small, or accept one larger
re-merge at restart and rely on the anchor tag for the known-good point. Given the
parking is expected to last months, the tag-and-accept-a-big-re-merge approach is
probably the lower total cost; just do not let the branches be force-moved without
the tag in place.

## 5. What was verified, and what was not

**Verified:**
- Fast/unit tests pass; the touched unit suites pass (35/35 at last check).
- Populated-DB migration tested locally 2026-06-09 (seed from `main`, boot the
  branch with `keepdbintact`): all changesets applied, no PK collisions, live
  insert test passed. See the devops-handover doc.
- JDBC driver (mysql-connector-j 8.4.0, `com.mysql.cj.jdbc.Driver`) connectivity
  against MariaDB 10.11.
- First-login content-init and `/workspace` render after the M1 revert.

**Not yet verified / open:**
- Full IT/MVC suite against a populated DB on a real Tomcat 10 deployment.
- Search is the least-exercised area (Lucene 5 to 9, Hibernate Search 5 to 7); the
  on-disk index format is incompatible and a full reindex is mandatory after migrating.
- jitpack inter-project dependency specifiers are still unpinned
  (`rsdev-444-upgrade-to-spring-6-SNAPSHOT`); these must be pinned to release
  versions once each repo is merged and tagged.
- Drata records are still owed for the dependency upgrades (see compliance note).
- Audit/Envers end-to-end (an `HHH015007` Envers metamodel ERROR logs at every boot;
  known non-fatal, but version-history views need exercising).

## 6. Non-obvious gotchas to re-load before touching anything

- **The XML `<tx:annotation-driven>` advisors are load-bearing at runtime.** Do not
  "simplify" them to class-level `@Transactional`. M1 tried exactly that and was
  reverted (`3309c75d5`). The `shiroFilter` is a BeanPostProcessor whose
  `securityManager` eagerly instantiates ~200 service beans before the transaction
  advisor is ready, so those beans are permanently proxied without `@Transactional`
  advice (SHIRO-829). The explicit XML pointcut advisors are what keep them
  transactional. See M1 / M1a in the review doc. The same window also silently
  disables service-layer Shiro authorization annotations, and this exists on `main`
  too (deserves its own ticket).
- **id-sequence reseed runs against live customer data.** `hibernate6-reseed-sequences`
  reseeds every `hibernate_sequences` counter to `MAX(id)+51` and seeds a `default`
  insurance row. It is a stored procedure (first in the project), so the migrating DB
  user needs `CREATE ROUTINE` / `DROP ROUTINE`. Collision safety rests on the
  `default` insurance row (the `prefer_entity_table_as_segment_value` config was found
  to be a no-op and removed, B1a).
- **Spring Batch tables are dropped and recreated** (Batch 3 to 5 schema). Batch
  job-execution history is discarded; only affects `/api/v1/export`. Re-run any
  unfetched API export after upgrade.
- **`useSSL=true` now hard-fails against a non-SSL DB.** Connector/J 8 treats
  `useSSL=true` as `sslMode=REQUIRED`. Audit `jdbc.url` in every deployment's
  properties before upgrading; the rspace-docker template ships `useSSL=true`.
- **Multipart upload limits moved out of `web.xml`** to `-Dfiles.maxUploadSize`,
  then `deployment.properties`, then a 50 MB default. Confirm the prod default fits
  Tomcat `maxSwallowSize`.
- **Liquibase context must be `run`** for the prod migration. A wrong `-Denvironment`
  can execute dev-test fixtures against real data.
- **Build depends on unpinned JitPack branch-SNAPSHOTs**, so the WAR tracks the
  latest pushed commits of the sibling repos. Confirm all sibling pushes are current
  before building.

## 7. Companion docs (snapshotted into this directory)

These were previously only in `/Users/fraser/dev/` (outside any repo) and would not
have survived the parking. Snapshot them here so they are pinned to the branch:

- `RSDEV-444-fleet-tracker.md` - the 32-project tracker: version table, per-project
  change summaries, PR-message drafts, pre-PR checklist. (source: `spring-6-upgrade.md`)
- `RSDEV-444-spring6-review.md` - the full code review: every M*/B*/H* finding,
  what was fixed, what is deferred, design decisions for B1/B2. Internally current
  to branch HEAD `3309c75d5`. Note its scope header ("66 commits") predates the last
  upstream merge; the branch now carries ~90 commits.
- `RSDEV-444-devops-handover.md` - the prod-clone migration test plan for DevOps
  (Tomcat 10, sequence reseed, useSSL, reindex). Update its "re-merge done 2026-06-10"
  freshness caveat before any fresh prod-clone test (main has drifted 37 commits).

## 8. Deferred follow-up tickets to file (independent of restart)

- **rspace-audit `-parameters` / SpEL `#p1`** (review item 15): the SpEL cache key
  was switched to positional `#p1`; the cleaner fix is enabling `-parameters` in
  `rspace-parent` and reverting, but that is cross-cutting and was deferred.
- **Shiro early-instantiation cascade** (M1a / SHIRO-829): decouple the Shiro BPPs
  from the securityManager graph so annotation-driven advice is not silently dropped.
  Affects `main` too.
- **jitpack specifier pinning**: pin all inter-project `*-SNAPSHOT` specifiers to
  release versions at restart.

## 9. Quick wins extracted to `main` while parked

Two changes on the branch are independent of the Spring/Hibernate/Jakarta forcing
function and worth landing on `main` separately so the parking does not sit on them:

- **ArchivalCheckSum merge fix (M3, `9a12b89ee`)** - genuine latent bug present on
  `main`: `GenericDaoHibernate.save()` routes assigned-id entities through `merge()`,
  which returns the managed copy; `zipAndSaveChecksumInDB` discarded it. Clean,
  low-risk cherry-pick. [extracted: yes/no - update when done]
- **mysql-connector CVE upgrade (CVE-2023-22102, CVSS 8.3)** - `main` is on
  mysql-connector-java 5.1.47 (EOL, 2018), well below the 8.2.0 fix. NOT a clean
  cherry-pick: it is the full 5.1 to 8.x jump (driver class change, useSSL behaviour
  change) and needs its own testing. Track as a dedicated security ticket.

## 10. Compliance

The dependency upgrades in this migration (Spring 6, Hibernate 6/7, Shiro 2.1, JAXB
4, google-api-client 2.2.0, commons-lang3, mysql-connector-j 8.4.0, byte-buddy
1.14.11) still owe Drata records. Anyone resuming this work, or extracting the
connector bump to `main`, must follow the ResearchSpace AI and Third Party code
policy in Drata before introducing or upgrading any dependency.
