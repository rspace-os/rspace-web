# 4. Every operation has a process name (user-entered or fixed)

Date: 2026-07-16

## Status

Accepted

## Context

The operation wizard originally gave only Derive a "process name" (an input wired
via `effect.processNameFrom`). Remembered defaults were keyed per process name for
Derive, but per bare operation key for operations without one (Cryopreserve).

Two new features make a process name load-bearing for *every* operation:

- **Remembered process values** are saved and recalled under a single per-process
  key (see ADR-0003 and the single "remember" checkbox).
- The **Derived Sample name** is auto-derived as `"<origin name> <process name>"`.

Both need a process name to key/seed off, uniformly, for every operation. But
Cryopreserve has no natural free-text process the user would type.

## Decision

- **Every operation has a process name.** Operations that expose it (Derive) let the
  user enter or select it as free text in a field placed first on wizard step 1.
  Operations that do not expose a field (Cryopreserve) carry a **fixed** process
  name (Cryopreserve's is `"cryopreserve"`).
- The process name is the single key for Remembered process values and the seed for
  the Derived Sample name, with no per-operation special-casing downstream.
- The operation definition expresses which case applies: an operation either declares
  a user-entered process-name input (`processNameFrom`) or resolves to a fixed
  process name. Absence of a user field means "fixed", defaulting to the operation's
  own key/label.

## Consequences

- Uniform keying and naming: the remember checkbox and the sample-name derivation
  need one code path, not one per operation.
- Cryopreserve's process name `"cryopreserve"` is not an editable field, but it does
  surface to the user in the remember-checkbox label ("remember values for this
  process: cryopreserve") and in the derived sample name.
- Mild surprise for a future reader: an operation with no visible process-name field
  still has a process name internally. This ADR is the pointer that explains why.

## Alternatives considered

- **Keep process name Derive-only; special-case Cryopreserve keying by operation
  key.** Rejected: the single-checkbox persistence and the sample-name derivation
  would each need two branches (has-process-name vs not), which is exactly the
  per-operation coupling the framework avoids.
- **Make the process name a user-entered field for every operation, including
  Cryopreserve.** Rejected: there is no meaningful user choice for Cryopreserve, and
  an editable field implying one would mislead; a fixed value is clearer.
