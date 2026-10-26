---
status: accepted
---

# Sample requests: a standalone request entity with a guarded state machine

Date: 2026-10-06. Covers the backend: the setting and `Sample.requestable`, creating a
request, approving, rejecting, cancelling and fulfilling it, and the audit trail. The
mechanics live in the code and its tests; this ADR records the WHY and the traps.

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

Marking a sample `requestable` opts it in to being asked for by anyone, so requesting does
not need read access to the sample. For the same reason the `requestable=true` sample
search is instance-wide, and `requestable` survives the public and limited views of a
sample. An explicit `ownedBy` still narrows that search, and trashed samples never appear in
it, whatever `deletedItems` says, since they cannot be requested. Its name filter (`query`)
applies to requestable searches only; other sample searches go through Lucene as before.

The flag may be changed by whoever may both edit and transfer the sample
(`InventoryPermissionUtils.canUserChangeRequestable`): its owner, or a PI or Lab Admin of
the owner's group who may also edit it. A group member who may only edit the sample is
refused with 422. A PI cannot change it on a member's sample shared with no one
(`OWNER_ONLY`): they may transfer that sample but not edit it, so the owner's choice to
keep it private is not overridden. Responses mark who may change it with the
`CHANGE_REQUESTABLE` permitted action, from the same rule. Requests still go to the owner,
so a PI can put a member's shared sample up for requests: a lesser power than
giving the whole sample away, which they can already do. The flag cannot be set on create,
only by updating an existing sample, and it survives a transfer: the new owner, including a
requester who has just received it, keeps it requestable until they switch it off.

### D4 Everything is behind `inventory.sampleRequests.available`

The setting is DENIED by default. While it is off for the caller:

- every `/sampleRequests` endpoint answers 404, so the resource behaves as absent;
- turning `requestable` on is refused with 422, but turning it off, or resending `true` on a
  sample already requestable, is allowed, so existing samples stay editable after an admin
  switches the feature off;
- `GET /samples?requestable=true` returns an empty page rather than an error, as befits a
  search filter.

The setting is hierarchical, so one community can switch it off while others keep it on. A
request is therefore also refused, with the same 422 as a sample that is not requestable,
when the setting is off for the sample's owner: their community could not act on it. The
requestable search still lists such samples; hiding them would need a per-community filter
in the query, which is not worth it.

Requestable flags set before the feature was switched off stay in the database; with the
gates above they cannot be acted on. Request validation (a malformed body, query parameter
or global id: 400 or 422) runs before the feature check, so a malformed call can tell that
the endpoint exists; we accept that, as nothing about any request is disclosed.

### D5 Who is asked is fixed when the request is raised

`originalOwner` records who owned the sample when the request was raised. The OWNER role
in listings ("received") follows it, so a request stays with the person who was asked even
after they give the sample away, and a transfer recipient never inherits someone else's old
requests in their inbox.

Acting on a request is a different question: approve, reject and fulfil belong to the
sample's current owner. A request is visible to its requester, the current owner and the
original owner; anyone else gets 404, so its existence is not disclosed.

### D6 Users are stored by id and username, without foreign keys

The requester, the original owner and each history entry's actor are stored as a user id
(`requesterId`, `originalOwnerId`, `createdById`) and a username, with no foreign key to
`User`, so a request and its history survive the deletion of either party and never block
it. Access, listings, the requester's actions and the automatic fulfilment on transfer all
match on the id. A username can pass to a different person once its holder is deleted (a new
local signup, or an SSO identity provider reassigning it), but an id is never reused, so the
new account does not inherit the deleted user's requests. The username stays for display:
the API returns each party as a user object, and a deleted user keeps their id and username,
with no name. Deleting a user deletes the requests against their own samples, with their
history.

### D7 A fixed transition table, enforced in one place

| From | To | Who | Reason |
| --- | --- | --- | --- |
| PENDING | APPROVED | current owner | not accepted |
| PENDING, APPROVED | REJECTED | current owner | required |
| PENDING | CANCELLED | requester | not accepted |
| PENDING, APPROVED | FULFILLED | current owner | not accepted |

REJECTED, CANCELLED and FULFILLED are terminal. PENDING is only written when the request
is raised. The owner can withdraw an approval by rejecting it, which is also what a
transfer to someone else does to it (D9), but the requester cannot cancel an approved
request: the owner may already be preparing the material. CANCELLED is kept separate
from REJECTED because "withdrawn by the requester" and "refused by the owner" are
different facts in the record.

All transitions go through `updateStatus`, which checks the actor before legality, so a
caller who may not act at all learns nothing about the request's state. The transfer's
status changes (D9) check legality against the same table but not the actor, since a PI or
admin of the owner's group may make the transfer. A user may hold several open requests for
the same sample; there is no uniqueness rule.

### D8 Status history is a table, not the audit log

Every state, including the initial PENDING, is a `SampleRequestStatusChange` row, so the
details view reads the whole history from one table. RSpace's audit trail is written to log
files, not a table, so it can never feed a details view. Envers was rejected: it suits
records that change in many ways and may be restored, not a linear state history.

### D9 A transfer closes the open requests: fulfilled for the recipient, rejected for others

Changing a sample's owner closes every PENDING or APPROVED request against it, through the
table's transitions (D7), in the same transaction as the transfer:

- every request raised by the new owner is FULFILLED, recording the sample itself as the
  transferred sample and a reason saying it was fulfilled by the transfer. They now hold
  what they asked for, so rejecting it would put the opposite of what happened in the
  record.
- every other request is REJECTED, with a reason naming the new owner. The new owner did
  not agree to anything the previous owner was asked, so carrying the requests across
  would be wrong.

Fulfilling by transfer came late: the original idea was that a request asks for something
to be done with the sample, with the target sample itself rarely changing hands. Closing
the recipient's request inside the transfer lets a client hand the sample over with a
single change-owner call, so the transfer and the fulfilment succeed or fail together. A
request already marked FULFILLED before the transfer is no longer open and is untouched, so
the older fulfil-then-transfer sequence still works.

### D10 Concurrency: the row lock first, a constraint behind it, 409 for the rest

`updateStatus` and the transfer's status changes load the request with `PESSIMISTIC_WRITE`,
so two concurrent transitions serialise and the second sees the first's result. The
locking read must be the transaction's first read: MariaDB 11.6+ enables
`innodb_snapshot_isolation` by default, and locking a row changed since an earlier read in
the same transaction then fails with error 1020 instead of waiting. A unique constraint on
(request, status) backs up the lock, since no status is reachable twice; it is not expected
to fire, and if it did the client would get a 500 with nothing saved.

Raising a request takes a shared lock on the requested sample, as its first read. A
transfer saves the owner change before it locks the sample's open requests, so if one is
being raised at the same time the transfer waits for it to commit and then closes it (D9),
instead of leaving it PENDING and addressed to the previous owner. With snapshot isolation
the waiting transfer is refused with 1020 instead, as it has read the sample before. Only
raising a request takes the shared lock: the fulfilled-with sample is read without one.

Where 1020 still occurs, for example a transfer racing a fulfilment, the API returns
409 `EDIT_CONFLICT` ("changed by someone else at the same time"), not 500. Deadlocks
(1213) are mapped the same way: a fulfilment naming the request's own sample can deadlock
with a transfer of it, because saving the history row checks its foreign key to the sample.
MariaDB rolls the loser back, so nothing is saved and the client can retry. Lock wait
timeouts stay 500 for now. The mapping is in `ApiControllerAdvice`, so it applies to every
API endpoint, not only sample requests: for example, one user creating samples in parallel
now gets 409 instead of 500.

### D11 The fulfilled-with sample, by global id, on FULFILLED only

`transferredSampleGlobalId` on the status PUT optionally records which sample the request
was fulfilled with: usually the requested sample itself or a new one made from it, but any
sample the fulfiller can fully read is accepted. It is a global id, matching
`sampleGlobalId` on create. It is only accepted with FULFILLED (422 otherwise),
must name a sample (422), and must be fully readable by the fulfiller and not in the trash
(404), so a request's history cannot be made to carry a stranger's sample. Limited read,
as granted by a shared container or a List of Materials, is not enough. It is stored as a
foreign key, not a global id string, and nulled when that sample's owner is deleted.
Responses show it limited to what the viewer may read.

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
  requests have no name, type or modification date to sort by. A value that is invalid
  for every inventory listing also fails the inherited pattern, so its 400 lists both.
- Requests against a trashed sample drop out of listings but stay readable and actionable
  by id, since the sample can be restored.
- Validation errors: malformed body 400, wrong global-id type or illegal transition 422,
  missing or unreadable sample 404.
- Notifications to the owner and requester are not built yet.

## Rejected alternatives

- Reusing `model.comms` for requests (D1).
- An approver column fixed at creation as the actor for approve/reject: it would let a
  previous owner act on a sample they no longer hold (D5).
- Moving open requests to the new owner on transfer (D9).
- Storing the transferred sample as a global-id string: the codebase stores ids and clears
  them on deletion (D11).
- Envers or the audit log as the status history (D8).
