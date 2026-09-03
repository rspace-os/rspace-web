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

## Resource role permissions

- **Role scheme**: the roles, ordering, capabilities, assignment rules, and
  invariants for one protected resource type. Every scheme makes Owner its
  highest role, puts Manager immediately below it, prevents Managers from
  changing Owners, and requires one persisted Owner. Capabilities are
  monotonic: every higher role includes every capability of each lower role.
  Other roles and capabilities belong only to that resource type.
- **Role assignment**: one role granted directly to one grantee for one
  protected resource. A grantee has at most one direct role assignment for that
  resource.
- **Grantee**: a user, group, or dynamic audience that receives a role.
  _Avoid_: subject, actor, sharee
- **Capability**: one action allowed by a role scheme, such as changing a
  resource or assigning an Owner. Callers receive resolved capabilities so they
  do not have to reproduce a role scheme.
- **Effective role**: the highest role a user receives from all applicable
  direct, group, audience, and implicit sources for one resource.

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
- **Booking configuration state** — the lifecycle condition of a booking
  configuration, either Active or Archived.
  _Avoid_: deleted flag, archive flag
- **Active booking configuration** — a booking configuration that may
  participate in booking operations. Whether users can create new bookings also
  depends on whether the configuration is enabled.
- **Archived booking configuration** — a readable booking configuration that
  cannot receive settings, access, or booking changes until it is restored.
- **Archive a booking configuration** — move an Active booking configuration to
  Archived while retaining its settings, bookings, and audit history. Repeating
  the archive command leaves it Archived.
  _Avoid_: delete a booking configuration
- **Restore a booking configuration** — move an Archived booking configuration
  back to Active without recreating revoked calendar subscriptions.
- **Permanently delete a booking configuration** — irreversibly remove an
  Archived booking configuration and its live operational data. Audit history
  remains. Only a system administrator acting as themselves may do this.
- **Bookable-item calendar subscription** — one user's independently revocable,
  read-only external calendar of one booking configuration's full schedule. It
  remains available only while that user has effective permission to view the
  configuration. Losing the final permission ends the subscription; restoring
  access does not revive it.
  _Avoid_: instrument calendar link, item feed
- **Personal booking calendar subscription** — one user's revocable, read-only
  external calendar of the reservations they requested and the blockouts they
  created across bookable items.
  _Avoid_: per-user calendar link, user calendar
- **Subscription link** — the bearer URL for a bookable-item or personal booking
  calendar subscription.
- **Calendar cancellation tombstone** — the retained calendar representation of
  a cancelled or deleted booking event. It keeps the event's stable identity and
  original time range while declaring the event cancelled.
  _Avoid_: deleted calendar event, hidden cancellation
- **Booking access assignment**: one Booking role granted directly to one user
  or group for one booking configuration. The assignment governs the
  configuration and all of its bookings, blockouts, calendar views, audit
  history, and calendar-subscription eligibility.
- **Effective Booking role**: the highest Booking role a user receives from
  the All users audience, their direct Booking access assignment, and all group
  assignments. A weaker direct assignment never reduces access inherited
  through another source.
- **Booking Owner**: the highest Booking role. A Booking Owner can change the
  configuration, manage every role assignment and calendar event, and archive
  or restore the configuration, subject to the explicit-owner invariant.
- **Booking Manager**: a Booking role that can change the configuration, manage
  Manager, Booker, and Viewer assignments, manage every calendar event, and
  archive or restore the configuration. It cannot change Owner assignments.
- **Booker**: the Booking role whose defining permission is creating a booking.
  A Booker can change or cancel their own bookings but cannot manage another
  requester's bookings or create blockouts.
  _Avoid_: User, booking user
- **Viewer**: the lowest Booking role. A Viewer can read the configuration and
  calendar and create a personal calendar subscription, but cannot create or
  change calendar events.
- **Explicit-owner invariant**: every booking configuration has at least one
  persisted Owner assignment row for a user or supported group. A disabled
  user, deleted-group snapshot, or group with no enabled members still counts
  structurally, but grants no effective access. The implicit Owner access of a
  system administrator never satisfies this invariant.
- **Unavailable Booking role holder**: a disabled user, a hard-deleted group
  retained through its assignment snapshot, or a group with no enabled members.
  Availability is derived from live User and Group state rather than copied
  onto every assignment. The row remains visible, removable, and auditable,
  but grants no effective Booking access.
- **All users audience**: every user account on the RSpace instance, including
  accounts created after a booking configuration. It is a dynamic source of a
  Booking role rather than a list of individual access assignments.
- **Default shared with**: the instance-wide choice that supplies initial
  Booker access when a booking configuration is created. It grants Booker to
  the All users audience, to the users and groups selected with the setting, or
  to nobody beyond the creator, who is always an Owner. A new instance starts
  with All users selected.
- **Leave a booking configuration**: remove one's direct Booking access
  assignment. Access received through a group or the All users audience remains
  because Booking has no per-user exclusions. Removing the final persisted
  Owner is rejected.
- **Own-booking access after role loss**: a requester without a current Booking
  role can still read only their own past and future booking rows, whether the
  role loss was voluntary or involuntary. This is derived from each booking's
  requester relation, stores no departure marker, grants no configuration,
  calendar, audit, access, or subscription permission, and keeps those rows
  read-only.
- **Booking ownership transfer**: an optional part of transferring the target
  Instrument. It atomically makes the incoming Instrument owner a direct
  Booking Owner and removes the outgoing owner's direct Booking assignment only
  when that assignment is Owner. A lower outgoing role and every other Booking
  assignment are preserved.
- **Booking access directory**: the users and groups available for Booking role
  assignment through a resource-scoped, capability-protected search. An
  ordinary Owner or Manager can select their groups and fellow group members; a
  system administrator can select any active user or valid lab, collaboration,
  or project group. A community is never selectable. The Booking-settings
  variant is separately sysadmin-only.
- **Booking access**: access granted by a Booking role, independently of access
  to the target Inventory record. Booking access reveals only the target details
  needed inside Booking and grants no Inventory access.
- **System administrator Booking access**: implicit Booking Owner access held by
  every system administrator without a persisted assignment. While running as
  another user, the represented user's Booking access applies and the system
  administrator remains only the audit actor. This implicit access allows a
  system administrator to identify and repair a configuration whose persisted
  Owner rows grant no effective Owner access.
- **Booking identity boundary**: the current Shiro solution supplies the
  authenticated represented subject and original audit actor. Controllers pass
  both identities into Booking and generic resource-access services; those
  services never inspect Shiro or ambient thread-local identity themselves.
- **Booking defaults** — the instance-wide scheduling values copied into a new
  booking configuration at creation time. Changing them does not alter an
  existing booking configuration.
  _Avoid_: inherited settings, live defaults
- **Scheduling policy** — the rules stored on one booking configuration that
  determine its valid time increments, opening hours, booking buffers, maximum
  booking duration, and whether concurrent bookings are permitted.
  _Avoid_: global booking rules
- **Opening interval** — the daily wall-clock period in a booking
  configuration's timezone during which a booking may occur. `00:00–24:00`
  denotes the complete local day; an interval never crosses a closed overnight
  gap.
  _Avoid_: business hours, availability event
- **Booking buffer** — unavailable time immediately before or after a confirmed
  booking. The two persisted directions may differ even though the settings UI
  normally edits them as one value.
- **Maximum booking duration** — the maximum elapsed time permitted for one
  booking by one booking configuration. `0` disables this item-specific limit;
  the 366-day system safety limit still applies.
  _Avoid_: maximum occupancy, booking buffer
- **Double-booking** — permission for confirmed booking intervals on one
  bookable item to overlap. Opening intervals and time increments still apply.
  _Avoid_: unlimited availability, capacity
- **Time-slot booking** — one persisted reservation for one booking configuration.
  It stores a half-open UTC interval, its requester, optional purpose, state, and
  audit data. The system rejects durations over 366 days before acquiring the
  configuration lock, and the locked scheduling policy may impose a smaller
  maximum booking duration.
- **Booking privacy** — access to a booking's details is all or nothing in the
  Booking role iteration. Every caller authorized to read the booking
  configuration sees full event details; other callers cannot read its calendar.
  _Avoid_: busy-only Viewer
- **Half-open booking interval** — a booking window that includes its start and
  excludes its end. Two bookings that only touch at one boundary do not overlap.
- **Booking cancellation** — the one-way change from `CONFIRMED` to `CANCELLED`.
  Cancellation also soft-deletes the booking from normal Calendar reads; it does
  not physically delete the audit history.
