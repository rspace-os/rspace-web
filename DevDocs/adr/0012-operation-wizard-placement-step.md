---
status: accepted
---

# Operation wizard placement step: create, then move (RSDEV-1259)

Date: 2026-10-06. Builds on DevDocs/adr/0011. The developer notes are
`DevDocs/DeveloperNotes/InventoryOperationWizard.md`.

## Context

The six creating operations put their new subsamples on the user's workbench, and the
user then moves them with the Move dialog. RSDEV-1259 adds an optional Location step so
the wizard places them in one go. The product owner decided (2026-10-06) that the
subsamples are created on the workbench first and then placed. If placing fails, they
stay on the workbench and the user is told. The operation is never reported as failed
because of placement.

## Decision

The wizard calls the operations endpoint unchanged, then, when the user chose a
container, calls the existing bulk endpoint with `operationType: "MOVE"` and
`rollbackOnError: true`, one record per new subsample taken from the response's
`sample.subSamples`. No backend, schema or OpenAPI change.

The server validates the move as it does for the Move dialog: permission, capacity and
empty locations, plus the usual move audit event.

## Rejected alternative

Destination fields on each operations request, with the server placing inside the
creating transaction. The decision above asks for exactly the create-then-move
outcome. Placing in the request would need either exception handling inside the
creating transaction, so a bad destination does not roll back the operation, or a
duplicate pre-check, for the same visible result. Revisit only if API clients (for
example RSDEV-1309 fulfilment) need placement in one call.

## Consequences

- Not atomic, by design. A crash or a refused move between the two requests leaves the
  new subsamples on the workbench, which is the stated fallback. The wizard shows a
  warning and still closes, because the origins have already been charged and a retry
  would charge them again.
- All or nothing. `rollbackOnError: true` means either every new subsample is placed or
  none is, which matches one destination per run.
- The client's capacity check can go stale: someone may fill the box before Perform. The
  server then refuses the move and the fallback above applies.
- Only the container is remembered, never grid locations, so a remembered grid container
  withholds the step-one fast path.

## Trap: ContainerModel's move checks read the global moveStore

`ContainerModel.canStoreRecords`, `hasEnoughSpace`, `canStoreRecordTypes` and
`movingIntoItself` read `getRootStore().moveStore.selectedResults`. That selection is
empty in the wizard, so they report that anything fits. `placementBlocker` reads the
container's own fields (`availableLocations`, `canStoreSamples`, `canEdit`, `deleted`,
`cType`) instead.

The step also configures `container.contentSearch`, the same observable instance the
container's own page renders. The wizard restores its defaults and clears the location
selection when the container changes or the wizard closes (`releaseContainer`).
