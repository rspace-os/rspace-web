# 1. Frontend-declared operations, executed by a thin generic atomic backend endpoint

Date: 2026-07-10

## Status

Accepted

## Context

RSDEV-1231 introduces an Inventory "operation wizard" framework. Every operation
(Derive first, then Cryopreserve, and later Aliquot, Pool, Revive, Passage,
Dispose) has the same shape: select one or more origin subsamples, gather user
input, then create one new Sample that parents N new subsamples, attach a typed
relation link from the new records back to the origin(s), and adjust the origin's
quantity.

Two forces shape the design:

1. **Adding a new operation must be cheap and must not require backend code.** The
   product intent is that operations become configuration, which is also the
   substrate a future "end users configure their own operations" capability would
   need.
2. **The effect must be atomic.** The composite mutation (create sample +
   subsamples, add fields + links, change origin quantity) must roll back as a
   unit on any partial failure.

The building blocks already exist in the base (post RSDEV-1131): a `link`-typed
field carrying `{ relationType, targetGlobalId }`, the `DataCiteRelationType`
vocabulary validated for inventory record-to-record links, and
`POST /api/inventory/v1/samples` which creates a Sample + N subsamples +
extraFields (including links) + storage temperature in one call. What is missing
is atomicity across the sample-creation call and the origin-quantity update: the
bulk endpoint cannot mix CREATE and UPDATE, so combining them from the client is
non-atomic.

## Decision

- Operations are declared in a single `operations_config.json` that lives with the
  **frontend** code. It defines each operation's key, label, applicability
  (subsample only; at least two for Pool), the wizard inputs, and the declarative
  effect. The frontend reads it, renders the wizard, and resolves the user's
  inputs into a concrete mutation request. All observable effects of an operation
  are authored here, next to the UI that surfaces them.
- A single new backend endpoint, `POST /api/inventory/v1/operations`, is a **thin,
  generic, atomic coordinator**. It validates the incoming request and orchestrates
  existing service-layer managers (sample + subsample creation, link/extra fields,
  quantity updates) within one `@Transactional` method that rolls back on any
  failure.
- There is **no `operationType` enum, no switch, and no per-operation Java**. The
  endpoint applies whatever mutation the request describes. Adding an operation is
  a new `operations_config.json` entry and nothing else.
- The endpoint receives a client-built payload, so it enforces all permissions and
  invariants **generically, server-side**, reusing existing Inventory validators
  (origin readable/editable, link target readable, relation type valid, quantity
  non-negative, unit compatible). It coordinates; it does not blindly trust.

## Consequences

- Adding an operation needs no backend change, and its effects are visible and
  authored in the frontend config.
- Atomicity is guaranteed by a single server transaction; partial failures roll
  back cleanly.
- This is the substrate for a future "end users configure operations" capability:
  moving the definitions from a checked-in file to data is the main step, with no
  change to the coordinator.
- The generic request schema must be expressive enough for every planned operation
  even though only Derive and Cryopreserve are built now. In particular Dispose
  creates no new Sample and adds a field to the origin, and Pool has multiple
  origins and multiple links; the schema is designed with those in mind.
- This deviates from the epic's "backend / data-model changes out of scope"
  boundary: one new thin endpoint is added. No new data model is introduced.

## Alternatives considered

- **One endpoint per operation** (`/operations/derive`, ...). Rejected: a new
  controller + service per operation defeats the "operations are config" goal.
- **Single endpoint switching on `operationType`.** Rejected by the team: still
  requires backend code for each new operation.
- **Pure frontend orchestration of the existing non-atomic calls.** Rejected: a
  failure between "create new sample" and "decrement origin" leaves inconsistent
  state with no rollback.
