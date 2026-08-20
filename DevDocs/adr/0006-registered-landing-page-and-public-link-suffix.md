---
status: accepted
---

# Registered landing page and the pre-generated public link suffix (RSDEV-1254)

## Context

A PIDINST LandingPage is baked into a citable PID the moment a curator accepts
the B2INST record, and RSpace has no way to update a published record
afterwards. Until now the LandingPage RSpace registered was the instrument's
Landing page field, whose materialised default is the record's
`/globalId/IN…` address — a page that requires an RSpace sign-in, which is
unfit as the public face of a citable identifier.

RSpace already has an anonymous public page per identifier, addressed by the
identifier's `publicLink` (`/public/inventory/<suffix>`), used by IGSN. But
that suffix was generated inside `DigitalObjectIdentifier`'s constructor,
i.e. only *after* the provider had answered — too late to register the page's
address with the provider.

## Decision

1. The public link suffix is generated on the `ApiInventoryDOI` DTO when a new
   identifier registration begins (`SecureStringUtils.getURLSafeSecureRandomString(16)`,
   the same algorithm the entity used), *before* any provider call, and is
   threaded into `DigitalObjectIdentifier` through a new constructor parameter
   so the entity's `publicLink` names the same page that was registered.
   Generation is an explicit call made by the two new-registration flows — not
   the no-args constructor — because that constructor also backs Jackson
   deserialization and the sparse "update" DTOs, where a self-generated value
   would leak a fresh random URL into existing identifiers via
   `applyChangesToDatabaseDOI`. The entity constructor still self-generates
   when given no suffix, so callers that never carry one stay correct.
2. The **registered landing page** sent to B2INST is the Landing page field
   when it holds a value the user typed themselves; otherwise it is the public
   landing page built from the suffix. The materialised globalId default is
   never registered. It is recognised by its `/globalId/<globalId>` tail
   (`GlobalIdUrls.GLOBAL_ID_PATH`, shared with the default-fill) rather than by
   equality with the currently configured address: the tail is what the fill
   produces and names one record, while the host part is only whatever the
   server URL said at fill time, so whole-address comparison would stop
   recognising the fill as soon as the deployment was renamed or lost its server
   URL setting — and would then register the login-walled default. With no
   server URL configured the property is omitted, as before: a missing property
   is recoverable, a wrong published URL is not.
3. The generated URL is used for the outbound payload only. The identifier's
   `publicUrl` (PUBLIC_URL) keeps its publish-time semantics: the Inventory UI
   links an identifier row to `publicUrl` whenever it is present, and a draft
   B2INST identifier must keep linking to the provider record page rather than
   to a public page that answers 404 until the identifier is published. The
   landing-page preview already works from `rsPublicId`.

## Considered options

- Persisting the generated URL as `publicUrl` from draft onwards — rejected:
  it would replace the working provider-record-page link on draft identifiers
  with a dead one.
- Always registering the public landing page, ignoring the Landing page field
  — rejected: a user-typed institutional landing page is exactly what PIDINST's
  LandingPage is for, and the field would silently become documentation-only.
- Generating the suffix in `ApiInventoryDOI`'s no-args constructor — rejected
  for the `applyChangesToDatabaseDOI` leak described above.

## Consequences

- The Landing page *field* may still display the materialised globalId default
  while the *registered* value is the public landing page. This drift is
  deliberate: the field remains instrument documentation plus a user override;
  what leaves RSpace is governed by the registered-landing-page rule.
- The registered public landing page answers 404 until the identifier reaches
  the published ("findable") state. B2INST identifiers do not reach that state
  until curator-acceptance state sync exists (separate epic work); the address
  registered now is nevertheless permanent and correct, which is the point of
  generating the suffix up front.
- `DigitalObjectIdentifier` lives in rspace-core-model, so the constructor
  change rides a core-model release and a pinned-version bump here.
