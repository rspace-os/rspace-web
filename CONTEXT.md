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

## Platform Configuration

Canonical terms for Feature Flags. Implementation and operational details live
in `DevDocs/adr/0003-feature-flags.md`.

**Feature Flag**:
An internal boolean control for rollout, kill switches, and temporary changes.
It is never an authorization, licensing, tenant-isolation, or secrecy boundary.
_Avoid_: sysadmin setting, deployment property, app setting, toggle

**Feature Flag Definition**:
The manifest entry that declares a flag's identity and default state.
_Avoid_: database row, property key, frontend constant

**Feature Flag Manifest**:
The checked-in JSONC source of Feature Flag Definitions, read directly at
runtime and used to generate backend constants and frontend types.
_Avoid_: Java enum, TypeScript constant list, database seed

**Feature Flag Baseline**:
The instance-wide value before a User Feature Flag Override is applied.
Anonymous requests always receive this value.
_Avoid_: global final value, deployment setting

**User Feature Flag Override**:
An explicitly stored per-user value that replaces the Feature Flag Baseline for
that user. Without one, the baseline applies.
_Avoid_: targeted rollout, group override, community override

**Feature Flag Override Permission**:
The capability to set one's own overrides. It applies to every Feature Flag,
not selected flags.
_Avoid_: special user, per-flag permission, sysadmin setting

**Feature Flag State Map**:
The API response containing each flag's evaluated value, baseline, source, and
override availability.
_Avoid_: requested flag subset, private flag list

**Retired Feature Flag**:
A flag removed from the manifest. Startup removes its stored baseline and user
overrides.
_Avoid_: deprecated flag, disabled forever, archived flag

**Expired Feature Flag**:
A flag whose `expires` date has passed. It fails CI validation but not startup.
_Avoid_: runtime-failing flag, startup blocker
