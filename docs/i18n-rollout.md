# i18n full-app rollout

Goal: every user-facing string in the frontend is managed by i18next (keys-only
in code; English authored in the `en-US` catalogs). See
`docs/adr/0001-frontend-i18n-system.md` for the architecture.

## Per-module protocol (one commit per module)

For each module (namespace), in order:

1. Convert **trivial** strings to bare `t("section.key")` (keys-only, no
   `defaultValue`). Trivial = a standalone string: label, heading, button,
   tooltip, `placeholder`, `aria-label`, `alt`/`title`, option label,
   snackbar/alert/validation message.
2. `useTranslation("<ns>")` per component; cross-namespace components use
   `useTranslation(["<ns>", "common"])`. Wrap each root in `<I18nRoot>` (the
   `namespaces` prop is an optional preload hint, not required).
3. Author English in `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/<ns>.json`,
   then `pnpm run i18n:extract` and `pnpm run i18n:types`.
4. Add the module's glob to the `noJsxLiterals` override in `biome.jsonc`
   (bare rule; add unit symbols like `˚` to `allowedStrings` only).
5. Gate green: `pnpm run tsc`, `pnpm run lint`, `pnpm run i18n:check`,
   relevant tests. Commit the module.

## Deferred for human review (non-trivial / markup)

Anything that is NOT a standalone string is **deferred** — do not convert it
during the automated pass; record it here and a human reviews the whole list
after all modules are done. Defer when a string:

- contains inline markup/components mid-sentence (a `<Link>`/`<a>`/`<b>`/`<br/>`
  inside a sentence) — the old `<Trans>` cases;
- needs restructuring that changes translatability (splitting a sentence around
  a link/value, where word order matters across locales);
- needs pluralization/`select`/gender judgment (ICU).

### Review queue

| Module | File | Location | Why deferred |
|---|---|---|---|
| about | components/AppBar/AboutRSpaceDialog.tsx | support/account email lines, license `<br/>` | Already converted via label+link / two-key split; review whether the split reads well for RTL/other word orders, or should be ICU. |
| public | components/PublicPages/IdentifierPublicPage.tsx | (none — all strings were standalone) | — |

## Module order

public ✓ · about ✓ · then: apps · groups · dashboard · gallery · workspace ·
inventory · admin/system · common (shared, last — touched by many).
