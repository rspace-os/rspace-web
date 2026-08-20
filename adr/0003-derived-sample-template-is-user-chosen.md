# 3. The derived sample's template is user-chosen (none / any / from the origin's sample)

Date: 2026-07-10

## Status

Accepted. Revises the earlier "ad-hoc, template-less only" stance recorded in the
implementation plan and `DevDocs/DeveloperNotes/InventoryOperationWizard.md`.

## Context

The framework was first specified to always create an **ad-hoc, template-less**
sample (the wizard sent `templateId: null`). A later requirement reversed this:
the wizard must let the user optionally choose the new sample's template, with
three choices, plus a per-user "remember my choice" option:

- **(b) No template** — an ad-hoc sample (the original behaviour, still the
  default).
- **(a) Any existing template** — the user picks a sample template.
- **(c) A template created from the origin's parent Sample** — capture the
  structure of the sample the origin subsample belongs to as a new template, and
  create the derived sample from it.

The building blocks already exist: `POST /api/inventory/v1/samples` creates a
sample from a `templateId` (copying the template's fields), the `TemplatePicker`
lists and selects templates, and a template can be created from a sample entirely
client-side by POSTing that sample's fields to `POST /api/inventory/v1/sampleTemplates`.

## Decision

- The wizard gains a **template step** offering the three choices above and a
  "remember this choice" checkbox. It resolves to a single `templateId` (or null):
  - (b) → `null`.
  - (a) → the picked template's id.
  - (c) → **reuse the origin's parent-sample's own template** when it has one
    (`origin.sample.templateId`), and only create a new template from the sample
    (via `POST /sampleTemplates`) when it is template-less. This avoids creating a
    duplicate template on every run.
- For option (a), the wizard **checks the chosen template up front** and blocks the
  selection (with an error naming the offending fields) when the template has
  mandatory fields that have no default value, since the wizard cannot supply those
  values. The user is warned in the template step, never at submit.
- `buildOperationRequest` carries that `templateId` into `newSample.templateId`.
- **The backend `/operations` endpoint is unchanged.** It already forwards
  `newSample.templateId` to the sample-create manager, so options (a)/(b)/(c) all
  reduce to "send a `templateId` (or null)". No new backend code, consistent with
  adr/0001.
- "Remember" is a per-user, per-operation preference stored in `UI_JSON_SETTINGS`
  (via `useUiPreference`, keyed by operation), mirroring the documentation-link
  default. No backend change and no per-operation code.

## Consequences

- Option (c) reuses the origin sample's existing template, so no new template is
  created in the common case. A new template is created only when the origin sample
  is itself template-less; that creation is a separate call before the atomic
  operation, so a failure after it leaves an unused template (harmless, deletable).
  The operation itself remains atomic (adr/0001).
- Option (a) cannot silently fail at submit: a template whose mandatory fields lack
  defaults is blocked in the template step with a clear message. Collecting values
  for such template fields in the wizard is deferred; for now the user must pick a
  different template or use option (c).

## Alternatives considered

- **Keep ad-hoc only.** Rejected: the new requirement needs templates.
- **Make option (c) atomic in the backend** (a request field that creates the
  template in the operation's transaction). Rejected for now: it adds backend code
  and a manager call for a marginal atomicity gain; a stray template on failure is
  cheap. Revisit if orphan templates become a real problem.
