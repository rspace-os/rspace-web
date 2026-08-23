# Context Glossary

The shared, canonical vocabulary for this project. Terms are added as they are
resolved during design. This file is a glossary only — no implementation details.

## Inventory templates

- **Instrument template** — a reusable definition (name + custom fields) from which
  a user creates concrete Instruments. Persisted in the single-table
  `InstrumentEntity` hierarchy under `DTYPE='InstrumentTemplate'`; its sibling
  discriminator is the concrete `Instrument`.
- **Sample template** — the analogous reusable definition for Samples, in the
  `Sample`/`SampleTemplate` hierarchy. The instrument side is being brought to
  behavioural parity with it, not merged into it.
- **Default (system) template** — a template shipped by RSpace itself and made
  readable and duplicable by every user on every deployment, while remaining
  read-only to non-owners. The first such instrument template is named
  `Instrument (PIDINST 1.0)`: its fields map 1-to-1 to the PIDINST/B2INST payload.
  For samples the equivalents are the seeded standard templates (Antibody,
  Bacteria, etc.).
- **Default templates owner** — the single account that owns the default templates.
  All users may read (and therefore duplicate) any template owned by this account.
  For samples this account is resolved as the owner of the oldest template row,
  which is safe only because samples are seeded before any user can create one.
  For instruments the account is resolved explicitly as the sysadmin, because
  user-created instrument templates may already predate the default one.
- **Locked template** — a default template that cannot be edited, deleted, or
  transferred by anyone, enforced by a persisted `isEditable=false` flag rather
  than by ownership alone. Editability is a property of templates only: a
  concrete Instrument is never locked, and one created from a locked template is
  an ordinary, fully mutable instrument. Distinct from ordinary non-owner
  read-only access, which merely prevents *other* users from mutating a template
  they do not own.
- **Custom field** — a typed field that is part of a template's definition and is
  stamped onto every record created from that template (for the PIDINST template:
  Owner, Manufacturer, etc.).
- **Extra field** — an ad-hoc field (text, number or link) a user attaches to an
  individual record after creation, outside any template definition.

## Instrument PID registration (B2INST/PIDINST)

- **PIDINST-mapped field** — a custom field on a concrete Instrument that feeds
  the B2INST/PIDINST registration metadata because both its name (compared
  case-insensitively, ignoring surrounding whitespace) and its declared field
  type match a field of the default PIDINST template. A field matching by name
  but not by type is ignored. Participation is decided by name+type
  conformance, never by template lineage: any instrument carrying conforming
  fields is mapped, however it was created. The template's field names are the
  canonical spelling of the mapping contract.
- **Documentation-only field** — a field of the default PIDINST template that
  deliberately feeds no registration metadata, existing purely so users can
  record the fact against the instrument. The measurement technique, the
  calibration and the last calibration date are documentation-only: they have no
  PIDINST property that fits them, and inventing one was tried and rejected. A
  documentation-only field is still an ordinary instrument field, so users fill,
  edit and read it as usual; it simply never leaves RSpace.
- **Materialised default** — a value RSpace fills into a PIDINST-mapped field
  the user left empty, applied whenever the Instrument is saved rather than at
  PID registration, so the field is populated from the moment the instrument
  exists and regardless of whether it is ever registered. Applies to the landing
  page, whose default is the instrument's own public RSpace address. Only a blank
  field is filled and a user's own value is never replaced; once written it is an
  ordinary field value the user may edit, and clearing it and saving fills it
  again. Instruments carrying no conforming field are untouched, and templates
  are never filled, since one instrument's address must not be stamped onto every
  instrument later created from that template.
- **Provider record page** — the registered record's own page on the issuing
  provider, distinct from a citable public URL: it exists from registration
  onwards and may require signing in to that provider, so it is never presented
  as the identifier's public address.

## Record version history

- **Revision** — a single audit row: one recorded change to a record, identified
  by a monotonic audit id. An internal concept. Never shown to users as a number
  and never used in user-facing labels.
- **Version** — the user-facing counter on a record, incremented when a user
  makes a change the product considers significant (for a Gallery item, uploading
  new file content). **Several revisions can share one version**, because not
  every recorded change bumps the counter. Revision and version are therefore not
  interchangeable, and code that treats them as such is wrong.
  A version owns its own content and the metadata describing that content: its
  **filename** (a new version may be a differently named file), its description,
  its size and its modification date. All of those are properties of a version,
  never of the item. Only the item's identity (its id and Global ID) and the
  references made to it are shared across versions.
- **Version history** — the canonical name, in the UI and in code, for the list
  of a record's versions, newest first. Where several revisions share a version,
  the history shows that version once, representing its final state. Named this
  way everywhere even though the ELN workspace's equivalent legacy button is
  labelled "Revisions".
  _Avoid_: revision history, revisions (as a user-facing label)
- **Gallery item** — the user-facing name for a file a user keeps in the Gallery.
  The same thing the API and older code variously call a media file or a gallery
  file.
  _Avoid_: attachment, media record (when addressing users)
- **Local Gallery item** — a Gallery item whose bytes RSpace itself stores. The
  only kind that has a version history, because only these are audited.
- **Filestore item** — a Gallery item that lives on an external store (S3, iRODS,
  Samba) and is only referenced by RSpace. Has no version history at all: RSpace
  never recorded its changes and cannot.
- **Pinned version view** — a record displayed as it was at one past version,
  reached by a shareable link and never editable. The version history lists
  versions; a pinned version view shows one. Every record type has one (the ELN
  calls its own the audit view), and each states plainly which version is on
  screen and that it is locked.
  _Avoid_: revision view, historical view, audit view (outside the ELN's own)
- **Item-level reference** — a link or attachment from an ELN document or an
  Inventory item to a Gallery item. References name the item, never one of its
  versions: nothing records the version a reference was made against. So the
  references shown beside a pinned version view are the item's, not that
  version's, and must be worded so no one reads them as the latter.
  _Avoid_: backlink to a version, version reference

## Internationalization (i18n)

- **Canonical translation catalog** — i18next JSON. The runtime and
  translator-facing catalog format shared by the frontend and the backend's
  `JsonMessageSource`. English (`en-US`) text is authored in the base JSON;
  translated locale JSON is owned by the future translation workflow.
- **Namespace** — a named slice of translations, one per product module
  (`workspace`, `gallery`, `inventory`, `groups`, `dashboard`, `admin`, `apps`,
  `system`, `public`) plus a shared `common`. Loaded independently.
- **Base file** — the English (`en-US`) file for a namespace. The monolingual
  source/template every other locale is translated against. Locale codes are
  region-qualified BCP 47 and hyphenated (`en-US`, `zh-TW`, `zh-HK`) in catalog
  paths and at runtime.
- **Key** — a stable structured dot-notation identifier (`ns:section.name`) that
  names a string independently of its wording. Distinct from the **default
  value** stored in the base catalog.
- **Default value** — the English source string stored in the `en-US` base file.
  Mandatory for every key; copy changes start in the canonical base JSON.
- **ICU MessageFormat** — the message syntax used for all keys (plurals, gender/
  `select`, ordinals, inline number/date formatting).
- **Extraction** — the build step (via `i18next-cli`) that scans code for keys,
  adds missing entries to the `en-US` base files, and identifies unused keys.
  The same catalogs generate the TypeScript key types.
- **Ratchet** — the per-module enforcement progression: a converted module flips
  its `noJsxLiterals` lint rule to `error`, so the gated (fully-converted)
  surface only ever grows.
- **No-orchestration gap** — the period before Weblate is connected. While the
  product is English-only, this gap is invisible: every string falls back to its
  English default.
- **Locale** — a single BCP 47 tag (e.g. `de-DE`) that controls both the UI
  language (which translation catalog renders) and regional formatting (dates,
  numbers). One value, two effects; they are not independently configurable.
- **CSV-export carve-out** — machine-readable CSV exports are always formatted
  as `en-US` regardless of anyone's locale, so downstream parsers never see
  locale-dependent decimal separators or date formats.
- **First day of week** — fixed instance-wide, not part of a user's locale
  choice.
- **Bundled locale** — a locale whose translation catalog ships with the
  release. Only bundled locales can be allowed by a sysadmin.
- **Allowed set** — the sysadmin-chosen subset of bundled locales users may
  pick from. Always contains the instance default; never empty.
- **Instance default** — the locale served when no valid user choice applies,
  including to all anonymous visitors.
- **Effective locale** — the locale actually served: the user's stored choice
  if it is in the allowed set, otherwise the instance default. A stored choice
  outside the allowed set is kept (not erased) and springs back if re-allowed.

## Platform Configuration

Canonical terms for Feature Flags. Implementation and operational details live
in `DevDocs/adr/0003-feature-flags.md`.

**Feature Flag**:
An internal boolean control for rollout, kill switches, and temporary changes.
It is never an authorization, licensing, tenant-isolation, or secrecy boundary.
_Avoid_: sysadmin setting, deployment property, app setting, toggle

**Feature Flag Definition**:
The manifest entry that declares a flag's identity and default state.
_Avoid_: database row, property key, frontend constant

**Feature Flag Manifest**:
The checked-in JSONC source of Feature Flag Definitions, read directly at
runtime and used to generate backend constants and frontend types.
_Avoid_: Java enum, TypeScript constant list, database seed

**Feature Flag Baseline**:
The instance-wide value before a User Feature Flag Override is applied.
Anonymous requests always receive this value.
_Avoid_: global final value, deployment setting

**User Feature Flag Override**:
An explicitly stored per-user value that replaces the Feature Flag Baseline for
that user. Without one, the baseline applies.
_Avoid_: targeted rollout, group override, community override

**Feature Flag Override Permission**:
The capability to set one's own overrides. It applies to every Feature Flag,
not selected flags.
_Avoid_: special user, per-flag permission, sysadmin setting

**Feature Flag Resource**:
The caller-specific state of one Feature Flag in the REST API v2 collection.
The resource contains the effective value, baseline, source, and override availability.
_Avoid_: database row, manifest entry

**Retired Feature Flag**:
A flag removed from the manifest. Startup removes its stored baseline and user
overrides.
_Avoid_: deprecated flag, disabled forever, archived flag

**Expired Feature Flag**:
A flag whose `expires` date has passed. It fails CI validation but not startup.
_Avoid_: runtime-failing flag, startup blocker

## Booking

- **Available now** — the current instant falls in an available segment of the
  bookable item's current local day. Booking and blockout intervals are
  half-open, so the item becomes available exactly when an occupied interval
  ends.
- **Free later today** — the bookable item is not available now, but has a
  positive-duration available segment later in its current local day. An item
  that is already available now or remains occupied through local midnight is
  excluded.

- **Day timeline** — a read-only, horizontal representation of one calendar day,
  divided into 24 equal hour intervals and populated by events positioned by
  their exact start and end times. Its day boundaries and wall-clock labels are
  interpreted in an explicit IANA timezone rather than the viewer's implicit
  browser timezone.
- **Wall-clock day** — the stable 00:00–24:00 grid represented by a day timeline.
  It always has 24 hour intervals, including daylight-saving transition dates
  where elapsed time contains 23 or 25 hours; the timeline communicates skipped
  or repeated hours without changing the grid's shape.
- **Event** — a time-bounded item displayed on a resource's calendar. Events may
  overlap; overlap is not, by itself, evidence of a booking conflict.
- **Event kind** — the reason an event affects a resource's availability: either
  a booking or a blockout.
- **Booking event** — an event that reserves a resource for a user. Its full
  calendar card identifies who booked it, its exact period, and any notes the
  viewer is permitted to see.
- **Blockout event** — a non-booking event that marks a resource as unavailable,
  such as maintenance or downtime. It has kind-appropriate card content and no
  booker.
- **Event lane** — one horizontal visual track within a day timeline. Overlapping
  events occupy separate lanes so that each remains visible.
- **Calendar card** — the reusable visual representation of an event. Its compact
  state fits the geometry imposed by a calendar view; its expanded state exposes
  the event's complete display details without changing the event's time range.
- **Expanded calendar card** — a calendar card state that exposes the full booked-by
  heading, exact period, and notes when compact timeline geometry cannot show them.
  Expansion is distinct from creating or editing an event.
- **Availability window** — the explicit time interval over which a resource's
  availability is summarized. It can span part of a day, one day, or several days.
- **Availability bar** — a thin summary of one resource's availability within an
  availability window. Touching or overlapping events of the same kind form one
  continuous section, so individual event identity is deliberately absent.
- **Availability state** — the condition of a resource during one section of an
  availability window: available, booked, blocked out, or simultaneously booked
  and blocked out.
- **Booking configuration** — the settings that make one inventory instrument
  bookable. It supplies the booking timezone and the lock used to serialize
  overlap checks for that instrument.
- **Time-slot booking** — one persisted reservation for one booking configuration.
  It stores a half-open UTC interval, its requester, optional purpose, state, and
  audit data.
- **Booking privacy** — the response detail prepared for one caller. `full` shows
  the purpose and requester label. `busy` shows only the target and occupied time.
- **Half-open booking interval** — a booking window that includes its start and
  excludes its end. Two bookings that only touch at one boundary do not overlap.
- **Booking cancellation** — the one-way change from `CONFIRMED` to `CANCELLED`.
  Cancellation also soft-deletes the booking from normal Calendar reads; it does
  not physically delete the audit history.
