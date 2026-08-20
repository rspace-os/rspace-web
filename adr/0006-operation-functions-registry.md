# 6. Operation functions: a dev-authored code registry for effects config cannot express

Date: 2026-07-18

## Status

Accepted

## Context

Operations are declared as data (adr/0001): a config entry plus i18n, no per-operation
Java or wizard code. The declarative vocabulary covers a fixed set of effects - create a
sample + subsamples, links, text fields, storage temperature, an origin decrement.

Passage broke that ceiling. Its "Passage number" field must be `parent sample's passage
number + 1`, or `1` when the parent has none - a value **computed** from the origin's
parent sample, which no declarative primitive expressed. The first implementation added a
bespoke `originParentCounter` primitive (schema field + wizard code) for exactly that one
computation. That does not scale: every new computation shape (a different transform, a
different source) would need another one-off primitive, and the config vocabulary would
grow a special case per operation - the opposite of the framework's intent.

Three ways to let config express a computation:

1. Keep adding narrow declarative primitives (the `originParentCounter` path).
2. Embed a general expression language / user-supplied functions evaluated at runtime.
3. A curated registry of named functions **in code**, selected and fed by config.

## Decision

An **operation function registry** (`operationFunctions.ts`): named, pure **Operation
functions** live in code. `operations_config.json` gains `effect.computed[]`, where each
**computed value** declares which function to apply, how to source each of its arguments
(a named field on the origin's parent sample, a literal constant, or another wizard
input's value), and which input to write the single result into. The wizard resolves the
arguments at submit and applies the function; existing effect wiring (e.g. `textFields`)
then consumes the result. Computed values are evaluated in array order, so a later one may
read an earlier one's result via an `input` argument.

Adding a computation is therefore a **new registry function referenced from config** - the
wizard and backend are untouched and stay operation-agnostic. A function declares only its
parameter names (config binds the sources), so one function is reusable across operations.
Config references are validated at module load (the function must exist and its parameters
must match the bound arguments), failing fast like the existing valibot parse.

This is deliberately a **dev-only extension point, not an end-user expression language**.
Only developers edit the registry, so config never carries executable logic and there is
no injection/security surface; the flexibility of "any computation" is bought with a small
code change in one file, not with a DSL.

## Consequences

- New computation shapes are localized to the registry and reusable, instead of scattered
  as per-operation primitives or wizard branches.
- The "operations are pure config, no code" promise is now precisely scoped: it holds for
  effects already in the declarative vocabulary; a genuinely new computation is code in the
  registry (small, isolated, tested), referenced from config.
- Operation functions must be defensive: an argument may resolve to `undefined` (e.g. an
  absent field), so each function handles missing inputs.
- `parentSampleField` matches by the field's locale-resolved name, so a record created in
  one locale and operated on in another would not match (inherent to storing localized field
  names as data; acceptable in the English-only window per `CONTEXT.md`).
- Replaces the bespoke `originParentCounter`; Passage's passage number is now the registry
  function `increment` wired via `effect.computed`.

## Alternatives considered

- **A bespoke primitive per computation** (`originParentCounter`, and one more each time).
  Rejected: unbounded special-case sprawl in the config schema and wizard.
- **An expression language / user-defined functions in config.** Rejected: a large design
  with a real security surface (executable logic from config), and unnecessary - operations
  are authored by developers, so a code registry gives the same power with none of the risk.
- **Hardcode the computation in the wizard, branching on the operation key.** Rejected:
  violates adr/0001's operation-agnostic wizard; the wizard must never know an operation by
  name.
