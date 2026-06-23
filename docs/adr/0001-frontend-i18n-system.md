# Frontend i18n system (react-i18next + ICU, JSON-canonical, Weblate-ready)

Status: accepted

## Context

The React frontend (~900 `.tsx` files, 1,300+ TypeScript/TSX files overall) is
100% hard-coded English with no i18n infrastructure. The backend already
externalises strings via Spring `MessageSource` (~1,527 keys across ~14
`.properties` bundles, consumed by ~185 JSPs). We want one translation system,
with react-i18next rendering on the frontend, Weblate as the eventual
translation service (timing unknown), a
committed broad + morphologically-diverse target language set (incl. RTL
languages such as Arabic/Urdu), and the whole codebase slated to become React
over time.

## Decision

- **One catalog format, JSON-canonical.** i18next JSON is the runtime,
  translator-facing, and future cross-stack catalog format. For frontend English
  copy, the `en-US` JSON base catalog is generated from inline `defaultValue`s;
  translated locale JSON is owned by the future translation workflow. Spring
  `.properties` become a *generated artifact*. Existing backend keys stay
  untouched on day one and migrate to JSON only as their JSP is Reactified.
- **ICU MessageFormat from day one** via `i18next-icu` (set globally for all
  keys). Chosen over native i18next because the committed target set needs inline
  gender/`select` and ordinals; native covers all CLDR plurals but only fakes
  `select` via `context`, and a later native→ICU switch is a near-global rewrite.
- **Structured dot-notation keys** (`t('ns:section.key')`), consistent with the
  existing `.properties` convention. **No inline English in code** (decision
  2026-06-23, reversing the earlier mandatory-`defaultValue` rule): no
  `defaultValue` on `t()`, no `defaults` on `<Trans>`. English source text is
  authored directly in the `en-US` catalogs and lives ONLY there. Rationale: keep
  copy out of the JS bundle, and keep `t()`/`<Trans>` React-Compiler-friendly
  (inline-English Trans forms are the RC-fragile ones — see below). `i18next-cli
  extract` adds new keys with an empty primary value (author the English in the
  catalog) and **preserves** existing primary values (verified — it does not sync
  from code unless run with `--sync-primary`); `status` reports untranslated keys.
- **`<Trans>` markup + React Compiler.** Sentences with inline markup use plain
  `<Trans i18nKey components={{…}}>` (no `defaults`, no children); the template
  with placeholder tags lives in the catalog. React Compiler is **not active**
  today, so this is safe now. react-i18next's RC-safe ICU options — `IcuTrans`
  and the `icu.macro` babel macro — both reintroduce inline English
  (`IcuTrans` *requires* a `defaultTranslation` prop; `icu.macro` puts English in
  children) and `icu.macro` also reintroduces Babel into the oxc/rolldown build.
  Both conflict with the no-inline-English rule, so neither is adopted now.
  Decision is deferred to React-Compiler adoption, when the trade-off becomes
  real (options then: accept inline English via `IcuTrans`, reintroduce Babel for
  `icu.macro`, or a per-component compiler opt-out).
- **Per-module namespaces** mirroring the backend bundles
  (`workspace, gallery, inventory, groups, dashboard, admin, apps, system, public`)
  plus a shared `common` namespace.
- **Delivery:** JSON bundled into the Vite build, dynamic-imported per namespace;
  no runtime fetch endpoint. Only `common` loads during i18n init. Page/module
  namespaces load explicitly via `useTranslation(ns)` or an `I18nRoot`
  namespace-preload prop, so adding all namespaces to `init({ ns })` does not
  defeat lazy loading.
- **Init:** one shared `i18n.ts` module imported by every entry bundle; if
  Suspense is enabled, boundaries are required at every non-test root render
  site (currently 52 renders across 51 Vite bundle entries, including legacy
  island mounts and dynamic one-off roots).
- **Extraction:** `i18next-cli` (the maintained successor to the EOL
  `i18next-parser`) reconciles keys (no code defaults) with the `en-US` catalogs,
  adding new keys empty and preserving existing English. `pnpm run i18n:check`
  (`extract --ci`) fails CI on any uncommitted catalog drift (missing/orphan keys).
- **Type-safe keys:** `i18next-cli` generates `resources.d.ts`; the `i18next`
  module is augmented so mistyped keys/namespaces fail `tsc`.
- **Regression gate:** Biome's native `noJsxLiterals` is ratcheted to `error`
  per-module via `overrides` globs as each module is converted, with
  `noStrings: true` so JSX expression strings and attributes (`title`,
  `aria-label`, `placeholder`, MUI `slotProps`, etc.) are covered. This is still
  not a full user-text proof: non-JSX text such as string constants,
  object/option literals (`{ label: 'Yes' }`), alert/snackbar messages, thrown
  `Error` text, and legacy event payloads must be covered by `i18next-cli lint`
  (or a custom scanner if CLI strictness is insufficient) plus review.
- **Region-qualified BCP 47 locale codes.** Locales are language-REGION,
  hyphenated, at runtime (`en-US`, `zh-TW`, `zh-HK`) because `i18next-icu`/`Intl`
  only parse BCP 47 tags. The underscore form (`en_US`) is the Java `.properties`
  convention and is produced only by the deferred generator (`en-US` -> `en_US`);
  it never appears in the frontend runtime or JSON filenames.
- **Locale persistence deferred.** English-only now (`lng: 'en-US', fallbackLng:
  'en-US'`). `User.locale`, a language switcher, and a Spring `LocaleResolver`
  are added only when the first real second locale lands.
- **RTL deferred, conventions adopted.** No MUI `direction`/stylis-rtl wiring
  now; converted code uses CSS logical properties and avoids physical
  `left`/`right` so RTL becomes a theme flip, not a rewrite.
- **Weblate deferred but unblocking.** Commit `en-US` JSON in the standard
  `locales/{lng}/{namespace}.json` layout (`en-US` = monolingual base file,
  one namespace = one future component, ICU flag per component). Generated
  `.properties` are never Weblate components. Future import is config-only.
- **Tests render default English by default.** `react-i18next` is mocked so
  `t(key, { defaultValue })` returns `defaultValue ?? key`; `<Trans>` renders its
  fallback children/default. User-facing tests keep asserting accessible text,
  while targeted i18n tests assert keys and interpolation args through the mock's
  call history.
- **Rollout:** land a foundation + pilot PR first (deps, `i18n.ts`, namespace
  loading helper, Biome/i18next lint scaffold, CI extraction + unused-key + type
  gen, `locales/` layout). Then convert modules in follow-up PRs/commits that
  each add their namespace JSON and flip their `noJsxLiterals` override. Avoid a
  full-front-end mega-PR unless external constraints force it.

## Considered options (rejected)

- **Native i18next format:** simpler, full ergonomics, covers all CLDR plurals —
  but no clean inline gender/`select`, and switching to ICU later is a
  near-global migration. Rejected given the committed diverse target set.
- **Mozilla Fluent:** incompatible with the react-i18next runtime (1-level
  nesting vs i18next trees; real home is `@fluent/react`) and has no path through
  the Java `.properties` generator. Ruled out.
- **`.properties`-canonical / generate JSON:** lower short-term backend risk but
  fights the React end-state; we'd flip canonical anyway.
- **Natural-language keys (English as key):** free fallback but editing copy
  orphans translations and produces ugly generated `.properties`; poor fit at
  this scale.
- **Global raw-key test mock:** rejected because it removes coverage of
  accessible names, default copy, and basic interpolation/rendering. Raw-key
  assertions remain available in targeted i18n tests.

## Consequences

- A full-front-end conversion will conflict-rot against `main`; the rollout
  therefore separates foundation+pilot from module conversions so the toolchain
  can land early and failures are reversible by module.
- "Generate all namespaces to `.properties`" was chosen over a dedicated
  `server` namespace: perf cost is nil (Spring `MessageSource` is an O(1)
  HashMap), accepting translator-noise in the backend bundle for generator
  simplicity. The generator itself is deferred (no backend consumer while
  English-only).
- ICU keys use `intl-messageformat` semantics, not i18next's plural/context
  sugar.
- **The backend cannot render ICU plural/select with stock Spring.**
  `ReloadableResourceBundleMessageSource` uses `java.text.MessageFormat`, which
  supports `{0}` / `{0,number}` / `{0,choice}` but **not** `{count,plural,…}` or
  `{x,select,…}` (those are ICU4J, `com.ibm.icu.text.MessageFormat`). Before the
  JSON→`.properties` generator ships, the backend message source must be wired to
  ICU4J for any generated string using ICU plural/select. Does not affect the
  foundation PR (generator deferred; backend stays English-only).
- **`i18next-cli` caveats to verify/track:** the extractor must be configured for
  ICU mode so ICU nested-brace plurals are not misparsed as interpolation; test
  files/stories/comments must not create catalog keys; open issue #152
  (defaultValue not applied to the primary language on extraction) should be
  confirmed fixed/worked-around; and throw-on-missing-`defaultValue` strictness
  plus non-JSX hardcoded-string detection must be confirmed expressible via its
  `lint` command or covered by a small custom scanner.
- **Open question — generated vs existing bundles.** "Generate all namespaces to
  `.properties`" leaves undefined how generated files relate to the existing
  hand-maintained bundles (`ApplicationResources.properties` et al.): two writers
  of the same logical bundle and key-collision rules are unresolved. Deferred with
  the generator, but must be settled before it ships.
- For interpolated/ICU strings, the default-value test mock gives ordinary UI
  tests readable English; targeted i18n tests check the key and interpolation
  args via the mock's call args. Full ICU plural/select behavior is covered by a
  small real-i18next integration test suite, not every component test.
