# Context Glossary

The shared, canonical vocabulary for this project. Terms are added as they are
resolved during design. This file is a glossary only — no implementation details.

## Internationalization (i18n)

- **Canonical translation catalog** — i18next JSON. The runtime and
  translator-facing catalog format. The English (`en-US`) base JSON is generated
  from frontend `defaultValue`s; translated locale JSON is owned by the future
  translation workflow. Everything else (Spring `.properties`) is derived from
  JSON catalogs.
- **Generated bundle** — a Spring `.properties` file produced *from* the canonical
  JSON for backend-rendered text. Never edited by hand or by translators.
- **Namespace** — a named slice of translations, one per product module
  (`workspace`, `gallery`, `inventory`, `groups`, `dashboard`, `admin`, `apps`,
  `system`, `public`) plus a shared `common`. Loaded independently.
- **Base file** — the English (`en-US`) file for a namespace. The monolingual
  source/template every other locale is translated against. Locale codes are
  region-qualified BCP 47, hyphenated (`en-US`, `zh-TW`, `zh-HK`) at runtime;
  the underscore form (`en_US`) belongs only to the generated Java bundle.
- **Key** — a stable structured dot-notation identifier (`ns:section.name`) that
  names a string independently of its wording. Distinct from the **default
  value** (the English source text co-located at the call site).
- **Default value** — the English source string supplied inline at the `t()` call
  site and extracted into the `en-US` base file. Mandatory for every key; copy
  changes start in code and regenerate the base JSON, not by hand-editing
  `en-US` JSON.
- **ICU MessageFormat** — the message syntax used for all keys (plurals, gender/
  `select`, ordinals, inline number/date formatting).
- **Extraction** — the build step (via `i18next-cli`) that scans code for keys +
  default values and synchronizes the `en-US` base files; also the source of the
  generated key types and the unused-key check.
- **Ratchet** — the per-module enforcement progression: a converted module flips
  its `noJsxLiterals` lint rule to `error`, so the gated (fully-converted)
  surface only ever grows.
- **No-orchestration gap** — the period before Weblate is connected. While the
  product is English-only, this gap is invisible: every string falls back to its
  English default.

## Inventory operations wizard

- **Operation** — a user-initiated Inventory action that consumes one or more
  origin subsamples and produces one new Sample parenting N new subsamples, while
  recording a typed relation link from the new records back to the origin(s), and
  optionally changing the origin's quantity. Named instances: Derive,
  Cryopreserve, Aliquot, Pool, Revive, Passage, Dispose.
- **Origin** — the existing subsample(s) selected as input to an Operation. Only
  subsamples are eligible; never a Sample, Container, or Instrument. An Operation
  may decrement, increment, or leave unchanged an Origin's quantity.
- **Derived Sample** — the single new Sample an Operation creates, and the parent
  of every subsample that Operation creates. Distinct from the Origin's own parent
  Sample.
- **Process name** — a label for the kind of process an Operation run represents
  (e.g. "dna extraction"). Every Operation has one: free-text and user-selectable for
  Operations that expose it (Derive), or a fixed value for those that do not
  (Cryopreserve's Process name is "cryopreserve"). It scopes Remembered process
  values and seeds the Derived Sample's name.
- **Template choice** — the user's per-run decision about the Derived Sample's
  template: the Origin's parent Sample's own template (available only when it has
  one), an existing template, or none (ad-hoc). The wizard never creates a template;
  a template-less parent must have one made in a separate step first.
- **Created subsample** — a new subsample produced by an Operation, parented by the
  Derived Sample.
- **Created amount** — the quantity assigned to each Created subsample. Independent
  of the Origin's quantity change (material may be added or removed during the
  operation). Expressed in the chosen template's measurement category when a template
  is selected, otherwise the Origin's.
- **Amount taken** — the quantity removed from the Origin by the Operation (a
  **positive** decrement; must be > 0), expressed in the Origin's own measurement
  category. It must not exceed the Origin's current quantity: over-removal is rejected
  rather than silently clamped. Independent of the Created total (material may be
  added during the operation).
- **Relation link** — a typed link (a DataCite relation such as IsDerivedFrom,
  IsPartOf, HasPart) held on the Derived Sample and pointing back to the Origin(s).
  Links are one-directional: only the newly created records link to the Origin; the
  Origin's back-references are shown by the existing "items that link to" panel, not
  by a reciprocal link.
- **Operation definition** — the declarative description of one Operation: its
  applicability, wizard inputs, and effects. Authored as data; effects that the
  declarative vocabulary cannot express are supplied by an Operation function
  (below), keeping the definition itself data.
- **Computed value** — a value an Operation produces at submit by applying an
  Operation function to configured arguments, written into a named input slot that
  the Operation's effect wiring then consumes. Declared in the Operation definition.
  _Avoid_: derived value, custom field.
- **Operation function** — a named pure function held in code (the operation function
  registry), selected by a Computed value and handed resolved argument values; it
  returns a single value. A new computation is a new registry function referenced from
  config, not a new config primitive. _Avoid_: custom function, derived function.
- **Documentation link** — an optional typed link (relation IsDocumentedBy) from
  the Derived Sample to an ELN document, typically a standard operating procedure,
  captured during an Operation's documentation step.
- **Remembered process values** — a per-user, per-Process-name bundle of the values
  a user opted to keep (Template choice, Documentation link, and the amounts). Saved
  only when the user opts in for that run, and re-applied when that Process name is
  next used; the most-recently-remembered Process name is also pre-filled on the next
  run. Replaces the older per-item remembered defaults.

## RSpace inventory Link fields

Ubiquitous language for the inventory "Related Items" Link-field feature
(RSDEV-1131): the link card, its target-state pills, and the Open action.

- **Link target**:
The record an inventory Link field points at, identified by its Global ID.
Either an Inventory item (SA/SS/IC/IN/IT) or an ELN record (SD/NB/GL).
_Avoid_: linked item, destination

- **Target deleted (pill)**:
The link target currently exists in a soft-deleted state and is readable by
the viewer. Only readable targets can surface this state at all.
_Avoid_: trashed, removed

- **No access (pill)**:
The link target is not readable by the current viewer, for whatever reason:
unshared after the link was made, never shared with this viewer, or
hard-deleted by another owner. The system deliberately does not distinguish
these causes, and the state is indistinguishable from "record does not exist"
so that existence is never disclosed (see ADR-0002).
_Avoid_: Item unshared, unshared, hidden

- **Open (link card action)**:
Navigates to the link target. Offered only when following it lands somewhere
useful: removed for deleted or inaccessible ELN targets (their routes produce
error pages), kept for deleted Inventory targets (the trash viewer works) and
for all readable targets.
_Avoid_: view, go to
