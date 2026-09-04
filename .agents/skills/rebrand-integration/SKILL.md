---
name: rebrand-integration
description: Rename an existing RSpace integration's user-facing name and icons while preserving its code identifiers, paths, keys, and wiring. Use when a third-party service changes its name. Do not use for new integrations or icon-only changes.
---

# Rebrand an integration

Update the user-facing name, TinyMCE icon, Apps logo, and brand colour in place.
Preserve identifiers and wiring so OAuth redirects, credentials, and existing
documents keep working. Make edits sequentially, verify each step, and do not commit.

## Inputs

Get all four before editing:

- `<OriginalName>` and `<RebrandedName>` in PascalCase.
- `<TINYMCE_ICON_SVG>` and `<LOGO_SVG>` as full SVG markup supplied as text.

`<original>` is the lowercase original name used in existing paths and keys.

## Icons and colour

1. Confirm these files exist. If either is missing, stop and ask whether the
   user intended a new integration through `rspace-empty-integration`.
   - `src/main/webapp/ui/src/assets/branding/<original>/logo.svg`
   - `src/main/webapp/ui/src/eln/apps/integrations/<OriginalName>.tsx`
2. Extract each supplied SVG from its first `<svg` through the matching
   `</svg>`, discarding surrounding text.
3. For the toolbar SVG, set root `height="20px"` and `width="20px"`; preserve
   `viewBox`. Replace the entire `'<original>':` value in
   `src/main/webapp/scripts/tinymce/tinymce5109/icons/custom_icons/icons.js`
   with a backtick string. Handle existing strings, concatenations, or template
   literals; preserve the key, comma, and entry position.
4. Write the logo SVG to `src/main/webapp/ui/src/assets/branding/<original>/logo.svg`
   without changing any SVG attributes.
5. Extract its background fill from the first full-canvas rect, falling back
   to the outermost path covering the viewBox. Resolve style classes to hex.
   Convert to rounded integer HSL, hue 0–360 and saturation/lightness 0–100.
   Update only `LOGO_COLOR` in
   `src/main/webapp/ui/src/assets/branding/<original>/index.ts`.
   If the background is unclear, ask for HSL values. Leave other exports such
   as `ACCENT_COLOR` unchanged and flag them for review.

## Display text

Replace case-sensitive `<OriginalName>` with `<RebrandedName>` in visible JSX,
UI props, alerts/toasts, HTML/JSP titles and body text, bundle values, TinyMCE
`title:` / `tooltip:` / `text:` / `apprise(...)`, and instructions or errors.
Inspect each match's use before changing it.

Scan these files; skip absent paths:

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

`plugin.min.js` is hand-edited source despite its name; include its visible strings.

Preserve:

- Code identifiers, packages/classes, paths, URLs, integration keys, events,
  analytics names, bundle names, CSS classes, HTML IDs, property keys, SQL and
  Liquibase identifiers, including uppercase and lowercase brand forms.
- Comments, logs, README/Markdown files, `__tests__/`, `*.test.*`, `*.snap`,
  and JSON under `api_snaphots/`.
- `website=`, `docLink=`, and the matching slug in
  `src/main/webapp/ui/src/assets/DocLinks.ts`.
- Java strings, including OAuth `setDisplayName(...)` values.
- Deployment OAuth credentials and `*.api.url` / `*.web.url` properties.
- Other locales.

## Verify and report

Use `rg` to search the old PascalCase name in touched files and integration
directories. Every remaining match must fit an exclusion above. Check
`git status` and `git diff --stat` for unintended edits; follow `AGENTS.md`
for code checks and runtime verification.

Report files changed with brief edit descriptions and old/new HSL values.
List manual decisions for website, docs slug, Java OAuth token display name,
test fixtures after backend display-name changes, and any `ACCENT_COLOR`.
The OAuth service is typically
`src/main/java/com/researchspace/webapp/integrations/<original>/<OriginalName>OAuthService.java`.
Note that a full identifier, path, package, or schema rename is separate work.
Leave changes uncommitted for review.
