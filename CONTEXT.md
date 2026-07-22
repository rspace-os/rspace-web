# Context Glossary

The shared, canonical vocabulary for this project. Terms are added as they are
resolved during design. This file is a glossary only — no implementation details.

## Internationalization (i18n)

- **Canonical translation catalog** — i18next JSON. The runtime and
  translator-facing catalog format shared by the frontend and the backend's
  `JsonMessageSource`. English (`en-US`) text is authored in the base JSON;
  translated locale JSON is owned by the future translation workflow.
- **Namespace** — a named slice of translations, one per product module
  (`workspace`, `gallery`, `inventory`, `groups`, `dashboard`, `admin`, `apps`,
  `system`, `public`) plus a shared `common`. Loaded independently.
- **Base file** — the English (`en-US`) file for a namespace. The monolingual
  source/template every other locale is translated against. Locale codes are
  region-qualified BCP 47 and hyphenated (`en-US`, `zh-TW`, `zh-HK`) in catalog
  paths and at runtime.
- **Key** — a stable structured dot-notation identifier (`ns:section.name`) that
  names a string independently of its wording. Distinct from the **default
  value** stored in the base catalog.
- **Default value** — the English source string stored in the `en-US` base file.
  Mandatory for every key; copy changes start in the canonical base JSON.
- **ICU MessageFormat** — the message syntax used for all keys (plurals, gender/
  `select`, ordinals, inline number/date formatting).
- **Extraction** — the build step (via `i18next-cli`) that scans code for keys,
  adds missing entries to the `en-US` base files, and identifies unused keys.
  The same catalogs generate the TypeScript key types.
- **Ratchet** — the per-module enforcement progression: a converted module flips
  its `noJsxLiterals` lint rule to `error`, so the gated (fully-converted)
  surface only ever grows.
- **No-orchestration gap** — the period before Weblate is connected. While the
  product is English-only, this gap is invisible: every string falls back to its
  English default.
- **Locale** — a single BCP 47 tag (e.g. `de-DE`) that controls both the UI
  language (which translation catalog renders) and regional formatting (dates,
  numbers). One value, two effects; they are not independently configurable.
- **CSV-export carve-out** — machine-readable CSV exports are always formatted
  as `en-US` regardless of anyone's locale, so downstream parsers never see
  locale-dependent decimal separators or date formats.
- **First day of week** — fixed instance-wide, not part of a user's locale
  choice.
- **Bundled locale** — a locale whose translation catalog ships with the
  release. Only bundled locales can be allowed by a sysadmin.
- **Allowed set** — the sysadmin-chosen subset of bundled locales users may
  pick from. Always contains the instance default; never empty.
- **Instance default** — the locale served when no valid user choice applies,
  including to all anonymous visitors.
- **Effective locale** — the locale actually served: the user's stored choice
  if it is in the allowed set, otherwise the instance default. A stored choice
  outside the allowed set is kept (not erased) and springs back if re-allowed.
