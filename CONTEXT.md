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
  translator-facing catalog format. The English (`en-US`) base JSON is generated
  from frontend `defaultValue`s; translated locale JSON is owned by the future
  translation workflow. Everything else (Spring `.properties`) is derived from
  JSON catalogs.
- **Generated bundle** — a Spring `.properties` file produced *from* the canonical
  JSON for backend-rendered text. Never edited by hand or by translators.
- **Namespace** — a named slice of translations, one per product module
  (`workspace`, `gallery`, `inventory`, `groups`, `dashboard`, `admin`, `apps`,
  `system`, `public`) plus a shared `common`. Loaded independently.
- **Base file** — the English (`en-US`) file for a namespace. The monolingual
  source/template every other locale is translated against. Locale codes are
  region-qualified BCP 47, hyphenated (`en-US`, `zh-TW`, `zh-HK`) at runtime;
  the underscore form (`en_US`) belongs only to the generated Java bundle.
- **Key** — a stable structured dot-notation identifier (`ns:section.name`) that
  names a string independently of its wording. Distinct from the **default
  value** (the English source text co-located at the call site).
- **Default value** — the English source string supplied inline at the `t()` call
  site and extracted into the `en-US` base file. Mandatory for every key; copy
  changes start in code and regenerate the base JSON, not by hand-editing
  `en-US` JSON.
- **ICU MessageFormat** — the message syntax used for all keys (plurals, gender/
  `select`, ordinals, inline number/date formatting).
- **Extraction** — the build step (via `i18next-cli`) that scans code for keys +
  default values and synchronizes the `en-US` base files; also the source of the
  generated key types and the unused-key check.
- **Ratchet** — the per-module enforcement progression: a converted module flips
  its `noJsxLiterals` lint rule to `error`, so the gated (fully-converted)
  surface only ever grows.
- **No-orchestration gap** — the period before Weblate is connected. While the
  product is English-only, this gap is invisible: every string falls back to its
  English default.
