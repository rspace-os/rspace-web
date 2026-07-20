# 8. Terminal operations (Destroy): no output, empties the origin, adds a field to it

Date: 2026-07-20

## Status

Accepted

## Context

Every operation so far produced a new sample: the wizard collected a name, subsample count and
amounts, `buildOperationRequest` always emitted a `newSample`, and the backend always created one and
returned it. Destroy breaks all three assumptions. It disposes of a subsample: it sets the origin's
volume to zero, records a disposal date **on the origin itself**, and creates nothing. So it needs
three capabilities the framework did not have - producing no sample, adding a custom field to an
origin, and emptying an origin - plus a way to skip the now-irrelevant template and amounts steps.

Two constraints shaped the design. First, inventory subsample custom (extra) fields support only
text, number and link - there is **no native date type** (`ApiExtraField.ExtraFieldTypeEnum`). A real
date type exists only for sample *template* fields, which do not apply to an ad-hoc field on a
subsample. Second, adr/0001's thin, operation-agnostic backend: the backend applies what the request
says and never branches on the operation.

## Decision

- **`noOutput: true`** marks a terminal operation. The frontend builds no `newSample` (request
  `newSample` is null); the validator makes `newSample` optional (still requiring a name when one is
  present, and always at least one origin); the manager creates nothing and returns null. The backend
  stays operation-agnostic: "create a sample only if one was sent" is not operation-specific logic.
- **`effect.originFields`** adds custom fields to each origin subsample itself (as opposed to
  `textFields`, which go on the created sample). The backend gains `extraFields` on
  `ApiInventoryOperationOriginUpdate`; the manager applies them through the ordinary subsample-edit
  path (`updateApiSubSample`, each field `newFieldRequest`). This is the generalizable primitive - any
  future operation that annotates its origins reuses it.
- **`effect.emptiesOrigin: true`** sets the amount taken from each origin to that origin's **own full
  current quantity**, so its volume ends at zero. This reuses the existing decrement path unchanged:
  the backend clamps at zero, and taking exactly the full quantity is not over-removal (adr/0005), so
  no new backend quantity concept is needed.
- **The "disposed" field is a text field holding an ISO date** (`YYYY-MM-DD`), because a subsample has
  no native date field type. Its value - today - is computed **on the frontend** via a `today`
  Operation function in the registry (adr/0006), the same mechanism as Passage's number. The backend
  receives a plain text field and stays generic; the date is the user's local "today".
- **`steps`** lets an operation declare an explicit wizard-step subset. Destroy uses
  `["confirm"]` (it needs no input, so it goes straight to confirmation, skipping details, template
  and amounts); when omitted the wizard uses its default sequence. The operation's description is
  shown on the confirmation as an info panel, and the "cannot operate on an empty subsample" guard is
  enforced there too (Perform blocked with the reason shown), since there is no details step to hold
  it.

## Consequences

- No output, origin-field-add and origin-emptying are now config capabilities: a future terminal or
  annotating operation needs a config entry, not new wizard or backend code.
- Producing operations are unchanged: they omit `noOutput`/`originFields`/`emptiesOrigin`, send a
  `newSample`, and every existing behaviour holds.
- The backend stays operation-agnostic (adr/0001): it decrements origins, optionally adds fields to
  them, and optionally creates a sample - all driven by the request, none by the operation type.
- Field-name uniqueness is enforced on the origin, so disposing a subsample that already has a
  "disposed" field is rejected with a clear error rather than duplicating the field. Acceptable: a
  subsample is disposed once.
- The disposal date is the user's browser-local date. For a lab disposal record this is the desired
  meaning; it is not a server-authoritative timestamp.

## Alternatives considered

- **Stamp the date server-side.** Rejected: the backend would have to interpret a field as "today",
  which is operation-specific logic against the thin-backend principle (adr/0001). The registry
  computes it on the frontend instead, keeping the backend generic.
- **Skip template/amounts implicitly from `noOutput`.** Rejected in favour of an explicit `steps`
  list: step selection is then independently controllable and reads declaratively, at the cost of one
  extra config line.
- **A real date field type on the origin.** Unavailable: subsample extra fields are text/number/link
  only. A text ISO date is sortable and unambiguous, so it is the closest faithful representation.
- **A dedicated "set volume to zero" backend flag.** Rejected: sending the origin's full quantity as
  the amount taken reuses the existing, already-tested decrement-and-clamp path with no new concept.
