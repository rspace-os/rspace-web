---
name: rebrand-integration
description: Rebrand an existing RSpace integration when the third-party service changes its name (e.g. Clustermarket → Calira). Swaps the TinyMCE toolbar icon SVG and the Apps-page logo SVG, updates LOGO_COLOR to match the new logo background, and replaces every user-facing instance of the old brand name with the new one across the frontend, JSPs, TinyMCE plugin, and i18n property bundles. Use when the user asks to rebrand, rename, or migrate an integration's display name and icons (e.g. "rebrand Foo to Bar", "migrate <integration> to <new name>", "we're renaming X"). Do not use this skill to add a new integration (use `rspace-empty-integration`), to rename Java packages/classes/DB tables/URL paths/integration keys, or to make purely cosmetic icon-only changes.
---

# Rebrand Integration Skill

Rebrand an existing integration in-place when the third-party service changes
its name. Swap the two SVG icons (TinyMCE toolbar + Apps-page logo), update the
brand colour, and replace every user-facing occurrence of the old brand name
with the new one. Code identifiers, URLs, file paths, integration keys, event
names, CSS/HTML IDs, and Java packages/classes are deliberately left unchanged
so that backend wiring, OAuth redirects, persisted credentials and existing
documents continue to work.

## Inputs to gather from the user

Ask for all four up front. Do not start editing until all are supplied.

1. `<OriginalName>` — PascalCase of the integration's current name (e.g. `Clustermarket`).
2. `<RebrandedName>` — PascalCase of the new name (e.g. `Calira`).
3. `<TINYMCE_ICON_SVG>` — Full SVG markup for the new TinyMCE toolbar icon (the small 20×20-ish icon shown in the editor toolbar). Supplied as text.
4. `<LOGO_SVG>` — Full SVG markup for the new Apps-page card logo (typically a square brand tile). Supplied as text.

Derived values:
- `<original>` = `<OriginalName>` lowercased — used in file paths and existing identifiers (these are **not** renamed).
- `<rebranded>` = `<RebrandedName>` lowercased — informational only; do not propagate into paths or identifiers.

## Workflow

Make all edits sequentially. **Commit nothing.** Verify each step before
moving on.

### 1. Verify the integration exists

Confirm both of these exist:

- `src/main/webapp/ui/src/assets/branding/<original>/logo.svg`
- `src/main/webapp/ui/src/eln/apps/integrations/<OriginalName>.tsx`

If either is missing, abort and ask the user whether they meant to add a new
integration (`rspace-empty-integration`) instead.

### 2. Replace the TinyMCE toolbar icon

First, normalize `<TINYMCE_ICON_SVG>`:

- The user may paste extra text before or after the SVG. Extract only the
  substring from the first `<svg` to the matching `</svg>` (inclusive) and
  discard everything else.
- Force the toolbar icon to render at 20×20: on the root `<svg>` element, set
  `height="20px"` and `width="20px"`. Overwrite existing `height`/`width`
  values if present; add the attributes if missing. Leave `viewBox` alone.

Then, in `src/main/webapp/scripts/tinymce/tinymce5109/icons/custom_icons/icons.js`,
locate the entry whose key is `'<original>':`. Its value may be a single-quoted
string, a multi-line `'…' + '…'` concatenation, or a backticked template
literal — handle whichever is present.

Replace the value with the normalized `<TINYMCE_ICON_SVG>` wrapped in
backticks. Keep the key and trailing comma intact. Preserve the entry's
existing position in the object.

### 3. Replace the Apps-page logo

First, normalize `<LOGO_SVG>`:

- The user may paste extra text before or after the SVG. Extract only the
  substring from the first `<svg` to the matching `</svg>` (inclusive) and
  discard everything else.
- Do **not** modify `viewBox`, `width`, `height`, or any other attribute on
  the root `<svg>`. Whatever the supplied SVG declares is what gets written
  to disk.

Then overwrite `src/main/webapp/ui/src/assets/branding/<original>/logo.svg`
with the normalized `<LOGO_SVG>`.

### 4. Update LOGO_COLOR to match the new logo background

Extract the dominant background colour from `<LOGO_SVG>`:

- Prefer the `fill` of the first full-canvas `<rect width="…" height="…">` (the
  Apps card style places the brand colour on a background rect).
- If there is no background rect, fall back to the `fill` of the outermost
  background `<path>` that covers the viewBox.
- If the colour is defined via a `<style>` class, resolve the class to its hex.

Convert that hex to HSL (rounded integers, hue 0–360, saturation/lightness
0–100) and overwrite the constant in
`src/main/webapp/ui/src/assets/branding/<original>/index.ts`:

```ts
/**
 * The colour used in the background of the logo.
 */
export const LOGO_COLOR = {
  hue: <H>,
  saturation: <S>,
  lightness: <L>,
};
```

If the file exports more than `LOGO_COLOR` (some integrations also export
`ACCENT_COLOR`), update only `LOGO_COLOR` and leave the rest alone — flag
`ACCENT_COLOR` as a manual follow-up in the final report.

If no clear background colour can be extracted, stop and ask the user for the
HSL values.

### 5. Replace user-facing text

For each file in the list below (skip any that do not exist), replace every
**case-sensitive** occurrence of `<OriginalName>` (PascalCase) with
`<RebrandedName>` **only** where it appears in user-visible display text:

- Alert / toast / `mkAlert` `message:` strings
- JSX text content
- JSX attribute strings that render to UI: `name=`, `tooltip=`, `text:`,
  `title:`, `helpLinkText=`, `usageText=`, `explanatoryText=`
- HTML `<title>` elements, `<button>` text, `<p>` / `<div>` body text in JSPs
- The right-hand value of entries in `apps.properties` and `system.properties`
  (never the property key)
- TinyMCE plugin literal strings: `title:`, `tooltip:`, `text:`,
  `apprise('…')` user-message arguments
- Setup / instructions / error-message copy

Leave alone:

- Function, component, type, interface, hook, constant, and variable names
  (e.g. `function Clustermarket`, `useClustermarketEndpoint`,
  `ClustermarketArgs`, `ClustermarketIcon`).
- The integration key in **UPPERCASE** form (e.g. `CLUSTERMARKET`,
  `CLUSTERMARKET_CONNECTED`) — used as a map key, event name, and discriminant.
- The **lowercase** form (e.g. `clustermarket`, `clustermarketSearchOrder`,
  `optClustermarket`) — used in URL paths, CSS classes, HTML `id`s,
  `data-tableSource`, settings keys (`clustermarket.web.url`,
  `clustermarket.api.url`), event names (`clustermarket-insert`), and Webpack
  bundle names. Leaving lowercase alone is what keeps the rebrand non-breaking.
- Property-bundle keys (left of `=`) and Liquibase / SQL identifiers.
- Comments: `//`, `/* … */`, JSDoc, `<!-- … -->`, JSX `{/* … */}`. These are
  developer-facing.
- Log messages (`console.log/warn/error`, `log.info(...)`, `log.warn(...)`,
  `log.error(...)`).
- Analytics / tracking event names (`RS.trackEvent('Fetch<Original>...'),`).
- The `website=` prop value — it is the actual domain, not a brand label. Flag
  as manual follow-up.
- The `docLink=` prop and the matching slug in
  `src/main/webapp/ui/src/assets/DocLinks.ts`. Flag as manual follow-up.
- Java backend strings (e.g. `setDisplayName("RSpace Clustermarket access
  token")`). Flag as manual follow-up.
- Test fixtures and snapshots: anything under `__tests__/`, `*.test.*`,
  `*.snap`, and JSON files under `api_snaphots/` (sic).
- README / `*.md` files under the integration's folders.

Files to scan (each one is optional — skip if absent):

- `src/main/webapp/ui/src/eln/apps/integrations/<OriginalName>.tsx`
- `src/main/webapp/ui/src/eln/apps/use<OriginalName>.tsx`
- `src/main/webapp/ui/src/eln/apps/use<OriginalName>.ts`
- `src/main/webapp/ui/src/tinyMCE/<original>/ErrorView.tsx`
- `src/main/webapp/ui/src/tinyMCE/<original>/<OriginalName>.tsx`
- `src/main/webapp/ui/src/tinyMCE/<original>/index.js`
- `src/main/webapp/WEB-INF/pages/connect/<original>/connected.jsp`
- `src/main/webapp/WEB-INF/pages/connect/<original>/connect.jsp`
- `src/main/webapp/scripts/externalTinymcePlugins/<original>/dialog.html`
- `src/main/webapp/scripts/externalTinymcePlugins/<original>/plugin.min.js`
- `src/main/resources/bundles/apps/apps.properties`
- `src/main/resources/bundles/system/system.properties`

> `plugin.min.js` is easy to forget — it is despite its name a hand-edited
> source file that contains several user-visible strings (`title:`,
> `tooltip:`, `text:`, and the `apprise(...)` failure messages). Do not skip
> it.

For each file, decide each match line-by-line. When in doubt, look at how the
string is used: if it ends up in the rendered DOM, change it; if it's a key,
identifier, URL, log line, or analytics event, leave it.

### 6. Sanity check

Run a final grep for the capitalised old name across the touched files plus a
broad sweep of the integration's directories. Every remaining hit must be a
comment, JSDoc, log message, analytics event name, test fixture, README, or
explicitly out-of-scope per step 5.

```bash
grep -rn "<OriginalName>" \
  src/main/webapp/ui/src/eln/apps/integrations/<OriginalName>.tsx \
  src/main/webapp/ui/src/eln/apps/use<OriginalName>.* \
  src/main/webapp/ui/src/tinyMCE/<original>/ \
  src/main/webapp/WEB-INF/pages/connect/<original>/ \
  src/main/webapp/scripts/externalTinymcePlugins/<original>/ \
  src/main/resources/bundles/apps/apps.properties \
  src/main/resources/bundles/system/system.properties \
  2>/dev/null
```

Then run a `git status` / `git diff --stat` to confirm only the expected files
are dirty.

### 7. Report

Output:

- A bullet list of files changed, with one short note per file describing
  what was edited (icon swap / logo swap / LOGO_COLOR / N display strings).
- The old and new HSL `LOGO_COLOR` values.
- A checklist of manual follow-ups for the developer to review **and decide
  on** — the skill does not change these:
  - `website=` prop value in
    `src/main/webapp/ui/src/eln/apps/integrations/<OriginalName>.tsx` — does
    the actual domain change?
  - `docLink=` slug in the same file and the matching entry in
    `src/main/webapp/ui/src/assets/DocLinks.ts` — point to the new docs page?
  - Java OAuth token display name in
    `src/main/java/com/researchspace/webapp/integrations/<original>/<OriginalName>OAuthService.java`
    (`setDisplayName(...)`).
  - Test fixtures (e.g.
    `src/main/webapp/ui/src/eln/apps/__tests__/allIntegrationsAreDisabled.json`
    `displayName`) — refresh once the backend display name lands.
  - Any `ACCENT_COLOR` block in `branding/<original>/index.ts` if the file
    exports one.
  - Folder names, package names, class names, DB tables, integration key
    (UPPERCASE), system-property keys, URL paths, and TinyMCE plugin/menu
    identifiers all still reference `<original>`. A full rename of those is
    a separate, much more invasive change and is out of scope here.

**Do not commit.** Surface the summary and let the user review and commit.

## Out of scope

- Adding a brand-new integration (use `rspace-empty-integration`).
- Renaming Java packages, classes, or DB tables.
- Renaming URL paths, integration keys, system-property keys, event names,
  Webpack bundle names, CSS classes, or HTML IDs.
- Auto-updating `DocLinks.ts` to a new vendor docs URL — flag as manual
  follow-up.
- Changing OAuth client / secret / `*.api.url` / `*.web.url` property values
  in deployment property files.
- Translating the new name into other locales.
