---
status: proposed
---

# Releasing the default (B2INST) instrument template to all deployments

## Context

RSDEV-1219 requires a B2INST-compatible instrument template that every user on
every RSpace deployment can read and duplicate but nobody can edit, delete, or
transfer. Sample templates already solve the "readable by all" half of this with
a *default templates owner*: the owner of the oldest template row is treated as a
system account, and any template it owns is world-readable. That heuristic is
safe for samples only because they are seeded at first boot, before any user can
create one.

## Decision

We deliberately diverge from the sample-template mechanism on three points:

1. **Explicit sysadmin as default owner, not the oldest row.** Instrument
   templates are already live on customer instances, so the oldest instrument
   template row may belong to an ordinary user. Resolving the default owner as
   the oldest row would leak that user's templates to everyone. We resolve the
   sysadmin account explicitly instead.

2. **A persisted `isEditable` flag enforces the lock, not ownership — checked at
   the central permission choke point.** Samples rely on ownership alone
   (non-owners cannot mutate), which still lets the owning sysadmin delete a
   default template. RSDEV-1219 wants the template locked against *everyone*, so
   we add a non-null `isEditable` flag (default `true` for existing rows). The
   flag is modelled on the concrete `InstrumentTemplate`, not on the
   `InstrumentEntity` base class, because editability is only meaningful for
   templates and never for concrete instruments; single-table inheritance keeps
   its column on the shared `InstrumentEntity` table regardless. The guard lives
   in `InventoryPermissionUtils.canUserEditInventoryRecord` (plus the transfer
   assertion) and fires for locked `InstrumentTemplate` instances, not in the
   template-specific assert methods: mutation routes such as file attach bypass
   those asserts, and only the central check also flows into the
   `permittedActions` the UI uses to render the template read-only.
   The template icon-upload endpoints, which historically asserted only read
   before mutating, are tightened to assert edit permission as part of the same
   work, so they consult the central check too.

3. **Seeding via a Liquibase custom change, not an app initialiser.** Sample
   templates are seeded by `SampleTemplateAppInitialiser` on startup. We instead
   create the default instrument template from a `customChange` Java task
   (modelled on `UpdatingOwnerIdColumnOnDigitalObjectIdentifier_RSDEV607`) so the
   release rides the existing Liquibase migration path that already reaches every
   deployment, and runs exactly once per database.

   The custom change calls `InstrumentEntityApiManager.createInstrumentTemplate`
   directly. That create path resolves `modifiedBy` via
   `IActiveUserStrategy.CHECK_OPERATE_AS`, which calls
   `SecurityUtils.getSubject().isRunAs()`; but the Liquibase migration runs before
   `GlobalInitManagerImpl` binds Shiro's `SecurityManager` (on
   `ContextRefreshedEvent`). The custom change therefore binds a minimal
   `SecurityManager` to the Shiro `ThreadContext` for the duration of the create
   and unbinds it afterwards. This is the trade-off of the Liquibase route over an
   app initialiser (which runs post-context, with Shiro already available).

## Consequences

- Instrument templates carry a schema change (an `isEditable` column on the
  `InstrumentEntity` and `InstrumentEntity_AUD` tables, shared by the whole
  single-table hierarchy) whose entity mapping lives on `InstrumentTemplate` in
  the sibling `rspace-core-model` repo, so the entity change ships ahead of the
  web change and the pinned model version is bumped. Concrete `Instrument` rows
  simply take the column default (`1`).
- The default-owner concept is duplicated (samples and instruments each keep their
  own `getDefaultTemplatesOwner`) rather than unified, keeping the two hierarchies
  independent at the cost of some parallel code.
- Because seeding happens during the Liquibase migration phase (before the app is
  fully up), the newly created template's appearance in Lucene-backed global search
  depends on it being indexed at that point or by the standard startup reindex.
  Verify the index is populated on a real deployment; if not, trigger a targeted
  reindex of the seeded template.
