---
status: accepted
---

# Sample requests: a standalone request entity with a guarded state machine (RSDEV-1309)

Date: 2026-10-06. Covers the backend of RSDEV-1366 (create a request), RSDEV-1368
(approve, reject, cancel, fulfil), RSDEV-1512 (audit trail) and the backend parts of
RSDEV-1365 (the setting and `Sample.requestable`). The mechanics live in the code and its
tests; this ADR records the WHY and the traps.

## Context

A user needs material from a sample another user owns. Before this branch that happened
outside RSpace, so there was no record of who asked, who agreed and what was handed over.
The feature lets an owner mark a sample requestable, lets anyone ask for it with a note,
and gives the owner approve, reject and fulfil actions, with the requester able to cancel.
Fulfilling usually means transferring ownership of the sample, or of a new sample made from
it with an inventory operation (ADR 0011).

## Decisions

### D1 A standalone entity, not the legacy comms engine

`SampleRequest` and its `SampleRequestStatusChange` history are new entities with their
own DAO, manager and API, in the inventory packages. The ELN messaging system
(`model.comms`, `MessageOrRequest`) looks like a fit but is ELN-specific, untouched for
years and partly obsolete. Joining the two is a possible later step, not a starting point.

### D2 The request targets the Sample, with a free-text note

A request names a Sample, never a SubSample: the owner picks the source subsample when
preparing the material. The only payload is a required note (max 2000 characters), with no
structured quantity. The owner turns the note into an operation, and structure can be added
once real requests show what it should be.

### D3 Requestable is the gate, not read permission

An owner marks a sample `requestable` as their opt-in to being asked by anyone, so
requesting does not need read access to the sample. For the same reason the
`requestable=true` sample search is instance-wide, and `requestable` survives the public and
limited views of a sample. An explicit `ownedBy` still narrows that search.

Only the owner may change the flag. It cannot be set on create, only by updating an
existing sample.

### D4 Everything is behind `inventory.sampleRequests.available`

The setting is DENIED by default. While it is off for the caller:

- every `/sampleRequests` endpoint answers 404, so the resource behaves as absent;
- turning `requestable` on is refused with 422, but turning it off, or resending `true` on a
  sample already requestable, is allowed, so existing samples stay editable after an admin
  switches the feature off;
- `GET /samples?requestable=true` returns an empty page rather than an error, as befits a
  search filter.

Requestable flags set before the feature was switched off stay in the database; with the
gates above they are inert.

### D5 Who is asked is fixed when the request is raised

`originalOwner` records who owned the sample when the request was raised. The OWNER role
in listings ("received") follows it, so a request stays with the person who was asked even
after they give the sample away, and a transfer recipient never inherits someone else's old
requests in their inbox.

Acting on a request is a different question: approve, reject and fulfil belong to the
sample's current owner. A request is visible to its requester, the current owner and the
original owner; anyone else gets 404, so its existence is not disclosed.

### D6 Users are stored by username

`requesterUsername`, `originalOwner` and each history entry's `createdByUsername` are
usernames, not foreign keys, so a request and its history survive the deletion of either
party. The API returns them as user objects; a deleted user keeps their username with a
null id. Deleting a user deletes the requests against their own samples, with their
history.

### D7 A fixed transition table, enforced in one place

| To | From | Who | Reason |
| --- | --- | --- | --- |
| APPROVED | PENDING | current owner | not accepted |
| REJECTED | PENDING | current owner | required |
| CANCELLED | PENDING | requester | not accepted |
| FULFILLED | PENDING, APPROVED | current owner | not accepted |

REJECTED, CANCELLED and FULFILLED are terminal. PENDING is only written when the request
is raised. Approved requests can only be fulfilled: neither side can back out of an
approval, except through a transfer (D9). CANCELLED is kept separate from REJECTED because
"withdrawn by the requester" and "refused by the owner" are different facts in the record.

All transitions go through `updateStatus`, which checks the actor before legality, so a
caller who may not act at all learns nothing about the request's state. A user may hold
several open requests for the same sample; there is no uniqueness rule.

### D8 Status history is a table, not the audit log

Every state, including the initial PENDING, is a `SampleRequestStatusChange` row, so the
details view reads the history in one query. RSpace's audit trail is written to log files,
not a table, so it can never feed a details view. Envers was rejected: it suits records
that change in many ways and may be restored, not a linear state history.

### D9 A transfer rejects the open requests

Changing a sample's owner rejects every PENDING or APPROVED request against it, with a
reason naming the new owner. The new owner did not agree to anything the previous owner was
asked, so carrying the requests across would be wrong. The request being fulfilled is
already FULFILLED by then, so it is untouched. Open question: an open request raised by the
recipient themselves is rejected too, although they now own the sample.

### D10 Concurrency: the row lock first, a constraint behind it, 409 for the rest

`updateStatus` and the transfer's auto-reject load the request with `PESSIMISTIC_WRITE`,
so two concurrent transitions serialise and the second sees the first's result. The
locking read must be the transaction's first read: MariaDB 11.6+ enables
`innodb_snapshot_isolation` by default, and locking a row changed since an earlier read in
the same transaction then fails with error 1020 instead of waiting. A unique constraint on
(request, status) backs up the lock, since no status is reachable twice.

Where 1020 still occurs, for example a transfer racing a fulfilment, the API returns
409 `EDIT_CONFLICT` ("changed by someone else at the same time"), not 500. Only 1020 is
mapped; deadlocks and lock timeouts stay 500 for now.

### D11 The fulfilled-with sample, by global id, on FULFILLED only

`transferredSampleGlobalId` on the status PUT optionally records which sample the request
was fulfilled with: the requested sample itself, or a new one made from it. It is a global
id, matching `sampleGlobalId` on create. It is only accepted with FULFILLED (422 otherwise),
must name a sample (422) and must be readable by the fulfiller (404), so a request's history
cannot be made to carry a stranger's sample. It is stored as a foreign key, not a global id
string, and nulled when that sample's owner is deleted. Responses show it limited to what
the viewer may read.

### D12 The request has no global id of its own

Requests use a plain numeric id. The epic's global-id requirement is about references to
samples and users, not the request's own identity.

### D13 Audit: a separate REQUEST domain

Each change logs one of REQUEST_SENT, REQUEST_APPROVED, REQUEST_REJECTED,
REQUEST_CANCELLED and REQUEST_FULFILLED under its own REQUEST domain, with the requested
sample's global id as the audit identifier, so the audit page links to the sample. A
separate domain keeps the mechanism generic for requests on other item types. Events are
published by the manager and written by a listener after commit, so a rolled-back change
is never logged. REQUEST_SENT's description names the owner who was asked, since the User
column already shows the requester.

## Smaller decisions worth keeping

- Listings are newest first by default. `orderBy` accepts `creationDate asc|desc` only;
  requests have no name, type or modification date to sort by.
- Requests against a trashed sample drop out of listings but stay readable and actionable
  by id, since the sample can be restored.
- Validation errors: malformed body 400, wrong global-id type or illegal transition 422,
  missing or unreadable sample 404.
- Notifications to the owner and requester are not built (RSDEV-1353).

## Rejected alternatives

- Reusing `model.comms` for requests (D1).
- An approver column fixed at creation as the actor for approve/reject: it would let a
  previous owner act on a sample they no longer hold (D5).
- Moving open requests to the new owner on transfer (D9).
- Storing users and the transferred sample as global-id strings: the codebase stores ids
  and clears them on deletion (D6, D11).
- Envers or the audit log as the status history (D8).
