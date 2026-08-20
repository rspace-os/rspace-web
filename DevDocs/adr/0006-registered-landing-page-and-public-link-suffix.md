---
status: accepted
---

# Registered landing page and the pre-generated public link suffix (RSDEV-1254)

## Context

A PIDINST LandingPage is baked into a citable PID the moment a curator accepts
the B2INST record, and RSpace has no way to update a published record
afterwards. Until now the LandingPage RSpace registered was the instrument's
Landing page field, which RSpace itself auto-filled with the record's
`/globalId/IN…` address whenever a user left it blank — a page that requires an
RSpace sign-in, which is unfit as the public face of a citable identifier.

RSpace already has an anonymous public page per identifier, addressed by the
identifier's `publicLink` (`/public/inventory/<suffix>`), used by IGSN. But
that suffix was generated inside `DigitalObjectIdentifier`'s constructor,
i.e. only *after* the provider had answered — too late to register the page's
address with the provider.

## Decision

1. The public link suffix is generated on the `ApiInventoryDOI` DTO when a new
   identifier registration begins (`SecureStringUtils.getURLSafeSecureRandomString(16)`,
   the same algorithm the entity used), and is threaded into
   `DigitalObjectIdentifier` through a new constructor parameter so the entity's
   `publicLink` names the same page that was registered. On the B2INST path
   generation happens *before* the provider call, because that payload carries
   the address. On the DataCite/IGSN path the DTO is constructed from the
   provider's response, so generation follows the call; nothing there consumes
   the address beforehand, so the invariant is unaffected. What matters on every
   path is that generation precedes entity creation.
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

   The tail is matched against the address's *normalised* path, not its raw
   text: query and fragment dropped, dot segments resolved, percent-escapes
   decoded, trailing slashes stripped, case folded. None of those change which
   page an address names, so all of them have to compare equal. An exact match let a default a user had
   since edited to `.../globalId/IN5/` or `.../globalId/IN5?from=email` read as
   user-typed, and registered the login-walled page after all. Two consequences
   are accepted, both erring towards omission: a deliberate link to some *other*
   RSpace's `/globalId/<same id>` is discarded in favour of this identifier's
   public page, and a differently-cased global id is treated as the default even
   though it may resolve to nothing — an address that resolves to nothing is no
   more fit to register. The same recognition now also decides whether the field
   counts as empty for the write in item 4, so one rule governs both.

   A value also has to be an address a resolver can follow: only an absolute
   `http`/`https` URL is registered. A typed value that is not falls back to the
   public landing page, and the substitution is logged at WARN, since the field
   goes on displaying what the user typed and nothing else would tell them. The
   same rule is applied *again* after the fallback, because the public landing
   page is built from the deployment's server URL and nothing validates that for
   a scheme — a deployment configured as `rspace.example.org` would otherwise
   register exactly the scheme-less form we refuse from users. Failing that
   second check omits the property rather than falling back further. The field's own validation is just `new URI(...)`
   parsing, which accepts a bare host (`lab.example.org/aws-42`), a relative
   path, and non-web schemes such as `javascript:`. RSpace already refuses to
   emit a site-relative address of its own, so accepting a typed one would be
   inconsistent, and PIDINST defines LandingPage as the page the identifier
   resolves to.

   Because LandingPage is *mandatory* in the PIDINST 1.0 schema that this path
   asserts, omitting it is logged at WARN naming the record, so an operator can
   see why a mandatory property left RSpace empty instead of hearing it from a
   curator.
3. The auto-fill is retired. A blank Landing page stays blank until a user types
   a value or an identifier is registered. It previously ran on every instrument
   save — create, update and duplicate — so a user who cleared the field got the
   globalId address written straight back on the next save, and every instrument
   carried a login-walled address whether or not it was ever registered. Values
   the old fill already wrote stay in the data and are read as an empty field.
4. Registering an instrument identifier writes the registered address into the
   instrument's **Landing page field**, but only when that field held no address
   the user typed. This is what stops the field and the registered value
   drifting apart, and it is what lets item 3 retire the auto-fill without
   leaving the field permanently empty. It applies to both PIDINST provider
   paths, B2INST and DataCite, so the same user action leaves the instrument
   looking the same whichever provider a deployment has enabled — even though
   DataCite has no LandingPage property and so transmits the value nowhere. The
   write lands only after the provider has accepted the registration, so a
   failed registration leaves the field as it was.
5. Deleting an identifier clears the address it wrote back out of the Landing
   page field, so an instrument never keeps pointing at a public page that no
   longer exists. Only an address RSpace wrote is cleared, recognised by its
   `/public/inventory/<suffix>` tail for the suffix of the identifier being
   deleted: a value the user typed is theirs and survives, and an address
   belonging to a *different* identifier is left alone. The same asymmetry as
   item 4, applied in reverse.
6. The generated URL is still never persisted as the identifier's `publicUrl`
   (PUBLIC_URL), which keeps its publish-time semantics: the Inventory UI links
   an identifier row to `publicUrl` whenever it is present, and a draft B2INST
   identifier must keep linking to the provider record page rather than to a
   public page that answers 404 until the identifier is published. The
   landing-page preview already works from `rsPublicId`. Item 4 writes an
   instrument *field*; it does not touch the identifier's own URLs.

## Considered options

- Persisting the generated URL as `publicUrl` from draft onwards — rejected:
  it would replace the working provider-record-page link on draft identifiers
  with a dead one.
- Always registering the public landing page, ignoring the Landing page field
  — rejected: a user-typed institutional landing page is exactly what PIDINST's
  LandingPage is for, and the field would silently become documentation-only.
- Generating the suffix in `ApiInventoryDOI`'s no-args constructor — rejected
  for the `applyChangesToDatabaseDOI` leak described above.
- Keeping the auto-fill and letting the field differ from the registered value
  — rejected, and this ADR previously accepted it. The field is what users read
  and edit, so a field showing a login-walled address while a different address
  was registered is a discrepancy nobody can see the reason for.
- Writing the field *before* the provider call, so the adapter could simply
  register whatever the field holds — rejected: a registration that then failed
  would leave the instrument permanently carrying the address of an identifier
  that does not exist.
- Clearing the Landing page unconditionally when an identifier is deleted —
  rejected: it would throw away an institutional landing page the user typed,
  which the registration path deliberately never touched.
- Migrating the values the old fill wrote to blank in a Liquibase changeset —
  rejected: an irreversible bulk edit that cannot distinguish a value RSpace
  wrote from the same value a user typed. Reading them as empty achieves the
  same outcome lazily and reversibly.

## Consequences

- An instrument nobody has registered and whose user typed nothing now shows a
  blank Landing page where it used to show the globalId address. That is the
  intent: the field states what someone chose, not what RSpace could derive.
- The registered value and the field agree from registration onwards, so the
  drift an earlier revision of this ADR accepted no longer exists.
- The field is now RSpace's to fill and to empty for as long as no user has
  typed in it, and the user's to keep the moment they do. That single asymmetry
  is what items 4 and 5 both follow from.
- One residual drift is accepted. A typed value that no resolver could follow
  (no scheme, say) is not registered — the public page is registered instead —
  but it is still the user's value, so it is neither overwritten nor cleared.
  The substitution is logged at WARN, since the field goes on showing their text
  and nothing else would tell them.
- Because a legacy auto-filled value reads as empty, registering overwrites it.
  A user who deliberately typed some RSpace's `/globalId/<same id>` address
  loses it. Accepted for the same reason as in item 2: an address needing a
  sign-in is unfit to register either way.
- The registered public landing page answers 404 until the identifier reaches
  the published ("findable") state. B2INST identifiers do not reach that state
  until curator-acceptance state sync exists (separate epic work); the address
  registered now is nevertheless permanent and correct, which is the point of
  generating the suffix up front.
- `DigitalObjectIdentifier` lives in rspace-core-model, so the constructor
  change rides a core-model release and a pinned-version bump here.
