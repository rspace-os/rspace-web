---
status: accepted
---

# Trashing an instrument unlinks its identifier, locally only (RSDEV-1504)

## Context

An instrument holds at most one identifier. Until now, trashing the instrument left that
identifier active, which has two consequences. The PID stays claimed: `findActiveByIdentifierAndType`
still finds it, so a re-import of the same PID is refused with a 409 naming a record the
user cannot see, and the lookup page stamps the hit as already linked to it (ADR 0009).
And the identifier goes on existing against a record that has left the workspace.

Two kinds of identifier reach this path. A **linked identifier** is a PID another party
minted, attached on import; RSpace owns nothing at the provider for it. A **registered
identifier** is one RSpace minted itself through DataCite or B2INST, and RSpace does own
the provider record.

## Decision

Trashing an instrument soft-deletes every identifier it carries, **in RSpace only**. No
provider call is made, for a registered identifier as much as for a linked one: nothing is
deleted at the provider and nothing is retracted. Nico settled this on 2026-09-16.

The unlink is not undone by restoring the instrument. A restored instrument comes back
without its identifier, and the user re-imports or re-registers if they want one.

The Landing page field is left as it stands, unlike the explicit delete-identifier path,
which clears an address RSpace wrote (ADR 0006).

## Considered options

- **Retract what can be, then unlink.** A findable DataCite DOI would be moved back to
  `registered`, DataCite's own remedy for a DOI that should stop being discoverable, before
  the local unlink. Rejected: it makes a reversible workspace action reach out and change
  public registry state, and it cannot be applied evenly, because B2INST has no retract at all.
- **Delete at the provider.** Not available. DataCite deletes drafts only; registered and
  findable DOIs cannot be deleted. B2INST refuses deletion of a published record outright
  (403, verified on b2inst-test in ADR 0009).
- **Refuse to trash an instrument that carries a published identifier**, making the user
  delete or retract it first. Nothing would happen behind the user's back, but it leaves a
  published instrument undeletable and changes an existing endpoint's contract.
- **Reattach the identifier on restore.** Lossless, but it needs a rule for the PID having
  been imported onto another instrument while this one sat in the trash, which is exactly
  the situation the unlink exists to enable.
- **Leave the identifier alone and make the already-linked lookup skip trashed records.**
  A one-query fix for the re-import block on its own, but the identifier would stay visible
  on the trashed instrument and two active identifiers could then exist for one PID.

## Consequences

- A registered DOI outlives the instrument it described. It keeps resolving, and its RSpace
  public landing page stops being served the moment the identifier is soft-deleted, because
  `getLatestIdentifierByPublicLink` filters on `deleted = false`. For a findable DOI that
  means a live DOI pointing at an address RSpace no longer answers. Accepted here as the
  price of not touching the registry; a deployment that cares should retract the identifier
  before trashing the instrument.
- Trashing is reversible for the instrument and irreversible for its link, which is a
  sharper edge than the "Trash" label suggests. There is no confirmation dialog for trashing
  an Inventory record today, so nothing warns the user. Worth revisiting if it bites.
- ADR 0009 recorded that "the UI offers no way to unlink" and that removing a link needed the
  API. That is now only half true: the UI cannot unlink an identifier from an instrument the
  user keeps, but trashing the instrument unlinks it.
- Instrument templates are untouched. They can carry an identifier through the update path,
  but registering a PID for a template is not something the product does.
- Other Inventory record types are untouched too. A trashed sample or container keeps its
  IGSN, as before; changing that is a separate product decision.
