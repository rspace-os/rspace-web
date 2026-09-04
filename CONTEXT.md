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
  the B2INST/PIDINST and DataCite PIDINST registration metadata because both its
  name (compared case-insensitively, ignoring surrounding whitespace) and its
  declared field type match a field of the default PIDINST template. A field
  matching by name but not by type is ignored. Participation is decided by name+type
  conformance, never by template lineage: any instrument carrying conforming
  fields is mapped, however it was created. The template's field names are the
  canonical spelling of the mapping contract.
- **Documentation-only field** — a field of the default PIDINST template that
  deliberately feeds no registration metadata, existing purely so users can
  record the fact against the instrument. Only the last calibration date is
  documentation-only now: PIDINST's Date vocabulary has no home for it. The
  measurement technique and calibration links were documentation-only until
  RSDEV-1253 mapped them to RelatedIdentifier entries with a fixed IsDescribedBy
  relation (see ADR 0007); the relation type stored on the link itself still
  never leaves RSpace. A documentation-only field is still an ordinary
  instrument field, so users fill, edit and read it as usual.
- **Legacy auto-filled landing page** — a landing page RSpace itself wrote into
  an instrument's Landing page field, back when saving an instrument filled a
  blank field with the record's own globalId address. RSpace no longer writes
  these, and a blank Landing page now stays blank until a user types a value or
  an identifier is registered. The ones already in the data are recognised by
  their `/globalId/<globalId>` tail and are treated as an empty field wherever
  the field is read: never registered with a provider, since the address needs an
  RSpace sign-in, and replaced by the public landing page when an identifier is
  registered. A user who deliberately types such an address is therefore also
  overridden; that is accepted, because an address needing a sign-in is unfit
  either way.
- **Identity-bound field** — a field whose value names exactly one concrete
  Instrument, so deriving a new record from an existing one must not carry it
  over. The Landing page is identity-bound. Three derivation paths enforce this
  today: duplicating an instrument, duplicating a template, and creating an
  instrument from a template all start the derived record's Landing page blank,
  whether the source value was written by RSpace or typed by a user. It then
  stays blank, on an instrument as on a template, until a user types a value or
  an identifier is registered for it. A value the user supplies directly *on the
  new record itself* (e.g. typed into the creation form, or sent in the creation
  request) is theirs and is kept: it is derivation that discards a landing page,
  never user input. The one value that does *not* count as user input on the new
  record is the source template's own Landing page echoed back unchanged in the
  creation request, which is what a client posting a template's fields verbatim
  sends; it is discarded like any other inherited value, so the guarantee is a
  property of the service rather than of client cooperation. Because the rule
  spans layers, the field is recognised by the same name-and-type test in the
  service layer (`PidinstFields`, shared with the PIDINST mapping) and in the
  Inventory UI (`InstrumentModel.tsx`), and those two must be changed together.
  Both resolve a single field, so a record with two conforming fields has only
  its first treated as identity-bound.
  Three things are deliberately out of RSDEV-1307's scope. Syncing an instrument
  to a newer template version keeps the instrument's existing Landing page
  (correct — it is that instrument's own address) but a Landing page field newly
  added by the sync stays blank until the next ordinary save, because that path
  alone does not run the fill. Creating a *template from an instrument* copies
  the instrument's Landing page into the new template only when the user
  explicitly ticks that field in the create dialog (content is opt-in per field,
  and defaults to off), which still leaves a reusable definition holding one
  instrument's address. And records derived *before* this rule existed are not
  backfilled: they keep their source's Landing page, and will not self-heal,
  because the fill only ever writes into a blank field. A migration was not
  written because a blanket update cannot distinguish an inherited value from
  one the user legitimately typed.
  Finally, the rule keys off the English display label "Landing page". If seeded
  template field labels are ever localised, or a user names the field in another
  language, every derivation path silently stops clearing it.
- **Provider record page** — the registered record's own page on the issuing
  provider, distinct from a citable public URL: it exists from registration
  onwards and may require signing in to that provider, so it is never presented
  as the identifier's public address.
- **Public link suffix** — the unguessable random token that names an
  identifier's public landing page. Generated when a new identifier registration
  begins — always before the identifier is created, and before the provider call
  on the path whose payload carries the address — then immutable for the
  identifier's lifetime. Every identifier has one, whichever provider registers
  it. (ADR 0006 records the per-provider ordering and why it differs.)
- **Public landing page** — the page RSpace serves anonymously for a published
  identifier, addressed by the public link suffix
  (`/public/inventory/<suffix>`). Distinct from the instrument's Landing page
  field (a field value on the record), from the provider record page (the
  record's page on the provider's site), and from the record's globalId address
  (which needs an RSpace sign-in). The address exists from the moment
  registration begins; the page itself resolves only once the identifier is
  published.

  The page serves the record *as of the newest revision of the identifier row*,
  for both providers. Two consequences worth knowing. Changes to the identifier
  row itself reach the page without a republish, which B2INST needs because it
  has no republish operation. And because the record snapshot moves with that
  revision, record edits made after publishing become public as soon as anything
  writes the identifier row — the `customFieldsOnPublicPage` toggle, an
  identifier metadata edit, a B2INST status refresh — rather than staying private
  until a deliberate republish. DataCite used to be held at the publication-time
  snapshot instead; that difference was removed deliberately.
- **Updatable identifier state** — an identifier state in which the provider's
  copy of the metadata can be rewritten in place by RSpace. The two providers
  express it from opposite ends. For B2INST it is every state *except*
  `accepted`: an InvenioRDM draft stays writable right up to acceptance, which
  publishes the record and removes the draft, and since `refreshIdentifier`
  stores the community review's status verbatim an identifier can legitimately
  sit in `draft`, `created`, `submitted`, `cancelled`, `declined` or `expired`,
  each with a live draft behind it (verified against b2inst-test.gwdg.de,
  August 2026). For DataCite it is *only* `draft`: a findable DOI is refreshed
  through the existing Republish, which already resends the full current
  metadata (retract then publish), and a retracted (`registered`) DOI is left
  alone by decision rather than because DataCite would refuse it. An accepted
  B2INST record is never updated in place; it would need a whole new
  draft-and-review round.
- **External metadata update** — re-running the usual PIDINST mapping over an
  instrument's current fields and pushing the result to the provider record
  registered for it, so the external record keeps describing the instrument as
  it is today rather than drifting. Triggered by saving an instrument that
  carries an identifier in an updatable identifier state; saving *is* the
  explicit user action, there is no separate "update external PIDINST" button.
  A failed push never blocks or reverts the save: the instrument edit is
  persisted regardless, the identifier's own state is left untouched, and the
  failure is reported on the identifier in the API response, and the web
  interface shows it after a save or a transfer: a provider failure as an error,
  a record frozen by its own state as a notice, and success silently
  (RSDEV-1356). A save reports one toast per identifier; a bulk transfer groups
  them into one alert per kind, because both kinds wait to be dismissed. The
  toast decides by the machine-readable `outcome` (`UPDATED`, `FAILED`,
  `NOT_UPDATABLE`) that travels with the reason, never by the wording of the
  reason itself, and an outcome it does not recognise is reported as a failure
  rather than passed over. Every
  qualifying save pushes, whether or not a mapped field changed, so retrying a
  failed push is just saving again. An identifier whose state has frozen its
  provider record is reported too, rather than passed over in silence, in a
  message that explains why in words without echoing the provider's own state
  token; an identifier belonging to a provider whose integration is switched
  off is passed over silently, because there is nothing to act on. Saving an
  instrument and transferring one both push. A transfer is told apart from an
  instrument with no identifiers by reading the record, because the response a
  departing owner receives is a limited view that lists none either way; the
  outcome is then reported only to a caller who can still see the identifier.
  Template sync, restore, duplicate and delete do not push, and edits to samples,
  subsamples and containers do not (their draft-DOI metadata still drifts).
  Neither does a *bulk* change of owner run with rollback-on-error: that runs the
  whole batch in one transaction, and pushing from inside it could leave the
  provider holding an owner a later rollback took away. Those instruments drift
  until their next ordinary save, which is the recoverable direction.
  (RSDEV-1251)
- **Registered landing page** — the LandingPage value RSpace sends to a PID
  provider when registering an instrument identifier: the Landing page field
  when it holds an absolute http(s) address the user typed themselves, otherwise
  the identifier's public landing page. A legacy auto-filled landing page is
  never registered — it is a login-walled address, and a landing page is baked into a
  citable PID once a curator accepts — and neither is any value a resolver could
  not follow. When no registrable address exists the property is omitted, a
  missing property being recoverable where a wrong published one is not.

  Registering an instrument identifier also writes that same address into the
  Landing page field whenever the field held no address the user typed, so the
  field afterwards shows exactly what was registered rather than drifting from
  it. A value the user typed is left untouched. The write happens only once the
  provider has accepted the registration, so a failed registration leaves the
  field as it was. Deleting the identifier takes that address back out again,
  leaving the field as empty as it started; here too a value the user typed, and
  an address belonging to another identifier, are left alone.

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
